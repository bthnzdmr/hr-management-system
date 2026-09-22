package com.proje.employee.service;

import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.event.OutboxWriter;
import com.proje.employee.entity.LeaveRequest;
import com.proje.employee.entity.LeaveStatus;
import com.proje.employee.entity.LeaveType;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import com.proje.employee.exception.LeaveRequestNotFoundException;
import com.proje.employee.exception.LeaveRuleViolationException;
import com.proje.employee.repository.EmployeeRepository;
import com.proje.employee.repository.LeaveRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Izin kararini kim verebilir. */
@ExtendWith(MockitoExtension.class)
class LeaveDecisionScopeTest {

    @Mock
    private OutboxWriter outbox;

    @Mock
    private NotificationPreferenceService preferences;

    @Mock
    private LeaveRequestRepository leaveRequests;

    @Mock
    private EmployeeRepository employees;

    @Mock
    private EmployeeVisibility visibility;

    // Bu sinif KARAR kapsamini siniyor; bakiye kontrolu yalnizca talep
    // ACARKEN calisiyor, dolayisiyla burada taklit yeterli.
    @Mock
    private LeaveBalanceService balances;

    private LeaveRequestService service;
    private Employee grace;
    private Employee ada;
    private User decider;

    @BeforeEach
    void setUp() {
        service = new LeaveRequestService(leaveRequests, employees, visibility, balances, outbox, preferences);

        Department department = new Department("Software Development");
        grace = employee(212L, "Grace", "Hopper", department, null);
        ada = employee(218L, "Ada", "Lovelace", department, grace);
        decider = new User("grace@example.com", "hash", Set.of(Role.MANAGER));
    }

