package com.proje.employee.service;

import com.proje.employee.audit.AuditAction;
import com.proje.employee.audit.Auditable;
import com.proje.employee.dto.LeaveRequestCreateRequest;
import com.proje.employee.dto.LeaveRequestResponse;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.LeaveRequest;
import com.proje.employee.entity.LeaveStatus;
import com.proje.employee.entity.LeaveType;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    private final EmployeeVisibility visibility;
    private final LeaveBalanceService balances;

    public LeaveRequestService(LeaveRequestRepository leaveRequests, EmployeeRepository employees,
                               EmployeeVisibility visibility, LeaveBalanceService balances) {
        this.leaveRequests = leaveRequests;
        this.employees = employees;
        this.visibility = visibility;
        this.balances = balances;
    }

    /** Yeni izin istegi. */
    @Auditable(action = AuditAction.LEAVE_REQUESTED, targetType = "LEAVE_REQUEST")
    @Transactional
    public LeaveRequestResponse create(LeaveRequestCreateRequest request, User author,
                                       AccessScope scope) {
        Employee employee = employees.findById(request.employeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));

        requireCanRecordFor(employee, scope);

        // Ayrilmis personele izin girilemez. Ayni degismez "ayrilmis personele
        // hesap acilamaz" kuralinin kardesi; kural bir kez yazilir, iki yerde
        // tekrarlanmaz.
        if (!employee.isActive()) {
            throw new LeaveRuleViolationException(
                    "Leave cannot be recorded for an employee who has left");
        }

        requireBalanceCovers(request);

        LeaveRequest leave = new LeaveRequest(employee, author, request.type(),
                request.startDate(), request.endDate(), request.note());

        return LeaveRequestResponse.from(persist(leave, employee));
    }

    /**
     * Yillik izin hakki asilamaz.
     *
     * YALNIZCA `ANNUAL` sayilir: hastalik, ucretsiz ve ebeveyn izni ayri
     * haklardir ve yillik bakiyeden dusmezler.
     *
     * Mevcut `EXCLUDE` kisiti yalnizca TARIH cakismasini engelliyordu; ayni
     * kisi cakismayan tarihlerle hakkindan fazla izin isteyebiliyordu.
     *
     * Kontrol talep ANINDA yapiliyor, karar aninda degil: kullanicinin
     * alamayacagi bir izni talep etmesine izin vermek, sonunda reddedilmek
     * uzere bir karar uretmek olurdu. Ayni gerekceyle cakisan tarih de talep
     * aninda reddediliyor.
     *
     * BILINEN SINIR: bu kontrol ile yazma arasinda bir pencere var -- iki es
     * zamanli talep ikisi de "yeter" gorup gecebilir. Tarih cakismasinda bu
     * pencere `EXCLUDE` kisitiyla kapatilmisti; burada karsiligi yok, cunku
     * "toplam gun" bir satir kisiti olarak ifade edilemez. Asilma en fazla bir
     * talep kadar olur ve karar asamasinda gorulur.
     */
    private void requireBalanceCovers(LeaveRequestCreateRequest request) {
        if (request.type() != LeaveType.ANNUAL) {
            return;
        }

        int requested = (int) (ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1);
        int available = balances
                .balanceFor(request.employeeId(), request.startDate().getYear())
                .availableDays();

        if (requested > available) {
            throw new LeaveRuleViolationException(
                    "Not enough annual leave: " + requested + " day(s) requested, "
                            + available + " remaining");
        }
    }

    /**
     * Baskasi adina talep acmak Ik'ya aittir; herkes KENDI adina acabilir.
     *
     * Yonetici de ASTI adina acamaz: talebi acan ile karar veren ayni kisi
     * olurdu ve kendi iznine karar verme yasagi bu yoldan atlatilirdi.
     */
    private void requireCanRecordFor(Employee employee, AccessScope scope) {
        if (scope.isUnrestricted()) {
            return;
        }

        if (!employee.getId().equals(scope.employeeId())) {
            throw new LeaveRuleViolationException(
                    "You can only request leave for yourself");
        }
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
    public Page<LeaveRequestResponse> list(Collection<LeaveStatus> statuses, Long employeeId,
                                           Long departmentId,
                                           LocalDate from, LocalDate until,
                                           AccessScope scope, Pageable pageable) {
        // Rolu olan ama personel kaydi olmayan hesap kimseyi goremez.
        if (scope.isEmpty()) {
            return Page.empty(pageable);
        }

        Collection<Long> visible = narrowToRequested(visibleEmployees(scope), employeeId);

        // Istenen kisi kapsamin DISINDAysa bos sayfa doner, hata degil: bir
        // suzgec, gorulemeyen kaydin VARLIGINI da sizdirmamali.
        if (visible != null && visible.isEmpty()) {
            return Page.empty(pageable);
        }

        // Departman suzgeci de kapsami DARALTIR, genisletmez: gorulebilir
        // kimlikler listesiyle VE'lenir. Kapsam disindaki bir departman
        // istenirse sonuc bos doner, hata degil -- ayni gerekce kisi
        // suzgecinde de gecerliydi.
        return leaveRequests.search(emptyToNull(statuses), visible, departmentId,
                        from == null ? LeaveRequestRepository.BEGINNING_OF_TIME : from,
                        until == null ? LeaveRequestRepository.END_OF_TIME : until,
                        pageable)
                .map(LeaveRequestResponse::from);
    }

    /** Kapsam ile istenen kisinin KESISIMI; suzgec kapsami genisletemez. */
    private Collection<Long> narrowToRequested(Collection<Long> visible, Long employeeId) {
        if (employeeId == null) {
            return visible;
        }
        if (visible == null) {
            return List.of(employeeId);
        }
        return visible.contains(employeeId) ? List.of(employeeId) : List.of();
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
            includeArguments = false, summary = "approved")
    @Transactional
    public LeaveRequestResponse approve(Long id, User decider, AccessScope scope) {
        LeaveRequest leave = load(id);
        requireCanDecide(leave, scope);
        requirePending(leave);
        leave.approve(decider);

        return LeaveRequestResponse.from(leave);
    }

    @Auditable(action = AuditAction.LEAVE_DECIDED, targetType = "LEAVE_REQUEST",
            includeArguments = false, summary = "rejected")
    @Transactional
    public LeaveRequestResponse reject(Long id, String note, User decider, AccessScope scope) {
        LeaveRequest leave = load(id);
        requireCanDecide(leave, scope);
        requirePending(leave);
        leave.reject(decider, note);

        return LeaveRequestResponse.from(leave);
    }

    @Auditable(action = AuditAction.LEAVE_DECIDED, targetType = "LEAVE_REQUEST",
            includeArguments = false, summary = "withdrawn")
    @Transactional
    public LeaveRequestResponse cancel(Long id, AccessScope scope) {
        LeaveRequest leave = load(id);
        requireCanCancel(leave, scope);
        requirePending(leave);
        leave.cancel();

        return LeaveRequestResponse.from(leave);
    }

    private LeaveRequest load(Long id) {
        return leaveRequests.findByIdWithEmployee(id)
                .orElseThrow(() -> new LeaveRequestNotFoundException(id));
    }

    /**
     * Geri cekmek karar vermek DEGILDIR: kisi kendi talebinden vazgecebilir.
     *
     * Onay ve rette "kendi iznine karar veremezsin" kurali gecerlidir; iptalde
     * degildir, cunku vazgecmek gorevler ayriligini ihlal etmez. Baskasinin
     * talebini geri cekmek ise karar yetkisi ister.
     */
    private void requireCanCancel(LeaveRequest leave, AccessScope scope) {
        boolean ownRequest = scope.employeeId() != null
                && leave.getEmployee().getId().equals(scope.employeeId());

        if (ownRequest) {
            return;
        }

        requireCanDecide(leave, scope);
    }

    /** Ik her istege, yonetici yalnizca DOGRUDAN astininkine karar verir. */
    private void requireCanDecide(LeaveRequest leave, AccessScope scope) {
        Employee owner = leave.getEmployee();
        boolean ownRequest = scope.employeeId() != null
                && owner.getId().equals(scope.employeeId());

        // Kendi iznini onaylamak GORUNURLUKTEN once gelir. Once
        // isUnrestricted() sorulunca kural yalnizca yonetici icin isliyordu:
        // personele bagli bir Ik uzmani kendi talebini acip kendisi
        // onaylayabiliyordu. Olculdu.
        if (ownRequest) {
            throw new LeaveRuleViolationException("You cannot decide your own leave");
        }

        if (scope.isUnrestricted()) {
            return;
        }

        if (!canSee(leave, scope)) {
            throw new LeaveRequestNotFoundException(leave.getId());
        }

        boolean directReport = owner.getManager() != null
                && owner.getManager().getId().equals(scope.employeeId());

        if (!directReport) {
            throw new LeaveRuleViolationException(
                    "Only this person's manager or HR can decide this request");
        }
    }

    /** Nihai bir istegi tekrar karara baglamak reddedilir. */
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

    /**
     * Kapsamin gorebilecegi personel kimlikleri.
     *
     * Kural artik `EmployeeVisibility`de: bakiye ucu de ayni soruyu soruyor ve
     * iki yere yazilan bir kuralin biri zamanla geride kalir.
     */
    private Collection<Long> visibleEmployees(AccessScope scope) {
        return visibility.visibleEmployeeIds(scope);
    }

    private <T> Collection<T> emptyToNull(Collection<T> values) {
        return values == null || values.isEmpty() ? null : values;
    }
}
