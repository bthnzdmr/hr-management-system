package com.proje.employee.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Is faktoru 10 varsayilandir; donanim hizlandikca artirilabilir.
    private static final int BCRYPT_STRENGTH = 10;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    JwtAuthenticationFilter jwtFilter,
                                    SecurityProblemWriter problemWriter) throws Exception {
        return http
                // Token header'da tasindigi icin CSRF gecerli bir tehdit degil.
                // Cerez tabanli oturuma gecilirse GERI ACILMALIDIR.
                .csrf(csrf -> csrf.disable())

                // Sunucu oturum tutmaz; her istek kendi kimligini tasir.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
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

                // JWT filtresi, kullanici adi/parola filtresinden once calisir:
                // kimlik zaten token'dan gelir, form girisi denenmez.
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

                // Varsayilan giris noktasi kimliksiz istege de 403 doner; oysa
                // dogrusu 401'dir. Ikisi farkli seyler soyler.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                problemWriter.write(request, response, HttpStatus.UNAUTHORIZED,
                                        "Authentication required", "Valid credentials are required"))
                        .accessDeniedHandler((request, response, ex) ->
                                problemWriter.write(request, response, HttpStatus.FORBIDDEN,
                                        "Access denied", "You do not have permission for this operation")))

                .build();
    }
}
