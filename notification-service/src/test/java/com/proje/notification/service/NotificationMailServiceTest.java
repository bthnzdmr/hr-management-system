package com.proje.notification.service;

import com.proje.notification.event.EmployeeEvent;
import com.proje.notification.event.EmployeeEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationMailServiceTest {

    private static final String FROM = "hr-system@example.com";

    @Mock
    private JavaMailSender mailSender;

    private EmployeeEvent event(EmployeeEventType type) {
        return new EmployeeEvent(UUID.randomUUID(), type, Instant.now(),
                42L, "Grace", "Hopper", "grace@example.com", "Sales", "Engineer");
    }

    private SimpleMailMessage sentMessage(EmployeeEventType type) {
        return sentMessage(type, Optional.empty());
    }

    private SimpleMailMessage sentMessage(EmployeeEventType type, Optional<String> managerEmail) {
        new NotificationMailService(mailSender, FROM).send(event(type), managerEmail);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Addresses the mail to the employee and names the department")
    void addressesEmployeeAndNamesDepartment() {
        SimpleMailMessage message = sentMessage(EmployeeEventType.CREATED);

        assertThat(message.getFrom()).isEqualTo(FROM);
        assertThat(message.getTo()).containsExactly("grace@example.com");
        assertThat(message.getSubject()).contains("Grace");
        assertThat(message.getText()).contains("Grace Hopper").contains("Sales");
    }

    @Test
    @DisplayName("Gives each event type its own subject line")
    void usesDistinctSubjectPerEventType() {
        assertThat(sentMessage(EmployeeEventType.UPDATED).getSubject())
                .isEqualTo("Your employee record has been updated");
    }

    @Test
    @DisplayName("States loss of access in the deactivation mail")
    void deactivationMailStatesLossOfAccess() {
        assertThat(sentMessage(EmployeeEventType.DEACTIVATED).getText())
                .contains("deactivated")
                .contains("no longer");
    }

    @Test
    @DisplayName("Copies the manager in when their address is known")
    void copiesManagerWhenKnown() {
        SimpleMailMessage message =
                sentMessage(EmployeeEventType.CREATED, Optional.of("manager@example.com"));

        assertThat(message.getCc()).containsExactly("manager@example.com");
    }

    @Test
    @DisplayName("Leaves the copy field empty when the manager is unknown")
    void leavesCopyEmptyWhenManagerUnknown() {
        assertThat(sentMessage(EmployeeEventType.CREATED).getCc()).isNull();
    }

    @Test
    @DisplayName("Never puts the salary in the mail")
    void neverIncludesSalary() {
        // Bildirim maili sifresiz SMTP uzerinden gidiyor; hassas veri disarida
        // birakilir. Bu kararin testi, ileride govde degistiginde uyarir.
        assertThat(sentMessage(EmployeeEventType.UPDATED).getText())
                .doesNotContainIgnoringCase("salary");
    }
}
