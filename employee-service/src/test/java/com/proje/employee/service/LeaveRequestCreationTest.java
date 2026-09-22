package com.proje.employee.service;

import com.proje.employee.dto.LeaveBalanceResponse;
import com.proje.employee.dto.LeaveRequestCreateRequest;
import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.event.OutboxWriter;
import com.proje.employee.entity.LeaveRequest;
import com.proje.employee.entity.LeaveStatus;
import com.proje.employee.entity.LeaveType;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.TerminationReason;
import com.proje.employee.entity.User;
import com.proje.employee.exception.EmployeeNotFoundException;
import com.proje.employee.exception.LeaveRuleViolationException;
import com.proje.employee.exception.OverlappingLeaveException;
import com.proje.employee.repository.EmployeeRepository;
import com.proje.employee.repository.LeaveRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Talep ACMA yolu ve listeleme suzgecleri.
 *
 * <p>{@code LeaveDecisionScopeTest} "kim karar verebilir" sorusunu tutuyordu;
 * kapsam olcumu servisi yine de %62,5'te buldu. Bosluk KARARDA degil, talebin
 * acildigi yolda ve listelemedeydi -- ve orada uc tane olculmus tuzak var:
 *
 * <ul>
 *   <li>Cakisma tespiti {@code getConstraintName()} ile yapilamiyor (olculdu:
 *       {@code null} donuyor), SQLState'e dayaniyor.</li>
 *   <li>Bakiye kontrolu YALNIZCA yillik izne uygulanmali.</li>
 *   <li>Tarih uclari {@code LocalDate.MIN/MAX} OLAMAZ -- PostgreSQL'in
 *       {@code DATE} araligina sigmiyor ve surucu sorguyu dusuruyor.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LeaveRequestCreationTest {

    private static final long EMPLOYEE_ID = 212L;

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

    @Mock
    private LeaveBalanceService balances;

    private LeaveRequestService service;
    private Employee grace;
    private User author;

    @BeforeEach
    void setUp() {
        service = new LeaveRequestService(leaveRequests, employees, visibility, balances, outbox, preferences);

        grace = new Employee("Grace", "Hopper", "grace@example.com",
                new Department("Software Development"), "Engineer", LocalDate.now());
        ReflectionTestUtils.setField(grace, "id", EMPLOYEE_ID);

        author = new User("hr@example.com", "hash", Set.of(Role.HR_SPECIALIST));
    }

    private AccessScope hrScope() {
        return new AccessScope(AccessScope.Kind.ALL, null, false);
    }

    private LeaveRequestCreateRequest request(LeaveType type, LocalDate start, LocalDate end) {
        return new LeaveRequestCreateRequest(EMPLOYEE_ID, type, start, end, null);
    }

    /** Alti is gunu: 10-15 Mart dahil. */
    private LeaveRequestCreateRequest sixDayRequest() {
        return request(LeaveType.ANNUAL, LocalDate.of(2033, 3, 10), LocalDate.of(2033, 3, 15));
    }

    private void employeeExists() {
        when(employees.findByIdForUpdate(EMPLOYEE_ID)).thenReturn(Optional.of(grace));
    }

    private void havingAnnualBalance(int available) {
        when(balances.balanceFor(any(), anyInt())).thenReturn(new LeaveBalanceResponse(
                EMPLOYEE_ID, 2033, available, 0, 0, 0, available,
                LeaveBalanceResponse.Source.DEFAULT));
    }

    private void savingSucceeds() {
        when(leaveRequests.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /**
     * Gercek bir dislama kisiti ihlalini taklit eder.
     *
     * <p>Zincir SART: servis {@code getCause()} boyunca yuruyup
     * {@code SQLException}'i ariyor -- istisnanin KENDISINE bakmiyor.
     */
    private DataIntegrityViolationException exclusionViolation(String constraint) {
        SQLException sql = new SQLException(
                "ERROR: conflicting key value violates exclusion constraint \"" + constraint + "\"",
                "23P01");
        return new DataIntegrityViolationException("could not execute statement", sql);
    }

    // ---------------------------------------------------------------- create

    @Test
    @DisplayName("Records a request for an active employee")
    void recordsARequest() {
        employeeExists();
        havingAnnualBalance(20);
        savingSucceeds();

        service.create(sixDayRequest(), author, hrScope());

        ArgumentCaptor<LeaveRequest> saved = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequests).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(saved.getValue().getEmployee()).isEqualTo(grace);
    }

    @Test
    @DisplayName("An unknown employee is reported as not found, not as a rule violation")
    void unknownEmployeeIsNotFound() {
        when(employees.findByIdForUpdate(EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(sixDayRequest(), author, hrScope()))
                .isInstanceOf(EmployeeNotFoundException.class);

        verify(leaveRequests, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Leave cannot be recorded for someone who has left")
    void noLeaveForATerminatedEmployee() {
        // "Ayrilmis personele hesap acilamaz" kuralinin kardesi. Ayni degismez
        // iki yolda uygulanmali; projede tam olarak bu kalip uc kez atlandi.
        grace.terminate(LocalDate.now(), TerminationReason.RESIGNED);
        employeeExists();

        assertThatThrownBy(() -> service.create(sixDayRequest(), author, hrScope()))
                .isInstanceOf(LeaveRuleViolationException.class)
                .hasMessageContaining("has left");

        verify(leaveRequests, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("The status check runs before the balance is even read")
    void terminationIsCheckedBeforeTheBalance() {
        // Ayrilmis birinin bakiyesini sormak anlamsiz bir sorgudur; sira
        // yanlis olsaydi her reddedilen talep bir bakiye hesabi odetirdi.
        grace.terminate(LocalDate.now(), TerminationReason.RESIGNED);
        employeeExists();

        assertThatThrownBy(() -> service.create(sixDayRequest(), author, hrScope()));

        verify(balances, never()).balanceFor(any(), anyInt());
    }

    // --------------------------------------------------------------- balance

    @Test
    @DisplayName("A request larger than the remaining entitlement is refused")
    void refusesMoreDaysThanRemain() {
        employeeExists();
        havingAnnualBalance(5);   // alti gun isteniyor

        assertThatThrownBy(() -> service.create(sixDayRequest(), author, hrScope()))
                .isInstanceOf(LeaveRuleViolationException.class)
                .hasMessageContaining("6 day(s) requested")
                .hasMessageContaining("5 remaining");
    }

    @Test
    @DisplayName("Spending the last remaining day is allowed")
    void theLastDayIsStillAllowed() {
        // Sinir kosulu: kural ">" olmali, ">=" degil. Yanlis operator kimsenin
        // hakkinin SON gununu kullanmasina izin vermezdi ve bu, sikayet
        // gelene kadar gorunmez bir hata olurdu.
        employeeExists();
        havingAnnualBalance(6);
        savingSucceeds();

        assertThatCode(() -> service.create(sixDayRequest(), author, hrScope()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A single-day request counts as one day, not zero")
    void aSingleDayCountsAsOne() {
        // Gun sayisi son gun DAHIL hesaplanir ([) semantiginin uygulama
        // tarafindaki karsiligi). Cikarma tek basina 0 verirdi.
        employeeExists();
        havingAnnualBalance(0);
        LocalDate day = LocalDate.of(2033, 3, 10);

        assertThatThrownBy(() -> service.create(
                request(LeaveType.ANNUAL, day, day), author, hrScope()))
                .hasMessageContaining("1 day(s) requested");
    }

    @Test
    @DisplayName("Sick leave does not consume the annual entitlement")
    void sickLeaveIgnoresTheAnnualBalance() {
        // Bakiye YALNIZCA yillik izne uygulanir: hastalik, ucretsiz ve
        // ebeveyn izni ayri haklardir. Kontrol hepsine uygulansaydi bakiyesi
        // biten biri RAPOR bile alamazdi.
        employeeExists();
        savingSucceeds();

        assertThatCode(() -> service.create(
                request(LeaveType.SICK, LocalDate.of(2033, 3, 10), LocalDate.of(2033, 3, 30)),
                author, hrScope()))
                .doesNotThrowAnyException();

        verify(balances, never()).balanceFor(any(), anyInt());
    }

    // -------------------------------------------------------------- overlap

    @Test
    @DisplayName("An exclusion violation becomes a message the user understands")
    void overlapBecomesAReadableError() {
        // OLCULDU: getConstraintName() bu kurulumda null donuyor, dolayisiyla
        // tespit SQLState (23P01) + kisit adina dayaniyor. Bozulursa kullanici
        // "bu personelin o tarihlerde zaten izni var" yerine genel bir "veri
        // cakismasi" gorurdu -- ve ihlal yine 409 dondugu icin bu SESSIZ bir
        // bozulma olurdu.
        employeeExists();
        havingAnnualBalance(20);
        when(leaveRequests.saveAndFlush(any()))
                .thenThrow(exclusionViolation("ex_leave_no_overlap"));

        assertThatThrownBy(() -> service.create(sixDayRequest(), author, hrScope()))
                .isInstanceOf(OverlappingLeaveException.class)
                .hasMessageContaining("overlaps those dates");
    }

    @Test
    @DisplayName("A different constraint is not disguised as an overlap")
    void anotherConstraintIsNotMistakenForAnOverlap() {
        // Ayni istisna TIPI baska sebeplerle de gelir. Hepsini cakisma saymak,
        // olmayan bir izin cakismasi bildirmek olurdu.
        employeeExists();
        havingAnnualBalance(20);
        when(leaveRequests.saveAndFlush(any()))
                .thenThrow(exclusionViolation("ex_some_other_rule"));

        assertThatThrownBy(() -> service.create(sixDayRequest(), author, hrScope()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(OverlappingLeaveException.class);
    }

    @Test
    @DisplayName("A violation with no SQL cause at all is passed through untouched")
    void aViolationWithoutASqlCauseIsPassedThrough() {
        // Zincirde SQLException yoksa dongu sonuna kadar gider ve false doner.
        employeeExists();
        havingAnnualBalance(20);
        when(leaveRequests.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("no cause here"));

        assertThatThrownBy(() -> service.create(sixDayRequest(), author, hrScope()))
                .isNotInstanceOf(OverlappingLeaveException.class);
    }

    // ------------------------------------------------------------------ list

    @Test
    @DisplayName("An account with a role but no employee record sees nothing")
    void anEmptyScopeSeesAnEmptyPage() {
        Page<?> page = service.list(null, null, null, null, null,
                new AccessScope(AccessScope.Kind.SELF, null, false), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isZero();
        // Sorgu veritabanina HIC gitmemeli.
        verify(leaveRequests, never()).search(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Open date bounds become real dates a DATE column can hold")
    void openDateBoundsBecomeRealDates() {
        // OLCULDU: LocalDate.MIN/MAX kullanilamaz -- yillari +-999999999 ve
        // PostgreSQL'in DATE araligina sigmiyor, surucu sorguyu dusuruyor.
        // ":x IS NULL OR ..." kalibi de bir kez patlamisti (turu belirtilmemis
        // NULL'u PostgreSQL bytea saniyor).
        when(visibility.visibleEmployeeIds(any())).thenReturn(null);
        when(leaveRequests.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        service.list(null, null, null, null, null, hrScope(), PageRequest.of(0, 20));

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> until = ArgumentCaptor.forClass(LocalDate.class);
        verify(leaveRequests).search(any(), any(), any(),
                from.capture(), until.capture(), any());

        assertThat(from.getValue()).isEqualTo(LeaveRequestRepository.BEGINNING_OF_TIME);
        assertThat(until.getValue()).isEqualTo(LeaveRequestRepository.END_OF_TIME);
        assertThat(from.getValue().getYear()).isPositive();
        assertThat(until.getValue().getYear()).isLessThan(10_000);
    }

    @Test
    @DisplayName("An empty status filter is sent as null, not as an empty list")
    void anEmptyStatusFilterBecomesNull() {
        // Bos bir koleksiyon "IN ()" uretir ve HICBIR satir eslesmez: suzgec
        // uygulanmamis gibi degil, her seyi eleyen bir suzgec gibi davranirdi.
        when(visibility.visibleEmployeeIds(any())).thenReturn(null);
        when(leaveRequests.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        service.list(List.of(), null, null, null, null, hrScope(), PageRequest.of(0, 20));

        verify(leaveRequests).search(eq(null), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Asking for somebody outside the scope returns an empty page, not an error")
    void filteringForAnInvisiblePersonReturnsNothing() {
        // Hata donmek o kaydin VARLIGINI dogrulardi.
        when(visibility.visibleEmployeeIds(any())).thenReturn(List.of(999L));

        Page<?> page = service.list(null, EMPLOYEE_ID, null, null, null,
                new AccessScope(AccessScope.Kind.TEAM, 999L, false), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isZero();
        verify(leaveRequests, never()).search(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Filtering by a visible person narrows the query to that one person")
    void filteringNarrowsToTheRequestedPerson() {
        when(visibility.visibleEmployeeIds(any())).thenReturn(List.of(999L, EMPLOYEE_ID));
        when(leaveRequests.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        service.list(null, EMPLOYEE_ID, null, null, null,
                new AccessScope(AccessScope.Kind.TEAM, 999L, false), PageRequest.of(0, 20));

        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.captor();
        verify(leaveRequests).search(any(), ids.capture(), any(), any(), any(), any());
        assertThat(ids.getValue()).containsExactly(EMPLOYEE_ID);
    }

    @Test
    @DisplayName("An unrestricted caller asking for one person is not widened back to everyone")
    void hrFilteringForOnePersonStaysNarrow() {
        // Kapsam null (=sinirsiz) iken suzgec YINE uygulanmali; null'u
        // "suzgec yok" saymak, secilen kisiyi yok sayip herkesi dondururdu.
        when(visibility.visibleEmployeeIds(any())).thenReturn(null);
        when(leaveRequests.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        service.list(null, EMPLOYEE_ID, null, null, null, hrScope(), PageRequest.of(0, 20));

        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.captor();
        verify(leaveRequests).search(any(), ids.capture(), any(), any(), any(), any());
        assertThat(ids.getValue()).containsExactly(EMPLOYEE_ID);
    }

    @Test
    @DisplayName("The department filter is passed straight through")
    void theDepartmentFilterIsPassedThrough() {
        when(visibility.visibleEmployeeIds(any())).thenReturn(null);
        when(leaveRequests.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        Pageable pageable = PageRequest.of(0, 20);
        service.list(null, null, 3L, null, null, hrScope(), pageable);

        verify(leaveRequests).search(any(), any(), eq(3L), any(), any(), eq(pageable));
    }
}
