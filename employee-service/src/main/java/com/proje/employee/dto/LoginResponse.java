package com.proje.employee.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds
) {
}
