package com.proje.notification.client;

import com.proje.notification.client.dto.LoginRequest;
import com.proje.notification.client.dto.LoginResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Servis hesabinin token'ini alir ve suresi dolana kadar saklar.
 *
 * Her istekte yeniden giris yapilsaydi BCrypt dogrulamasinin maliyeti her
 * cagrida odenirdi; JWT'yi tam da bundan kacinmak icin secmistik.
 */
@Component
public class ServiceTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenProvider.class);

    // Token tam bitis aninda degil, biraz oncesinde yenilenir: aksi halde
    // yolda olan bir istek sunucuya suresi dolmus token ile varabilir.
    private static final Duration RENEWAL_MARGIN = Duration.ofSeconds(30);

    private final AuthClient authClient;
    private final String email;
    private final String password;

    private String token;
    private Instant expiresAt = Instant.EPOCH;

    public ServiceTokenProvider(AuthClient authClient,
                                @Value("${app.employee-service.username}") String email,
                                @Value("${app.employee-service.password}") String password) {
        this.authClient = authClient;
        this.email = email;
        this.password = password;
    }

    public synchronized String token() {
        if (token == null || Instant.now().isAfter(expiresAt)) {
            login();
        }
        return token;
    }

    /** Sunucu token'i reddettiginde (401) onbellegi bosaltir. */
    public synchronized void invalidate() {
        this.token = null;
        this.expiresAt = Instant.EPOCH;
    }

    private void login() {
        LoginResponse response = authClient.login(new LoginRequest(email, password));

        this.token = response.token();
        this.expiresAt = Instant.now()
                .plusSeconds(response.expiresInSeconds())
                .minus(RENEWAL_MARGIN);

        log.debug("Service token acquired, valid until {}", expiresAt);
    }
}
