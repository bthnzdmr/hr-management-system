package com.proje.notification.client;

import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

/**
 * Yalnizca EmployeeClient'a baglanan yapilandirma.
 *
 * Bilerek @Configuration DEGIL: bu anotasyonla ve bilesen taramasinin altinda
 * olsaydi interceptor TUM Feign istemcilerine uygulanirdi -- AuthClient dahil.
 * O zaman token almak icin token gerekirdi.
 */
public class AuthorizedFeignConfig {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Bean
    RequestInterceptor bearerTokenInterceptor(ServiceTokenProvider tokenProvider) {
        return template -> template.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.token());
    }

    /**
     * Korelasyon kimligini GERI tasir.
     *
     * Bu servis, olayi isleyip yonetici aramak icin Employee Service'i tekrar
     * cagiriyor. Baslik konmasaydi o istek KENDI yeni kimligini uretir ve
     * zincirin ikinci yarisi birincisiyle iliskilendirilemezdi -- tam da
     * izlemek istedigimiz yerde iz kopardi.
     */
    @Bean
    RequestInterceptor correlationIdInterceptor() {
        return template -> {
            String correlationId = MDC.get(MDC_KEY);
            if (correlationId != null) {
                template.header(CORRELATION_HEADER, correlationId);
            }
        };
    }
}
