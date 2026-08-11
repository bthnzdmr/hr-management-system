package com.proje.employee.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String CLAIM_ROLE = "role";
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey key;
    private final Duration validity;

    public JwtService(@Value("${app.jwt.secret}") String base64Secret,
                      @Value("${app.jwt.validity-minutes:15}") long validityMinutes) {

        byte[] keyBytes = Decoders.BASE64.decode(base64Secret);

        // HS256 en az 256 bit anahtar ister. Kisa anahtar kaba kuvvetle bulunabilir
        // ve anahtari bilen kendine ADMIN rolunde token uretebilir.
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes (256 bits), was: " + keyBytes.length);
        }

        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.validity = Duration.ofMinutes(validityMinutes);
    }

    public String generateToken(String email, String role) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }

    // Gecersiz, suresi dolmus veya kurcalanmis token icin bos doner.
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());

        } catch (ExpiredJwtException e) {
            // Normal ve beklenen durum: kullanicinin token'inin suresi doldu.
            log.debug("Token expired for subject {}", e.getClaims().getSubject());
            return Optional.empty();

        } catch (JwtException | IllegalArgumentException e) {
            // Imza tutmuyor ya da token bicimi bozuk. Bu bir GUVENLIK sinyali
            // olabilir ve suresi dolmus token'dan ayirt edilebilmelidir.
            // Token'in kendisi loglanmaz: gecerliyse dogrudan kimlik bilgisidir.
            log.warn("Rejected an invalid token: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public String roleOf(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }
}
