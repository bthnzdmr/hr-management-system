package com.proje.employee.config;

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

    @InjectMocks
    private UserSeeder userSeeder;

    @Test
    @DisplayName("Creates the read-only account with the USER role, not ADMIN")
    void createsReadOnlyAccountWithUserRole() throws Exception {
        // Rol ayrimini elle denemek icin gereken hesap budur; ADMIN olarak
        // olusturulsaydi test ettigimiz kisitlama hic devreye girmezdi.
        when(userRepository.existsByEmail("viewer@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");

        userSeeder.seedReadOnlyUser(userRepository, passwordEncoder, "viewer@example.com", "secret")
                .run(arguments);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());

        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(saved.getValue().getEmail()).isEqualTo("viewer@example.com");
        // Parola ASLA duz metin yazilmaz.
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
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
