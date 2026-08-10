package com.proje.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Employee Service'in yayinladigi olayin bu servisteki karsiligi.
 *
 * Ortak bir modul uzerinden PAYLASILMAZ: paylasilan sinif, iki servisi ayni
 * derleme birimine baglar ve mikroservise gecerken kopardigimiz bagi geri kurar.
 * Servisler arasindaki sozlesme JSON'dur, Java sinifi degil.
 *
 * ignoreUnknown: uretici olaya yeni bir alan eklediginde bu servis kirilmasin.
 * Alan eklemek geriye donuk uyumlu bir degisikliktir; alan silmek degildir.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmployeeEvent(
        UUID eventId,
        EmployeeEventType eventType,
        Instant occurredAt,
        Long employeeId,
        String firstName,
        String lastName,
        String email,
        String departmentName,
        String jobTitle
) {

    public String fullName() {
        return firstName + " " + lastName;
    }
}
