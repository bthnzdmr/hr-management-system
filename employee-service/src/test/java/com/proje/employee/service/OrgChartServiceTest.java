package com.proje.employee.service;

import com.proje.employee.dto.OrgChartResponse;
import com.proje.employee.repository.DashboardRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgChartServiceTest {

    @Mock
    private DashboardRepository dashboardRepository;

    /** Sorgunun dondurdugu duz satiri taklit eder. */
    private DashboardRepository.OrgNode row(long id, String last, Long managerId, int depth) {
        return new DashboardRepository.OrgNode() {
            public Long getEmployeeId() {
                return id;
            }

            public String getFirstName() {
                return "F" + id;
            }

            public String getLastName() {
                return last;
            }

            public String getJobTitle() {
                return "Engineer";
            }

            public String getDepartmentName() {
                return "Sales";
            }

            public Long getManagerId() {
                return managerId;
            }

            public int getDepth() {
                return depth;
            }
        };
    }

    private OrgChartResponse build(List<DashboardRepository.OrgNode> rows, long active) {
        when(dashboardRepository.orgChart(anyInt())).thenReturn(rows);
        when(dashboardRepository.countActive()).thenReturn(active);
        return new OrgChartService(dashboardRepository).build();
    }

    @Test
    @DisplayName("Nests each person under the manager they report to")
    void buildsTheTree() {
        //  1 (kok)
        //  ├── 2
        //  │   └── 4
        //  └── 3
        OrgChartResponse chart = build(List.of(
                row(1, "Root", null, 1),
                row(2, "Alpha", 1L, 2),
                row(3, "Beta", 1L, 2),
                row(4, "Gamma", 2L, 3)), 4);

        assertThat(chart.roots()).hasSize(1);
        OrgChartResponse.Node root = chart.roots().get(0);
        assertThat(root.reports()).extracting(OrgChartResponse.Node::id)
                .containsExactly(2L, 3L);
        assertThat(root.reports().get(0).reports()).extracting(OrgChartResponse.Node::id)
                .containsExactly(4L);
        assertThat(chart.placed()).isEqualTo(4);
    }

    @Test
    @DisplayName("Reports several roots when the company has several heads")
    void supportsSeveralRoots() {
        // Yoneticisi olmayan birden fazla kisi normaldir: organizasyonun
        // tepesindeki departman baskanlari. Bu bir veri eksikligi DEGIL.
        OrgChartResponse chart = build(List.of(
                row(1, "Ada", null, 1),
                row(2, "Grace", null, 1)), 2);

        assertThat(chart.roots()).hasSize(2);
        assertThat(chart.unreachable()).isZero();
    }

    @Test
    @DisplayName("Counts the people the tree could not reach instead of losing them")
    void countsUnreachablePeople() {
        // Yoneticisi pasiflesmis personel koke baglanamaz ve sorgudan hic
        // donmez. Sessizce kaybetmek, EKSIK OLDUGUNU SOYLEMEYEN bir sema
        // uretirdi: ekranda 8 kisi gorunur, sirkette 10 kisi calisir ve
        // aradaki fark kimseye bildirilmez.
        OrgChartResponse chart = build(List.of(
                row(1, "Root", null, 1),
                row(2, "Alpha", 1L, 2)), 10);

        assertThat(chart.placed()).isEqualTo(2);
        assertThat(chart.unreachable()).isEqualTo(8);
    }

    @Test
    @DisplayName("Produces an empty chart rather than failing when nobody is active")
    void handlesEmptyOrganisation() {
        OrgChartResponse chart = build(List.of(), 0);

        assertThat(chart.roots()).isEmpty();
        assertThat(chart.placed()).isZero();
        assertThat(chart.unreachable()).isZero();
    }
}
