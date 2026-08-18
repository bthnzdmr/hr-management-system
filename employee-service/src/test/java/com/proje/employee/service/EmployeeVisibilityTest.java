package com.proje.employee.service;

import com.proje.employee.entity.Employee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.proje.employee.repository.EmployeeRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kapsam olcumu bu sinifi %10'da buldu ve o rakam rahatsiz ediciydi: burasi
 * "kim kimin kaydini gorebilir" sorusunun TEK cevabi, yani yetkilendirmenin
 * yatay ekseni.
 *
 * Uctan uca testler kurallari kapsiyordu ama onlar ayri bir Maven projesinde
 * kosuyor ve bu modulun kapsamina hic yansimiyor. Daha onemlisi: uctan uca bir
 * kosu "yonetici torunlarini gormuyor" gibi bir KENAR durumu ancak o veri
 * kurulmussa sinar. Burada kenar durum dogrudan yaziliyor.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmployeeVisibilityTest {

    @Mock
    private EmployeeRepository employees;

    private EmployeeVisibility visibility;

    @BeforeEach
    void setUp() {
        visibility = new EmployeeVisibility(employees);
    }

    /**
     * Sahte personel listesi.
     *
     * Tek tek `when(...)` cagrilari DISARIDA yapilir: `when(repo...)`
     * ifadesinin ICINDE yeni bir stub kurmak Mockito'da yarim kalmis
     * stub'a yol acar (UnfinishedStubbing) ve testler kodu hic calistiramaz.
     */
    private static List<Employee> employeesWithIds(Long... ids) {
        List<Employee> result = new java.util.ArrayList<>();

        for (Long id : ids) {
            Employee employee = mock(Employee.class);
            when(employee.getId()).thenReturn(id);
            result.add(employee);
        }

        return result;
    }

    private static AccessScope all() {
        return new AccessScope(AccessScope.Kind.ALL, null, false);
    }

    private static AccessScope team(Long employeeId) {
        return new AccessScope(AccessScope.Kind.TEAM, employeeId, false);
    }

    private static AccessScope self(Long employeeId) {
        return new AccessScope(AccessScope.Kind.SELF, employeeId, false);
    }

    @Test
    @DisplayName("Puts no filter on an unrestricted scope")
    void unrestrictedScopeHasNoFilter() {
        // `null` "filtre yok" demektir, "hicbiri" DEGIL. Bu ayrim yanlis
        // anlasilirsa Ik hicbir kayit goremez hale gelir.
        assertThat(visibility.visibleEmployeeIds(all())).isNull();
        verify(employees, never()).findByManagerIdOrderByLastNameAsc(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Limits a plain employee to their own record")
    void selfScopeSeesOnlyItself() {
        assertThat(visibility.visibleEmployeeIds(self(7L))).containsExactly(7L);
        // Ast sorgusu HIC atilmamali: atilsaydi bos donerdi ve sonuc yine
        // dogru gorunurdu, ama her istekte gereksiz bir sorgu olurdu.
        verify(employees, never()).findByManagerIdOrderByLastNameAsc(7L);
    }

    @Test
    @DisplayName("Gives a manager their own record and their direct reports")
    void teamScopeSeesSelfAndDirectReports() {
        List<Employee> reports = employeesWithIds(11L, 12L);
        when(employees.findByManagerIdOrderByLastNameAsc(5L)).thenReturn(reports);

        assertThat(visibility.visibleEmployeeIds(team(5L)))
                .containsExactlyInAnyOrder(5L, 11L, 12L);
    }

    @Test
    @DisplayName("Stops at direct reports and does not reach grandchildren")
    void teamScopeDoesNotReachGrandchildren() {
        // OLCULMUS BIR SIZINTI: ayni hata `getDirectReports` ucunda bir kez
        // yasandi. Astin astini gormek, kapsami sessizce butun agaca acardi.
        List<Employee> reports = employeesWithIds(11L);
        List<Employee> grandchildren = employeesWithIds(99L);
        when(employees.findByManagerIdOrderByLastNameAsc(5L)).thenReturn(reports);
        when(employees.findByManagerIdOrderByLastNameAsc(11L)).thenReturn(grandchildren);

        assertThat(visibility.visibleEmployeeIds(team(5L))).doesNotContain(99L);
        verify(employees, never()).findByManagerIdOrderByLastNameAsc(11L);
    }

    @Test
    @DisplayName("Says yes to anything when the scope is unrestricted")
    void unrestrictedScopeCanSeeAnyone() {
        assertThat(visibility.canSee(12345L, all())).isTrue();
    }

    @Test
    @DisplayName("Says no to everything when the scope has no employee behind it")
    void emptyScopeSeesNothing() {
        // Personel kaydi olmayan bir hesap (ornegin dis denetci) hicbir kaydi
        // gormemeli. Bos kapsam "sinirsiz" ile KARISTIRILMAMALI; karistirilsaydi
        // en dar kullanici en genis yetkiyi alirdi.
        AccessScope orphan = new AccessScope(AccessScope.Kind.SELF, null, false);

        assertThat(orphan.isEmpty()).isTrue();
        assertThat(visibility.canSee(1L, orphan)).isFalse();
    }

    @Test
    @DisplayName("Lets a manager see a direct report but not a stranger")
    void managerSeesReportButNotStranger() {
        List<Employee> reports = employeesWithIds(11L);
        when(employees.findByManagerIdOrderByLastNameAsc(5L)).thenReturn(reports);

        assertThat(visibility.canSee(11L, team(5L))).isTrue();
        assertThat(visibility.canSee(5L, team(5L))).isTrue();
        assertThat(visibility.canSee(77L, team(5L))).isFalse();
    }

    @Test
    @DisplayName("Lets an employee see their own record and nobody else's")
    void employeeSeesOnlyItself() {
        assertThat(visibility.canSee(7L, self(7L))).isTrue();
        assertThat(visibility.canSee(8L, self(7L))).isFalse();
    }
}
