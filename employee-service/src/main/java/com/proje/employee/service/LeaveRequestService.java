package com.proje.employee.service;

import com.proje.employee.audit.AuditAction;
import com.proje.employee.audit.Auditable;
import com.proje.employee.dto.LeaveRequestCreateRequest;
import com.proje.employee.dto.LeaveRequestResponse;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.LeaveRequest;
import com.proje.employee.entity.LeaveStatus;
import com.proje.employee.entity.User;
import com.proje.employee.exception.EmployeeNotFoundException;
import com.proje.employee.exception.LeaveRequestNotFoundException;
import com.proje.employee.exception.LeaveRuleViolationException;
import com.proje.employee.exception.OverlappingLeaveException;
import com.proje.employee.repository.EmployeeRepository;
import com.proje.employee.repository.LeaveRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

@Service
public class LeaveRequestService {

    private static final Logger log = LoggerFactory.getLogger(LeaveRequestService.class);

    /** Cakisma kuralinin veritabanindaki adi; ihlali bununla taniyoruz. */
    private static final String OVERLAP_CONSTRAINT = "ex_leave_no_overlap";

    /** SQL standardinda "exclusion_violation"; PostgreSQL bunu dislama kisiti icin doner. */
    private static final String EXCLUSION_VIOLATION = "23P01";

    private final LeaveRequestRepository leaveRequests;
    private final EmployeeRepository employees;

    public LeaveRequestService(LeaveRequestRepository leaveRequests, EmployeeRepository employees) {
        this.leaveRequests = leaveRequests;
        this.employees = employees;
    }

    /** Yeni izin istegi. */
    @Auditable(action = AuditAction.LEAVE_REQUESTED, targetType = "LEAVE_REQUEST")
    @Transactional
    public LeaveRequestResponse create(LeaveRequestCreateRequest request, User author) {
        Employee employee = employees.findById(request.employeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));

        // Ayrilmis personele izin girilemez. Ayni degismez "ayrilmis personele
        // hesap acilamaz" kuralinin kardesi; kural bir kez yazilir, iki yerde
        // tekrarlanmaz.
        if (!employee.isActive()) {
            throw new LeaveRuleViolationException(
                    "Leave cannot be recorded for an employee who has left");
        }

        LeaveRequest leave = new LeaveRequest(employee, author, request.type(),
                request.startDate(), request.endDate(), request.note());

