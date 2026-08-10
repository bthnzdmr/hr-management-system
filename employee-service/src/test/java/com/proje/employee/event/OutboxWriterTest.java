package com.proje.employee.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proje.employee.entity.OutboxEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import com.proje.employee.repository.OutboxRepository;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxWriterTest {

    @Mock
    private OutboxRepository outboxRepository;

    // Uygulamadaki bean ile ayni yapilandirma: ciplak ObjectMapper Instant
    // serilestiremez, bu yuzden testin gercegi yansitmasi icin builder kullanilir.
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private EmployeeEvent employeeEvent(EmployeeEventType type) {
        return new EmployeeEvent(UUID.randomUUID().toString(), type, Instant.now(),
                7L, "Ada", "Lovelace", "ada@example.com", "Sales", "Analyst");
    }

    @Test
    @DisplayName("Stores the event with the routing key its type maps to")
    void storesEventWithRoutingKey() {
        OutboxWriter writer = new OutboxWriter(outboxRepository, objectMapper);
        EmployeeEvent event = employeeEvent(EmployeeEventType.UPDATED);

        writer.write(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent stored = captor.getValue();

        assertThat(stored.getEventId()).hasToString(event.eventId());
        assertThat(stored.getEventType()).isEqualTo("UPDATED");
        assertThat(stored.getRoutingKey()).isEqualTo("employee.updated");
    }

    @Test
    @DisplayName("Stores the payload as JSON that can be read back field by field")
    void storesPayloadAsReadableJson() throws Exception {
        OutboxWriter writer = new OutboxWriter(outboxRepository, objectMapper);
        EmployeeEvent event = employeeEvent(EmployeeEventType.CREATED);

        writer.write(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        EmployeeEvent restored = objectMapper.readValue(captor.getValue().getPayload(), EmployeeEvent.class);
        assertThat(restored).isEqualTo(event);
    }
}
