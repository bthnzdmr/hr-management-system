package com.proje.employee.service;

import com.proje.employee.dto.OrgChartResponse;
import com.proje.employee.repository.DashboardRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Duz sorgu sonucundan ic ice agac kurar.
 *
 * <p>Agaci SQL kuramaz: ozyinelemeli CTE satirlari duz dondurur, ic ice yapi
 * uygulama tarafinda olusur. Bu dogru is bolumu -- veritabani gezinmeyi tek
 * sorguda yapar, sekillendirmeyi bellek yapar.
 *
 * <p>Alternatif, her seviye icin ayri sorgu atmakti; o da tam olarak
 * assertNoCycle'da kapatilan N+1'in ta kendisi olurdu.
 */
@Service
public class OrgChartService {

    /**
     * Ozyinelemenin ust siniri.
     *
     * Gercek bir organizasyonda 20 seviye zaten asiridir; sinir dogru veri
     * icin degil, BOZUK veri icin vardir: dolayli bir dongu (A -> B -> A)
     * ozyinelemeyi sonsuza kadar calistirir ve sorgu sunucuyu tuketir.
     */
    private static final int MAX_DEPTH = 20;

    private final DashboardRepository dashboardRepository;
    private final int maxNodes;

    public OrgChartService(DashboardRepository dashboardRepository,
                           @Value("${app.org-chart.max-nodes}") int maxNodes) {
        this.dashboardRepository = dashboardRepository;
        this.maxNodes = maxNodes;
    }

    /**
     * Iki sorgu TEK transaction'da: agac ve toplam kadro ayni anlik goruntuden
     * okunur. Ayri okunsalardi aradaki bir ise alim "ulasilamayan" gibi
     * gorunurdu.
     */
    @Transactional(readOnly = true)
    public OrgChartResponse build() {
        // Tavandan BIR FAZLA istenir: asildigini anlamanin tek yolu bu.
        // Tam tavan kadar istenseydi "tam doldu" ile "tastı" ayirt edilemezdi.
        // Ayni desen CSV disa aktarmada da kullaniliyor.
        List<DashboardRepository.OrgNode> rows =
                dashboardRepository.orgChart(MAX_DEPTH, maxNodes + 1);

        boolean truncated = rows.size() > maxNodes;
        if (truncated) {
            rows = rows.subList(0, maxNodes);
        }

        long active = dashboardRepository.countActive();

        // Sorgu derinlige gore SIRALI donuyor, yani bir dugume ulasildiginda
        // yoneticisi coktan haritada. Tek gecis yetiyor.
        Map<Long, List<OrgChartResponse.Node>> reportsOf = new LinkedHashMap<>();
        Map<Long, OrgChartResponse.Node> nodes = new LinkedHashMap<>();
        List<OrgChartResponse.Node> roots = new ArrayList<>();

        for (DashboardRepository.OrgNode row : rows) {
            List<OrgChartResponse.Node> reports = new ArrayList<>();
            OrgChartResponse.Node node = new OrgChartResponse.Node(
                    row.getEmployeeId(),
                    row.getFirstName(),
                    row.getLastName(),
                    row.getJobTitle(),
                    row.getDepartmentName(),
                    row.getDepth(),
                    reports);

            nodes.put(row.getEmployeeId(), node);
            reportsOf.put(row.getEmployeeId(), reports);

            if (row.getManagerId() == null) {
                roots.add(node);
            } else {
                // Yonetici agacta yoksa (pasiflesmis) dugum de bagli kalamaz.
                // Sorgu zaten boyle bir satiri getirmez; bu yalnizca savunma.
                List<OrgChartResponse.Node> siblings = reportsOf.get(row.getManagerId());
                if (siblings != null) {
                    siblings.add(node);
                }
            }
        }

        // Kirpildiysa "ulasilamayan" HESAPLANMAZ: cizilmeyenlerin hangisinin
        // veri kusuru (yoneticisi pasif), hangisinin sinir yuzunden disarida
        // kaldigi ayirt edilemez. Tahmini bir sayi vermek, olmayan bir
        // kesinlik uydurmak olurdu.
        long unreachable = truncated ? 0 : active - nodes.size();

        return new OrgChartResponse(roots, nodes.size(), unreachable, truncated);
    }
}
