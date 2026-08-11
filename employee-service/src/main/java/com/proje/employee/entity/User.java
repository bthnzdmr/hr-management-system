package com.proje.employee.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    /**
     * Roller ayri bir tabloda ama User'in PARCASI.
     *
     * @ElementCollection secildi, @OneToMany degil: rolun kendi kimligi ve
     * yasam dongusu yok -- kullanicidan bagimsiz bir "rol kaydi" diye bir sey
     * anlamsizdir. Kullanici silinince rolleri de gider.
     *
     * EAGER cunku her istekte yetkilendirme icin okunuyor; LAZY olsaydi
     * kimlik dogrulamanin her adiminda ayri bir sorgu acilirdi.
     *
     * STRING sart: varsayilan ORDINAL, enum sirasini sayi olarak yazar ve
     * enum'a yeni bir deger eklenip sira degisirse mevcut satirlarin anlami kayar.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    // Opsiyonel: sistem hesaplarinin personel kaydi olmayabilir.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(String email, String passwordHash, Set<Role> roles) {
        this.email = email;
        this.passwordHash = passwordHash;
        setRoles(roles);
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

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** Degistirilemez kopya doner: rol kumesi yalnizca setRoles ile degisir. */
    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    /**
     * Rol kumesini komple degistirir.
     *
     * Kolleksiyon nesnesinin KENDISI degistirilmez, icerigi guncellenir:
     * Hibernate'in izledigi kolleksiyonu yeni bir ornekle degistirmek
     * "A collection with orphanDelete was no longer referenced" hatasina
     * ve sessiz kayiplara yol acar.
     */
    public void setRoles(Set<Role> next) {
        if (next == null || next.isEmpty()) {
            throw new IllegalArgumentException("A user must have at least one role");
        }
        roles.clear();
        roles.addAll(next);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    /** Test ve tohumlama kolayligi icin. */
    public static Set<Role> rolesOf(Role first, Role... rest) {
        return EnumSet.of(first, rest);
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
