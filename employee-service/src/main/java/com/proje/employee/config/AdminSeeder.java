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
public class AdminSeeder {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    // Parola migration'a veya koda yazilmaz; ortam degiskeninden okunur.
    @Bean
    ApplicationRunner seedAdmin(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.admin.email:}") String email,
                                @Value("${app.admin.password:}") String password) {

        return args -> {
            if (email.isBlank() || password.isBlank()) {
                log.warn("ADMIN_EMAIL / ADMIN_PASSWORD tanimli degil, yonetici hesabi olusturulmadi");
                return;
            }

            if (userRepository.existsByEmail(email)) {
                log.info("Yonetici hesabi zaten var: {}", email);
                return;
            }

            userRepository.save(new User(email, passwordEncoder.encode(password), Role.ADMIN));
            log.info("Yonetici hesabi olusturuldu: {}", email);
        };
    }
}
