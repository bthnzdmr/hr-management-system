package com.proje.employee.service;

import com.proje.employee.dto.PasswordChangeRequest;
import com.proje.employee.dto.UserCreateRequest;
import com.proje.employee.dto.UserResponse;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import com.proje.employee.exception.EmailAlreadyExistsException;
import com.proje.employee.exception.EmployeeNotFoundException;
import com.proje.employee.exception.InvalidPasswordException;
import com.proje.employee.exception.UserNotFoundException;
import com.proje.employee.exception.UserRuleViolationException;
import com.proje.employee.mapper.UserMapper;
import com.proje.employee.repository.EmployeeRepository;
import com.proje.employee.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository,
                       EmployeeRepository employeeRepository,
                       RefreshTokenService refreshTokenService,
                       PasswordEncoder passwordEncoder,
                       UserMapper userMapper) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAll(Pageable pageable) {
        return userRepository.findAllWithEmployee(pageable).map(userMapper::toResponse);
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        // Kullaniciya anlamli mesaj donmek icin. Dogruluk garantisi bu kontrol
        // degil, veritabanindaki uk_users_email kisitidir.
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role());

        if (request.employeeId() != null) {
            Employee employee = employeeRepository.findById(request.employeeId())
                    .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));
            user.setEmployee(employee);
        }

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse changeRole(Long id, Role role, String actingUserEmail) {
        User user = find(id);

        // Kendi rolunu dusurmek, yonetici arayuzunu kendine kapatmaktir.
        // Baska bir yonetici geri veremezse sistem yonetilemez hale gelir.
        if (user.getEmail().equals(actingUserEmail) && role != Role.ADMIN) {
            throw new UserRuleViolationException("You cannot remove your own administrator role");
        }

        if (user.getRole() == role) {
            return userMapper.toResponse(user);
        }

        if (user.getRole() == Role.ADMIN) {
            assertNotLastActiveAdmin(user);
        }

        user.setRole(role);

        // Rol JWT'nin ICINDE tasiniyor: dusurulen kullanicinin elindeki token
        // suresi dolana kadar hala ADMIN diyor. Yenileme jetonlarini iptal etmek
        // pencereyi 15 dakikayla sinirlar -- kapatmaz. JWT'nin bilinen bedeli.
        refreshTokenService.revokeAllFor(user.getId(), "role changed");

        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse changeStatus(Long id, boolean active, String actingUserEmail) {
        User user = find(id);

        if (user.getEmail().equals(actingUserEmail) && !active) {
            throw new UserRuleViolationException("You cannot deactivate your own account");
        }

        if (user.isActive() == active) {
            return userMapper.toResponse(user);
        }

        if (!active && user.getRole() == Role.ADMIN) {
            assertNotLastActiveAdmin(user);
        }

        user.setActive(active);

        if (!active) {
            // Hesabi kapatmanin anlami budur. Yalnizca girisi engellemek yetmez:
            // elindeki yenileme jetonuyla oturumunu suresiz surdururdu.
            refreshTokenService.revokeAllFor(user.getId(), "account deactivated");
        }

        return userMapper.toResponse(user);
    }

    @Transactional
    public void changeOwnPassword(String email, PasswordChangeRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserRuleViolationException("Account no longer exists"));

        // Mevcut parola sorulur: oturum acilmis bir tarayiciyi ele geciren biri,
        // parolayi bilmeden yenisini belirleyip hesabi kalicilastirabilirdi.
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordException();
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));

        // Parola degistirmenin amaci zaten budur: "baskasinin elindeki her sey
        // gecersiz olsun". Oturumlar acik kalsaydi islem bir sey ifade etmezdi.
        refreshTokenService.revokeAllFor(user.getId(), "password changed");
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * Sistemde en az bir aktif yonetici kalmasini garanti eder.
     *
     * Sorgu satirlari KILITLER: kilitsiz olsaydi iki yonetici ayni anda birbirini
     * dusurur, ikisi de "hala iki admin var" gorur ve sistem yoneticisiz kalirdi.
     */
    private void assertNotLastActiveAdmin(User target) {
        boolean anotherAdminRemains = userRepository.findActiveByRoleForUpdate(Role.ADMIN).stream()
                .anyMatch(admin -> !admin.getId().equals(target.getId()));

        if (!anotherAdminRemains) {
            throw new UserRuleViolationException("At least one active administrator must remain");
        }
    }
}
