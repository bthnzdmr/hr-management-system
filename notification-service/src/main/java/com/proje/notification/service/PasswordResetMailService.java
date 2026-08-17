package com.proje.notification.service;

import com.proje.notification.event.AccountEvent;
import com.proje.notification.event.AccountEventType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(event.email());
        message.setSubject(subject(event));
        message.setText(body(event));

        // CC YOK: yoneticinin bu maili gormesi, parolayi sifirlayabilecek
        // ikinci bir kisi demektir. Personel bildirimlerindeki CC bir
        // zenginlestirmeydi; burada bir aciktir.
        mailSender.send(message);
    }

    private String subject(AccountEvent event) {
        return switch (event.eventType()) {
            case INVITED -> "Your account is ready -- choose a password";
            case PASSWORD_RESET_REQUESTED -> "Reset your password";
        };
    }

    private String body(AccountEvent event) {
        // Jeton URL parcasi olarak tasiniyor; kodlanmazsa Base64URL disi bir
        // karakter cikarsa baglanti sessizce bozulurdu.
        String link = appBaseUrl + "/reset-password?token="
                + URLEncoder.encode(event.resetToken(), StandardCharsets.UTF_8);

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
}
