package com.proje.employee.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proje.employee.entity.OutboxEvent;
import com.proje.employee.repository.OutboxRepository;
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
        outboxRepository.save(new OutboxEvent(
                UUID.fromString(event.eventId()),
                event.eventType().name(),
                event.eventType().routingKey(),
                serialize(event)));
    }

    private String serialize(EmployeeEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // Yutulmaz: olay kaydedilemiyorsa is verisi de yazilmamalidir.
            throw new IllegalStateException("Event could not be serialized: " + event.eventId(), e);
        }
    }
}
