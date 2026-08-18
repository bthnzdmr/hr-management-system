package com.proje.notification.service;

import com.proje.notification.event.EmployeeEvent;
import com.proje.notification.event.EmployeeEventType;
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationMailServiceTest {

    private static final String FROM = "hr-system@example.com";

    @Mock
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        // Sahte gonderici GERCEK bir MimeMessage dondurmeli: aksi halde servis
        // null uzerinde calisir ve sinanan sey kurgunun kendisi olurdu.
        when(mailSender.createMimeMessage()).thenAnswer(call -> new MimeMessage((Session) null));
    }

    private EmployeeEvent event(EmployeeEventType type) {
        return event(type, "Sales", "Engineer");
    }

    private EmployeeEvent event(EmployeeEventType type, String department, String jobTitle) {
        return new EmployeeEvent(UUID.randomUUID(), type, Instant.now(),
                42L, "Grace", "Hopper", "grace@example.com", department, jobTitle);
    }

    /** Gonderilen mailin iki govdesi: duz metin ve HTML. */
    private record Bodies(String plain, String html) {
    }

    private MimeMessage sent(EmployeeEventType type, Optional<String> managerEmail) {
        new NotificationMailService(mailSender, FROM).send(event(type), managerEmail);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private MimeMessage sent(EmployeeEventType type) {
        return sent(type, Optional.empty());
    }

    /**
     * `multipart/alternative` ic ice sarilidir (mixed -> related -> alternative),
     * bu yuzden parcalar OZYINELEMELI toplanir. Duz bir dongu yalnizca en
     * disttaki kabi gorur ve iki govdeyi de bulamazdi.
     */
    private static Bodies bodiesOf(MimeMessage message) throws Exception {
        // MIME basliklarini SONLANDIRIR. Cagrilmazsa parcalarin icerik tipi
        // henuz yazilmamis olur, hepsi varsayilan `text/plain` gorunur ve HTML
        // govdesi duz metin sanilir. Gercek gonderimde bunu `send()` yapiyor.
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
    @DisplayName("Addresses the mail to the employee and names the department")
    void addressesEmployeeAndNamesDepartment() throws Exception {
        MimeMessage message = sent(EmployeeEventType.CREATED);

        assertThat(message.getFrom()[0].toString()).isEqualTo(FROM);
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("grace@example.com");
        assertThat(message.getSubject()).contains("Grace");
        assertThat(bodiesOf(message).html()).contains("Grace Hopper").contains("Sales");
    }

    @Test
    @DisplayName("Keeps a plain text body beside the HTML one")
    void keepsAPlainTextBody() throws Exception {
        // Istemcilerin bir kismi HTML'i engelliyor. HTML'i duz metnin YERINE
        // koymak, o kullanicilara BOS bir mail gondermek olurdu.
        Bodies bodies = bodiesOf(sent(EmployeeEventType.CREATED));

        assertThat(bodies.plain()).contains("Grace Hopper").contains("Sales");
        assertThat(bodies.plain()).doesNotContain("<table").doesNotContain("<html");
    }

    @Test
    @DisplayName("Gives each event type its own subject line")
    void usesDistinctSubjectPerEventType() throws Exception {
        assertThat(sent(EmployeeEventType.UPDATED).getSubject())
                .isEqualTo("Your employee record has been updated");
    }

    @Test
    @DisplayName("States loss of access in the deactivation mail")
    void deactivationMailStatesLossOfAccess() throws Exception {
        Bodies bodies = bodiesOf(sent(EmployeeEventType.DEACTIVATED));

        // Iki surum de AYNI seyi soylemeli; ayrisirlarsa kullanicilar
        // birbirinden farkli mailler okur.
        assertThat(bodies.plain()).contains("deactivated").contains("no longer");
        assertThat(bodies.html()).contains("deactivated").contains("no longer");
    }

    @Test
    @DisplayName("Copies the manager in when their address is known")
    void copiesManagerWhenKnown() throws Exception {
        MimeMessage message = sent(EmployeeEventType.CREATED, Optional.of("manager@example.com"));

        assertThat(message.getRecipients(jakarta.mail.Message.RecipientType.CC)[0].toString())
                .isEqualTo("manager@example.com");
    }

    @Test
    @DisplayName("Leaves the copy field empty when the manager is unknown")
    void leavesCopyEmptyWhenManagerUnknown() throws Exception {
        assertThat(sent(EmployeeEventType.CREATED)
                .getRecipients(jakarta.mail.Message.RecipientType.CC)).isNull();
    }

    @Test
    @DisplayName("Never puts the salary in the mail")
    void neverIncludesSalary() throws Exception {
        Bodies bodies = bodiesOf(sent(EmployeeEventType.UPDATED));

        assertThat(bodies.plain()).doesNotContainIgnoringCase("salary");
        assertThat(bodies.html()).doesNotContainIgnoringCase("salary");
    }

    @Test
    @DisplayName("Escapes values so a stray angle bracket cannot break the markup")
    void escapesValuesInHtml() throws Exception {
        // Arayuzde React kacirmayi kendiligiden yapiyordu; burada isaretlemeyi
        // ELLE kuruyoruz, yani sorumluluk bizde. Kacirilmazsa bir departman
        // adindaki `<` duzeni bozar ve isaretleme enjekte edilebilir.
        new NotificationMailService(mailSender, FROM).send(
                event(EmployeeEventType.CREATED, "R&D", "<b>Engineer</b>"), Optional.empty());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        String html = bodiesOf(captor.getValue()).html();

        assertThat(html).contains("R&amp;D").contains("&lt;b&gt;Engineer&lt;/b&gt;");
        assertThat(html).doesNotContain("<b>Engineer</b>");
    }
}
