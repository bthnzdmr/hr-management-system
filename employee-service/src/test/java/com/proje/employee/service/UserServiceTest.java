package com.proje.employee.service;

import com.proje.employee.dto.PasswordChangeRequest;
import com.proje.employee.dto.UserCreateRequest;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import com.proje.employee.exception.EmailAlreadyExistsException;
import com.proje.employee.exception.InvalidPasswordException;
import com.proje.employee.exception.UserNotFoundException;
import com.proje.employee.exception.UserRuleViolationException;
import com.proje.employee.mapper.UserMapper;
import com.proje.employee.repository.EmployeeRepository;
import com.proje.employee.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private final UserMapper userMapper = new UserMapper();

    private UserService service() {
        return new UserService(userRepository, employeeRepository, refreshTokenService,
                passwordEncoder, userMapper);
    }

    private User user(Long id, String email, Role role, boolean active) {
        User user = new User(email, "stored-hash", role);
        ReflectionTestUtils.setField(user, "id", id);
        user.setActive(active);
        return user;
    }

    /** Yonetim uclari kaydi id ile bulur. */
    private void existing(User user) {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    /** Parola degistirme kaydi e-posta ile bulur: "/me" token'in sahibidir. */
    private void signedIn(User user) {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("Stores the password as a hash, never as given")
    void storesHashedPassword() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("correct horse battery")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        service().create(new UserCreateRequest(
                "new@example.com", "correct horse battery", Role.USER, null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
    }

    @Test
    @DisplayName("Never returns the password hash in the response")
    void neverReturnsPasswordHash() {
        // Bir kez cevaba girerse her istemciye, her loga ve her tarayici
        // gecmisine girer.
        User ada = user(1L, "ada@example.com", Role.USER, true);

        assertThat(userMapper.toResponse(ada).toString()).doesNotContain("stored-hash");
    }

    @Test
    @DisplayName("Rejects an account whose email is already registered")
    void rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service().create(
                new UserCreateRequest("taken@example.com", "a-long-password", Role.USER, null)))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reports a missing account instead of failing obscurely")
    void reportsMissingAccount() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().changeStatus(99L, false, "admin@example.com"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("Refuses to let an administrator deactivate their own account")
    void refusesSelfDeactivation() {
        // Kendini disari kilitlemek geri alinamaz: hesabi geri acacak kimse
        // kalmayabilir.
        User admin = user(1L, "admin@example.com", Role.ADMIN, true);
        existing(admin);

        assertThatThrownBy(() -> service().changeStatus(1L, false, "admin@example.com"))
                .isInstanceOf(UserRuleViolationException.class)
                .hasMessageContaining("your own account");

        assertThat(admin.isActive()).isTrue();
    }

    @Test
    @DisplayName("Refuses to let an administrator drop their own role")
    void refusesSelfDemotion() {
        User admin = user(1L, "admin@example.com", Role.ADMIN, true);
        existing(admin);

        assertThatThrownBy(() -> service().changeRole(1L, Role.USER, "admin@example.com"))
                .isInstanceOf(UserRuleViolationException.class);

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("Refuses to deactivate the last active administrator")
    void refusesToRemoveLastAdmin() {
        // Sistem yoneticisiz kalirsa hicbir hesap acilamaz, rol degistirilemez.
        User lastAdmin = user(1L, "last@example.com", Role.ADMIN, true);
        existing(lastAdmin);
        when(userRepository.findActiveByRoleForUpdate(Role.ADMIN)).thenReturn(List.of(lastAdmin));

        assertThatThrownBy(() -> service().changeStatus(1L, false, "other@example.com"))
                .isInstanceOf(UserRuleViolationException.class)
                .hasMessageContaining("administrator");

        assertThat(lastAdmin.isActive()).isTrue();
    }

    @Test
    @DisplayName("Allows deactivating an administrator while another one remains")
    void allowsDeactivationWhenAnotherAdminRemains() {
        User target = user(1L, "one@example.com", Role.ADMIN, true);
        User other = user(2L, "two@example.com", Role.ADMIN, true);
        existing(target);
        when(userRepository.findActiveByRoleForUpdate(Role.ADMIN))
                .thenReturn(List.of(target, other));

        service().changeStatus(1L, false, "two@example.com");

        assertThat(target.isActive()).isFalse();
    }

    @Test
    @DisplayName("Revokes the sessions of a deactivated account")
    void revokesSessionsOnDeactivation() {
        // Yalnizca girisi engellemek yetmez: elindeki yenileme jetonuyla
        // oturumunu suresiz surdururdu.
        User target = user(5L, "leaver@example.com", Role.USER, true);
        existing(target);

        service().changeStatus(5L, false, "admin@example.com");

        verify(refreshTokenService).revokeAllFor(eq(5L), anyString());
    }

    @Test
    @DisplayName("Changes nothing and revokes nothing when the status already matches")
    void statusChangeIsIdempotent() {
        User target = user(5L, "leaver@example.com", Role.USER, false);
        existing(target);

        service().changeStatus(5L, false, "admin@example.com");

        verify(refreshTokenService, never()).revokeAllFor(anyLong(), anyString());
    }

    @Test
    @DisplayName("Revokes the sessions of a user whose role changed")
    void revokesSessionsOnRoleChange() {
        // Rol JWT'nin icinde tasiniyor; jetonlari iptal etmek pencereyi
        // erisim jetonunun omruyle sinirlar.
        User target = user(5L, "promoted@example.com", Role.USER, true);
        existing(target);

        service().changeRole(5L, Role.ADMIN, "admin@example.com");

        assertThat(target.getRole()).isEqualTo(Role.ADMIN);
        verify(refreshTokenService).revokeAllFor(eq(5L), anyString());
    }

    @Test
    @DisplayName("Refuses a password change when the current password is wrong")
    void refusesWrongCurrentPassword() {
        User ada = user(1L, "ada@example.com", Role.USER, true);
        signedIn(ada);
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> service().changeOwnPassword("ada@example.com",
                new PasswordChangeRequest("wrong", "a-brand-new-password")))
                .isInstanceOf(InvalidPasswordException.class);

        assertThat(ada.getPasswordHash()).isEqualTo("stored-hash");
        verify(refreshTokenService, never()).revokeAllFor(anyLong(), anyString());
    }

    @Test
    @DisplayName("Revokes every session when the password changes")
    void revokesSessionsOnPasswordChange() {
        // Parola degistirmenin amaci zaten budur: baskasinin elindeki her sey
        // gecersiz olsun. Oturumlar acik kalsaydi islem bir sey ifade etmezdi.
        User ada = user(1L, "ada@example.com", Role.USER, true);
        signedIn(ada);
        when(passwordEncoder.matches("current-password", "stored-hash")).thenReturn(true);
        when(passwordEncoder.encode("a-brand-new-password")).thenReturn("new-hash");

        service().changeOwnPassword("ada@example.com",
                new PasswordChangeRequest("current-password", "a-brand-new-password"));

        assertThat(ada.getPasswordHash()).isEqualTo("new-hash");
        verify(refreshTokenService).revokeAllFor(eq(1L), anyString());
    }
}
