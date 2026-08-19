package com.proje.employee.config;

import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import com.proje.employee.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Girisin ilk adimi: hesabi yukleyip Spring Security'nin anlayacagi hale
 * cevirmek.
 *
 * <p>Kapsam olcumu bu sinifi %12,5'te buldu -- kod tabanindaki en dusuk deger.
 * Sinif kucuk ama <b>kimlik dogrulamasinin girisinde</b> duruyor ve uc ayri
 * seyi ceviriyor: hesap var mi, ACIK mi, hangi yetkileri tasiyor. Ucunun de
 * bozulmasi ayni sonucu verir: birinin girmemesi gerekirken girmesi.
 */
@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    private static final String EMAIL = "ada@example.com";
    private static final String HASH = "$2a$10$notarealbcrypthashbutlongenough";

    @Mock
    private UserRepository userRepository;

    private AppUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new AppUserDetailsService(userRepository);
    }

    private UserDetails load(User user) {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        return service.loadUserByUsername(EMAIL);
    }

    private User account(Role... roles) {
        return new User(EMAIL, HASH, Set.of(roles));
    }

    @Test
    @DisplayName("Hands the stored hash to the framework, never a plaintext password")
    void handsOverTheStoredHash() {
        // Parola karsilastirmasini cerceve yapar; buradan cikan sey daima
        // ozettir. Duz metin gecseydi BCrypt dogrulamasi sessizce basarisiz
        // olur ve kimse giremezdi.
        assertThat(load(account(Role.EMPLOYEE)).getPassword()).isEqualTo(HASH);
    }

    @Test
    @DisplayName("An employee who has left cannot sign in")
    void aClosedAccountIsDisabled() {
        // OLCULMUS ACIK. Personel "ayrildi" isaretlendiginde hesabi da
        // kapatiliyor (JML'in leaver adimi) ama o kapatma BURADA okunmazsa
        // hicbir sey yapmaz: sahipsiz hesap, iceriden tehdidin en bilinen
        // kaynagidir.
        User user = account(Role.EMPLOYEE);
        user.setActive(false);

        assertThat(load(user).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("An open account is enabled")
    void anOpenAccountIsEnabled() {
        // Karsi kosul da sinanmali: her hesabi kapali dondurmek de testi
        // yukaridaki iddiadan gecirirdi.
        assertThat(load(account(Role.EMPLOYEE)).isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Roles are handed over with the prefix the security rules expect")
    void rolesCarryTheExpectedPrefix() {
        // SecurityConfig `hasRole("HR_SPECIALIST")` yaziyor ve Spring bunu
        // ROLE_HR_SPECIALIST diye arar. Onek dusseydi HICBIR kural eslesmez ve
        // yetkisi olan herkes 403 alirdi.
        assertThat(load(account(Role.HR_SPECIALIST)).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_HR_SPECIALIST");
    }

    @Test
    @DisplayName("Every role of a multi-role account is carried over")
    void allRolesAreCarriedOver() {
        // Ikisini birden yapan kisi IKI role birden sahiptir. Biri dusseydi
        // kullanici sebebini goremeyecegi bir 403 alirdi.
        assertThat(load(account(Role.HR_SPECIALIST, Role.MANAGER)).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_HR_SPECIALIST", "ROLE_MANAGER");
    }

    @Test
    @DisplayName("The username is the address as stored, not as typed")
    void theUsernameComesFromTheRecord() {
        // Sonraki her adim (kapsam cozumu, denetim izi) bu ada gore calisir.
        assertThat(load(account(Role.EMPLOYEE)).getUsername()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("An unknown address is refused without confirming whether it exists")
    void anUnknownAddressSaysNothingAboutExistence() {
        // Mesaj kasitli olarak belirsiz: "boyle bir hesap yok" demek, elindeki
        // e-posta listesini deneyen birine gecerli adres listesi cikartirdi.
        // Ayni ilke sifirlama ucunda ve hiz sinirinda da uygulaniyor.
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername(EMAIL))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Invalid credentials");
    }
}
