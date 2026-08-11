package com.proje.employee.controller;

import com.proje.employee.config.JwtService;
import com.proje.employee.dto.LoginRequest;
import com.proje.employee.dto.LoginResponse;
import com.proje.employee.dto.RefreshRequest;
import com.proje.employee.service.RefreshTokenService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String TOKEN_TYPE = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final long validityMinutes;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          RefreshTokenService refreshTokenService,
                          @Value("${app.jwt.validity-minutes:15}") long validityMinutes) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.validityMinutes = validityMinutes;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        // Parola dogrulamasi burada yapilir: BCrypt maliyeti yalnizca bu ucta odenir.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        String email = authentication.getName();

        return respond(email, rolesOf(authentication), refreshTokenService.issue(email));
    }

    /**
     * Suresi dolan erisim jetonunun yerine yenisini verir.
     *
     * Kimlik dogrulamasi ISTEMEZ: kimlik zaten jetonun kendisidir. Erisim
     * jetonu istenseydi uc anlamsiz olurdu -- tam da o jeton oldugu icin
     * buraya geliniyor.
     */
    @PostMapping("/refresh")
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
