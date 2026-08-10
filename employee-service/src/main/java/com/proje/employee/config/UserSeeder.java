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
                "Yonetici hesabi", "ADMIN_EMAIL / ADMIN_PASSWORD");
    }

    // Notification Service bu hesapla giris yapar. Rolu USER: bildirim gonderen
    // bir servisin personel kaydi degistirmeye ihtiyaci yoktur (en az yetki).
    @Bean
    ApplicationRunner seedServiceAccount(UserRepository userRepository,
                                         PasswordEncoder passwordEncoder,
                                         @Value("${app.service-account.email:}") String email,
                                         @Value("${app.service-account.password:}") String password) {

        return args -> seed(userRepository, passwordEncoder, email, password, Role.USER,
                "Servis hesabi", "SERVICE_ACCOUNT_EMAIL / SERVICE_ACCOUNT_PASSWORD");
    }

    private void seed(UserRepository userRepository,
                      PasswordEncoder passwordEncoder,
                      String email,
                      String password,
                      Role role,
                      String label,
                      String variables) {

        if (email.isBlank() || password.isBlank()) {
            log.warn("{} tanimli degil, {} olusturulmadi", variables, label.toLowerCase());
            return;
        }

        if (userRepository.existsByEmail(email)) {
            log.info("{} zaten var: {}", label, email);
            return;
        }

        userRepository.save(new User(email, passwordEncoder.encode(password), role));
        log.info("{} olusturuldu: {}", label, email);
    }
}