        return LeaveRequestResponse.from(persist(leave, employee));
    }

    /** Kaydi yazip veritabanina ANINDA gonderir. */
    private LeaveRequest persist(LeaveRequest leave, Employee employee) {
        try {
            return leaveRequests.saveAndFlush(leave);
        } catch (DataIntegrityViolationException ex) {
            if (isOverlap(ex)) {
                log.info("Overlapping leave rejected for employee {}", employee.getId());
                throw new OverlappingLeaveException(
                        "This employee already has leave that overlaps those dates");
            }
            throw ex;
        }
    }

    /** Ihlal edilen kisit cakisma kisiti mi? Ayni istisna tipi baska sebeplerle de gelir (olmayan bir personel kimligi, olmayan bir kullanici). */
    private boolean isOverlap(DataIntegrityViolationException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && EXCLUSION_VIOLATION.equals(sql.getSQLState())
                    && String.valueOf(sql.getMessage()).contains(OVERLAP_CONSTRAINT)) {
                return true;
            }
        }

        return false;
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> list(Collection<LeaveStatus> statuses,
                                           AccessScope scope, Pageable pageable) {
        // Rolu olan ama personel kaydi olmayan hesap kimseyi goremez.
        if (scope.isEmpty()) {
            return Page.empty(pageable);
        }

        return leaveRequests.search(emptyToNull(statuses), visibleEmployees(scope), pageable)
                .map(LeaveRequestResponse::from);
    }

    @Transactional(readOnly = true)
    public LeaveRequestResponse getById(Long id, AccessScope scope) {
        LeaveRequest leave = load(id);

        // Kapsam disindaki kayit 403 degil 404 doner: 403 "bu kayit var ama
        // goremezsin" der ve id deneyerek kayit sayisi ogrenilebilirdi.
        if (!canSee(leave, scope)) {
            throw new LeaveRequestNotFoundException(id);
        }

        return LeaveRequestResponse.from(leave);
    }

    @Auditable(action = AuditAction.LEAVE_DECIDED, targetType = "LEAVE_REQUEST",
            includeArguments = false)
    @Transactional
    public LeaveRequestResponse approve(Long id, User decider, AccessScope scope) {
        LeaveRequest leave = load(id);
        requireCanDecide(leave, scope);
        requirePending(leave);
        leave.approve(decider);

        return LeaveRequestResponse.from(leave);
    }

    @Auditable(action = AuditAction.LEAVE_DECIDED, targetType = "LEAVE_REQUEST",
            includeArguments = false)
    @Transactional
    public LeaveRequestResponse reject(Long id, String note, User decider, AccessScope scope) {
        LeaveRequest leave = load(id);
        requireCanDecide(leave, scope);
        requirePending(leave);
        leave.reject(decider, note);

        return LeaveRequestResponse.from(leave);
    }

    @Auditable(action = AuditAction.LEAVE_DECIDED, targetType = "LEAVE_REQUEST",
            includeArguments = false)
    @Transactional
    public LeaveRequestResponse cancel(Long id, AccessScope scope) {
        LeaveRequest leave = load(id);
        requireCanDecide(leave, scope);
        requirePending(leave);
        leave.cancel();

        return LeaveRequestResponse.from(leave);
    }

    private LeaveRequest load(Long id) {
        return leaveRequests.findByIdWithEmployee(id)
                .orElseThrow(() -> new LeaveRequestNotFoundException(id));
    }

    /** Nihai bir istegi tekrar karara baglamak reddedilir. */
    // Karar yetkisi: Ik her istegi, yonetici yalnizca DOGRUDAN astlarininkini.
    // Kendi iznini onaylamak gorevler ayriligina aykiri; kimse kendi rolune
    // dokunamaz kuralinin ayni ailesinden.
    private void requireCanDecide(LeaveRequest leave, AccessScope scope) {
        if (scope.isUnrestricted()) {
            return;
        }

        if (!canSee(leave, scope)) {
            throw new LeaveRequestNotFoundException(leave.getId());
        }

        Employee owner = leave.getEmployee();
        boolean ownRequest = owner.getId().equals(scope.employeeId());
        boolean directReport = owner.getManager() != null
                && owner.getManager().getId().equals(scope.employeeId());

        if (ownRequest || !directReport) {
            throw new LeaveRuleViolationException(ownRequest
                    ? "You cannot decide your own leave"
                    : "Only this person's manager or HR can decide this request");
        }
    }

    private void requirePending(LeaveRequest leave) {
        if (!leave.isPending()) {
            throw new LeaveRuleViolationException(
                    "This request was already " + leave.getStatus().name().toLowerCase());
        }
    }

    private boolean canSee(LeaveRequest leave, AccessScope scope) {
        if (scope.isUnrestricted()) {
            return true;
        }
        if (scope.isEmpty()) {
            return false;
        }

        Employee owner = leave.getEmployee();

        if (scope.employeeId().equals(owner.getId())) {
            return true;
        }

        return scope.includesDirectReports()
                && owner.getManager() != null
                && scope.employeeId().equals(owner.getManager().getId());
    }

    /** Kapsamin gorebilecegi personel kimlikleri. */
    private Collection<Long> visibleEmployees(AccessScope scope) {
        if (scope.isUnrestricted()) {
            return null;
        }

        Long self = scope.employeeId();

        if (!scope.includesDirectReports()) {
            return List.of(self);
        }

        // Yonetici kendi kaydini ve DOGRUDAN astlarini gorur; torunlari degil.
        // Ayni sizinti org chart ucunda bir kez kapatilmisti.
        List<Long> reports = employees.findByManagerIdOrderByLastNameAsc(self).stream()
                .map(Employee::getId)
                .toList();

        return java.util.stream.Stream.concat(java.util.stream.Stream.of(self), reports.stream())
                .toList();
    }

    private <T> Collection<T> emptyToNull(Collection<T> values) {
        return values == null || values.isEmpty() ? null : values;
    }
}
