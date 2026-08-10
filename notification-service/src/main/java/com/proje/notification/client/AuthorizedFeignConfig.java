package com.proje.notification.client;

import feign.RequestInterceptor;
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

    @Bean
    RequestInterceptor bearerTokenInterceptor(ServiceTokenProvider tokenProvider) {
        return template -> template.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.token());
    }
}
