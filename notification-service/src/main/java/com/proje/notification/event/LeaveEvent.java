package com.proje.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDate;

/**
 * Izin olayinin tuketici tarafindaki karsiligi.
 *
 * <p>`ignoreUnknown`: uretici olaya alan eklerse bu servis KIRILMAZ. Alan
 * eklemek geriye donuk uyumludur, alan silmek degildir.
 *
 * <p>Yuk KIME gonderilecegini soylemez; `employeeEmail`, `managerEmail` ve
 * `actorEmail` birer olgudur ve aliciyi bu servis secer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LeaveEvent(

        UUID eventId,
        LeaveEventType eventType,
        Instant occurredAt,

        Long leaveRequestId,
        Long employeeId,
        String employeeFullName,
        String employeeEmail,
        String managerEmail,

        String leaveType,
        LocalDate startDate,
        LocalDate endDate,
        long days,

        String status,
        String note,
        String decisionNote,
        Long actorEmployeeId,
        String actorEmail,

        /** Alicilarin olay ANINDA susturmus oldugu bildirim turleri. */
        Set<String> employeeMuted,
        Set<String> managerMuted
) {
}
