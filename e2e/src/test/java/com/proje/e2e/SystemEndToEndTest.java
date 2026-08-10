package com.proje.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calisan sisteme disaridan bakan kara kutu testleri.
 *
 * Bu testler ayaga kalkmis bir sistem gerektirir ve bilerek ayri bir projede
 * durur: servislerin kendi "mvn test" kosusuna karismazlar.
 *
 *   docker compose --profile full up -d
 *   cd e2e && mvn test
 */
class SystemEndToEndTest {

    private static final Duration MAIL_TIMEOUT = Duration.ofSeconds(30);

    private final SystemClient client = new SystemClient();

    @BeforeAll
    static void requireRunningSystem() {
        if (SystemClient.ADMIN_EMAIL == null || SystemClient.ADMIN_PASSWORD == null) {
            throw new IllegalStateException(
                    "ADMIN_EMAIL / ADMIN_PASSWORD tanimli degil. .env dosyasini kabuga yukle.");
        }

        // Baglanti hic kurulamazsa istisna buradan gecer; ham hatayi yukari
        // birakmak yerine ne yapilmasi gerektigini soyluyoruz.
        int status;
        try {
            status = new SystemClient().get(SystemClient.API_URL + "/actuator/health", null).status();
        } catch (RuntimeException e) {
            throw systemNotRunning(e);
        }

        if (status != 200) {
            throw systemNotRunning(null);
        }
    }

    private static IllegalStateException systemNotRunning(Throwable cause) {
        return new IllegalStateException("""
                Sistem calismiyor: %s
                Once ayaga kaldir:
                  docker compose --profile full up -d
                Ya da servisleri makinede calistir ve E2E_API_URL degiskenini ayarla.
                """.formatted(SystemClient.API_URL), cause);
    }

    private String signIn() {
        SystemClient.Response response = client.post(
                SystemClient.API_URL + "/api/auth/login", null,
                """
                {"email":"%s","password":"%s"}
                """.formatted(SystemClient.ADMIN_EMAIL, SystemClient.ADMIN_PASSWORD));

        assertThat(response.status()).isEqualTo(200);
        return response.body().get("token").asText();
    }

    private String createEmployee(String token, String email) {
        SystemClient.Response response = client.post(
                SystemClient.API_URL + "/api/employees", token,
                """
                {"firstName":"E2E","lastName":"Probe","email":"%s","departmentId":1,
                 "jobTitle":"Engineer","hireDate":"2024-08-01"}
                """.formatted(email));

        assertThat(response.status()).isEqualTo(201);
        return response.body().get("id").asText();
    }

    /** MailHog'u belirli bir adrese mail dusene kadar yoklar. */
    private JsonNode awaitMail(String recipient) {
        Instant deadline = Instant.now().plus(MAIL_TIMEOUT);

        while (Instant.now().isBefore(deadline)) {
            JsonNode messages = client
                    .get(SystemClient.MAILHOG_URL + "/api/v2/messages?limit=50", null)
                    .body()
                    .get("items");

            for (JsonNode message : messages) {
                JsonNode to = message.at("/Content/Headers/To");
                if (to.toString().contains(recipient)) {
                    return message;
                }
            }

            sleep(Duration.ofSeconds(1));
        }
        throw new AssertionError("Mail did not arrive within " + MAIL_TIMEOUT + " for " + recipient);
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("Rejects a request that carries no token")
    void rejectsRequestWithoutToken() {
        assertThat(client.get(SystemClient.API_URL + "/api/employees", null).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("Issues a token for valid credentials")
    void issuesTokenForValidCredentials() {
        assertThat(signIn()).isNotBlank();
    }

    @Test
    @DisplayName("Delivers a notification mail after an employee is created")
    void deliversNotificationMailAfterCreation() {
        // Bu testin degeri sudur: zincirin TAMAMINI dogrular -- outbox yazimi,
        // relay'in yayini, kuyruk, tuketicinin idempotency kontrolu ve mail.
        String token = signIn();
        String email = "e2e." + UUID.randomUUID() + "@example.com";

        createEmployee(token, email);

        JsonNode mail = awaitMail(email);
        assertThat(mail.at("/Content/Headers/Subject").toString()).contains("Welcome to the team");
    }

    @Test
    @DisplayName("Rejects a second employee with an email that is already registered")
    void rejectsDuplicateEmail() {
        String token = signIn();
        String email = "e2e.duplicate." + UUID.randomUUID() + "@example.com";
        createEmployee(token, email);

        SystemClient.Response second = client.post(
                SystemClient.API_URL + "/api/employees", token,
                """
                {"firstName":"E2E","lastName":"Duplicate","email":"%s","departmentId":1,
                 "jobTitle":"Engineer","hireDate":"2024-08-01"}
                """.formatted(email));

        assertThat(second.status()).isEqualTo(409);
        assertThat(second.body().get("title").asText()).isEqualTo("Email already registered");
    }

    @Test
    @DisplayName("Serves the departments used by the employee form")
    void servesDepartments() {
        SystemClient.Response response =
                client.get(SystemClient.API_URL + "/api/departments", signIn());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).isNotEmpty();
    }
}
