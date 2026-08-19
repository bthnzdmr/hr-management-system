package com.proje.employee.service;

import com.proje.employee.repository.DashboardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Kapsam olcumu bu sinifi %4,3'te buldu: panelin BUTUN sayilari buradan
 * geciyordu ve tek bir birim testi yoktu.
 *
 * Sinanan sey SQL degil (o ayri bir is), sorgulardan donen degerlerin nasil
 * BIRLESTIRILDIGI: bir bolumun eslenmesi unutulursa panel o kutucugu sessizce
 * bos gosterir, ve sifira bolme "%NaN devir" yazdirir.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    @Mock
    private DashboardRepository dashboardRepository;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(dashboardRepository);
        givenHeadcount(30, 5, 2, 6, 3);
        givenSpanOfControl(4, 2.3333333, 7);

        when(dashboardRepository.headcountByDepartment()).thenReturn(List.of());
        when(dashboardRepository.turnoverByMonth()).thenReturn(List.of());
        when(dashboardRepository.hiresByMonth()).thenReturn(List.of());
        when(dashboardRepository.terminationReasons()).thenReturn(List.of());
    }

    private void givenHeadcount(long active, long inactive, long hired30, long hired90, long left) {
        DashboardRepository.Headcount headcount = mock(DashboardRepository.Headcount.class);
        when(headcount.getActiveCount()).thenReturn(active);
        when(headcount.getInactiveCount()).thenReturn(inactive);
        when(headcount.getHiredLast30Days()).thenReturn(hired30);
        when(headcount.getHiredLast90Days()).thenReturn(hired90);
        when(headcount.getLeftLast12Months()).thenReturn(left);
        when(dashboardRepository.headcount()).thenReturn(headcount);
    }

    private void givenSpanOfControl(long managers, double average, long largest) {
        DashboardRepository.SpanOfControl span = mock(DashboardRepository.SpanOfControl.class);
        when(span.getManagerCount()).thenReturn(managers);
        when(span.getAverageDirectReports()).thenReturn(average);
        when(span.getLargestTeamSize()).thenReturn(largest);
        when(dashboardRepository.spanOfControl()).thenReturn(span);
    }

    /**
     * Satirlar `when(...)` ifadesinin DISINDA kurulur.
     *
     * Icinde kurulsaydi Mockito yarim kalmis bir stub gorur ve test kodu hic
     * calistiramaz (UnfinishedStubbing). Bu tuzaga bu oturumda bir kez daha
     * dusuldugu icin yardimci dogrudan LISTE donduruyor: cagiran tarafta
     * hataya dusulecek bir yer birakmiyor.
     */
    private List<DashboardRepository.MonthlyCount> months(String... monthAndTotal) {
        List<DashboardRepository.MonthlyCount> rows = new java.util.ArrayList<>();

        for (String pair : monthAndTotal) {
            String[] parts = pair.split(":");
            DashboardRepository.MonthlyCount row = mock(DashboardRepository.MonthlyCount.class);
            when(row.getMonth()).thenReturn(parts[0]);
            when(row.getTotal()).thenReturn(Long.parseLong(parts[1]));
            rows.add(row);
        }

        return rows;
    }

    @Test
    @DisplayName("Reports zero turnover instead of NaN when nobody is active")
    void reportsZeroTurnoverOnAnEmptySystem() {
        // Bos bir sistemde "%NaN devir" yazmak, sayinin kendisinden daha kotu
        // bir hatadir: gosterge bozuk gorunur ve guveni komple yikar.
        givenHeadcount(0, 0, 0, 0, 4);

        assertThat(service.overview().headcount().turnoverRate()).isZero();
    }

    @Test
    @DisplayName("Rounds the turnover rate to one decimal")
    void roundsTheTurnoverRate() {
        // 3/30 = %10 tam; 5/30 ise 16.666... ve ham double JSON'a
        // "16.666666666666664" olarak giderdi.
        givenHeadcount(30, 0, 0, 0, 5);

        assertThat(service.overview().headcount().turnoverRate()).isEqualTo(16.7);
    }

    @Test
    @DisplayName("Rounds the average team size as well")
    void roundsTheAverageTeamSize() {
        assertThat(service.overview().spanOfControl().averageDirectReports()).isEqualTo(2.3);
    }

    @Test
    @DisplayName("Carries every headcount figure through untouched")
    void carriesHeadcountThrough() {
        var headcount = service.overview().headcount();

        assertThat(headcount.active()).isEqualTo(30);
        assertThat(headcount.inactive()).isEqualTo(5);
        assertThat(headcount.hiredLast30Days()).isEqualTo(2);
        assertThat(headcount.hiredLast90Days()).isEqualTo(6);
        assertThat(headcount.leftLast12Months()).isEqualTo(3);
    }

    @Test
    @DisplayName("Keeps the months in the order the query returned them")
    void keepsMonthOrder() {
        // Sira SORGUNUN isi: `generate_series` bosluklari sifirla dolduruyor
        // ve burada yeniden siralamak, olmayan aylari kaydirirdi.
        var rows = months("2026-01:1", "2026-02:0", "2026-03:4");
        when(dashboardRepository.turnoverByMonth()).thenReturn(rows);

        assertThat(service.overview().turnoverByMonth())
                .extracting(m -> m.month() + ":" + m.leavers())
                .containsExactly("2026-01:1", "2026-02:0", "2026-03:4");
    }

    @Test
    @DisplayName("Maps hires separately from leavers")
    void mapsHiresSeparatelyFromLeavers() {
        // Iki seri AYNI projeksiyonu paylasiyor (MonthlyCount) ve anlami
        // yalnizca hangi alana konduklari belirliyor. Karistirmak, grafigi
        // sessizce tersine cevirirdi.
        var leavers = months("2026-01:2");
        var hires = months("2026-01:9");
        when(dashboardRepository.turnoverByMonth()).thenReturn(leavers);
        when(dashboardRepository.hiresByMonth()).thenReturn(hires);

        var overview = service.overview();

        assertThat(overview.turnoverByMonth().get(0).leavers()).isEqualTo(2);
        assertThat(overview.hiresByMonth().get(0).hires()).isEqualTo(9);
    }

    @Test
    @DisplayName("Reports the data quality counts it was given")
    void reportsDataQuality() {
        // Bunlar UYARI degil BILGI: yoneticisi olmayan aktif personel cogu
        // zaman departman baskanidir ve bir veri hatasi degildir.
        when(dashboardRepository.countActiveWithoutManager()).thenReturn(5L);
        when(dashboardRepository.countEmptyDepartments()).thenReturn(1L);

        var quality = service.overview().dataQuality();

        assertThat(quality.activeWithoutManager()).isEqualTo(5);
        assertThat(quality.emptyDepartments()).isEqualTo(1);
    }
}