    /** Bakiye sorgusunu verilen kalan gunle taklit eder. */
    private void havingAnnualBalance(int available) {
        when(balances.balanceFor(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new com.proje.employee.dto.LeaveBalanceResponse(
                        1L, 2033, available, 0, 0, 0, available,
                        com.proje.employee.dto.LeaveBalanceResponse.Source.DEFAULT));
    }

    private Employee employee(long id, String first, String last, Department dept, Employee manager) {
        Employee e = new Employee(first, last, first + "@example.com", dept, "Engineer", LocalDate.now());
        ReflectionTestUtils.setField(e, "id", id);
        e.setManager(manager);
        return e;
    }

    private LeaveRequest leaveOf(Employee owner) {
        LeaveRequest leave = new LeaveRequest(owner, decider, LeaveType.ANNUAL,
                LocalDate.of(2032, 3, 10), LocalDate.of(2032, 3, 15), null);
        ReflectionTestUtils.setField(leave, "id", 7L);
        when(leaveRequests.findByIdWithEmployee(7L)).thenReturn(Optional.of(leave));
        return leave;
    }

    private AccessScope managerScope() {
        return new AccessScope(AccessScope.Kind.TEAM, grace.getId(), false);
    }

    @Test
    @DisplayName("a manager may approve leave for a direct report")
    void managerApprovesDirectReport() {
        leaveOf(ada);

        assertThat(service.approve(7L, decider, managerScope()).status())
                .isEqualTo(LeaveStatus.APPROVED);
    }

    @Test
    @DisplayName("an HR specialist may not approve their own leave either")
    void hrCannotApproveOwnLeave() {
        // Kural yalnizca yonetici icin isliyordu: "isUnrestricted()" ilk
        // satirdaydi ve kendi-talebi kontrolune hic ulasilmiyordu. Personele
        // bagli bir Ik uzmani kendi talebini acip kendisi onayliyordu.
        leaveOf(grace);
        AccessScope hrWhoIsAlsoGrace = new AccessScope(AccessScope.Kind.ALL, grace.getId(), false);

        assertThatThrownBy(() -> service.approve(7L, decider, hrWhoIsAlsoGrace))
                .isInstanceOf(LeaveRuleViolationException.class)
                .hasMessageContaining("your own leave");
    }

    @Test
    @DisplayName("a manager may not approve their own leave")
    void managerCannotApproveOwnLeave() {
        // Kendi iznini onaylamak gorevler ayriligina aykiri; kimse kendi
        // rolune dokunamaz kuralinin ayni ailesinden.
        leaveOf(grace);

        assertThatThrownBy(() -> service.approve(7L, decider, managerScope()))
                .isInstanceOf(LeaveRuleViolationException.class)
                .hasMessageContaining("your own leave");
    }

    @Test
    @DisplayName("a manager may not decide leave for someone outside their team")
    void managerCannotDecideForStranger() {
        Employee stranger = employee(300L, "Alan", "Kay", new Department("Sales"), null);
        leaveOf(stranger);

        assertThatThrownBy(() -> service.approve(7L, decider, managerScope()))
                .isInstanceOf(LeaveRequestNotFoundException.class);
    }

    @Test
    @DisplayName("HR may decide leave for someone with no manager at all")
    void hrDecidesForPeopleWithoutAManager() {
        // Bes departman baskaninin yoneticisi yok; Ik yedegi olmasaydi
        // izinleri askida kalirdi.
        leaveOf(grace);

        assertThat(service.approve(7L, decider, new AccessScope(AccessScope.Kind.ALL, null, false))
                .status()).isEqualTo(LeaveStatus.APPROVED);
    }

    @Test
    @DisplayName("an employee may not decide anyone's leave, not even their own")
    void employeeCannotDecide() {
        leaveOf(ada);

        assertThatThrownBy(() -> service.approve(7L, decider,
                new AccessScope(AccessScope.Kind.SELF, ada.getId(), false)))
                .isInstanceOf(LeaveRuleViolationException.class);
    }

    @Test
    @DisplayName("Filtering by employee cannot widen what the caller may see")
    void filterCannotWidenScope() {
        // Suzgec bir GORUNURLUK araci degildir. Kapsam disindaki bir kisi
        // istendiginde bos sayfa doner -- hata degil: hata, o kaydin
        // VARLIGINI dogrulardi.
        var result = service.list(null, 9999L, null, null, null,
                new AccessScope(AccessScope.Kind.SELF, ada.getId(), false),
                PageRequest.of(0, 20));

        assertThat(result).isEmpty();
        // Sorgu HIC atilmamali: kapsam disindaki kimlik veritabanina bile gitmez.
        verify(leaveRequests, never()).search(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("An employee may request leave for themselves")
    void employeeRequestsOwnLeave() {
        when(employees.findByIdForUpdate(ada.getId())).thenReturn(java.util.Optional.of(ada));
        when(leaveRequests.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        // Yillik izin artik bakiyeye bakiyor; bu test KAPSAMI siniyor, o yuzden
        // bakiye bol verilir ve kural yolun disinda tutulur.
        havingAnnualBalance(20);

        var request = new com.proje.employee.dto.LeaveRequestCreateRequest(
                ada.getId(), LeaveType.ANNUAL,
                java.time.LocalDate.of(2033, 5, 1), java.time.LocalDate.of(2033, 5, 3), null);

        assertThatCode(() -> service.create(request, decider,
                new AccessScope(AccessScope.Kind.SELF, ada.getId(), false)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Nobody opens a request on somebody else's behalf without HR rights")
    void managerCannotRequestForTheirReport() {
        // Yonetici ASTI adina acamaz: talebi acan ile karar veren ayni kisi
        // olurdu ve "kendi iznine karar veremezsin" kurali bu yoldan atlatilirdi.
        when(employees.findByIdForUpdate(ada.getId())).thenReturn(java.util.Optional.of(ada));

        var request = new com.proje.employee.dto.LeaveRequestCreateRequest(
                ada.getId(), LeaveType.ANNUAL,
                java.time.LocalDate.of(2033, 6, 1), java.time.LocalDate.of(2033, 6, 3), null);

        assertThatThrownBy(() -> service.create(request, decider, managerScope()))
                .isInstanceOf(LeaveRuleViolationException.class)
                .hasMessageContaining("only request leave for yourself");

        verify(leaveRequests, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Anyone may withdraw their own pending request")
    void ownRequestCanBeWithdrawn() {
        // Vazgecmek karar vermek DEGILDIR: "kendi iznine karar veremezsin"
        // kurali iptali kapsamaz, yoksa calisan actigi talebi geri
        // cekemez ve yonetici mesgul edilirdi.
        leaveOf(ada);

        assertThatCode(() -> service.cancel(7L, decider,
                new AccessScope(AccessScope.Kind.SELF, ada.getId(), false)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Withdrawing somebody else's request still needs the authority to decide")
    void othersRequestNeedsAuthority() {
        leaveOf(grace);

        assertThatThrownBy(() -> service.cancel(7L, decider,
                new AccessScope(AccessScope.Kind.SELF, ada.getId(), false)))
                .isInstanceOf(LeaveRequestNotFoundException.class);
    }

    @Test
    @DisplayName("An employee still cannot approve their own request")
    void ownRequestCannotBeApproved() {
        leaveOf(ada);

        assertThatThrownBy(() -> service.approve(7L, decider,
                new AccessScope(AccessScope.Kind.SELF, ada.getId(), false)))
                .isInstanceOf(LeaveRuleViolationException.class)
                .hasMessageContaining("your own leave");
    }
}
