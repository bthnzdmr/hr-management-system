package com.proje.employee.controller;

import com.proje.employee.config.JwtService;
import com.proje.employee.dto.LoginRequest;
import com.proje.employee.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String TOKEN_TYPE = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final long validityMinutes;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          @Value("${app.jwt.validity-minutes:15}") long validityMinutes) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.validityMinutes = validityMinutes;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        // Parola dogrulamasi burada yapilir: BCrypt maliyeti yalnizca bu ucta odenir.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Authenticated user has no role"));

        String token = jwtService.generateToken(authentication.getName(), role);

        return new LoginResponse(token, TOKEN_TYPE, TimeUnit.MINUTES.toSeconds(validityMinutes));
    }
}
