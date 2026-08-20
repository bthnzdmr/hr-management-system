package com.proje.employee.audit;

import com.proje.employee.dto.DepartmentResponse;
import com.proje.employee.dto.SalaryResponse;
import com.proje.employee.dto.EmployeeResponse;
import com.proje.employee.dto.LeaveEntitlementResponse;
import com.proje.employee.dto.LeaveRequestResponse;
import com.proje.employee.dto.UserResponse;
import com.proje.employee.entity.LeaveStatus;
import com.proje.employee.entity.LeaveType;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.TerminationReason;
import com.proje.employee.export.ExportResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Denetim izinde okunan CUMLE.
 *
 * <p>Onceden detay, Java'nin urettigi {@code Type[a=1, b=2]} dokumuydu ve
 * ekranda <i>"kind: ALL · Employee: null · allSalaries: false"</i> gorunuyordu.
 * Iki kusur vardi ve ikisi de okuma aninda kapatilamaz: ic makine
 * kaydediliyordu (orada bilgi yok) ve isim yerine id kaydediliyordu (ad veride
 * hic yoktu).
 *
 * <p>Bu testler her eylemin bir INSAN CUMLESI urettigini tutuyor. Ayrica
 * yapisal bir kural var: cumleler {@code ad=deger} kalibi ICERMEZ -- arayuz
 * eski satirlari o kalibi arayarak ayirt ediyor.
 */
class AuditDetailTest {

    private EmployeeResponse employee(boolean active, String manager,
                                      LocalDate terminatedAt, TerminationReason reason) {
        return new EmployeeResponse(1L, 0L, "Grace", "Hopper", "grace@example.com", null,
                3L, "Software Development", manager == null ? null : 2L, manager,
                "Compiler Engineer", LocalDate.of(2024, 3, 1), active, terminatedAt, reason);
    }

    private LeaveRequestResponse leave(LeaveStatus status, long days) {
        return new LeaveRequestResponse(7L, 1L, "Grace Hopper", LeaveType.ANNUAL, status,
                LocalDate.of(2033, 3, 10), LocalDate.of(2033, 3, 15), days,
                null, null, "hr@example.com", null, null, Instant.now());
    }

    // ------------------------------------------------------------- personel

    @Test
    @DisplayName("A new employee reads as a role in a named department")
    void aNewEmployeeReadsAsARole() {
        // Istekten uretilseydi "Department: 3" yazardi -- cevap ADI tasiyor.
        assertThat(employee(true, "Ada Lovelace", null, null).auditDetail())
                .isEqualTo("Compiler Engineer in Software Development · reports to Ada Lovelace");
    }

    @Test
    @DisplayName("Somebody at the top of the chart has no dangling manager clause")
    void nobodyGetsAnEmptyManagerClause() {
        // Departman baskanlarinin yoneticisi yoktur ve bu bir veri eksikligi
        // degildir; "reports to null" yazmak yalan olurdu.
        assertThat(employee(true, null, null, null).auditDetail())
                .isEqualTo("Compiler Engineer in Software Development");
    }

    @Test
    @DisplayName("Leaving reads as a date and a reason, not as a boolean")
    void leavingReadsAsADateAndReason() {
        // Onceki hali "active=false, reason=RESIGNED" idi.
        assertThat(employee(false, null, LocalDate.of(2026, 8, 19), TerminationReason.RESIGNED)
                .auditDetail())
                .isEqualTo("Left on 19-08-2026 (Resigned)");
    }

    @Test
    @DisplayName("Dates match the format used everywhere else on screen")
    void datesUseTheSameFormatAsTheRestOfTheApp() {
        // ISO daha "sunucu isi" gorunurdu ama bu metin dogrudan bir insana
        // gosteriliyor; ayni ekranda iki farkli tarih bicimi olmamali.
        assertThat(employee(false, null, LocalDate.of(2026, 8, 19), TerminationReason.RETIRED)
                .auditDetail())
                .contains("19-08-2026")
                .doesNotContain("2026-08-19");
    }

