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

@Configuration
public class UserSeeder {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    // Parolalar migration'a veya koda yazilmaz; ortam degiskeninden okunur.
    @Bean
    ApplicationRunner seedAdmin(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.admin.email:}") String email,
                                @Value("${app.admin.password:}") String password) {

        return args -> seed(userRepository, passwordEncoder, email, password, Role.ADMIN,
                "Admin account", "ADMIN_EMAIL / ADMIN_PASSWORD");
    }

    // Salt okuyan INSAN hesabi. Servis hesabinin rolu de USER'dir ama o bir
    // programa aittir; rol ayrimini elle denemek icin ayri bir hesap gerekir.
    @Bean
    ApplicationRunner seedReadOnlyUser(UserRepository userRepository,
                                       PasswordEncoder passwordEncoder,
                                       @Value("${app.user.email:}") String email,
                                       @Value("${app.user.password:}") String password) {

        return args -> seed(userRepository, passwordEncoder, email, password, Role.USER,
                "Read-only user account", "USER_EMAIL / USER_PASSWORD");
    }

    // Notification Service bu hesapla giris yapar. Rolu USER: bildirim gonderen
    // bir servisin personel kaydi degistirmeye ihtiyaci yoktur (en az yetki).
    @Bean
    ApplicationRunner seedServiceAccount(UserRepository userRepository,
                                         PasswordEncoder passwordEncoder,
                                         @Value("${app.service-account.email:}") String email,
                                         @Value("${app.service-account.password:}") String password) {

        return args -> seed(userRepository, passwordEncoder, email, password, Role.USER,
                "Service account", "SERVICE_ACCOUNT_EMAIL / SERVICE_ACCOUNT_PASSWORD");
    }

    private void seed(UserRepository userRepository,
                      PasswordEncoder passwordEncoder,
                      String email,
                      String password,
                      Role role,
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

        userRepository.save(new User(email, passwordEncoder.encode(password), role));
        log.info("{} created: {}", label, email);
    }
}
