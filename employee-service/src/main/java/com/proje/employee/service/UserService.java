package com.proje.employee.service;

import com.proje.employee.audit.AuditAction;
import com.proje.employee.audit.Auditable;
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

import java.util.Set;

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
    @Auditable(action = AuditAction.ACCOUNT_CREATED, targetType = "USER")
    public UserResponse create(UserCreateRequest request) {
        // Kullaniciya anlamli mesaj donmek icin. Dogruluk garantisi bu kontrol
        // degil, veritabanindaki uk_users_email kisitidir.
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.roles());

        if (request.employeeId() != null) {
            Employee employee = employeeRepository.findById(request.employeeId())
                    .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));
            user.setEmployee(employee);
        }

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    @Auditable(action = AuditAction.ROLES_CHANGED, targetType = "USER")
    public UserResponse changeRoles(Long id, Set<Role> roles, String actingUserEmail) {
        User user = find(id);

        // Kendi sistem yoneticiligini birakmak, hesap yonetimini kendine
        // kapatmaktir. Baska bir yonetici geri veremezse sistem yonetilemez.
        if (user.getEmail().equals(actingUserEmail) && !roles.contains(Role.SYSTEM_ADMIN)) {
            throw new UserRuleViolationException(
                    "You cannot remove your own system administrator role");
        }

        if (user.getRoles().equals(roles)) {
            return userMapper.toResponse(user);
        }

        // Yalnizca SYSTEM_ADMIN kaybediliyorsa kontrol edilir: Ik uzmanligini
        // birakmak sistemi yonetilemez hale getirmez.
        if (user.hasRole(Role.SYSTEM_ADMIN) && !roles.contains(Role.SYSTEM_ADMIN)) {
            assertNotLastActiveSystemAdmin(user);
        }

        user.setRoles(roles);

        // Rol JWT'nin ICINDE tasiniyor: dusurulen kullanicinin elindeki token
        // suresi dolana kadar hala ADMIN diyor. Yenileme jetonlarini iptal etmek
        // pencereyi 15 dakikayla sinirlar -- kapatmaz. JWT'nin bilinen bedeli.
        refreshTokenService.revokeAllFor(user.getId(), "role changed");

        return userMapper.toResponse(user);
    }

    @Transactional
    @Auditable(action = AuditAction.ACCOUNT_STATUS_CHANGED, targetType = "USER")
    public UserResponse changeStatus(Long id, boolean active, String actingUserEmail) {
        User user = find(id);

        if (user.getEmail().equals(actingUserEmail) && !active) {
            throw new UserRuleViolationException("You cannot deactivate your own account");
        }

        if (user.isActive() == active) {
            return userMapper.toResponse(user);
        }

        if (!active && user.hasRole(Role.SYSTEM_ADMIN)) {
            assertNotLastActiveSystemAdmin(user);
        }

        user.setActive(active);

        if (!active) {
            // Hesabi kapatmanin anlami budur. Yalnizca girisi engellemek yetmez:
            // elindeki yenileme jetonuyla oturumunu suresiz surdururdu.
            refreshTokenService.revokeAllFor(user.getId(), "account deactivated");
        }

        return userMapper.toResponse(user);
    }

    /**
     * Personel isten ayrildiginda ona bagli hesabi kapatir (JML "leaver" adimi).
     *
     * Olculdu: bu adim olmadan ayrilan personelin hesabiyla giris yapilabiliyordu.
     * Mekanizma zaten vardi -- hesap pasiflestirildiginde oturumlar iptal
     * ediliyordu -- yalnizca personel ayrilisi ona baglanmamisti. Sahipsiz
     * hesap (orphaned account), iceriden tehdidin en bilinen kaynagidir.
     *
     * Yeniden ise alimda hesap KENDILIGINDEN acilmaz: erisimi geri vermek
     * bilincli bir karar olmali. Guvenlikte varsayilan "kapali"dir.
     */
    @Transactional
    public void disableAccountOf(Long employeeId) {
        userRepository.findByEmployeeId(employeeId).ifPresent(account -> {
            if (!account.isActive()) {
                return;
            }

            // Son sistem yoneticisinin hesabini kapatmak sistemi yonetilemez
            // birakirdi. Islemi sessizce atlamak daha kotu olurdu: ayrilan
            // kisi yonetici yetkisiyle sistemde kalirdi. Bu yuzden personel
            // ayrilisi REDDEDILIR ve ne yapilmasi gerektigi soylenir.
            //
            // Mesaj bu baglamda yeniden yazilir: kullanici personel
            // pasiflestiriyor ve "en az bir yonetici kalmali" tek basina
            // hangi islemi neden reddettigimizi anlatmaz.
            if (account.hasRole(Role.SYSTEM_ADMIN) && isLastActiveSystemAdmin(account)) {
                throw new UserRuleViolationException(
                        "This employee holds the last active system administrator account. "
                                + "Give that role to somebody else before recording the departure.");
            }

            account.setActive(false);
            refreshTokenService.revokeAllFor(account.getId(), "employee left the company");
        });
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
     * Sistemde en az bir aktif SISTEM YONETICISI kalmasini garanti eder.
     *
     * Sorgu satirlari KILITLER: kilitsiz olsaydi iki yonetici ayni anda birbirini
     * dusurur, ikisi de "hala baska bir admin var" gorur ve sistem yoneticisiz
     * kalirdi -- ne hesap acilabilir ne rol verilebilirdi.
     *
     * Ik uzmanligi icin ayni kural YOK: son Ik uzmani gitse bile sistem
     * yoneticisi yenisini atayabilir. Kilitlenmeye yol acan tek rol budur.
     */
    private void assertNotLastActiveSystemAdmin(User target) {
        if (isLastActiveSystemAdmin(target)) {
            throw new UserRuleViolationException(
                    "At least one active system administrator must remain");
        }
    }

    /** Sorgu satirlari kilitler; ayrintisi assertNotLastActiveSystemAdmin'de. */
    private boolean isLastActiveSystemAdmin(User target) {
        return userRepository.findActiveByRoleForUpdate(Role.SYSTEM_ADMIN).stream()
                .noneMatch(admin -> !admin.getId().equals(target.getId()));
    }
}
