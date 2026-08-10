package com.proje.employee.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Is faktoru 10 varsayilandir; donanim hizlandikca artirilabilir.
    private static final int BCRYPT_STRENGTH = 10;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Token header'da tasindigi icin CSRF gecerli bir tehdit degil.
                // Cerez tabanli oturuma gecilirse GERI ACILMALIDIR.
                .csrf(csrf -> csrf.disable())

                // Sunucu oturum tutmaz; her istek kendi kimligini tasir.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()

                        // Yazma uclari yalnizca yoneticiye.
                        .requestMatchers(HttpMethod.POST, "/api/employees/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/employees/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/employees/**").hasRole("ADMIN")

                        // Okuma uclari icin giris yapmis olmak yeterli.
                        .requestMatchers(HttpMethod.GET, "/api/employees/**").authenticated()

                        // Kural yazilmayan her sey reddedilir.
                        .anyRequest().authenticated())

                .httpBasic(basic -> {})
                .build();
    }
}
