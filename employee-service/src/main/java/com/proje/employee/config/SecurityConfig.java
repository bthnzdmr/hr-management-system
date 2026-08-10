package com.proje.employee.config;

import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

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

    /**
     * Izinli kaynaklar acikca yazilir.
     *
     * "*" yazmak, kimlik bilgisi tasiyan isteklerde tarayici tarafindan zaten
     * reddedilir; ayrica herhangi bir sitenin kullanicinin token'iyla bu API'ye
     * istek atabilmesi demektir.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Preflight cevabinin tarayicida onbelleklenme suresi; her istekten
        // once ikinci bir tur atilmasini engeller.
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        return source;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    JwtAuthenticationFilter jwtFilter,
                                    SecurityProblemWriter problemWriter,
                                    CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                // Tarayici, farkli kaynaktan gelen istekleri CORS basliklari
                // olmadan engeller. Bu bir TARAYICI korumasidir; curl veya
                // Postman CORS'a hic bakmaz, dolayisiyla yetkilendirmenin
                // yerini tutmaz.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

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

                        // Maas kurali GENEL okuma kuralindan ONCE gelmek zorunda:
                        // Spring Security ilk eslesen kurali uygular. Sonra yazilsaydi
                        // "/api/employees/**" once eslesir ve maas USER'a acik kalirdi.
                        .requestMatchers(HttpMethod.GET, "/api/employees/*/salary").hasRole("ADMIN")

                        // Yazma uclari yalnizca yoneticiye.
                        .requestMatchers(HttpMethod.POST, "/api/employees/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/employees/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/employees/**").hasRole("ADMIN")

                        // Okuma uclari icin giris yapmis olmak yeterli.
                        .requestMatchers(HttpMethod.GET, "/api/employees/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/departments").authenticated()

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
