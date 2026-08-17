package com.proje.employee.controller;

import com.proje.employee.config.JwtService;
import com.proje.employee.dto.LoginRequest;
import com.proje.employee.dto.PasswordResetConfirmRequest;
import com.proje.employee.dto.LoginResponse;
import com.proje.employee.dto.PasswordResetRequest;
import com.proje.employee.dto.RefreshRequest;
import com.proje.employee.service.LoginAttemptService;
import com.proje.employee.service.PasswordResetService;
import com.proje.employee.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Tag(name = "Authentication", description = "Giris, jeton yenileme ve cikis.")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String TOKEN_TYPE = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordResetService passwordResetService;
    private final long validityMinutes;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          RefreshTokenService refreshTokenService,
                          LoginAttemptService loginAttemptService,
                          PasswordResetService passwordResetService,
                          @Value("${app.jwt.validity-minutes:15}") long validityMinutes) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
        this.passwordResetService = passwordResetService;
        this.validityMinutes = validityMinutes;
    }

    @PostMapping("/login")
    @Operation(summary = "Giris")
    @ApiResponse(responseCode = "200", description = "Erisim jetonu ve yenileme jetonu")
    @ApiResponse(responseCode = "401", description = "Gecersiz kimlik bilgisi")
    @ApiResponse(responseCode = "429",
            description = "Cok fazla basarisiz deneme. Cevap hesabin VAR OLUP OLMADIGINI soylemez")
    public LoginResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest httpRequest) {

        String clientIp = httpRequest.getRemoteAddr();

        // Kimlik dogrulamasi bir KAYNAKTIR ve sinirsiz tuketilemez. Kontrol
        // authenticate() cagrisindan ONCE yapilir: amac yalnizca tahmini
        // engellemek degil, BCrypt'in CPU maliyetini de odememektir.
        loginAttemptService.assertNotBlocked(request.email(), clientIp);

        try {
            // Parola dogrulamasi burada yapilir: BCrypt maliyeti yalnizca bu ucta odenir.
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));

            String email = authentication.getName();
            loginAttemptService.recordSuccess(email, clientIp);

            return respond(email, rolesOf(authentication), refreshTokenService.issue(email));
        } catch (AuthenticationException e) {
            loginAttemptService.recordFailure(request.email(), clientIp);
            throw e;
        }
    }

    /**
     * Suresi dolan erisim jetonunun yerine yenisini verir.
     *
     * Kimlik dogrulamasi ISTEMEZ: kimlik zaten jetonun kendisidir. Erisim
     * jetonu istenseydi uc anlamsiz olurdu -- tam da o jeton oldugu icin
     * buraya geliniyor.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Jetonu yenile",
            description = """
                    Her yenilemede yeni bir jeton verilir ve eskisi iptal edilir.
                    Iptal edilmis bir jeton tekrar sunulursa bu, bir kopyasinin
                    dolastiginin kanitidir ve kullanicinin TUM oturumlari kapatilir.
                    """)
    @ApiResponse(responseCode = "401", description = "Jeton taninmiyor, suresi dolmus veya iptal edilmis")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(request.refreshToken());

        return respond(rotation.email(), rotation.roles(), rotation.refreshToken());
    }

    /**
     * Oturumu sunucu tarafinda da kapatir.
     *
     * Yalnizca istemcinin jetonu silmesi yeterli degildi: yenileme jetonunun
     * bir kopyasi kalmissa oturum yasamaya devam ederdi. Erisim jetonu yine
     * suresi dolana kadar gecerlidir -- JWT'nin kabul edilmis bedeli budur.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());

        return ResponseEntity.noContent().build();
    }

    /**
     * Sifirlama baglantisi ister.
     *
     * Hesap olsa da olmasa da 202 doner. 404 donseydi saldirgan elindeki
     * e-posta listesini deneyip hangilerinin bu sistemde hesabi oldugunu
     * cikarirdi -- hedefli oltalama icin hazir bir liste.
     */
    @PostMapping("/password-reset")
    @Operation(summary = "Parola sifirlama baglantisi iste")
    @ApiResponse(responseCode = "202",
            description = "Istek alindi. Cevap hesabin VAR OLUP OLMADIGINI soylemez")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.request(request.email());

        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    @Operation(summary = "Yeni parolayi belirle")
    @ApiResponse(responseCode = "204", description = "Parola degistirildi, butun oturumlar kapatildi")
    @ApiResponse(responseCode = "400",
            description = "Baglanti gecersiz, suresi dolmus ya da kullanilmis. "
                    + "Hangisi oldugu SOYLENMEZ")
    public ResponseEntity<Void> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {

        passwordResetService.confirm(request);

        return ResponseEntity.noContent().build();
    }

    private LoginResponse respond(String email, List<String> roles, String refreshToken) {
        return new LoginResponse(
                jwtService.generateToken(email, roles),
                TOKEN_TYPE,
                TimeUnit.MINUTES.toSeconds(validityMinutes),
                refreshToken);
    }

    // "ROLE_" oneki Spring Security'nin ic sozlesmesidir; token'a onsuz yazilir
    // ve filtre okurken yeniden ekler. Onek token'a girseydi, ic detay disariya
    // sizmis olurdu.
    private List<String> rolesOf(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();

        if (roles.isEmpty()) {
            throw new IllegalStateException("Authenticated user has no role");
        }
        return roles;
    }
}
