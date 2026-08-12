package com.proje.employee.dto;

import java.util.List;

/**
 * Gosterge panelinin tek cevabi.
 *
 * Her kutucuk icin ayri uc acilsaydi ekran acilisinda yedi istek giderdi ve
 * hepsi ayni ani gormezdi -- biri digerinden once donup tutarsiz bir tablo
 * olusturabilirdi. Tek istek, tek anlik goruntu.
 *
 * MAAS BILEREK YOK: SYSTEM_ADMIN paneli gorebiliyor ama ucret bilgisine
 * erisemez. Maasi eklemek cevabin role gore sekil degistirmesini gerektirirdi
 * ve "hangi alan kime gidiyor" sorusu her degisiklikte yeniden sorulurdu.
 */
public record DashboardResponse(
        Headcount headcount,
        List<DepartmentHeadcount> byDepartment,
        List<MonthlyTurnover> turnoverByMonth,
        List<TerminationReasonCount> terminationReasons,
        SpanOfControl spanOfControl,
        DataQuality dataQuality
) {

    public record Headcount(
            long active,
            long inactive,
            long hiredLast30Days,
            long hiredLast90Days,
            long leftLast12Months,
            /** Yuzde olarak: son 12 ayda ayrilan / mevcut aktif kadro. */
            double turnoverRate
    ) {
    }

    public record DepartmentHeadcount(String department, long active) {
    }

    public record MonthlyTurnover(String month, long leavers) {
    }

    public record TerminationReasonCount(String reason, long count) {
    }

    public record SpanOfControl(long managerCount, double averageDirectReports, long largestTeam) {
    }

    public record DataQuality(long activeWithoutManager, long emptyDepartments) {
    }
}
