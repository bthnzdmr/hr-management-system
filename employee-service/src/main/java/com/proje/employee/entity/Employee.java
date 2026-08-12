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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    // Ust yonetici. En ust kademede null oldugu icin optional = true (varsayilan).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;

    @Column(name = "job_title", nullable = false, length = 100)
    private String jobTitle;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "salary", precision = 12, scale = 2)
    private BigDecimal salary;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // Ayrilma tarihi ve sebebi. Yalnizca pasif kayitta dolu olabilir; kural
    // veritabaninda CHECK kisitiyla da yaziyor, cunku kodda unutulabilir.
    @Column(name = "terminated_at")
    private LocalDate terminatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "termination_reason", length = 30)
    private TerminationReason terminationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Employee() {
    }

    public Employee(String firstName, String lastName, String email,
                    Department department, String jobTitle, LocalDate hireDate) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.department = department;
        this.jobTitle = jobTitle;
        this.hireDate = hireDate;
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

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public Employee getManager() {
        return manager;
    }

    public void setManager(Employee manager) {
        this.manager = manager;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public BigDecimal getSalary() {
        return salary;
    }

    public void setSalary(BigDecimal salary) {
        this.salary = salary;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDate getTerminatedAt() {
        return terminatedAt;
    }

    public TerminationReason getTerminationReason() {
        return terminationReason;
    }

    /**
     * Ayrilma bilgisini yazar ve kaydi pasiflestirir.
     *
     * Ikisi TEK metotta: ayri setter'lar olsaydi biri cagrilip digeri
     * unutulabilir ve veritabanindaki CHECK kisiti acilista degil, calisma
     * aninda patlardi. Gecersiz bir ara duruma girmek mumkun olmamali.
     */
    public void terminate(LocalDate date, TerminationReason reason) {
        this.active = false;
        this.terminatedAt = date;
        this.terminationReason = reason;
    }

    /**
     * Kaydi yeniden aktiflestirir ve ayrilma bilgisini SILER.
     *
     * Aktif bir kaydin cikis tarihi olamaz -- kisit bunu zaten reddederdi.
     * Gecmis bilgi kayboluyor ama "aktif ama ayrilmis" diye bir durum yoktur.
     */
    public void reactivate() {
        this.active = true;
        this.terminatedAt = null;
        this.terminationReason = null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
