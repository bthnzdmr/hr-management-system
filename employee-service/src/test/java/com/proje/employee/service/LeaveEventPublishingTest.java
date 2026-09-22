package com.proje.employee.service;

import com.proje.employee.dto.LeaveBalanceResponse;
import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.LeaveRequest;
import com.proje.employee.entity.LeaveType;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import com.proje.employee.event.LeaveEvent;
import com.proje.employee.event.LeaveEventType;
import com.proje.employee.event.OutboxWriter;
import com.proje.employee.repository.EmployeeRepository;
import com.proje.employee.repository.LeaveRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Izin olaylari yayinlaniyor mu ve yuk DOGRU olgulari tasiyor mu?
 *
 * <p>Bu davranis uzun sure HIC yoktu: bir kisi izin talep ediyor, karar
 * veriliyor ve kendisine hicbir sey bildirilmiyordu.
 */
@ExtendWith(MockitoExtension.class)
class LeaveEventPublishingTest {

    @Mock
    private LeaveRequestRepository leaveRequests;

    @Mock
    private EmployeeRepository employees;

    @Mock
    private EmployeeVisibility visibility;

    @Mock
    private LeaveBalanceService balances;

    @Mock
    private OutboxWriter outbox;

    @Mock
    private NotificationPreferenceService preferences;

    private LeaveRequestService service;
    private Employee grace;
    private Employee ada;
    private User hr;

    @BeforeEach
    void setUp() {
        service = new LeaveRequestService(leaveRequests, employees, visibility, balances, outbox, preferences);

        Department department = new Department("Software Development");
        grace = employee(212L, "Grace", "Hopper", null);
        ada = employee(218L, "Ada", "Lovelace", grace);
        ReflectionTestUtils.setField(grace, "department", department);
        ReflectionTestUtils.setField(ada, "department", department);

        hr = new User("hr@example.com", "hash", Set.of(Role.HR_SPECIALIST));
    }

    private Employee employee(long id, String first, String last, Employee manager) {
        Employee e = new Employee(first, last, first.toLowerCase(java.util.Locale.ROOT) + "@example.com",
                new Department("Software Development"), "Engineer", LocalDate.now());
        ReflectionTestUtils.setField(e, "id", id);
        e.setManager(manager);
        return e;
    }

    private LeaveRequest pendingLeaveOf(Employee owner, User author) {
        LeaveRequest leave = new LeaveRequest(owner, author, LeaveType.ANNUAL,
                LocalDate.of(2032, 3, 10), LocalDate.of(2032, 3, 15), "dentist");
        ReflectionTestUtils.setField(leave, "id", 7L);
        when(leaveRequests.findByIdWithEmployee(7L)).thenReturn(Optional.of(leave));
        return leave;
    }

