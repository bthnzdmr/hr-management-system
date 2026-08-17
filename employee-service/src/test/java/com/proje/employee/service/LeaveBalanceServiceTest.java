package com.proje.employee.service;

import com.proje.employee.dto.LeaveBalanceResponse;
import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.LeaveEntitlement;
import com.proje.employee.exception.EmployeeNotFoundException;
import com.proje.employee.repository.EmployeeRepository;
import com.proje.employee.repository.LeaveEntitlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Annual leave balance")
class LeaveBalanceServiceTest {

    private static final int DEFAULT_DAYS = 20;
    private static final Long EMPLOYEE_ID = 7L;

    @Mock
    private LeaveEntitlementRepository entitlements;

    @Mock
    private EmployeeRepository employees;

    @Mock
    private EmployeeVisibility visibility;

    private LeaveBalanceService service;

    @BeforeEach
    void setUp() {
        service = new LeaveBalanceService(entitlements, employees, visibility, DEFAULT_DAYS);
    }

    /** Kullanilan ve rezerve gunleri veren tek satirlik sonuc. */
    private void usage(int used, int reserved) {
        when(entitlements.findAnnualUsage(anyLong(), anyInt()))
                .thenReturn(new LeaveEntitlementRepository.AnnualLeaveUsage() {
                    @Override
                    public int getUsed() {
                        return used;
                    }

                    @Override
                    public int getReserved() {
                        return reserved;
                    }
                });
    }

    private LeaveEntitlement granted(int entitled, int carriedOver) {
        Employee employee = new Employee("Ada", "Lovelace", "ada@example.com",
                new Department("Software Development"), "Engineer", LocalDate.of(2020, 1, 1));

        return new LeaveEntitlement(employee, 2026, entitled, carriedOver, null);
    }

    @Test
    @DisplayName("Falls back to the configured default and says the source was not a grant")
    void usesDefaultWhenNothingGranted() {
        // "Verilmis hak" ile "varsayilan" ayni ekranda ayni gorunmemeli:
        // ikincisi bir karar degil, bir tahmindir.
        when(employees.existsById(EMPLOYEE_ID)).thenReturn(true);
        when(entitlements.findByEmployeeIdAndYear(EMPLOYEE_ID, 2026)).thenReturn(Optional.empty());
        usage(0, 0);

        LeaveBalanceResponse balance = service.balanceFor(EMPLOYEE_ID, 2026);

        assertThat(balance.entitledDays()).isEqualTo(DEFAULT_DAYS);
        assertThat(balance.availableDays()).isEqualTo(DEFAULT_DAYS);
        assertThat(balance.source()).isEqualTo(LeaveBalanceResponse.Source.DEFAULT);
    }

    @Test
    @DisplayName("A grant replaces the default and carries the previous year over")
    void grantReplacesDefault() {
        when(employees.existsById(EMPLOYEE_ID)).thenReturn(true);
        when(entitlements.findByEmployeeIdAndYear(EMPLOYEE_ID, 2026))
                .thenReturn(Optional.of(granted(14, 3)));
        usage(0, 0);

        LeaveBalanceResponse balance = service.balanceFor(EMPLOYEE_ID, 2026);

        assertThat(balance.entitledDays()).isEqualTo(14);
        assertThat(balance.carriedOverDays()).isEqualTo(3);
        assertThat(balance.availableDays()).isEqualTo(17);
        assertThat(balance.source()).isEqualTo(LeaveBalanceResponse.Source.GRANTED);
    }

    @Test
    @DisplayName("Pending days are reserved, not free")
    void pendingDaysAreReserved() {
        // Dusulmeseydi iki gunu kalan biri uc ayri iki gunluk talep acabilir ve
        // ucu de onaylanabilir gorunurdu. Mevcut EXCLUDE kisiti yalnizca TARIH
        // cakismasini engelliyor, hakkin asilmasini degil.
        when(employees.existsById(EMPLOYEE_ID)).thenReturn(true);
        when(entitlements.findByEmployeeIdAndYear(EMPLOYEE_ID, 2026)).thenReturn(Optional.empty());
        usage(12, 6);

        LeaveBalanceResponse balance = service.balanceFor(EMPLOYEE_ID, 2026);

        assertThat(balance.usedDays()).isEqualTo(12);
        assertThat(balance.reservedDays()).isEqualTo(6);
        assertThat(balance.availableDays()).isEqualTo(2);
    }

    @Test
    @DisplayName("Reports a negative balance rather than clamping it to zero")
    void reportsNegativeBalance() {
        // Sifira kirpmak, hakkin asildigi gercegini GIZLERDI; Ik'nin gormesi
        // gereken tam da bu.
        when(employees.existsById(EMPLOYEE_ID)).thenReturn(true);
        when(entitlements.findByEmployeeIdAndYear(EMPLOYEE_ID, 2026))
                .thenReturn(Optional.of(granted(10, 0)));
        usage(14, 0);

        assertThat(service.balanceFor(EMPLOYEE_ID, 2026).availableDays()).isEqualTo(-4);
    }

    @Test
    @DisplayName("Hides a record outside the caller's scope as missing, not forbidden")
    void hidesOutOfScopeAsNotFound() {
        // 403 "bu kayit var ama goremezsin" derdi ve id deneyerek personel
        // varligi ogrenilebilirdi.
        when(visibility.canSee(EMPLOYEE_ID, new AccessScope(AccessScope.Kind.SELF, 99L, false))).thenReturn(false);

        assertThatThrownBy(() -> service.balanceFor(EMPLOYEE_ID, 2026, new AccessScope(AccessScope.Kind.SELF, 99L, false)))
                .isInstanceOf(EmployeeNotFoundException.class);
    }
}
