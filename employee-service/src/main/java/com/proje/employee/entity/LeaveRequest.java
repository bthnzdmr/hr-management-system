package com.proje.employee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** Bir personelin izin istegi. */
@Entity
@Table(name = "leave_request")
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** Kaydi GIREN hesap; iznin sahibiyle ayni olmak zorunda degil. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 20)
    private LeaveType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LeaveStatus status = LeaveStatus.PENDING;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Son gun DAHILDIR; aralik kolonunda +1 olarak saklanir. */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Talebi acan kisinin gerekcesi; karar bunu DEGISTIRMEZ. */
    @Column(name = "note", length = 500)
    private String note;

    /** Karari verenin gerekcesi. Reddetme aciklamasi buraya yazilir. */
    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LeaveRequest() {
    }

    public LeaveRequest(Employee employee, User createdBy, LeaveType type,
                        LocalDate startDate, LocalDate endDate, String note) {
        this.employee = employee;
        this.createdBy = createdBy;
        this.type = type;
        this.startDate = startDate;
        this.endDate = endDate;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Isteği onaylar. */
    public void approve(User decider) {
        requirePending();
        this.status = LeaveStatus.APPROVED;
        this.decidedBy = decider;
        this.decidedAt = Instant.now();
    }

    public void reject(User decider, String decisionNote) {
        requirePending();
        this.status = LeaveStatus.REJECTED;
        this.decidedBy = decider;
        this.decidedAt = Instant.now();

        // Talebin gerekcesi KORUNUR. Once bu alan uzerine yaziliyordu ve
        // "hastane randevusu" diye girilmis bir talep, reddedildikten sonra
        // bos kaliyordu -- karar notu verilmemisse tamamen siliniyordu.
        this.decisionNote = decisionNote;
    }

    /** Karara varilmadan geri ceker; karar bilgisi bos kalir. */
    public void cancel() {
        requirePending();
        this.status = LeaveStatus.CANCELLED;
    }

    private void requirePending() {
        if (status != LeaveStatus.PENDING) {
            throw new IllegalStateException("Leave request is already " + status);
        }
    }

    public boolean isPending() {
        return status == LeaveStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public LeaveType getType() {
        return type;
    }

    public LeaveStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getNote() {
        return note;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public User getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
