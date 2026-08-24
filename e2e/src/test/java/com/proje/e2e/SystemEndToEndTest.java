package com.proje.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
                    "ADMIN_EMAIL / ADMIN_PASSWORD are not set. Load .env into the shell first.");
        }

        // Baglanti hic kurulamazsa istisna buradan gecer; ham hatayi yukari
        // birakmak yerine ne yapilmasi gerektigini soyluyoruz.
        // Olcut "actuator 200" OLAMAZ: yonetim portu bilerek disari
        // yayimlanmiyor. Kara kutunun gorebilecegi sinyal, API'nin HTTP cevabi
        // vermesidir; kimlik dogrulamasi devrede oldugu icin bu 401'dir.
        int status;
        try {
            status = new SystemClient().get(SystemClient.API_URL + "/api/employees", null).status();
        } catch (RuntimeException e) {
            throw systemNotRunning(e);
        }

        if (status != 401) {
            throw systemNotRunning(null);
        }
    }

    private static IllegalStateException systemNotRunning(Throwable cause) {
        return new IllegalStateException("""
                The system is not running: %s
                Start it first:
                  docker compose --profile full up -d
                Or run the services locally and set E2E_API_URL.
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
    @DisplayName("Copies the manager in, proving the cross-service call really ran")
    void copiesTheManagerIn() {
        // BU TESTIN VARLIK SEBEBI olculdu. httpclient5 tek basina yukseltilince
        // calisma zamaninda NoClassDefFoundError firladi, Eureka kaydi
        // tazelenemedi ve Feign yoneticiyi bulamadi. Sonuc TAMAMEN SESSIZDI:
        // mail yine gitti, yalnizca CC bostu.
        //
        // Paketteki diger mail testleri bunu goremezdi cunku hicbiri yonetici
        // ATAMIYOR -- yani zenginlestirme yolu hic surulmuyordu ve "mail geldi"
        // ile "servisler arasi cagri calisti" ayni sey saniliyordu.
        String token = signIn();

        String managerEmail = "e2e.chief." + UUID.randomUUID() + "@example.com";
        String managerId = createEmployee(token, managerEmail);

        String reportEmail = "e2e.report." + UUID.randomUUID() + "@example.com";
        SystemClient.Response created = client.post(
                SystemClient.API_URL + "/api/employees", token,
                """
                {"firstName":"E2E","lastName":"Report","email":"%s","departmentId":1,
                 "managerId":%s,"jobTitle":"Engineer","hireDate":"2024-08-01"}
                """.formatted(reportEmail, managerId));

        assertThat(created.status()).isEqualTo(201);

        JsonNode mail = awaitMail(reportEmail, "Welcome to the team");

        // Yoneticinin adresi CC'de: bunu yazabilmek icin tuketicinin
        // employee-service'e Feign ile gidip kaydi okumus olmasi gerekir.
        assertThat(mail.at("/Content/Headers/Cc").toString()).contains(managerEmail);
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
                {"active":false,"terminationReason":"RESIGNED"}
                """);

        assertThat(deactivated.status()).isEqualTo(200);
        assertThat(deactivated.body().get("active").asBoolean()).isFalse();
        // Ayrilma bilgisi kaydedilmis olmali: devir orani buna dayanir.
        assertThat(deactivated.body().get("terminationReason").asText()).isEqualTo("RESIGNED");
        assertThat(deactivated.body().get("terminatedAt").isNull()).isFalse();

        SystemClient.Response reactivated = client.put(
                SystemClient.API_URL + "/api/employees/" + id + "/status", token,
                """
                {"active":true}
                """);

        assertThat(reactivated.status()).isEqualTo(200);
        assertThat(reactivated.body().get("active").asBoolean()).isTrue();
        // "Aktif ama ayrilmis" diye bir durum yok; kisit da bunu reddederdi.
        assertThat(reactivated.body().get("terminatedAt").isNull()).isTrue();

        // Zincir yeniden aktiflestirme icin de isliyor mu: outbox, relay,
        // kuyruk ve tuketici.
        awaitMail(email, "Welcome back");
    }

    @Test
    @DisplayName("Refuses to deactivate an employee without a termination reason")
    void refusesTerminationWithoutReason() {
        String token = signIn();
        String id = createEmployee(token, "e2e.noreason." + UUID.randomUUID() + "@example.com");

        SystemClient.Response response = client.put(
                SystemClient.API_URL + "/api/employees/" + id + "/status", token,
                """
                {"active":false}
                """);

        // Varsayilan bir sebep atamak devir oranini sessizce bozardi.
        assertThat(response.status()).isEqualTo(400);

        // Kayit hala aktif olmali: reddedilen istek yarim degisiklik birakmaz.
        assertThat(client.get(SystemClient.API_URL + "/api/employees/" + id, token)
                .body().get("active").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Rejects an inactive employee as a manager")
    void rejectsInactiveManager() {
        String token = signIn();
        String managerId = createEmployee(token, "e2e.manager." + UUID.randomUUID() + "@example.com");

        client.put(SystemClient.API_URL + "/api/employees/" + managerId + "/status", token,
                """
                {"active":false,"terminationReason":"RETIRED"}
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

    /** Her cagri benzersiz bir hesap uretir: testler birbirinin verisine dokunmaz. */
    /**
     * Hesabi acar VE davet baglantisiyla parolayi belirler.
     *
     * Istek parola TASIMAZ: hesabi acan kisi parolayi belirleseydi onunla giris
     * yapip kullanicinin kimligine burunebilirdi. Kara kutu olarak dogru yol,
     * gercek bir kullanici gibi maildeki baglantiyi kullanmaktir.
     */
    private String createAccount(String token, String email, String password, String role) {
        SystemClient.Response response = client.post(
                SystemClient.API_URL + "/api/users", token,
                """
                {"email":"%s","roles":["%s"]}
                """.formatted(email, role));

        assertThat(response.status()).isEqualTo(201);

        acceptInvite(email, password);
        return response.body().get("id").asText();
    }

    private void acceptInvite(String email, String password) {
        JsonNode mail = awaitMail(email, "choose a password");
        Matcher found = INVITE_LINK.matcher(mail.at("/Content/Body").asText());

        assertThat(found.find())
                .describedAs("invite mail should carry a set-password link")
                .isTrue();

        SystemClient.Response set = client.post(
                SystemClient.API_URL + "/api/auth/password-reset/confirm", null,
                """
                {"token":"%s","newPassword":"%s"}
                """.formatted(found.group(1), password));

        assertThat(set.status()).isEqualTo(204);
    }

    /** Mail govdesindeki davet/sifirlama baglantisindan jetonu ayiklar. */
    private static final Pattern INVITE_LINK =
            Pattern.compile("reset-password\\?token=([A-Za-z0-9_%-]+)");

    private SystemClient.Response signIn(String email, String password) {
        return client.post(SystemClient.API_URL + "/api/auth/login", null,
                """
                {"email":"%s","password":"%s"}
                """.formatted(email, password));
    }

    @Test
    @DisplayName("An account created by an administrator can sign in with read-only access")
    void createdAccountCanSignIn() {
        String adminToken = signIn();
        String email = "e2e.account." + UUID.randomUUID() + "@example.com";
        createAccount(adminToken, email, "a-long-enough-password", "EMPLOYEE");

        SystemClient.Response login = signIn(email, "a-long-enough-password");
        assertThat(login.status()).isEqualTo(200);

        String userToken = login.body().get("token").asText();
        assertThat(client.get(SystemClient.API_URL + "/api/employees?size=1", userToken).status())
                .isEqualTo(200);

        // Rolu EMPLOYEE: hesap yonetimi ona kapali.
        assertThat(client.get(SystemClient.API_URL + "/api/users", userToken).status())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("The password hash never appears in any account response")
    void neverExposesPasswordHash() {
        String adminToken = signIn();
        String email = "e2e.nohash." + UUID.randomUUID() + "@example.com";
        createAccount(adminToken, email, "a-long-enough-password", "EMPLOYEE");

        String listed = client.get(SystemClient.API_URL + "/api/users?size=100", adminToken)
                .body().toString();

        // BCrypt ozetleri "$2a$" / "$2b$" ile baslar.
        assertThat(listed).doesNotContain("$2a$").doesNotContain("$2b$");
    }

    @Test
    @DisplayName("Deactivating an account ends the session it already had")
    void deactivationEndsExistingSession() {
        // Bu tam da duzeltilen kusurdur: pasif hesap giris yapamiyordu ama
        // elindeki yenileme jetonuyla oturumunu suresiz surduruyordu.
        String adminToken = signIn();
        String email = "e2e.revoked." + UUID.randomUUID() + "@example.com";
        String id = createAccount(adminToken, email, "a-long-enough-password", "EMPLOYEE");

        String refreshToken = signIn(email, "a-long-enough-password")
                .body().get("refreshToken").asText();

        SystemClient.Response deactivated = client.put(
                SystemClient.API_URL + "/api/users/" + id + "/status", adminToken,
                """
                {"active":false}
                """);
        assertThat(deactivated.status()).isEqualTo(200);

        assertThat(signIn(email, "a-long-enough-password").status()).isEqualTo(401);
        assertThat(client.post(SystemClient.API_URL + "/api/auth/refresh", null,
                """
                {"refreshToken":"%s"}
                """.formatted(refreshToken)).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("Changing the password ends every existing session")
    void passwordChangeEndsSessions() {
        String adminToken = signIn();
        String email = "e2e.password." + UUID.randomUUID() + "@example.com";
        createAccount(adminToken, email, "a-long-enough-password", "EMPLOYEE");

        SystemClient.Response login = signIn(email, "a-long-enough-password");
        String userToken = login.body().get("token").asText();
        String refreshToken = login.body().get("refreshToken").asText();

        SystemClient.Response changed = client.put(
                SystemClient.API_URL + "/api/users/me/password", userToken,
                """
                {"currentPassword":"a-long-enough-password","newPassword":"a-different-password"}
                """);
        assertThat(changed.status()).isEqualTo(204);

        assertThat(client.post(SystemClient.API_URL + "/api/auth/refresh", null,
                """
                {"refreshToken":"%s"}
                """.formatted(refreshToken)).status()).isEqualTo(401);

        assertThat(signIn(email, "a-different-password").status()).isEqualTo(200);
    }

    @Test
    @DisplayName("Rejects a password change that gives the wrong current password")
    void rejectsWrongCurrentPassword() {
        String adminToken = signIn();
        String email = "e2e.wrongpass." + UUID.randomUUID() + "@example.com";
        createAccount(adminToken, email, "a-long-enough-password", "EMPLOYEE");

        String userToken = signIn(email, "a-long-enough-password").body().get("token").asText();

        SystemClient.Response response = client.put(
                SystemClient.API_URL + "/api/users/me/password", userToken,
                """
                {"currentPassword":"not-the-password","newPassword":"a-different-password"}
                """);

        // 401 DEGIL 400: 401 arayuze "oturum bitti" der ve kullanici parolasini
        // yanlis yazdi diye sistemden atilirdi.
        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("Refuses to let an administrator deactivate their own account")
    void refusesSelfDeactivation() {
        String adminToken = signIn();

        String ownId = null;
        for (com.fasterxml.jackson.databind.JsonNode account
                : client.get(SystemClient.API_URL + "/api/users?size=100", adminToken)
                .body().get("content")) {

            if (SystemClient.ADMIN_EMAIL.equals(account.get("email").asText())) {
                ownId = account.get("id").asText();
            }
        }
        assertThat(ownId).isNotNull();

        SystemClient.Response response = client.put(
                SystemClient.API_URL + "/api/users/" + ownId + "/status", adminToken,
                """
                {"active":false}
                """);

        assertThat(response.status()).isEqualTo(409);
        // Hesap hala calisiyor olmali: kural yalnizca reddetmekle kalmayip
        // gercekten korumali.
        assertThat(signIn(SystemClient.ADMIN_EMAIL, SystemClient.ADMIN_PASSWORD).status())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("An employee who leaves can no longer sign in")
    void departureClosesTheAccount() {
        // JML'in "leaver" adimi. Olculdu: bu baglanti olmadan ayrilan
        // personelin hesabiyla giris yapilabiliyordu -- sahipsiz hesap,
        // iceriden tehdidin en bilinen kaynagidir.
        String adminToken = signIn();
        String unique = UUID.randomUUID().toString();

        String employeeId = createEmployee(adminToken, "e2e.leaver." + unique + "@example.com");
        String accountEmail = "e2e.leaveracc." + unique + "@example.com";

        SystemClient.Response created = client.post(
                SystemClient.API_URL + "/api/users", adminToken,
                """
                {"email":"%s","roles":["EMPLOYEE"],"employeeId":%s}
                """.formatted(accountEmail, employeeId));
        assertThat(created.status()).isEqualTo(201);

        // Parolayi kullanici kendisi belirler; hesap davete kadar giremez.
        acceptInvite(accountEmail, "a-long-enough-password");
        assertThat(signIn(accountEmail, "a-long-enough-password").status()).isEqualTo(200);

        client.put(SystemClient.API_URL + "/api/employees/" + employeeId + "/status", adminToken,
                """
                {"active":false,"terminationReason":"RESIGNED"}
                """);

        assertThat(signIn(accountEmail, "a-long-enough-password").status()).isEqualTo(401);

        // Yeniden ise alim hesabi KENDILIGINDEN acmaz: erisimi geri vermek
        // bilincli bir karar olmali.
        client.put(SystemClient.API_URL + "/api/employees/" + employeeId + "/status", adminToken,
                """
                {"active":true}
                """);

        assertThat(signIn(accountEmail, "a-long-enough-password").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("Serves the departments used by the employee form")
    void servesDepartments() {
        SystemClient.Response response =
                client.get(SystemClient.API_URL + "/api/departments", signIn());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).isNotEmpty();
    }

    // ------------------------------------------------------------ aktarma

    @Test
    @DisplayName("Exports the directory as a CSV file, without any salary column")
    void exportsTheDirectoryAsCsv() {
        // BU TEST BIR SINIF HATAYI YAKALAR. Uc bir kez 500 donuyordu
        // (`@Transactional(readOnly = true)` + denetim yazmasi) ve 441 birim
        // testin HICBIRI goremedi: transaction sinirini ilgilendiren bir
        // davranis, gercek veritabani olmadan dogrulanmis sayilmaz.
        String token = signIn();

        SystemClient.Response response =
                client.get(SystemClient.API_URL + "/api/exports/employees?active=true", token);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.rawBody()).contains("Id,First name,Last name");
        // Butun tasarim maasi dar bir yetki cemberinde tutuyor; aktarma o
        // cemberi delen bir arka kapi olamaz.
        assertThat(response.rawBody().toLowerCase()).doesNotContain("salary");
    }

    @Test
    @DisplayName("A cell that looks like a formula leaves the system neutralised")
    void exportNeutralisesFormulas() {
        // Zarari veriyi GIREN degil, dosyayi ACAN gorur: elektronik tablo
        // programlari "=" ile baslayan bir hucreyi FORMUL sayar.
        String token = signIn();
        String email = "e2e.formula." + UUID.randomUUID() + "@example.com";

        SystemClient.Response created = client.post(
                SystemClient.API_URL + "/api/employees", token,
                """
                {"firstName":"=cmd|calc","lastName":"Probe","email":"%s","departmentId":1,
                 "jobTitle":"Engineer","hireDate":"2024-08-01"}
                """.formatted(email));
        assertThat(created.status()).isEqualTo(201);

        String csv = client
                .get(SystemClient.API_URL + "/api/exports/employees?search=e2e.formula", token)
                .rawBody();

        assertThat(csv).contains("'=cmd|calc");
        assertThat(csv).doesNotContain(",=cmd|calc");
    }

    @Test
    @DisplayName("Only HR may pull the whole directory")
    void exportIsClosedToOtherRoles() {
        String adminToken = signIn();
        String email = "e2e.exporter." + UUID.randomUUID() + "@example.com";
        createAccount(adminToken, email, "a-long-enough-password", "EMPLOYEE");

        String userToken = signIn(email, "a-long-enough-password").body().get("token").asText();

        assertThat(client.get(SystemClient.API_URL + "/api/exports/employees", userToken).status())
                .isEqualTo(403);
    }

    // -------------------------------------------------------- izin hakki

    @Test
    @DisplayName("An entitlement set by HR shows up in the balance as a granted amount")
    void entitlementReachesTheBalance() {
        String token = signIn();
        String employeeId = createEmployee(token,
                "e2e.entitlement." + UUID.randomUUID() + "@example.com");

        SystemClient.Response set = client.put(
                SystemClient.API_URL + "/api/leave-entitlements/" + employeeId + "/2026", token,
                """
                {"entitledDays":26,"carriedOverDays":4,"note":"E2E probe"}
                """);
        assertThat(set.status()).isEqualTo(200);

        JsonNode balance = client
                .get(SystemClient.API_URL + "/api/leave-balances/" + employeeId + "?year=2026", token)
                .body();

        assertThat(balance.get("entitledDays").asInt()).isEqualTo(26);
        assertThat(balance.get("carriedOverDays").asInt()).isEqualTo(4);
        // "Verilmis hak" ile "varsayilan" ayni ekranda ayni gorunmemeli.
        assertThat(balance.get("source").asText()).isEqualTo("GRANTED");
    }

    // ------------------------------------------------------- denetim izi

    @Test
    @DisplayName("The audit trail reads as sentences, never as a record dump")
    void auditDetailIsASentence() {
        // Detay bir zamanlar Java'nin `Type[a=1, b=2]` dokumuydu ve ekranda
        // "kind: ALL · Employee: null · allSalaries: false" gorunuyordu.
        // Bu iddia o bicimin GERI DONMEDIGINI tutuyor.
        String token = signIn();
        String employeeId = createEmployee(token,
                "e2e.audit." + UUID.randomUUID() + "@example.com");

        client.put(SystemClient.API_URL + "/api/leave-entitlements/" + employeeId + "/2027", token,
                """
                {"entitledDays":18,"carriedOverDays":0,"note":"Audit sentence probe"}
                """);

        JsonNode rows = client
                .get(SystemClient.API_URL + "/api/audit?action=LEAVE_ENTITLEMENT_SET&size=1", token)
                .body()
                .get("content");

        assertThat(rows).isNotEmpty();
        JsonNode newest = rows.get(0);

        assertThat(newest.get("detail").asText())
                .contains("entitlement:")
                .doesNotContain("=")
                .doesNotContain("AccessScope");
        // Iz KIMI gosterdigini de soylemeli: bos birakilsaydi ekranda
        // "LEAVE_ENTITLEMENT #906" gorunurdu.
        assertThat(newest.get("targetLabel").asText()).contains("E2E");
    }

    @Test
    @DisplayName("An export is recorded in the trail with how many records left")
    void exportIsAudited() {
        // Toplu veri cikisinin asil kontrolu bu satirdir: "kim, ne zaman,
        // hangi suzgecle, KAC KAYIT indirdi".
        String token = signIn();

        client.get(SystemClient.API_URL + "/api/exports/employees?active=true", token);

        JsonNode rows = client
                .get(SystemClient.API_URL + "/api/audit?action=EMPLOYEES_EXPORTED&size=1", token)
                .body()
                .get("content");

        assertThat(rows).isNotEmpty();
        assertThat(rows.get(0).get("detail").asText())
                .contains("Exported")
                .contains("employee record");
    }


    // ------------------------------------------------------ ice aktarma

    private static final String IMPORT_HEADER =
            "First name,Last name,Email,Phone,Department,Job title,Hire date,Manager email";

    /** Tek bir CSV satiri; bos alanlar bilerek bos birakiliyor. */
    private String importRow(String first, String email, String managerEmail) {
        return String.join(",", first, "Bulk", email, "", "Software Development",
                "Analyst", "2024-05-01", managerEmail);
    }

    private SystemClient.Response upload(String token, String... rows) {
        return client.postCsv(SystemClient.API_URL + "/api/imports/employees", token,
                IMPORT_HEADER + "\r\n" + String.join("\r\n", rows) + "\r\n");
    }

    @Test
    @DisplayName("Loads a whole file of people in one request")
    void importsAFile() {
        String token = signIn();
        String chief = "e2e.import.chief." + UUID.randomUUID() + "@example.com";
        String report = "e2e.import.report." + UUID.randomUUID() + "@example.com";

        SystemClient.Response response = upload(token,
                importRow("Chief", chief, ""),
                importRow("Report", report, chief));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("imported").asInt()).isEqualTo(2);

        // Yonetici GERCEKTEN baglandi mi? Sayi dogru olsa bile bag kurulmamis
        // olabilirdi ve o, iki gecisli cozumun tek varlik sebebi.
        SystemClient.Response found = client.get(
                SystemClient.API_URL + "/api/employees?search=" + report, token);
        assertThat(found.body().at("/content/0/managerFullName").asText()).contains("Chief");
    }

    @Test
    @DisplayName("One bad line rejects the file and nothing at all is written")
    void aBadLineWritesNothing() {
        // BU TESTI YALNIZCA UCTAN UCA YAPABILIR. "Hepsi ya da hicbiri"
        // transaction sinirinda yasiyor ve taklit edilmis bir repository'de
        // geri alma diye bir sey yoktur -- ayni ders `readOnly` hatasinda ve
        // `REQUIRES_NEW` vakasinda alinmisti.
        String token = signIn();
        String good = "e2e.import.good." + UUID.randomUUID() + "@example.com";

        SystemClient.Response response = upload(token,
                importRow("Good", good, ""),
                ",Missing,e2e.import.bad@example.com,,No Such Dept,Analyst,not-a-date,");

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.body().get("title").asText()).isEqualTo("Import rejected");

        // GECERLI satir da yazilmamali. Yazilsaydi operator 500 kisilik bir
        // dosyanin kacinin girdigini bilemezdi.
        SystemClient.Response found = client.get(
                SystemClient.API_URL + "/api/employees?search=" + good, token);
        assertThat(found.body().get("totalElements").asInt()).isZero();
    }

    @Test
    @DisplayName("Every rejected line is reported with a number that matches the file")
    void everyRejectedLineIsNumbered() {
        // Kullanici dosyayi bir editorde aciyor; numara oradaki satirla
        // ortusmezse gerekce ise yaramaz. Baslik 1'dir.
        String token = signIn();

        SystemClient.Response response = upload(token,
                ",Missing,e2e.import.x@example.com,,Software Development,Analyst,2024-05-01,");

        assertThat(response.status()).isEqualTo(422);

        JsonNode errors = response.body().get("errors");
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0).get("line").asInt()).isEqualTo(2);
        assertThat(errors.get(0).get("reason").asText()).contains("First name is required");
    }

    @Test
    @DisplayName("Only HR may load a file")
    void importIsClosedToOtherRoles() {
        String adminToken = signIn();
        String email = "e2e.importer." + UUID.randomUUID() + "@example.com";
        createAccount(adminToken, email, "a-long-enough-password", "EMPLOYEE");

        String userToken = signIn(email, "a-long-enough-password").body().get("token").asText();

        assertThat(upload(userToken).status()).isEqualTo(403);
    }

    @Test
    @DisplayName("A bulk load is recorded in the trail with how many people arrived")
    void importIsAudited() {
        String token = signIn();

        upload(token, importRow("Audited",
                "e2e.import.audit." + UUID.randomUUID() + "@example.com", ""));

        JsonNode rows = client
                .get(SystemClient.API_URL + "/api/audit?action=EMPLOYEES_IMPORTED&size=1", token)
                .body()
                .get("content");

        assertThat(rows).isNotEmpty();
        assertThat(rows.get(0).get("detail").asText()).contains("Imported 1 employee");
    }

    @Test
    @DisplayName("A file that fails only at the last step still writes nothing")
    void aLateFailureRollsBackWhatWasWritten() {
        // BU, TRANSACTION SINIRINI SINAYAN TESTTIR.
        //
        // Onceki test yetmiyordu ve bunu OLCEREK gordum: orada bozuk satir
        // DOGRULAMADA yakalaniyor, yani henuz hicbir sey yazilmamis oluyor ve
        // `@Transactional` kaldirildiginda bile test geciyordu.
        //
        // Yonetici cozumu ise ancak kayitlar YAZILDIKTAN sonra yapilabiliyor.
        // Butun satirlar gecerli, yalnizca yonetici e-postasi bulunamiyor --
        // geri alma olmasaydi yoneticisiz YARIM bir yukleme kalirdi.
        String token = signIn();
        String orphan = "e2e.import.orphan." + UUID.randomUUID() + "@example.com";

        SystemClient.Response response = upload(token,
                importRow("Orphan", orphan, "nobody." + UUID.randomUUID() + "@example.com"));

        assertThat(response.status()).isEqualTo(422);

        SystemClient.Response found = client.get(
                SystemClient.API_URL + "/api/employees?search=" + orphan, token);
        assertThat(found.body().get("totalElements").asInt()).isZero();
    }
    @Test
    @DisplayName("Tells the manager about a new leave request and the employee about the decision")
    void leaveDecisionsReachTheirRecipients() {
        // Bu davranis uzun sure HIC yoktu: kisi izin talep ediyor, karar
        // veriliyor ve kendisine bir sey bildirilmiyordu.
        //
        // Zincirin TAMAMI suruluyor: outbox -> relay -> RabbitMQ -> ayri bir
        // tuketici kuyrugu -> mail. Birim testler yalnizca uclari tutar.
        String token = signIn();

        String managerEmail = "e2e.leave.manager." + UUID.randomUUID() + "@example.com";
        String managerId = createEmployee(token, managerEmail);

        String reportEmail = "e2e.leave.report." + UUID.randomUUID() + "@example.com";
        SystemClient.Response report = client.post(
                SystemClient.API_URL + "/api/employees", token,
                """
                {"firstName":"E2E","lastName":"Leave","email":"%s","departmentId":1,
                 "managerId":%s,"jobTitle":"Engineer","hireDate":"2024-08-01"}
                """.formatted(reportEmail, managerId));
        assertThat(report.status()).isEqualTo(201);
        String reportId = report.body().get("id").asText();

        SystemClient.Response created = client.post(
                SystemClient.API_URL + "/api/leave-requests", token,
                """
                {"employeeId":%s,"type":"UNPAID","startDate":"2036-05-04",
                 "endDate":"2036-05-08","note":"e2e probe"}
                """.formatted(reportId));
        assertThat(created.status()).isEqualTo(201);

        // Talep KARAR VERECEK kisiye gider, talep edene degil.
        awaitMail(managerEmail, "requested leave");

        SystemClient.Response decision = client.put(
                SystemClient.API_URL + "/api/leave-requests/"
                        + created.body().get("id").asText() + "/decision",
                token, """
                {"status":"APPROVED"}""");
        assertThat(decision.status()).isEqualTo(200);

        // Karar ise talebin SAHIBINE gider.
        JsonNode mail = awaitMail(reportEmail, "approved");

        // Tarih bicimi arayuzdekiyle ayni ve yerel ayara bakmiyor; gun sayisi
        // son gunu DAHIL eder (4-8 Mayis = 5 gun).
        String body = mail.at("/Content/Body").asText();
        assertThat(body).contains("04-05-2036").contains("08-05-2036").contains("5 days");
    }
}
