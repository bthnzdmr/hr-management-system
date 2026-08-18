package com.proje.notification.service;

import com.proje.notification.client.EmployeeClient;
import com.proje.notification.client.ServiceTokenProvider;
import com.proje.notification.client.dto.EmployeeSummary;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerLookupServiceTest {

    @Mock
    private EmployeeClient employeeClient;

    @Mock
    private ServiceTokenProvider tokenProvider;

    private SimpleMeterRegistry registry;
    private ManagerLookupService managerLookupService;

    @BeforeEach
    void setUp() {
        // Gercek bir kayit defteri: sahte olsaydi sayaclarin kaydedilip
        // kaydedilmedigi sinanamazdi, yalnizca "increment cagrildi mi" bakilirdi.
        registry = new SimpleMeterRegistry();
        managerLookupService = new ManagerLookupService(employeeClient, tokenProvider, registry);
    }

    /** Bir sonucun o ana kadarki sayimi. */
    private double count(String outcome) {
        return registry.get("hr.manager.lookup").tag("outcome", outcome).counter().count();
    }

    private EmployeeSummary employee(Long id, String email, Long managerId) {
        return new EmployeeSummary(id, email, managerId);
    }

    private FeignException.Unauthorized unauthorized() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/employees/1",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate());
        return new FeignException.Unauthorized("unauthorized", request, null, Collections.emptyMap());
    }

    @Test
    @DisplayName("Returns the manager address by following the manager reference")
    void returnsManagerAddress() {
        when(employeeClient.findById(1L)).thenReturn(employee(1L, "grace@example.com", 9L));
        when(employeeClient.findById(9L)).thenReturn(employee(9L, "boss@example.com", null));

        assertThat(managerLookupService.managerEmail(1L)).contains("boss@example.com");
    }

    @Test
    @DisplayName("Returns nothing when the employee has no manager")
    void returnsNothingWithoutManager() {
        when(employeeClient.findById(1L)).thenReturn(employee(1L, "grace@example.com", null));

        assertThat(managerLookupService.managerEmail(1L)).isEmpty();
        verify(employeeClient, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Returns nothing instead of failing when the call breaks down")
    void swallowsCallFailure() {
        // Zenginlestirme mail gondermenin on kosulu degildir: cagri patlarsa
        // mail CC'siz gitmelidir, hic gitmemesi degil.
        when(employeeClient.findById(1L)).thenThrow(new RuntimeException("connection refused"));

        assertThat(managerLookupService.managerEmail(1L)).isEmpty();
    }

    @Test
    @DisplayName("Renews a rejected token and retries the lookup once")
    void renewsRejectedTokenAndRetriesOnce() {
        when(employeeClient.findById(1L))
                .thenThrow(unauthorized())
                .thenReturn(employee(1L, "grace@example.com", 9L));
        when(employeeClient.findById(9L)).thenReturn(employee(9L, "boss@example.com", null));

        assertThat(managerLookupService.managerEmail(1L)).contains("boss@example.com");
        verify(tokenProvider).invalidate();
    }

    @Test
    @DisplayName("Gives up quietly when the lookup still fails after renewing the token")
    void givesUpAfterSecondFailure() {
        when(employeeClient.findById(1L)).thenThrow(unauthorized()).thenThrow(unauthorized());

        assertThat(managerLookupService.managerEmail(1L)).isEmpty();
        verify(tokenProvider).invalidate();
        verify(employeeClient, times(2)).findById(1L);
    }

    @Test
    @DisplayName("Registers every outcome at startup, before anything happens")
    void registersEveryOutcomeUpFront() {
        // Micrometer bir sayaci ancak DOKUNULDUGUNDA yayinlar. Kaydedilmemis
        // bir seri Prometheus'ta hic gorunmez ve kural sessizce hicbir zaman
        // atesLENMEZDI -- bu projede iki kez yasanmis bir tuzak.
        assertThat(count("found")).isZero();
        assertThat(count("no_manager")).isZero();
        assertThat(count("failed")).isZero();
    }

    @Test
    @DisplayName("Separates a missing manager from a broken call")
    void separatesMissingManagerFromBrokenCall() {
        // Disaridan ikisi de AYNI gorunuyor: mail CC'siz gidiyor. Ayrimi
        // yalnizca bu iki sayac yapiyor ve bugun olculen ariza tam buydu.
        when(employeeClient.findById(1L)).thenReturn(employee(1L, "grace@example.com", null));
        managerLookupService.managerEmail(1L);

        when(employeeClient.findById(2L)).thenThrow(new RuntimeException("connection refused"));
        managerLookupService.managerEmail(2L);

        assertThat(count("no_manager")).isEqualTo(1);
        assertThat(count("failed")).isEqualTo(1);
        assertThat(count("found")).isZero();
    }

    @Test
    @DisplayName("Counts a lookup that succeeds only after the token is renewed")
    void countsSuccessAfterTokenRenewal() {
        // Yenilemeden SONRA basarili olan cagri da bir basaridir; ayri bir
        // yolda oldugu icin sayilmasi kolayca unutulurdu.
        when(employeeClient.findById(1L))
                .thenThrow(unauthorized())
                .thenReturn(employee(1L, "grace@example.com", 9L));
        when(employeeClient.findById(9L)).thenReturn(employee(9L, "boss@example.com", null));

        managerLookupService.managerEmail(1L);

        assertThat(count("found")).isEqualTo(1);
        assertThat(count("failed")).isZero();
    }
}
