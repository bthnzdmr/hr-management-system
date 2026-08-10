package com.proje.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Calisan sisteme disaridan istek atan ince istemci.
 *
 * Adresler ortam degiskeninden okunur: testler hem makinede calisan servislere
 * hem de konteynerdeki sisteme ayni sekilde bakabilsin.
 */
final class SystemClient {

    static final String API_URL = env("E2E_API_URL", "http://localhost:8080");
    static final String MAILHOG_URL = env("E2E_MAILHOG_URL", "http://localhost:8025");
    static final String ADMIN_EMAIL = env("ADMIN_EMAIL", null);
    static final String ADMIN_PASSWORD = env("ADMIN_PASSWORD", null);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    record Response(int status, JsonNode body) {
    }

    Response get(String url, String token) {
        return send(request(url, token).GET());
    }

    Response post(String url, String token, String json) {
        return send(request(url, token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    private HttpRequest.Builder request(String url, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10));

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private Response send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body = response.body().isBlank() ? MAPPER.nullNode() : MAPPER.readTree(response.body());

            return new Response(response.statusCode(), body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Request interrupted", e);
        } catch (Exception e) {
            // getMessage() bazi baglanti hatalarinda null doner; toString()
            // en azindan istisnanin turunu soyler.
            throw new IllegalStateException("Request failed: " + e, e);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
