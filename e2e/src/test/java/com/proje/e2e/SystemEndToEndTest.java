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

    /**
     * Her cagri YENI bir yenileme jetonu uretir.
     *
     * Bu, tekrar kullanim testinin digerlerini bozmamasi icin onemlidir: o test
     * kullanicinin tum jetonlarini iptal eder, dolayisiyla hicbir test baska
     * bir testin jetonuna guvenmemelidir.
     */
    private SystemClient.Response signInFully() {
        SystemClient.Response response = client.post(
                SystemClient.API_URL + "/api/auth/login", null,
                """
                {"email":"%s","password":"%s"}
                """.formatted(SystemClient.ADMIN_EMAIL, SystemClient.ADMIN_PASSWORD));

        assertThat(response.status()).isEqualTo(200);
        return response;
    }

    private String signIn() {
        return signInFully().body().get("token").asText();
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

    /**
     * MailHog'u belirli bir adrese, belirli konulu mail dusene kadar yoklar.
     *
     * Konu da aranir: ayni adrese birden fazla mail gidiyor ve yalnizca alicaya
     * bakmak, olusturma mailini yeniden aktiflestirme maili sanmaya yol acar.
     */
    private JsonNode awaitMail(String recipient, String subjectFragment) {
        Instant deadline = Instant.now().plus(MAIL_TIMEOUT);

        while (Instant.now().isBefore(deadline)) {
            JsonNode messages = client
                    .get(SystemClient.MAILHOG_URL + "/api/v2/messages?limit=50", null)
                    .body()
                    .get("items");

            for (JsonNode message : messages) {
                JsonNode to = message.at("/Content/Headers/To");
                JsonNode subject = message.at("/Content/Headers/Subject");

                if (to.toString().contains(recipient) && subject.toString().contains(subjectFragment)) {
                    return message;
                }
            }

            sleep(Duration.ofSeconds(1));
        }
        throw new AssertionError("Mail '%s' did not arrive within %s for %s"
                .formatted(subjectFragment, MAIL_TIMEOUT, recipient));
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

        JsonNode mail = awaitMail(email, "Welcome to the team");
        assertThat(mail.at("/Content/Headers/To").toString()).contains(email);
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
    @DisplayName("Deactivation can be undone and both changes are announced by mail")
    void deactivationCanBeUndone() {
        // Kullanicinin bildirdigi eksigin uctan uca karsiligi: pasiflestirme
        // tek yonlu bir kapi degildir.
        String token = signIn();
        String email = "e2e.status." + UUID.randomUUID() + "@example.com";
        String id = createEmployee(token, email);
        awaitMail(email, "Welcome to the team");

        SystemClient.Response deactivated = client.put(
                SystemClient.API_URL + "/api/employees/" + id + "/status", token,
                """
                {"active":false}
                """);

        assertThat(deactivated.status()).isEqualTo(200);
        assertThat(deactivated.body().get("active").asBoolean()).isFalse();

        SystemClient.Response reactivated = client.put(
                SystemClient.API_URL + "/api/employees/" + id + "/status", token,
                """
                {"active":true}
                """);

        assertThat(reactivated.status()).isEqualTo(200);
        assertThat(reactivated.body().get("active").asBoolean()).isTrue();

        // Zincir yeniden aktiflestirme icin de isliyor mu: outbox, relay,
        // kuyruk ve tuketici.
        awaitMail(email, "Welcome back");
    }

    @Test
    @DisplayName("Rejects an inactive employee as a manager")
    void rejectsInactiveManager() {
        String token = signIn();
        String managerId = createEmployee(token, "e2e.manager." + UUID.randomUUID() + "@example.com");

        client.put(SystemClient.API_URL + "/api/employees/" + managerId + "/status", token,
                """
                {"active":false}
                """);

        SystemClient.Response response = client.post(
                SystemClient.API_URL + "/api/employees", token,
                """
                {"firstName":"E2E","lastName":"Report","email":"e2e.report.%s@example.com",
                 "departmentId":1,"managerId":%s,"jobTitle":"Engineer","hireDate":"2024-08-01"}
                """.formatted(UUID.randomUUID(), managerId));

        // Istemcinin gonderdigi gecersiz bir iliski sunucu hatasi degildir.
        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("Filters the employee list by search term and status")
    void filtersEmployeeList() {
        String token = signIn();
        String marker = "zz" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        createEmployee(token, "e2e.filter." + marker + "@example.com");

        SystemClient.Response found = client.get(
                SystemClient.API_URL + "/api/employees?search=" + marker + "&active=true", token);

        assertThat(found.status()).isEqualTo(200);
        assertThat(found.body().get("totalElements").asInt()).isEqualTo(1);

        // Ayni kayit pasifler arasinda gorunmemeli.
        SystemClient.Response amongInactive = client.get(
                SystemClient.API_URL + "/api/employees?search=" + marker + "&active=false", token);

        assertThat(amongInactive.body().get("totalElements").asInt()).isZero();
    }

    @Test
    @DisplayName("Exchanges a refresh token for a working access token")
    void refreshesTheAccessToken() {
        SystemClient.Response login = signInFully();
        String refreshToken = login.body().get("refreshToken").asText();

        SystemClient.Response refreshed = client.post(
                SystemClient.API_URL + "/api/auth/refresh", null,
                """
                {"refreshToken":"%s"}
                """.formatted(refreshToken));

        assertThat(refreshed.status()).isEqualTo(200);
        // Dondurme: yeni bir yenileme jetonu da gelir, eskisi artik gecersizdir.
        assertThat(refreshed.body().get("refreshToken").asText()).isNotEqualTo(refreshToken);

        String newAccessToken = refreshed.body().get("token").asText();
        assertThat(client.get(SystemClient.API_URL + "/api/employees?size=1", newAccessToken).status())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("Revokes every session when a refresh token is presented twice")
    void detectsRefreshTokenReuse() {
        String refreshToken = signInFully().body().get("refreshToken").asText();

        SystemClient.Response first = client.post(
                SystemClient.API_URL + "/api/auth/refresh", null,
                """
                {"refreshToken":"%s"}
                """.formatted(refreshToken));
        assertThat(first.status()).isEqualTo(200);

        // Ayni jeton ikinci kez sunuluyor: bir kopyasi dolasiyor demektir.
        SystemClient.Response replay = client.post(
                SystemClient.API_URL + "/api/auth/refresh", null,
                """
                {"refreshToken":"%s"}
                """.formatted(refreshToken));
        assertThat(replay.status()).isEqualTo(401);

        // Tekrar kullanim tespit edildiginde YALNIZCA sunulan jeton degil,
        // o kullanicinin butun oturumlari kapatilir -- ilk yenilemeden cikan
        // saglam jeton da artik gecersizdir.
        SystemClient.Response afterLockdown = client.post(
                SystemClient.API_URL + "/api/auth/refresh", null,
                """
                {"refreshToken":"%s"}
                """.formatted(first.body().get("refreshToken").asText()));

        assertThat(afterLockdown.status()).isEqualTo(401);
    }

    @Test
    @DisplayName("Signing out makes the refresh token unusable")
    void signOutRevokesTheRefreshToken() {
        String refreshToken = signInFully().body().get("refreshToken").asText();

        SystemClient.Response logout = client.post(
                SystemClient.API_URL + "/api/auth/logout", null,
                """
                {"refreshToken":"%s"}
                """.formatted(refreshToken));
        assertThat(logout.status()).isEqualTo(204);

        SystemClient.Response afterLogout = client.post(
                SystemClient.API_URL + "/api/auth/refresh", null,
                """
                {"refreshToken":"%s"}
                """.formatted(refreshToken));

        assertThat(afterLogout.status()).isEqualTo(401);
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
