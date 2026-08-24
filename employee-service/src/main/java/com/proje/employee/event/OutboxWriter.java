package com.proje.employee.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proje.employee.entity.OutboxEvent;
import com.proje.employee.config.CorrelationIdFilter;
import com.proje.employee.repository.OutboxRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OutboxWriter {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    // Cagiranin transaction'ina katilir; kendi transaction'ini acmaz. Olay kaydi
    // ile is verisi ayni commit'te yazilir, atomiklik buradan gelir.
    public void write(EmployeeEvent event) {
        write(event.eventId(), event.eventType().name(), event.eventType().routingKey(), event);
    }

    public void write(AccountEvent event) {
        write(event.eventId(), event.eventType().name(), event.eventType().routingKey(), event);
    }

    public void write(LeaveEvent event) {
        write(event.eventId(), event.eventType().name(), event.eventType().routingKey(), event);
    }

    private void write(String eventId, String type, String routingKey, Object payload) {
        // MDC BURADA dolu: bu metot istegin ipliginde calisir. Relay ise
        // zamanlayici ipliginde calisir ve oranin MDC'si bos olur -- kimlik
        // bu yuzden satira yazilir, sonra okunur.
        outboxRepository.save(new OutboxEvent(
                UUID.fromString(eventId),
                type,
                routingKey,
                serialize(eventId, payload),
                MDC.get(CorrelationIdFilter.MDC_KEY)));
    }

    private String serialize(String eventId, Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // Yutulmaz: olay kaydedilemiyorsa is verisi de yazilmamalidir.
            throw new IllegalStateException("Event could not be serialized: " + eventId, e);
        }
    }
}
