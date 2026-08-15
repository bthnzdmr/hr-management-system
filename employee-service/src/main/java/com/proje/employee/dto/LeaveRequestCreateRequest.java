package com.proje.employee.dto;

import com.proje.employee.entity.LeaveType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Yeni izin istegi.
 *
 * <p>Izni GIREN kisi govdede yer almaz: kimlik jetondan okunur. Istemciden
 * gelen bir "ben kimim" alanina guvenmek, herkesin baskasi adina kayit
 * girebilmesi demekti.
 *
 * <p>Cakisma kontrolu de burada YOK: o kural veritabaninda yasiyor. Buradaki
 * dogrulama yalnizca istegin KENDI ICINDE tutarli olmasini sorar.
 */
public record LeaveRequestCreateRequest(

        @NotNull(message = "Employee is required")
        Long employeeId,

        @NotNull(message = "Leave type is required")
        LeaveType type,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        @Size(max = 500, message = "Note cannot be longer than 500 characters")
        String note
) {

    /**
     * Bitis, baslangictan once olamaz; tek gunluk izin gecerlidir.
     *
     * Kural veritabaninda da yazili ama oradaki hata mesaji ("range lower bound
     * must be less than or equal to...") kullaniciya gosterilemez. Burasi ayni
     * kurali ANLASILIR bir 400 olarak doner; veritabani ise son sozu soyler.
     */
    @AssertTrue(message = "End date cannot be before the start date")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
