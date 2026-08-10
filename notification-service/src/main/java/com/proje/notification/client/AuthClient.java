package com.proje.notification.client;

import com.proje.notification.client.dto.LoginRequest;
import com.proje.notification.client.dto.LoginResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Giris ucu icin ayri istemci.
 *
 * EmployeeClient'tan ayri olmasinin sebebi: token ekleyen interceptor yalnizca
 * ona baglidir. Ayni istemci uzerinden giris yapilsaydi, token almak icin token
 * gerekir ve sonsuz dongu olusurdu.
 *
 * contextId zorunlu: iki istemci de ayni servis adini hedefliyor ve Feign
 * varsayilan olarak bean adini servis adindan turetir.
 */
@FeignClient(name = "employee-service", contextId = "authClient", path = "/api/auth")
public interface AuthClient {

    @PostMapping("/login")
    LoginResponse login(@RequestBody LoginRequest request);
}
