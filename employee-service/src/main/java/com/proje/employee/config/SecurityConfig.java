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
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", CorrelationIdFilter.HEADER));
        // Tarayici, kendi gonderdigi ozel basliklari cevapta OKUYAMAZ -- acikca
        // aciga cikarilmasi gerekir. Aksi halde arayuz bir hatayi bildirirken
        // hangi istegin izini verecegini bilemezdi.
        configuration.setExposedHeaders(List.of(CorrelationIdFilter.HEADER));
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
                        // Ucler tek tek yazilir, "/api/auth/**" degil: yarin bu
                        // altina eklenecek bir uc (parola degistirme gibi)
                        // sessizce herkese acik olmasin.
                        .requestMatchers("/api/auth/login").permitAll()
                        // Kimlik, tasinan yenileme jetonunun KENDISIDIR. Erisim
                        // jetonu istenseydi uc anlamsiz olurdu: tam da o jetonun
                        // suresi doldugu icin buraya geliniyor.
                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/logout").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()

                        // Kendi parolasini herkes degistirebilir. Bu kural
                        // "/api/users/**" kuralindan ONCE gelmek zorunda: sonra
                        // yazilsaydi genel kural once eslesir ve yalnizca
                        // yoneticiler parolasini degistirebilirdi.
                        .requestMatchers(HttpMethod.PUT, "/api/users/me/password").authenticated()

                        // Gosterge paneli TOPLU veri gosterir. Kapsami sinirli
                        // bir kullanici (EMPLOYEE, MANAGER) tek tek goremedigi
                        // kisilerin toplamini da gormemeli; kucuk bir grupta
                        // toplam, bireyi ele verir.
                        .requestMatchers("/api/dashboard/**")
                        .hasAnyRole("HR_SPECIALIST", "SYSTEM_ADMIN")

                        // Organizasyon semasi da TOPLU yapisal veridir: butun
                        // raporlama cizgilerini tek ekranda gosterir. Kapsami
                        // sinirli bir kullaniciya acilmasi, tek tek goremedigi
                        // kisileri toplu halde gostermek olurdu.
                        .requestMatchers("/api/org-chart/**")
                        .hasAnyRole("HR_SPECIALIST", "SYSTEM_ADMIN")

                        // Hesap ve erisim yonetimi sistem yoneticisine ait.
                        // Metot belirtilmez: HEAD dahil her sey kapsanmali.
                        .requestMatchers("/api/users/**").hasRole("SYSTEM_ADMIN")

                        // Maas kurali GENEL okuma kuralindan ONCE gelmek zorunda:
                        // Spring Security ilk eslesen kurali uygular. Sonra yazilsaydi
                        // "/api/employees/**" once eslesir ve maas herkese acik kalirdi.
                        //
                        // Metot BELIRTILMEZ. HttpMethod.GET yazildiginda HEAD kapsam
                        // disinda kaliyordu: Spring Security HEAD'i GET saymaz ama
                        // Spring MVC HEAD istegini @GetMapping metoduna yonlendirir.
                        // Sonuc: yetkisiz rol HEAD ile 200 aliyordu (olculdu).
                        //
                        // SYSTEM_ADMIN de goremez: erisimi yoneten kisinin ucret
                        // bilgisine ihtiyaci yoktur.
                        .requestMatchers("/api/employees/*/salary").hasRole("HR_SPECIALIST")

                        // Personel verisini yalnizca Ik uzmani degistirir. Durum
                        // degisikligi de buraya girer: PUT /{id}/status bir yazmadir.
                        .requestMatchers(HttpMethod.POST, "/api/employees/**").hasRole("HR_SPECIALIST")
                        .requestMatchers(HttpMethod.PUT, "/api/employees/**").hasRole("HR_SPECIALIST")

                        // Okumaya kimlerin GIREBILECEGI burada, HANGI SATIRLARI
                        // gorecegi servis katmaninda belirlenir. Bir kural
                        // "bu satir senin ekibinde mi" diye soramaz; uc bazli
                        // eslesme satiri tanimaz.
                        .requestMatchers(HttpMethod.GET, "/api/employees/**")
                        .hasAnyRole("EMPLOYEE", "MANAGER", "HR_SPECIALIST", "SYSTEM_ADMIN", "SERVICE")
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