    // --------------------------------------------------------------- hesap

    @Test
    @DisplayName("An account reads as its state, its roles and who it belongs to")
    void anAccountReadsAsStateAndRoles() {
        UserResponse user = new UserResponse(1L, "ada@example.com",
                Set.of(Role.HR_SPECIALIST, Role.MANAGER), true, 5L, "Ada Lovelace", null);

        assertThat(user.auditDetail())
                .isEqualTo("Active · Roles: HR specialist, Manager · Linked to Ada Lovelace");
    }

    @Test
    @DisplayName("A service account with no employee record has no dangling link clause")
    void aServiceAccountHasNoLinkClause() {
        UserResponse service = new UserResponse(2L, "notification-service@internal",
                Set.of(Role.SERVICE), true, null, null, null);

        assertThat(service.auditDetail()).isEqualTo("Active · Roles: Service account");
    }

    @Test
    @DisplayName("A disabled account says so first")
    void aDisabledAccountSaysSoFirst() {
        UserResponse disabled = new UserResponse(1L, "ada@example.com",
                Set.of(Role.EMPLOYEE), false, null, null, null);

        assertThat(disabled.auditDetail()).startsWith("Disabled");
    }

    @Test
    @DisplayName("Roles read in a stable order so two entries can be compared")
    void rolesReadInAStableOrder() {
        // Set siralamasi garanti degildir; siralanmasaydi ayni rol kumesi iki
        // kayitta farkli yazilir ve "ne degisti" sorusu cevaplanamazdi.
        UserResponse first = new UserResponse(1L, "a@example.com",
                Set.of(Role.MANAGER, Role.EMPLOYEE), true, null, null, null);
        UserResponse second = new UserResponse(2L, "b@example.com",
                Set.of(Role.EMPLOYEE, Role.MANAGER), true, null, null, null);

        assertThat(first.auditDetail()).isEqualTo(second.auditDetail());
    }

    // ----------------------------------------------------------- departman

    @Test
    @DisplayName("A department reads as open or closed with its headcount")
    void aDepartmentReadsAsStateAndHeadcount() {
        assertThat(new DepartmentResponse(1L, "Sales", true, 4).auditDetail())
                .isEqualTo("Open · 4 people");
        assertThat(new DepartmentResponse(1L, "Sales", false, 0).auditDetail())
                .isEqualTo("Closed · 0 people");
    }

    @Test
    @DisplayName("One person is not written as 1 people")
    void oneIsSingular() {
        assertThat(new DepartmentResponse(1L, "Legal", true, 1).auditDetail())
                .contains("1 person");
    }

    // ----------------------------------------------------------------- izin

    @Test
    @DisplayName("A leave request reads as its decision, its span and its length")
    void leaveReadsAsDecisionSpanAndLength() {
        assertThat(leave(LeaveStatus.APPROVED, 6).auditDetail())
                .isEqualTo("Approved · Annual leave · 10-03-2033 - 15-03-2033 (6 days)");
    }

    @Test
    @DisplayName("A withdrawn request says withdrawn, not approved")
    void aWithdrawnRequestSaysSo() {
        // Karar bilgisi cevabin `status` alanindan geliyor; annotasyondaki
        // summary EKLENMIYOR, yoksa "withdrawn Cancelled ..." diye tekrarlardi.
        assertThat(leave(LeaveStatus.CANCELLED, 6).auditDetail()).startsWith("Cancelled");
    }

    @Test
    @DisplayName("A single day is not written as 1 days")
    void aSingleDayIsSingular() {
        assertThat(leave(LeaveStatus.PENDING, 1).auditDetail()).contains("(1 day)");
    }

    @Test
    @DisplayName("An entitlement reads as the year, the days and the reason")
    void anEntitlementReadsAsYearDaysAndReason() {
        assertThat(new LeaveEntitlementResponse(1L, "Grace Hopper", 2026, 26, 3, "Long service award")
                .auditDetail())
                .isEqualTo("2026 entitlement: 26 days + 3 carried over · Long service award");
    }

