package com.proje.notification.service;

import com.proje.notification.client.EmployeeClient;
import com.proje.notification.client.ServiceTokenProvider;
import com.proje.notification.client.dto.EmployeeSummary;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private ManagerLookupService managerLookupService;

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
}
