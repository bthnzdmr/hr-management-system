package com.proje.employee.service;

import com.proje.employee.dto.DashboardResponse;
import com.proje.employee.repository.DashboardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class DashboardService {

    private final DashboardRepository dashboardRepository;

    public DashboardService(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    /**
     * readOnly = true: sorgular tek bir transaction icinde calisir.
     *
     * Ayri ayri calissalardi her sorgu kendi anini gorurdu ve iki kutucuk
     * birbiriyle celisen sayilar gosterebilirdi -- ornegin toplam kadro ile
     * departman toplamlari tutmayabilirdi. Tek transaction, tek anlik goruntu.
     */
    @Transactional(readOnly = true)
    public DashboardResponse overview() {
        DashboardRepository.Headcount headcount = dashboardRepository.headcount();
        DashboardRepository.SpanOfControl span = dashboardRepository.spanOfControl();

        return new DashboardResponse(
                new DashboardResponse.Headcount(
                        headcount.getActiveCount(),
                        headcount.getInactiveCount(),
                        headcount.getHiredLast30Days(),
                        headcount.getHiredLast90Days(),
                        headcount.getLeftLast12Months(),
                        turnoverRate(headcount.getLeftLast12Months(), headcount.getActiveCount())),

                dashboardRepository.headcountByDepartment().stream()
                        .map(row -> new DashboardResponse.DepartmentHeadcount(
                                row.getDepartmentName(), row.getActiveCount()))
                        .toList(),

                dashboardRepository.turnoverByMonth().stream()
                        .map(row -> new DashboardResponse.MonthlyTurnover(
                                row.getMonth(), row.getTotal()))
                        .toList(),

                dashboardRepository.hiresByMonth().stream()
                        .map(row -> new DashboardResponse.MonthlyHires(
                                row.getMonth(), row.getTotal()))
                        .toList(),

                dashboardRepository.terminationReasons().stream()
                        .map(row -> new DashboardResponse.TerminationReasonCount(
                                row.getReason(), row.getLeaverCount()))
                        .toList(),

                new DashboardResponse.SpanOfControl(
                        span.getManagerCount(),
                        round(span.getAverageDirectReports()),
                        span.getLargestTeamSize()),

                new DashboardResponse.DataQuality(
                        dashboardRepository.countActiveWithoutManager(),
                        dashboardRepository.countEmptyDepartments()));
    }

    /**
     * Devir orani yuzdesi.
     *
     * Aktif kadro sifirsa SIFIR doner, sonsuz veya NaN degil: bos bir sistemde
     * "%NaN devir" yazmak, sayinin kendisinden daha kotu bir hatadir.
     */
    private double turnoverRate(long leavers, long activeHeadcount) {
        if (activeHeadcount == 0) {
            return 0;
        }
        return round(100.0 * leavers / activeHeadcount);
    }

    // Tek ondalik yeter; ham double JSON'a "18.799999999999997" olarak gider.
    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
