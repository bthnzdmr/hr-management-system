package com.proje.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    // Uretilen bir id yok: anahtar olayin kendi kimligidir (dogal anahtar).
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 30, updatable = false)
    private String eventType;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(UUID eventId, String eventType, Long employeeId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.employeeId = employeeId;
    }

    @PrePersist
    void onCreate() {
        this.processedAt = Instant.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
