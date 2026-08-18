package com.proje.notification.service;

import com.proje.notification.event.AccountEvent;
import com.proje.notification.event.AccountEventType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@Service
public class PasswordResetMailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String appBaseUrl;

    public PasswordResetMailService(JavaMailSender mailSender,
                                    @Value("${app.mail.from}") String fromAddress,
                                    @Value("${app.base-url}") String appBaseUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        // Sondaki egik cizgi iki kez yazilirsa baglanti bozulur.
        this.appBaseUrl = appBaseUrl.endsWith("/")
                ? appBaseUrl.substring(0, appBaseUrl.length() - 1)
                : appBaseUrl;
    }

    public void send(AccountEvent event) {
        MimeMessage message = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(fromAddress);
            helper.setTo(event.email());
            helper.setSubject(subject(event));
            helper.setText(body(event), html(event));

            // CC YOK: yoneticinin bu maili gormesi, parolayi sifirlayabilecek
            // ikinci bir kisi demektir. Personel bildirimlerindeki CC bir
            // zenginlestirmeydi; burada bir aciktir.
        } catch (MessagingException cause) {
            throw new MailPreparationException("Could not build the account mail", cause);
        }

        mailSender.send(message);
    }

    private String subject(AccountEvent event) {
        return switch (event.eventType()) {
            case INVITED -> "Your account is ready -- choose a password";
            case PASSWORD_RESET_REQUESTED -> "Reset your password";
        };
    }

    /**
     * Jeton URL parcasi olarak tasiniyor; kodlanmazsa Base64URL disi bir
     * karakter cikarsa baglanti sessizce bozulurdu.
     */
    private String link(AccountEvent event) {
        return appBaseUrl + "/reset-password?token="
                + URLEncoder.encode(event.resetToken(), StandardCharsets.UTF_8);
    }

    private String body(AccountEvent event) {
        String link = link(event);

        long minutes = Duration.between(Instant.now(), event.expiresAt()).toMinutes();
        long hours = Duration.between(Instant.now(), event.expiresAt()).toHours();

        if (event.eventType() == AccountEventType.INVITED) {
            return """
                    Hello,

                    An account has been created for you in the HR system.
                    Choose your password to sign in for the first time:

                    %s

                    The link works once and expires in about %d hours.

                    Nobody else knows this password -- not even the person who
                    created the account.

                    HR System""".formatted(link, Math.max(hours, 1));
        }

        return """
                Hello,

                Someone asked to reset the password for this account.
                Open the link below to choose a new one:

                %s

                The link works once and expires in about %d minutes.

                If you did not ask for this, you can ignore this message --
                your password has not changed.

                HR System""".formatted(link, Math.max(minutes, 1));
    }

    /** Duz metin surumuyle AYNI bilgi; tek fark eylemin bir dugme olmasi. */
    private String html(AccountEvent event) {
        String link = link(event);
        boolean invited = event.eventType() == AccountEventType.INVITED;

        long hours = Math.max(Duration.between(Instant.now(), event.expiresAt()).toHours(), 1);
        long minutes = Math.max(Duration.between(Instant.now(), event.expiresAt()).toMinutes(), 1);

        if (invited) {
            return MailTemplate.page("Your account is ready",
                    MailTemplate.paragraph("An account has been created for you in the HR system. "
                            + "Choose your password to sign in for the first time.")
                            + MailTemplate.button(link, "Choose a password")
                            + MailTemplate.note("The link works once and expires in about "
                            + hours + " hours.")
                            + MailTemplate.note("Nobody else knows this password, not even the "
                            + "person who created the account."));
        }

        return MailTemplate.page("Reset your password",
                MailTemplate.paragraph("Someone asked to reset the password for this account. "
                        + "Choose a new one below.")
                        + MailTemplate.button(link, "Set a new password")
                        + MailTemplate.note("The link works once and expires in about "
                        + minutes + " minutes.")
                        + MailTemplate.note("If you did not ask for this, you can ignore this "
                        + "message. Your password has not changed."));
    }
}
