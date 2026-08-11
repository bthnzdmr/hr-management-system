package com.proje.employee.service;

import com.proje.employee.entity.Employee;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kimin hangi satirlari gorebildigi.
 *
 * Bu kural SecurityConfig'te YAZILAMAZ: uc bazli bir eslesme "bu kayit senin
 * ekibinde mi" diye soramaz, cunku satiri hic gormez.
 */
class AccessScopeTest {

    private User userWith(Long employeeId, Role... roles) {
        User user = new User("someone@example.com", "hash", User.rolesOf(roles[0],
                java.util.Arrays.copyOfRange(roles, 1, roles.length)));

        if (employeeId != null) {
            Employee employee = new Employee("First", "Last", "e@example.com", null,
                    "Engineer", LocalDate.of(2024, 1, 1));
            ReflectionTestUtils.setField(employee, "id", employeeId);
            user.setEmployee(employee);
        }
        return user;
    }

    @Test
    @DisplayName("An HR specialist sees every record")
    void hrSpecialistSeesEverything() {
        AccessScope scope = AccessScope.forUser(userWith(null, Role.HR_SPECIALIST));

        assertThat(scope.isUnrestricted()).isTrue();
    }

    @Test
    @DisplayName("A system administrator reads the directory but is not an HR specialist")
    void systemAdministratorReadsDirectory() {
        // Hesabi personele baglayabilmek icin kimin var oldugunu bilmeli.
        // Maasa erisimi ayri bir uc kuraliyla engellenir.
        AccessScope scope = AccessScope.forUser(userWith(null, Role.SYSTEM_ADMIN));

        assertThat(scope.isUnrestricted()).isTrue();
    }

    @Test
    @DisplayName("The service account reads the directory so it can name the manager")
    void serviceAccountReadsDirectory() {
        AccessScope scope = AccessScope.forUser(userWith(null, Role.SERVICE));

        assertThat(scope.isUnrestricted()).isTrue();
    }

    @Test
    @DisplayName("A manager sees their own team")
    void managerSeesTeam() {
        AccessScope scope = AccessScope.forUser(userWith(7L, Role.MANAGER));

        assertThat(scope.kind()).isEqualTo(AccessScope.Kind.TEAM);
        assertThat(scope.employeeId()).isEqualTo(7L);
        assertThat(scope.isUnrestricted()).isFalse();
    }

    @Test
    @DisplayName("A plain employee sees only their own record")
    void employeeSeesOnlySelf() {
        AccessScope scope = AccessScope.forUser(userWith(7L, Role.EMPLOYEE));

        assertThat(scope.kind()).isEqualTo(AccessScope.Kind.SELF);
        assertThat(scope.employeeId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("An account with no employee record sees nothing at all")
    void unlinkedAccountSeesNothing() {
        // Hangi kaydin "kendi" oldugu bilinemez. Bos sonuc dogru cevaptir;
        // varsayilan olarak HERKESI gostermek sessiz bir veri sizintisi olurdu.
        AccessScope scope = AccessScope.forUser(userWith(null, Role.EMPLOYEE));

        assertThat(scope.isEmpty()).isTrue();
        assertThat(scope.isUnrestricted()).isFalse();
    }

    @Test
    @DisplayName("The wider role wins when an account carries several")
    void widerRoleWins() {
        // Ayni kisi hem kendi ekibinin yoneticisi hem Ik uzmani olabilir;
        // dar kapsam uygulanirsa Ik uzmanligi islevsiz kalirdi.
        AccessScope scope = AccessScope.forUser(
                userWith(7L, Role.MANAGER, Role.HR_SPECIALIST));

        assertThat(scope.isUnrestricted()).isTrue();
    }

    @Test
    @DisplayName("A manager who is also an employee still gets the team scope")
    void managerRoleBeatsEmployeeRole() {
        AccessScope scope = AccessScope.forUser(userWith(7L, Role.EMPLOYEE, Role.MANAGER));

        assertThat(scope.kind()).isEqualTo(AccessScope.Kind.TEAM);
    }
}
