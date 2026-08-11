package com.proje.employee.dto;

/**
 * Giris ve yenileme cevabi.
 *
 * Iki jeton birden doner: kisa omurlu erisim jetonu her istekte kullanilir,
 * uzun omurlu yenileme jetonu yalnizca /api/auth/refresh ucunda.
 */
public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        String refreshToken
) {
}
