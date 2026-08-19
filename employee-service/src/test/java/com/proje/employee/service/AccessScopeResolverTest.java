package com.proje.employee.service;

import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import com.proje.employee.exception.StaleCredentialsException;
import com.proje.employee.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kapsamin nereden hesaplandigi.
 *
 * <p>{@code AccessScope.forUser} %100 kapsamda -- yani "hangi rol hangi
 * kapsami alir" sinaniyordu. Bu sinif ise %15'teydi ve arasindaki fark
 * onemli: cozucu, kapsamin <b>her istekte veritabanindan</b> okunmasini
 * saglayan yerdir. Bozulursa kapsam yanlis degil <b>BAYAT</b> olur ve o,
 * hicbir yetkilendirme testinin sormadigi sorudur.
 *
 * <p>Ustelik iki olculmus hata tam olarak burada yasadi: silinmis hesabin
 * {@code 500} donmesi ve kimligi gorunurluk alanindan okumak.
 */
@ExtendWith(MockitoExtension.class)
class AccessScopeResolverTest {

    private static final String EMAIL = "ada@example.com";

    @Mock
    private UserRepository userRepository;

    private AccessScopeResolver resolver;
    private Principal caller;

    @BeforeEach
    void setUp() {
        resolver = new AccessScopeResolver(userRepository);
        caller = () -> EMAIL;
    }

    private Employee employee(long id) {
        Employee e = new Employee("Ada", "Lovelace", EMAIL,
                new Department("Software Development"), "Engineer", LocalDate.now());
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private User accountOf(Employee employee, Role... roles) {
        User user = new User(EMAIL, "hash", Set.of(roles));
        user.setEmployee(employee);
        return user;
    }

    private void accountExists(User user) {
        org.mockito.Mockito.when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("Reads the account on every call so the scope is never stale")
    void readsTheAccountOnEveryCall() {
        // SINIFIN VARLIK SEBEBI. Kapsam token'a yazilsaydi jetonun omru boyunca
        // DONARDI: kisi yoneticilikten alindiginda elindeki jeton hala TEAM
        // kapsami tasirdi. Bedeli her istekte bir sorgu, kazanci guncellik.
        accountExists(accountOf(employee(212L), Role.MANAGER));

        resolver.resolve(caller);
        resolver.resolve(caller);

        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.times(2))
                .findByEmail(EMAIL);
    }

    @Test
    @DisplayName("A manager gets a team scope carrying their own employee id")
    void aManagerGetsATeamScope() {
        accountExists(accountOf(employee(212L), Role.MANAGER));

        AccessScope scope = resolver.resolve(caller);

        assertThat(scope.kind()).isEqualTo(AccessScope.Kind.TEAM);
        assertThat(scope.employeeId()).isEqualTo(212L);
    }

    @Test
    @DisplayName("An account with a role but no employee record can see nobody")
    void anAccountWithNoEmployeeRecordSeesNobody() {
        // Dis denetci veya danisman: hesabi var, personel kaydi yok. Bos kapsam
        // "hata yok ama veri de yok" demektir ve dogrusu budur.
        accountExists(accountOf(null, Role.EMPLOYEE));

        assertThat(resolver.resolve(caller).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("A deleted account behind a valid token is a client error, not a server fault")
    void aDeletedAccountIsAClientError() {
        // OLCULDU: once IllegalStateException firlatiliyor ve catch-all
        // uzerinden 500 donuyordu. Izleme kirlenirdi -- uyari kurulmus bir
        // sistemde OLMAYAN bir arizayi bildiren alarm demektir.
        org.mockito.Mockito.when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(caller))
                .isInstanceOf(StaleCredentialsException.class);
    }

    @Test
    @DisplayName("Asking who the caller is fails the same way for a deleted account")
    void identityAlsoFailsForADeletedAccount() {
        // Iki yol da ayni yardimciyi kullanmali. Ayri ayri yazilsaydi biri
        // 401, digeri 500 donerdi -- ayni sebep, iki farkli cevap.
        org.mockito.Mockito.when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.selfEmployeeId(caller))
                .isInstanceOf(StaleCredentialsException.class);
    }

    @Test
    @DisplayName("An unrestricted caller still has an identity of their own")
    void anUnrestrictedCallerStillHasAnIdentity() {
        // OLCULMUS HATA. Kimlik once `visibleEmployeeId()`den okunuyordu ve o
        // alan sinirsiz kapsamda BILEREK null'dir -- kisiyi kisitlayacak bir
        // filtre yoktur. Sonucu: personele bagli bir Ik uzmani KENDI kaydini
        // isteyince 404 aliyordu.
        //
        // Gorunurluk ile kimlik farkli sorulardir; ayni alandan cevaplanamaz.
        User hr = accountOf(employee(218L), Role.HR_SPECIALIST);
        accountExists(hr);

        assertThat(resolver.resolve(caller).visibleEmployeeId()).isNull();
        assertThat(resolver.selfEmployeeId(caller)).isEqualTo(218L);
    }

    @Test
    @DisplayName("An account with no employee record has no identity either")
    void anAccountWithNoEmployeeRecordHasNoIdentity() {
        // Servis hesaplari boyledir. null donmek dogru: uydurulmus bir kimlik,
        // olmayan bir kisinin kaydini acardi.
        accountExists(accountOf(null, Role.SERVICE));

        assertThat(resolver.selfEmployeeId(caller)).isNull();
    }
}
