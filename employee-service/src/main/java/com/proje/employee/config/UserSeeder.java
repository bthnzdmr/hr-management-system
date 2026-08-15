package com.proje.employee.config;

import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import com.proje.employee.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

/** Hesaplari acar. */
@Configuration
public class UserSeeder {

    /** Hesaplar personel baglantisindan ONCE acilmali. */
    static final int SEED_ACCOUNTS_FIRST = 1;

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    // Parolalar migration'a veya koda yazilmaz; ortam degiskeninden okunur.
    @Bean
    @Order(SEED_ACCOUNTS_FIRST)
    ApplicationRunner seedAdmin(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.admin.email:}") String email,
                                @Value("${app.admin.password:}") String password) {

        // Ilk hesap iki isi birden yapar: Ik verisini yonetir VE hesaplari
        // yonetir. Sistemi ayaga kaldiran kisinin baska turlu ikinci bir hesap
        // acmasi mumkun olmazdi -- yumurta-tavuk.
        return args -> seed(userRepository, passwordEncoder, email, password,
                User.rolesOf(Role.HR_SPECIALIST, Role.SYSTEM_ADMIN),
                "Admin account", "ADMIN_EMAIL / ADMIN_PASSWORD");
    }

    // Salt okuyan INSAN hesabi. Rol ayrimini elle denemek icin.
    @Bean
    @Order(SEED_ACCOUNTS_FIRST)
    ApplicationRunner seedReadOnlyUser(UserRepository userRepository,
                                       PasswordEncoder passwordEncoder,
                                       @Value("${app.user.email:}") String email,
                                       @Value("${app.user.password:}") String password) {

        return args -> seed(userRepository, passwordEncoder, email, password,
                User.rolesOf(Role.EMPLOYEE),
                "Read-only user account", "USER_EMAIL / USER_PASSWORD");
    }

    /** Rol basina birer demo hesabi. */
    @Bean
    @Order(SEED_ACCOUNTS_FIRST)
    ApplicationRunner seedRoleDemoAccounts(UserRepository userRepository,
                                           PasswordEncoder passwordEncoder,
                                           @Value("${app.demo.manager.email:}") String managerEmail,
                                           @Value("${app.demo.manager.password:}") String managerPassword,
                                           @Value("${app.demo.hr.email:}") String hrEmail,
                                           @Value("${app.demo.hr.password:}") String hrPassword,
                                           @Value("${app.demo.sysadmin.email:}") String adminEmail,
                                           @Value("${app.demo.sysadmin.password:}") String adminPassword) {

        return args -> {
            seed(userRepository, passwordEncoder, managerEmail, managerPassword,
                    User.rolesOf(Role.MANAGER),
                    "Manager demo account", "DEMO_MANAGER_EMAIL / DEMO_MANAGER_PASSWORD");

            seed(userRepository, passwordEncoder, hrEmail, hrPassword,
                    User.rolesOf(Role.HR_SPECIALIST),
                    "HR specialist demo account", "DEMO_HR_EMAIL / DEMO_HR_PASSWORD");

            seed(userRepository, passwordEncoder, adminEmail, adminPassword,
                    User.rolesOf(Role.SYSTEM_ADMIN),
                    "System admin demo account", "DEMO_SYSADMIN_EMAIL / DEMO_SYSADMIN_PASSWORD");
        };
    }

    /** Notification Service bu hesapla giris yapar. */
    @Bean
    @Order(SEED_ACCOUNTS_FIRST)
    ApplicationRunner seedServiceAccount(UserRepository userRepository,
                                         PasswordEncoder passwordEncoder,
                                         @Value("${app.service-account.email:}") String email,
                                         @Value("${app.service-account.password:}") String password) {

        return args -> {
            seed(userRepository, passwordEncoder, email, password, User.rolesOf(Role.SERVICE),
                    "Service account", "SERVICE_ACCOUNT_EMAIL / SERVICE_ACCOUNT_PASSWORD");

            reconcileServiceRoles(userRepository, email);
        };
    }

    /** Servis hesabinin rolunu her acilista dogrular. */
    private void reconcileServiceRoles(UserRepository userRepository, String email) {
        if (email.isBlank()) {
            return;
        }

        userRepository.findByEmail(email).ifPresent(account -> {
            if (account.getRoles().equals(User.rolesOf(Role.SERVICE))) {
                return;
            }
            log.warn("Service account had roles {}; resetting to [SERVICE]", account.getRoles());
            account.setRoles(User.rolesOf(Role.SERVICE));
            userRepository.save(account);
        });
    }

    private void seed(UserRepository userRepository,
                      PasswordEncoder passwordEncoder,
                      String email,
                      String password,
                      Set<Role> roles,
                      String label,
                      String variables) {

        if (email.isBlank() || password.isBlank()) {
            log.warn("{} not set, {} was not created", variables, label.toLowerCase());
            return;
        }

        // Var olan hesabin parolasi EZILMEZ: aksi halde uygulamayi yeniden
        // baslatabilen herkes parolayi sifirlayabilir ve uygulama icinden
        // degistirilen parola her acilista geri gelirdi.
        if (userRepository.existsByEmail(email)) {
            log.info("{} already exists: {}", label, email);
            return;
        }

        userRepository.save(new User(email, passwordEncoder.encode(password), roles));
        log.info("{} created: {} with roles {}", label, email, roles);
    }
}
