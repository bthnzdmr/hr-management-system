package com.proje.notification.service;

import com.proje.notification.event.EmployeeEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

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

    public void send(EmployeeEvent event, Optional<String> managerEmail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(event.email());
        message.setSubject(subjectFor(event));
        message.setText(bodyFor(event));
        managerEmail.ifPresent(message::setCc);

        mailSender.send(message);
    }

    private String subjectFor(EmployeeEvent event) {
        return switch (event.eventType()) {
            case CREATED -> "Welcome to the team, " + event.firstName();
            case UPDATED -> "Your employee record has been updated";
            case DEACTIVATED -> "Your employee record has been deactivated";
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
        };
    }
}
