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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@Configuration
public class UserSeeder {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    // Parolalar migration'a veya koda yazilmaz; ortam degiskeninden okunur.
    @Bean
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
    ApplicationRunner seedReadOnlyUser(UserRepository userRepository,
                                       PasswordEncoder passwordEncoder,
                                       @Value("${app.user.email:}") String email,
                                       @Value("${app.user.password:}") String password) {

        return args -> seed(userRepository, passwordEncoder, email, password,
                User.rolesOf(Role.EMPLOYEE),
                "Read-only user account", "USER_EMAIL / USER_PASSWORD");
    }

    /**
     * Notification Service bu hesapla giris yapar.
     *
     * Rolu SERVICE: bildirimi hazirlarken personelin yoneticisini sormasi
     * gerekiyor, yani rehberi okumali -- ama baska hicbir seye ihtiyaci yok.
     * EMPLOYEE verilseydi yalnizca "kendi" kaydini gorurdu ve personel kaydi
     * olmadigi icin hicbir sey goremezdi; HR_SPECIALIST verilseydi personel
     * silebilirdi. Ikisi de yanlis olurdu.
     */
    @Bean
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

    /**
     * Servis hesabinin rolunu her acilista dogrular.
     *
     * Makine kimliginin rolu VERI degil YAPILANDIRMADIR: hangi role sahip
     * olacagi kodda yazar, kimse arayuzden degistirmemelidir. Parola ise
     * korunur -- o gercekten bir sirdir.
     *
     * Somut sebep: roller bolundugunde bu hesap migration'da EMPLOYEE'ye
     * dustu ve tohumlayici var olan hesabi atladigi icin rehberi okuyamaz
     * hale geldi. Bildirim maili yine gider ama yonetici CC'si SESSIZCE
     * calismazdi -- fark edilmesi zor bir bozulma.
     */
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
