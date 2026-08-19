package com.proje.employee.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tahakkuku tetikleyen zamanlanmis is. */
@ExtendWith(MockitoExtension.class)
class LeaveEntitlementAccrualTest {

    @Mock
    private LeaveEntitlementService entitlements;

    private LeaveEntitlementAccrual jobAt(String instant, String zone) {
        return new LeaveEntitlementAccrual(entitlements,
                Clock.fixed(Instant.parse(instant), ZoneId.of(zone)));
    }

    @Test
    @DisplayName("Accrues for the calendar year the clock is in")
    void accruesForTheCurrentYear() {
        jobAt("2033-06-15T12:00:00Z", "Europe/Istanbul").accrueCurrentYear();

        verify(entitlements).accrueFor(2033);
    }

    @Test
    @DisplayName("The year comes from the local calendar, not from UTC")
    void theYearComesFromTheLocalCalendar() {
        // UTC'de 31 Aralik 23:00, Istanbul'da yeni yildir. Saat UTC'ye
        // sabitlenseydi is yilin ilk saatlerinde HALA gecen yili tamamlamaya
        // calisir ve yeni yilin satirlari bir gun gec yazilirdi.
        jobAt("2032-12-31T23:00:00Z", "Europe/Istanbul").accrueCurrentYear();

        verify(entitlements).accrueFor(2033);
    }

    @Test
    @DisplayName("A failure is logged and contained instead of escaping the scheduler")
    void aFailureIsContained() {
        // Firlatan bir @Scheduled metot yalnizca kendi turunu kaybeder ama
        // sebebi loglanmazsa hicbir yerde iz kalmaz. Yarin tekrar denenecek.
        when(entitlements.accrueFor(anyInt())).thenThrow(new RuntimeException("database down"));

        assertThatCode(() -> jobAt("2033-06-15T12:00:00Z", "UTC").accrueCurrentYear())
                .doesNotThrowAnyException();
    }
}
