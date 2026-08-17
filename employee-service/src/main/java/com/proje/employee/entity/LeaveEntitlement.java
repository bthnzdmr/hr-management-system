package com.proje.employee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Bir personelin bir yila ait yillik izin hakki.
 *
 * Hak SAKLANIR, ise giris tarihinden turetilmez: turetilseydi kurallar
 * degistigi gun gecmis yillarin bakiyesi de sessizce degisirdi.
 */
@Entity
@Table(name = "leave_entitlement")
public class LeaveEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "entitled_days", nullable = false)
    private int entitledDays;

    @Column(name = "carried_over_days", nullable = false)
    private int carriedOverDays;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LeaveEntitlement() {
    }

    public LeaveEntitlement(Employee employee, int year, int entitledDays,
                            int carriedOverDays, String note) {
        this.employee = employee;
        this.year = year;
        this.entitledDays = entitledDays;
        this.carriedOverDays = carriedOverDays;
        this.note = note;
    }

    /**
     * Hakki gunceller.
     *
     * Setter YOK: gun sayisi ile not ayri ayri yazilabilseydi arada notu eski,
     * sayisi yeni bir an olusurdu. Ayni ders `Employee.setActive` kaldirilirken
     * ogrenilmisti.
     */
    public void adjust(int entitledDays, int carriedOverDays, String note) {
        this.entitledDays = entitledDays;
        this.carriedOverDays = carriedOverDays;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public Employee getEmployee() {
        return employee;
    }

    public int getYear() {
        return year;
    }

    public int getEntitledDays() {
        return entitledDays;
    }

    public int getCarriedOverDays() {
        return carriedOverDays;
    }

    public String getNote() {
        return note;
    }
}
