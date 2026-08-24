package com.proje.employee.config;

import com.proje.employee.service.PasswordPolicy;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import com.proje.employee.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments arguments;

    // GERCEK politika: taklit edilseydi tohumlayicidaki uyari yolu hic
    // calismaz ve zayif bir parola sessizce gecerdi.
    private final UserSeeder userSeeder = new UserSeeder(new PasswordPolicy());

    @Test
    @DisplayName("Creates the read-only account as an employee, with no elevated role")
    void createsReadOnlyAccountWithEmployeeRole() throws Exception {
        // Rol ayrimini elle denemek icin gereken hesap budur; Ik uzmani olarak
        // olusturulsaydi test ettigimiz kisitlama hic devreye girmezdi.
        when(userRepository.existsByEmail("viewer@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");

        userSeeder.seedReadOnlyUser(userRepository, passwordEncoder, "viewer@example.com", "secret")
                .run(arguments);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());

        assertThat(saved.getValue().getRoles()).containsExactly(Role.EMPLOYEE);
        assertThat(saved.getValue().getEmail()).isEqualTo("viewer@example.com");
        // Parola ASLA duz metin yazilmaz.
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    @DisplayName("Gives the first account both the HR and the system administrator role")
    void firstAccountCarriesBothJobs() throws Exception {
        // Yumurta-tavuk: sistemi ayaga kaldiran kisi hem Ik verisini yonetmeli
        // hem de ikinci hesabi acabilmeli.
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");

        userSeeder.seedAdmin(userRepository, passwordEncoder, "admin@example.com", "secret")
                .run(arguments);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());

        assertThat(saved.getValue().getRoles())
                .containsExactlyInAnyOrder(Role.HR_SPECIALIST, Role.SYSTEM_ADMIN);
    }

    @Test
    @DisplayName("Resets the service account's roles when they drifted")
    void reconcilesServiceAccountRoles() throws Exception {
        // Makine kimliginin rolu yapilandirmadir. Roller bolundugunde bu hesap
        // migration'da EMPLOYEE'ye dustu ve rehberi okuyamaz hale geldi;
        // bildirim yine giderdi ama yonetici CC'si SESSIZCE calismazdi.
        User drifted = new User("svc@internal", "hashed", User.rolesOf(Role.EMPLOYEE));
        when(userRepository.existsByEmail("svc@internal")).thenReturn(true);
        when(userRepository.findByEmail("svc@internal")).thenReturn(Optional.of(drifted));

        userSeeder.seedServiceAccount(userRepository, passwordEncoder, "svc@internal", "secret")
                .run(arguments);

        assertThat(drifted.getRoles()).containsExactly(Role.SERVICE);
        verify(userRepository).save(drifted);
    }

    @Test
    @DisplayName("Leaves the service account alone when its roles are already correct")
    void leavesCorrectServiceRolesAlone() throws Exception {
        User correct = new User("svc@internal", "hashed", User.rolesOf(Role.SERVICE));
        when(userRepository.existsByEmail("svc@internal")).thenReturn(true);
        when(userRepository.findByEmail("svc@internal")).thenReturn(Optional.of(correct));

        userSeeder.seedServiceAccount(userRepository, passwordEncoder, "svc@internal", "secret")
                .run(arguments);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Gives the service account only the directory-reading role")
    void serviceAccountGetsLeastPrivilege() throws Exception {
        // EMPLOYEE verilseydi personel kaydi olmadigi icin hicbir sey goremez
        // ve yonetici CC'si sessizce calismazdi; HR_SPECIALIST verilseydi
        // bildirim gonderen bir program personel silebilirdi.
        when(userRepository.existsByEmail("svc@internal")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");

        userSeeder.seedServiceAccount(userRepository, passwordEncoder, "svc@internal", "secret")
                .run(arguments);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());

        assertThat(saved.getValue().getRoles()).containsExactly(Role.SERVICE);
    }

    @Test
    @DisplayName("Creates no read-only account when the variables are not set")
    void createsNothingWithoutCredentials() throws Exception {
        userSeeder.seedReadOnlyUser(userRepository, passwordEncoder, "", "").run(arguments);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Leaves an existing account untouched instead of resetting its password")
    void leavesExistingAccountAlone() throws Exception {
        // Aksi halde uygulamayi yeniden baslatabilen herkes parolayi sifirlar
        // ve uygulama icinden degistirilen parola her acilista geri gelirdi.
        when(userRepository.existsByEmail("viewer@example.com")).thenReturn(true);

        userSeeder.seedReadOnlyUser(userRepository, passwordEncoder, "viewer@example.com", "secret")
                .run(arguments);

        verify(userRepository, never()).save(any());
    }
}
