package com.proje.notification.service;

import com.proje.notification.event.AccountEvent;
import com.proje.notification.event.AccountEventType;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bu sinifin HIC testi yoktu ve kapsam olcumu bunu ortaya cikardi: %8,1.
 *
 * Onemi soyle: sifirlama JETONU buradan HTML'e giriyor ve mail, hesabin
 * kontrolunu devreden tek yol. Sessiz bir bicim hatasi baglantiyi bozar ve
 * kullanici sisteme hic giremez.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetMailServiceTest {

    private static final String FROM = "hr-system@example.com";
    private static final String BASE_URL = "http://localhost:5173";

    @Mock
    private JavaMailSender mailSender;

    private PasswordResetMailService service;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(call -> new MimeMessage((Session) null));
        service = new PasswordResetMailService(mailSender, FROM, BASE_URL);
    }

    private record Bodies(String plain, String html) {
    }

    private AccountEvent event(AccountEventType type, String token, Duration validFor) {
        return new AccountEvent(UUID.randomUUID(), type, Instant.now(),
                7L, "grace@example.com", token, Instant.now().plus(validFor));
    }

    private MimeMessage sent(AccountEvent event) {
        service.send(event);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static Bodies bodiesOf(MimeMessage message) throws Exception {
        // MIME basliklari ancak burada sonlandirilir; cagrilmazsa her parca
        // varsayilan `text/plain` gorunur.
        message.saveChanges();

        StringBuilder plain = new StringBuilder();
        StringBuilder html = new StringBuilder();
        collect(message, plain, html);

        return new Bodies(plain.toString(), html.toString());
    }

    private static void collect(Part part, StringBuilder plain, StringBuilder html) throws Exception {
        Object content = part.getContent();

        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                collect(multipart.getBodyPart(i), plain, html);
            }
        } else if (part.isMimeType("text/plain")) {
            plain.append(content);
        } else if (part.isMimeType("text/html")) {
            html.append(content);
        }
    }

    @Test
    @DisplayName("Puts the reset link in both bodies, pointing at the app")
    void putsTheLinkInBothBodies() throws Exception {
        Bodies bodies = bodiesOf(sent(
                event(AccountEventType.PASSWORD_RESET_REQUESTED, "abc123", Duration.ofMinutes(30))));

        String expected = BASE_URL + "/reset-password?token=abc123";

        assertThat(bodies.plain()).contains(expected);
        assertThat(bodies.html()).contains(expected);
    }

    @Test
    @DisplayName("Encodes a token that would otherwise break the address")
    void encodesTheToken() throws Exception {
        // Jeton URL parcasi olarak tasiniyor. `+` kodlanmazsa sunucu tarafinda
        // BOSLUGA cozulur ve jeton sessizce taninmaz hale gelir; kullanici
        // "baglanti gecersiz" gorur ve sebebi hicbir yerde yazmaz.
        Bodies bodies = bodiesOf(sent(
                event(AccountEventType.PASSWORD_RESET_REQUESTED, "a+b/c=", Duration.ofMinutes(30))));

        assertThat(bodies.plain()).contains("token=a%2Bb%2Fc%3D");
        assertThat(bodies.plain()).doesNotContain("token=a+b/c=");
    }

    @Test
    @DisplayName("Escapes the token in the HTML body as well")
    void escapesTheTokenInHtml() throws Exception {
        // Kodlanmis jeton `&` icerebilir ve HTML'e giriyor; kacirilmazsa
        // isaretleme bozulur.
        Bodies bodies = bodiesOf(sent(
                event(AccountEventType.PASSWORD_RESET_REQUESTED, "a\"b<c", Duration.ofMinutes(30))));

        assertThat(bodies.html()).doesNotContain("\"b<c");
        assertThat(bodies.html()).contains("%22b%3Cc");
    }

    @Test
    @DisplayName("Tells an invited person the account is ready, not that a reset was asked for")
    void distinguishesInviteFromReset() throws Exception {
        MimeMessage invite = sent(event(AccountEventType.INVITED, "t1", Duration.ofHours(24)));
        Bodies inviteBodies = bodiesOf(invite);

        assertThat(invite.getSubject()).contains("account is ready");
        assertThat(inviteBodies.html()).contains("Choose a password");
        assertThat(inviteBodies.plain()).contains("created for you");
    }

    @Test
    @DisplayName("Never copies anyone in on an account mail")
    void neverCopiesAnyoneIn() throws Exception {
        // CC burada bir ACIKTIR: baglantiyi goren herkes parolayi belirleyebilir.
        // Personel bildirimlerindeki CC bir zenginlestirmeydi, bu baska bir sey.
        MimeMessage message = sent(
                event(AccountEventType.PASSWORD_RESET_REQUESTED, "t2", Duration.ofMinutes(30)));

        assertThat(message.getRecipients(Message.RecipientType.CC)).isNull();
        assertThat(message.getRecipients(Message.RecipientType.BCC)).isNull();
        assertThat(message.getAllRecipients()).hasSize(1);
    }

    @Test
    @DisplayName("Never says the link lasts zero minutes")
    void neverReportsZeroValidity() throws Exception {
        // Olay teslim edilene kadar sure erimis olabilir. "0 dakika icinde
        // gecerli" demek, calisan bir baglantiyi olu gostermek olurdu.
        Bodies bodies = bodiesOf(sent(
                event(AccountEventType.PASSWORD_RESET_REQUESTED, "t3", Duration.ofSeconds(5))));

        assertThat(bodies.plain()).contains("1 minutes").doesNotContain("0 minutes");
    }

    @Test
    @DisplayName("Does not double the slash when the base url ends with one")
    void doesNotDoubleTheSlash() throws Exception {
        PasswordResetMailService withSlash =
                new PasswordResetMailService(mailSender, FROM, BASE_URL + "/");

        withSlash.send(event(AccountEventType.INVITED, "t4", Duration.ofHours(24)));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        assertThat(bodiesOf(captor.getValue()).plain())
                .contains(BASE_URL + "/reset-password")
                .doesNotContain("//reset-password");
    }
}