    private LeaveEvent published() {
        ArgumentCaptor<LeaveEvent> captor = ArgumentCaptor.forClass(LeaveEvent.class);
        verify(outbox).write(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Publishes a request event carrying the manager as a fact, not as an instruction")
    void publishesRequestEvent() {
        when(employees.findByIdForUpdate(218L)).thenReturn(Optional.of(ada));
        when(balances.balanceFor(any(), anyInt())).thenReturn(
                new LeaveBalanceResponse(218L, 2032, 20, 0, 0, 0, 20,
                        LeaveBalanceResponse.Source.DEFAULT));
        when(leaveRequests.saveAndFlush(any())).thenAnswer(call -> {
            LeaveRequest saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });

        service.create(new com.proje.employee.dto.LeaveRequestCreateRequest(
                        218L, LeaveType.ANNUAL,
                        LocalDate.of(2032, 3, 10), LocalDate.of(2032, 3, 15), "dentist"),
                hr, new AccessScope(AccessScope.Kind.ALL, null, false));

        LeaveEvent event = published();

        assertThat(event.eventType()).isEqualTo(LeaveEventType.REQUESTED);
        assertThat(event.employeeEmail()).isEqualTo("ada@example.com");
        // Yonetici adresi YUKTE tasinir; tuketici onu Feign ile ARAMAZ. Olay
        // bir ANIN olgusudur: sonradan aranan yonetici degismis olabilirdi.
        assertThat(event.managerEmail()).isEqualTo("grace@example.com");
        // Aktor talebi ACAN kisidir; Ik baskasi adina kaydedebilir.
        assertThat(event.actorEmail()).isEqualTo("hr@example.com");
    }

    @Test
    @DisplayName("Publishes a decision event whose day count includes the last day")
    void publishesDecisionEvent() {
        pendingLeaveOf(ada, hr);

        service.approve(7L, hr, new AccessScope(AccessScope.Kind.ALL, null, false));

        LeaveEvent event = published();

        assertThat(event.eventType()).isEqualTo(LeaveEventType.DECIDED);
        assertThat(event.status()).isEqualTo("APPROVED");
        assertThat(event.employeeEmail()).isEqualTo("ada@example.com");
        // 10-15 Mart DAHIL = 6 gun. Gun sayisi yeniden hesaplanmaz, anlik
        // goruntuden okunur; `+1` iki yerde yasasaydi biri geride kalirdi.
        assertThat(event.days()).isEqualTo(6);
    }

    @Test
    @DisplayName("Publishes a withdrawal event naming who withdrew it")
    void publishesCancellationEvent() {
        pendingLeaveOf(ada, hr);

        service.cancel(7L, hr, new AccessScope(AccessScope.Kind.ALL, null, false));

        LeaveEvent event = published();

        assertThat(event.eventType()).isEqualTo(LeaveEventType.CANCELLED);
        // Aktor kaydedilmeseydi tuketici "kendi cekti mi" sorusunu
        // cevaplayamaz ve kisi kendi isleminden mail alirdi.
        assertThat(event.actorEmail()).isEqualTo("hr@example.com");
        assertThat(event.employeeEmail()).isEqualTo("ada@example.com");
    }

    @Test
    @DisplayName("Identifies a self-withdrawal by employee id, not by email address")
    void selfWithdrawalIsIdentifiedByEmployeeId() {
        // OLCULEN KUSUR: karsilastirma once e-posta uzerindendi ve ikisi FARKLI
        // kimlik uzaylaridir -- `user@example.com` hesabiyla giren kisi kendi
        // talebini geri cekti ve KENDI islemi icin mail aldi.
        pendingLeaveOf(ada, hr);
        User adasAccount = new User("user@example.com", "hash", Set.of(Role.EMPLOYEE));

        service.cancel(7L, adasAccount, new AccessScope(AccessScope.Kind.SELF, ada.getId(), false));

        LeaveEvent event = published();

        assertThat(event.employeeId()).isEqualTo(ada.getId());
        assertThat(event.actorEmployeeId()).isEqualTo(ada.getId());
        // E-postalar farkli; kimlikler ayni. Tuketici KIMLIKLERE bakmali.
        assertThat(event.actorEmail()).isNotEqualTo(event.employeeEmail());
    }

    @Test
    @DisplayName("Publishes nothing when the decision is refused")
    void publishesNothingWhenRefused() {
        // Olay yalnizca GERCEKLESEN bir olgu icin yazilir. Reddedilen bir
        // istekte yazilsaydi, olmayan bir karari duyurmus olurduk -- ve
        // gonderilmis bir mail geri alinamaz.
        LeaveRequest leave = pendingLeaveOf(ada, hr);
        leave.approve(hr);

        try {
            service.approve(7L, hr, new AccessScope(AccessScope.Kind.ALL, null, false));
        } catch (RuntimeException expected) {
            // nihai bir istek tekrar karara baglanamaz
        }

        verify(outbox, never()).write(any(LeaveEvent.class));
    }
}
