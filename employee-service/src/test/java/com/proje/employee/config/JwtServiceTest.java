package com.proje.employee.config;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("a-test-secret-key-with-enough-length-32+".getBytes());

    private static final String OTHER_SECRET =
            Base64.getEncoder().encodeToString("a-different-secret-key-also-long-enough!".getBytes());

    private final JwtService jwtService = new JwtService(SECRET, 15);

    @Test
    @DisplayName("A generated token parses back with the same subject and roles")
    void roundTripsSubjectAndRoles() {
        String token = jwtService.generateToken("ada@example.com", List.of("HR_SPECIALIST"));

        Optional<Claims> claims = jwtService.parse(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("ada@example.com");
        assertThat(jwtService.rolesOf(claims.get())).containsExactly("HR_SPECIALIST");
    }

    @Test
    @DisplayName("A tampered token is rejected")
    void rejectsTamperedToken() {
        String token = jwtService.generateToken("ada@example.com", List.of("EMPLOYEE"));
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(jwtService.parse(tampered)).isEmpty();
    }

    @Test
    @DisplayName("A token signed with a different key is rejected")
    void rejectsTokenSignedWithAnotherKey() {
        String foreignToken = new JwtService(OTHER_SECRET, 15)
                .generateToken("attacker@example.com", List.of("HR_SPECIALIST"));

        assertThat(jwtService.parse(foreignToken)).isEmpty();
    }

    @Test
    @DisplayName("An expired token is rejected")
    void rejectsExpiredToken() {
        // Negatif gecerlilik: uretildigi anda suresi dolmus olur.
        String expired = new JwtService(SECRET, -1).generateToken("ada@example.com", List.of("EMPLOYEE"));

        assertThat(jwtService.parse(expired)).isEmpty();
    }

    @Test
    @DisplayName("Garbage input is rejected instead of throwing")
    void rejectsGarbageInput() {
        assertThat(jwtService.parse("not.a.token")).isEmpty();
        assertThat(jwtService.parse("")).isEmpty();
    }

    @Test
    @DisplayName("A key shorter than 256 bits is refused at startup")
    void refusesShortKey() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes());

        assertThatThrownBy(() -> new JwtService(shortSecret, 15))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
