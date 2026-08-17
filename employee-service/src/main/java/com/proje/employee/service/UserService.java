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

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetService passwordResetService;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository,
                       EmployeeRepository employeeRepository,
                       RefreshTokenService refreshTokenService,
                       PasswordEncoder passwordEncoder,
                       PasswordResetService passwordResetService,
                       UserMapper userMapper) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetService = passwordResetService;
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

        rejectServiceRole(request.roles());

        // Hicbir parolanin tutmadigi bir ozet. Hesap, sahibi davet
        // baglantisini kullanana kadar giris YAPAMAZ -- ve o ana kadar
        // parolayi kimse bilmez, acan kisi dahil.
        User user = new User(
                request.email(),
                passwordEncoder.encode(unguessableSecret()),
                request.roles());

        if (request.employeeId() != null) {
            Employee employee = employeeRepository.findById(request.employeeId())
                    .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));

            // Ayrilmis personele hesap acilmaz. Ayrilis hesabi KAPATIYOR
            // (disableAccountOf); acilis da ayni kurala uymak zorunda, yoksa
            // kural tek yonlu olur ve sirketten ayrilmis birine calisan girisi
            // verilebilirdi. uk_users_employee_id kisiti bunu yakalamaz --
            // ortada mevcut bir satir yok.
            if (!employee.isActive()) {
                throw new UserRuleViolationException(
                        "Cannot create an account for an employee who has left the company");
            }

            user.setEmployee(employee);
        }

        User saved = userRepository.save(user);

        // Davet, hesabin kendisiyle AYNI transaction'da uretilir: hesap
        // acilip davet uretilemezse ortada girilemeyen bir hesap kalirdi.
        passwordResetService.invite(saved);

        return userMapper.toResponse(saved);
    }

    /**
     * Tahmin edilemez, hicbir yerde saklanmayan bir dizge.
     *
     * Amaci parola OLMAK degil, hicbir parolanin tutmamasini saglamak. Bos
     * dizge veya sabit bir deger kullanilsaydi ayni "parolasiz" hesaplarin
     * hepsi ayni ozeti tasir ve biri kirilirsa hepsi acilirdi.
     */
    private String unguessableSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Makine kimliginin rolu VERI degil YAPILANDIRMADIR: servis hesabinin rolu
     * her acilista tohumlayici tarafindan dogrulanip duzeltilir. API'den
     * verilebilseydi bir insan hesabi servis kimligine burunebilir ve loglarda
     * "bu istegi kim yapti" sorusu cevapsiz kalirdi.
     */
    private void rejectServiceRole(Set<Role> roles) {
        if (roles != null && roles.contains(Role.SERVICE)) {
            throw new UserRuleViolationException(
                    "The service role is configuration, not something an account can be granted");
        }
    }

    @Transactional
    @Auditable(action = AuditAction.ROLES_CHANGED, targetType = "USER")
    public UserResponse changeRoles(Long id, Set<Role> roles, String actingUserEmail) {
        // Girdi, veritabanina gitmeden once dogrulanir: olmayan bir kullanici
        // icin bile istek gecersizdir ve varligini sizdirmadan reddedilir.
        rejectServiceRole(roles);

        User user = find(id);

        // Kendi rolune HIC dokunulamaz -- yalnizca dusurmek degil, YUKSELTMEK de
        // yasak. Onceki hali sadece "kendi SYSTEM_ADMIN rolunu cikarma" diyordu
        // ve bu, gorevler ayriligini tek istekle atlamaya izin veriyordu:
        // sistem yoneticisi kendine HR_SPECIALIST verip maasa erisebiliyordu.
        //
        // Rol modelinin butun gerekcesi "erisimi yoneten kisinin ucret bilgisine
        // ihtiyaci yoktur" idi; bu kural olmadan o ayrim kagit uzerinde kalirdi.
        // Degisikligi baska bir yonetici yapar: dort goz ilkesi.
        if (user.getEmail().equals(actingUserEmail) && !user.getRoles().equals(roles)) {
            throw new UserRuleViolationException(
                    "You cannot change your own roles; ask another system administrator");
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
