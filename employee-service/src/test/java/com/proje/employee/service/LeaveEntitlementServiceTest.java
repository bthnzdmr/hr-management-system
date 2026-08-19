package com.proje.employee.service;

import com.proje.employee.config.LeavePolicyFixture;
import com.proje.employee.dto.LeaveBalanceResponse;
import com.proje.employee.dto.LeaveEntitlementRequest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hakkin yazildigi taraf.
 *
 * <p>En kritik degismez IDEMPOTENTLIK: is her gun calisiyor ve var olan
 * satirin uzerine yazsaydi Ik'nin elle yaptigi duzeltme ertesi gun SESSIZCE
 * geri alinirdi -- kimsenin fark etmeyecegi bir gerileme, cunku sayi yine
 * makul gorunur.
 */
@ExtendWith(MockitoExtension.class)
class LeaveEntitlementServiceTest {

    private static final int YEAR = 2033;
    private static final long EMPLOYEE_ID = 212L;

    @Mock
    private LeaveEntitlementRepository entitlements;

    @Mock
    private EmployeeRepository employees;

    @Mock
    private LeaveBalanceService balances;

    private LeaveEntitlementService service;
    private Employee grace;

    @BeforeEach
    void setUp() {
        service = new LeaveEntitlementService(entitlements, employees, balances,
                LeavePolicyFixture.standard());
        grace = employeeHiredOn(LocalDate.of(2030, 6, 15));
    }

    private Employee employeeHiredOn(LocalDate hireDate) {
        Employee employee = new Employee("Grace", "Hopper", "grace@example.com",
                new Department("Software Development"), "Engineer", hireDate);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
        return employee;
    }

    private void awaitingAccrual(Employee... pending) {
        when(entitlements.findActiveWithoutEntitlement(YEAR)).thenReturn(List.of(pending));
    }

    /** Onceki yilda GERCEKTEN verilmis bir hak ve ondan kalan gun. */
    private void previousYearGranted(int remaining) {
        previousYear(remaining, LeaveBalanceResponse.Source.GRANTED);
    }

    private void previousYear(int remaining, LeaveBalanceResponse.Source source) {
        when(balances.balanceFor(anyLong(), anyInt())).thenReturn(new LeaveBalanceResponse(
                EMPLOYEE_ID, YEAR - 1, 20, 0, 20 - remaining, 0, remaining, source));
    }

    private LeaveEntitlement accrued() {
        ArgumentCaptor<LeaveEntitlement> saved = ArgumentCaptor.forClass(LeaveEntitlement.class);
        verify(entitlements).save(saved.capture());
        return saved.getValue();
    }

    // -------------------------------------------------------------- tahakkuk

    @Test
    @DisplayName("Writes an entitlement from the seniority ladder")
    void writesFromTheLadder() {
        // 2030-06-15'te ise alinan, 2033-01-01'de 2 yil TAMAMLAMIS: 1-5 yil
        // basamagi, yani 14 gun.
        awaitingAccrual(grace);
        previousYearGranted(0);

        assertThat(service.accrueFor(YEAR)).isEqualTo(1);
        assertThat(accrued().getEntitledDays()).isEqualTo(14);
    }

    @Test
    @DisplayName("Crossing a tier during the year earns the higher entitlement that year")
    void crossingATierDuringTheYearCountsForThatYear() {
        // 2028-03-01'de ise alinan, 2033-03-01'de besinci yilini DOLDURUR.
        // Olcum yil BASINDA yapilsaydi 14 gun alirdi -- yani hak ettigi
        // kademeyi bir yil gec.
        //
        // OLCULDU: yil basi olcumuyle 33 kisiden 9'u sifir gun aliyordu ve
        // besi o yil icinde bir yilini dolduruyordu.
        awaitingAccrual(employeeHiredOn(LocalDate.of(2028, 3, 1)));
        previousYearGranted(0);

        service.accrueFor(YEAR);

        assertThat(accrued().getEntitledDays()).isEqualTo(20);
    }

    @Test
    @DisplayName("Seniority comes from the year being accrued, not from today")
    void seniorityComesFromTheAccrualYear() {
        // Olcum "bugune" gore yapilsaydi isin hangi gun kostuguna bagli olarak
        // farkli cevap verirdi; ayni yil icin farkli cevap ureten bir kural,
        // kural degildir.
        awaitingAccrual(employeeHiredOn(LocalDate.of(2032, 2, 1)));
        previousYearGranted(0);

        service.accrueFor(YEAR);

        assertThat(accrued().getEntitledDays()).isEqualTo(14);
    }

    @Test
    @DisplayName("Somebody hired after the year began has no service yet")
    void someoneHiredDuringTheYearHasNoServiceYet() {
        // Yil basindan SONRA ise alinana negatif kidem cikar; sifira cekilmezse
        // merdiven cevapsiz kalir ve tahakkuk isi patlardi.
        awaitingAccrual(employeeHiredOn(LocalDate.of(YEAR, 7, 1)));
        previousYearGranted(0);

        service.accrueFor(YEAR);

        assertThat(accrued().getEntitledDays()).isZero();
    }