    @Test
    @DisplayName("No carry-over is left out rather than written as zero")
    void zeroCarryOverIsLeftOut() {
        assertThat(new LeaveEntitlementResponse(1L, "Grace Hopper", 2026, 14, 0, "Accrued automatically")
                .auditDetail())
                .isEqualTo("2026 entitlement: 14 days · Accrued automatically");
    }

    // ------------------------------------------------------------ aktarma

    @Test
    @DisplayName("An export records how many records left the system")
    void anExportRecordsItsSize() {
        // Bu, iki tur once "bilinen sinir" diye yazilan bosluktu: "kim butun
        // dizini indirdi" sorusu suzgeclerden dolayli cikariliyordu.
        assertThat(new ExportResult("...", 33, null, true).auditDetail())
                .isEqualTo("Exported 33 employee records · Active only");
    }

    @Test
    @DisplayName("An unfiltered export says so instead of leaving it blank")
    void anUnfilteredExportSaysSo() {
        // Bos birakmak "suzgec yok" ile "suzgec kaydedilmedi" arasindaki farki
        // gizlerdi -- toplu veri cikisinda tam da bilinmesi gereken sey.
        assertThat(new ExportResult("...", 33, null, null).auditDetail())
                .isEqualTo("Exported 33 employee records (no filter)");
    }

    @Test
    @DisplayName("A search term is carried into the trail")
    void theSearchTermIsRecorded() {
        assertThat(new ExportResult("...", 2, "hopper", null).auditDetail())
                .contains("Search: hopper");
    }

    // ------------------------------------------------------ yapisal kural

    @Test
    @DisplayName("No sentence contains the key=value shape the old format used")
    void noSentenceLooksLikeTheOldFormat() {
        // Arayuz eski satirlari tam da bu kalibi arayarak ayirt ediyor. Bir
        // cumleye "=" girseydi ayristirici devreye girer ve cumleyi bolerdi.
        assertThat(employee(true, "Ada Lovelace", null, null).auditDetail()).doesNotContain("=");
        assertThat(leave(LeaveStatus.APPROVED, 6).auditDetail()).doesNotContain("=");
        assertThat(new ExportResult("...", 1, null, true).auditDetail()).doesNotContain("=");
        assertThat(new DepartmentResponse(1L, "Sales", true, 4).auditDetail()).doesNotContain("=");
        assertThat(new LeaveEntitlementResponse(1L, "Grace Hopper", 2026, 14, 0, "x").auditDetail())
                .doesNotContain("=");
    }

    // ------------------------------------------------- iz KIMI gosteriyor

    @Test
    @DisplayName("Every audited response says who it is about")
    void everyResponseNamesItsSubject() {
        // Etiket bos kalirsa arayuz "EMPLOYEE #906" gosterir: anlamsiz degil
        // ama bir arama gerektirir. Denetim izinde "kime" sorusu bir tik
        // uzakta olmamali -- ozellikle ucrette.
        assertThat(new SalaryResponse(906L, "Grace Hopper", new BigDecimal("1"))
                .auditLabel()).isEqualTo("Grace Hopper");
        assertThat(new LeaveEntitlementResponse(906L, "Grace Hopper", 2026, 14, 0, "x")
                .auditLabel()).isEqualTo("Grace Hopper");
        assertThat(employee(true, null, null, null).auditLabel()).isEqualTo("Grace Hopper");
        assertThat(leave(LeaveStatus.PENDING, 1).auditLabel()).isEqualTo("Grace Hopper");
        assertThat(new DepartmentResponse(1L, "Sales", true, 4).auditLabel()).isEqualTo("Sales");
    }

    @Test
    @DisplayName("The salary label carries the name but never the amount")
    void theSalaryLabelDoesNotLeakTheAmount() {
        // Etiket de detay kadar gorunur bir alandir; tutari oraya koymak
        // detaydan gizlemeyi anlamsiz kilardi.
        assertThat(new SalaryResponse(906L, "Grace Hopper", new BigDecimal("123456"))
                .auditLabel()).doesNotContain("123456");
    }
}
