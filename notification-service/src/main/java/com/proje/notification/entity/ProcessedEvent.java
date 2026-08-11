package com.proje.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistable uygulanmasi ZORUNLUDUR.
 *
 * Id atanmis oldugu icin Spring Data varsayilan olarak nesneyi "yeni degil"
 * sayar ve save() cagrisini persist() yerine merge()'e cevirir. merge() once
 * SELECT atar; satir varsa INSERT yerine UPDATE uretir ve birincil anahtar
 * HIC DEVREYE GIRMEZ -- yani mukerrer kayit sessizce kabul edilir.
 *
 * isNew() ile Spring Data'ya bu nesnenin yeni oldugunu soyleyerek persist()
 * yolunu zorluyoruz; boylece ikinci kayit denemesi kisita takilir.
 */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent implements Persistable<UUID> {

    // Uretilen bir id yok: anahtar olayin kendi kimligidir (dogal anahtar).
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Transient
    private boolean newRecord = true;

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

    // Yazildiktan ya da okundugu andan sonra nesne artik yeni degildir.
    @PostPersist
    @PostLoad
    void markExisting() {
        this.newRecord = false;
    }

    @Override
    public UUID getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return newRecord;
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