    @Test
    @DisplayName("Unused days from last year carry over, up to the cap")
    void unusedDaysCarryOver() {
        awaitingAccrual(grace);
        previousYearGranted(12);

        service.accrueFor(YEAR);

        assertThat(accrued().getCarriedOverDays()).isEqualTo(5);
    }

    @Test
    @DisplayName("Nothing carries over from a year that was never managed")
    void nothingCarriesOverFromAnUnmanagedYear() {
        // Hak satiri olmayan bir yilin bakiyesi yapilandirmadaki VARSAYILANDAN
        // hesaplanir ve o sayi bir karar degil bir TAHMINDIR. Ondan devretmek
        // olmayan bir hakki uretmek olurdu.
        //
        // OLCULDU: ilk tahakkuk kosusunda 33 kisinin HEPSI, hak satiri hic
        // olmayan bir yildan 5 gun devralmisti.
        awaitingAccrual(grace);
        previousYear(20, LeaveBalanceResponse.Source.DEFAULT);

        service.accrueFor(YEAR);

        assertThat(accrued().getCarriedOverDays()).isZero();
    }

    @Test
    @DisplayName("The carry-over is read through the balance service, not recomputed")
    void theCarryOverComesFromTheBalanceService() {
        // Hesap SQL'de tekrarlanSAYDI iki kural olusur ve biri zamanla geride
        // kalirdi -- bu projede dort kez yasanmis bir hata. Sorulan yil da
        // ONCEKI yil olmali.
        awaitingAccrual(grace);
        previousYearGranted(3);

        service.accrueFor(YEAR);

        verify(balances).balanceFor(EMPLOYEE_ID, YEAR - 1);
    }

    @Test
    @DisplayName("An accrued row says it was written automatically")
    void anAccruedRowIsMarked() {
        // Tahakkuk isinin yazdigi satir ile Ik'nin ELLE degistirdigi satir ayni
        // tabloda duruyor; ayirt edilemeselerdi "neden bu kisinin hakki farkli"
        // sorusunun cevabi hicbir yerde olmazdi.
        awaitingAccrual(grace);
        previousYearGranted(0);

        service.accrueFor(YEAR);

        assertThat(accrued().getNote()).contains("Accrued");
    }

    @Test
    @DisplayName("Nothing is written when every employee already has a row")
    void nothingIsWrittenWhenAllRowsExist() {
        // ISIN EN ONEMLI OZELLIGI. Sorgu yalnizca EKSIK satirlari donduruyor,
        // dolayisiyla var olan satira -- yani Ik'nin duzeltmesine -- hicbir
        // zaman dokunulmuyor.
        when(entitlements.findActiveWithoutEntitlement(YEAR)).thenReturn(List.of());

        assertThat(service.accrueFor(YEAR)).isZero();

        verify(entitlements, never()).save(any());
        // Bakiye sorgusu bile atilmamali: bos bir liste icin is yapmak, gunde
        // bir kez de olsa bosuna yuktur.
        verify(balances, never()).balanceFor(anyLong(), anyInt());
    }

    // ------------------------------------------------------- Ik'nin karari

    @Test
    @DisplayName("HR can set an entitlement where none existed")
    void hrCreatesAMissingEntitlement() {
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.of(grace));
        when(entitlements.findByEmployeeIdAndYear(EMPLOYEE_ID, YEAR)).thenReturn(Optional.empty());
        doReturn(new LeaveEntitlement(grace, YEAR, 30, 2, "Negotiated on hire"))
                .when(entitlements).save(any());

        var response = service.set(EMPLOYEE_ID, YEAR,
                new LeaveEntitlementRequest(30, 2, "Negotiated on hire"));

        assertThat(response.entitledDays()).isEqualTo(30);
        assertThat(response.note()).isEqualTo("Negotiated on hire");
    }

    @Test
    @DisplayName("Setting an existing entitlement replaces it instead of adding a second row")
    void settingAnExistingEntitlementReplacesIt() {
        // Ikinci bir satir yazilsaydi veritabani kisiti (employee_id, year)
        // zaten reddederdi -- ama kullaniciya anlamsiz bir catisma hatasi
        // donerdi. Guncelleme dogru davranis.
        LeaveEntitlement existing = new LeaveEntitlement(grace, YEAR, 14, 0, "Accrued automatically");
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.of(grace));
        when(entitlements.findByEmployeeIdAndYear(EMPLOYEE_ID, YEAR)).thenReturn(Optional.of(existing));

        var response = service.set(EMPLOYEE_ID, YEAR,
                new LeaveEntitlementRequest(26, 5, "Long service award"));

        verify(entitlements, never()).save(any());
        assertThat(existing.getEntitledDays()).isEqualTo(26);
        assertThat(existing.getCarriedOverDays()).isEqualTo(5);
        assertThat(response.note()).isEqualTo("Long service award");
    }

    @Test
    @DisplayName("An entitlement cannot be set for an employee who does not exist")
    void anUnknownEmployeeIsRefused() {
        when(employees.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.set(EMPLOYEE_ID, YEAR,
                new LeaveEntitlementRequest(20, 0, "Correction")))
                .isInstanceOf(EmployeeNotFoundException.class);

        verify(entitlements, never()).save(any());
    }
}
