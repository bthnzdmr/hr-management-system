package com.proje.employee.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Denetim izinde tek bir satir.
 *
 * Setter YOK: denetim kaydi yazildiktan sonra degistirilemez. Degistirilebilen
 * bir denetim izi, denetim izi degildir -- kaydi silebilen biri kendi izini de
 * silebilir. Veritabani seviyesinde de yalnizca INSERT beklenir.
 */
@Entity
@Table(name = "audit_entry")
public class AuditEntry {

    private static final int DETAIL_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Islemi yapanin e-postasi.
     *
     * Kullanici id'si DEGIL: hesap silinse veya e-posta degisse bile kaydin
     * anlami korunmali. Denetim izi gecmisin fotografidir, canli veriye
     * referans degil.
     */
    @Column(name = "actor", nullable = false, length = 255, updatable = false)
    private String actor;

    @Column(name = "action", nullable = false, length = 50, updatable = false)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Column(name = "target_type", nullable = false, length = 50, updatable = false)
    private String targetType;

    @Column(name = "target_id", length = 64, updatable = false)
    private String targetId;

    @Column(name = "detail", length = DETAIL_MAX, updatable = false)
    private String detail;

    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditEntry() {
    }

    public AuditEntry(String actor, AuditAction action, String targetType, String targetId,
                      String detail, String correlationId) {
        this.actor = actor;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        // Kolon sinirini asan bir detay INSERT'i patlatir ve is islemini geri
        // alirdi: denetim kaydi ise ENGEL OLMAMALI, kirpilmali.
        this.detail = truncate(detail);
        this.correlationId = correlationId;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= DETAIL_MAX) {
            return value;
        }
        return value.substring(0, DETAIL_MAX - 1) + "…";
    }

    @PrePersist
    void onCreate() {
        this.occurredAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getDetail() {
        return detail;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
