package com.proje.employee.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

// Sorgu gercek veritabanina karsi kosar: taklit edilmis bir repository,
// PostgreSQL'in tip cikarma hatasini asla gostermezdi.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuditSearchTest {

    @Autowired
    private AuditEntryRepository auditEntries;

    private Instant now;

    @BeforeEach
    void setUp() {
        auditEntries.deleteAllInBatch();
        now = Instant.now();

        auditEntries.saveAll(java.util.List.of(
                entry("ada@example.com", AuditAction.ROLES_CHANGED, "USER"),
                entry("grace@example.com", AuditAction.LEAVE_DECIDED, "LEAVE_REQUEST"),
                entry("grace@example.com", AuditAction.DEPARTMENT_CREATED, "DEPARTMENT")));
        auditEntries.flush();
    }

    private AuditEntry entry(String actor, AuditAction action, String targetType) {
        return new AuditEntry(actor, action, targetType, "1", "detail", "corr");
    }

    /** Filtresiz cagri: butun kayitlar, en yeniden eskiye. */
    private org.springframework.data.domain.Page<AuditEntry> all() {
        return auditEntries.search("", EnumSet.allOf(AuditAction.class), "",
                Instant.EPOCH, PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("returns every entry when no filter is given")
    void returnsEverythingUnfiltered() {
        assertThat(all().getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("matches part of an actor's address, ignoring case")
    void filtersByActor() {
        assertThat(auditEntries.search("GRACE", EnumSet.allOf(AuditAction.class), "",
                Instant.EPOCH, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("filters by action")
    void filtersByAction() {
        assertThat(auditEntries.search("", EnumSet.of(AuditAction.ROLES_CHANGED), "",
                Instant.EPOCH, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("ignores the case of the target type, because past rows used another one")
    void filtersByTargetTypeIgnoringCase() {
        assertThat(auditEntries.search("", EnumSet.allOf(AuditAction.class), "leave_request",
                Instant.EPOCH, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("leaves out anything older than the given moment")
    void filtersBySince() {
        Instant future = now.plus(1, ChronoUnit.DAYS);

        assertThat(auditEntries.search("", EnumSet.allOf(AuditAction.class), "",
                future, PageRequest.of(0, 20)).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("puts the newest entry first, because that is the first question asked")
    void sortsNewestFirst() {
        var page = all();

        assertThat(page.getContent().get(0).getOccurredAt())
                .isAfterOrEqualTo(page.getContent().get(2).getOccurredAt());
    }
}
