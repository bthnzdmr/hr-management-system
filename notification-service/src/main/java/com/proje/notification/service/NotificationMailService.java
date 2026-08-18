package com.proje.notification.service;

import com.proje.notification.event.EmployeeEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class NotificationMailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public NotificationMailService(JavaMailSender mailSender,
                                   @Value("${app.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    /**
     * Mail HEM duz metin HEM HTML olarak gonderilir (multipart/alternative).
     *
     * HTML'in YERINE degil YANINA: istemcilerin bir kismi HTML'i engelliyor,
     * bir kismi metin tercihi tasiyor ve yalnizca HTML gonderen mailler daha
     * kolay istenmeyen sayiliyor. Istemci hangisini gosterecegine kendisi
     * karar verir; iki surum de ayni bilgiyi tasimak zorundadir.
     */
    public void send(EmployeeEvent event, Optional<String> managerEmail) {
        MimeMessage message = mailSender.createMimeMessage();

        try {
            // true: multipart. Karakter kumesi ACIKCA veriliyor -- verilmezse
            // platform varsayilanina duser ve Turkce ya da aksanli bir isim
            // alicida bozuk gorunur.
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(fromAddress);
            helper.setTo(event.email());
            helper.setSubject(subjectFor(event));
            // Sira onemli: once duz metin, sonra HTML.
            helper.setText(bodyFor(event), htmlFor(event));

            if (managerEmail.isPresent()) {
                helper.setCc(managerEmail.get());
            }
        } catch (MessagingException cause) {
            // Yutulmaz: mail kurulamiyorsa teslim de edilemez ve tuketici bunu
            // basarisizlik sayip yeniden denemelidir.
            throw new MailPreparationException("Could not build the notification mail", cause);
        }

        mailSender.send(message);
    }

    private String subjectFor(EmployeeEvent event) {
        return switch (event.eventType()) {
            case CREATED -> "Welcome to the team, " + event.firstName();
            case UPDATED -> "Your employee record has been updated";
            case DEACTIVATED -> "Your employee record has been deactivated";
            case REACTIVATED -> "Welcome back, " + event.firstName();
        };
    }

    // Maas bilincli olarak disarida birakildi: SMTP sifresiz calisiyor ve
    // saklanmayan gibi gonderilmeyen veri de sizamaz.
    private String bodyFor(EmployeeEvent event) {
        return switch (event.eventType()) {
            case CREATED -> """
                    Hello %s,

                    Your employee record has been created.

                    Department: %s
                    Job title: %s

                    HR System""".formatted(event.fullName(), event.departmentName(), event.jobTitle());

            case UPDATED -> """
                    Hello %s,

                    Your employee record has been updated.

                    Department: %s
                    Job title: %s

                    If you did not expect this change, please contact HR.

                    HR System""".formatted(event.fullName(), event.departmentName(), event.jobTitle());

            case DEACTIVATED -> """
                    Hello %s,

                    Your employee record has been deactivated and you no longer
                    have access to the system.

                    HR System""".formatted(event.fullName());

            case REACTIVATED -> """
                    Hello %s,

                    Your employee record has been reactivated and your access
                    has been restored.

                    Department: %s
                    Job title: %s

                    HR System""".formatted(event.fullName(), event.departmentName(), event.jobTitle());
        };
    }

    /**
     * Ayni bilginin HTML surumu.
     *
     * Duz metin surumuyle AYNI seyi soylemek zorunda: istemci hangisini
     * gosterecegine kendisi karar veriyor ve iki surum ayrisirsa kullanicilar
     * birbirinden farkli maillar okur.
     */
    private String htmlFor(EmployeeEvent event) {
        String heading = switch (event.eventType()) {
            case CREATED -> "Welcome to the team, " + event.firstName();
            case UPDATED -> "Your record has been updated";
            case DEACTIVATED -> "Your record has been deactivated";
            case REACTIVATED -> "Welcome back, " + event.firstName();
        };

        String details = MailTemplate.facts(
                "Department", event.departmentName(),
                "Job title", event.jobTitle());

        String content = switch (event.eventType()) {
            case CREATED -> MailTemplate.paragraph("Hello " + event.fullName()
                    + ", your employee record has been created.") + details;

            case UPDATED -> MailTemplate.paragraph("Hello " + event.fullName()
                    + ", your employee record has been updated.")
                    + details
                    + MailTemplate.note("If you did not expect this change, please contact HR.");

            case DEACTIVATED -> MailTemplate.paragraph("Hello " + event.fullName()
                    + ", your employee record has been deactivated and you no longer have "
                    + "access to the system.");

            case REACTIVATED -> MailTemplate.paragraph("Hello " + event.fullName()
                    + ", your employee record has been reactivated and your access has been "
                    + "restored.") + details;
        };

        return MailTemplate.page(heading, content);
    }
}
