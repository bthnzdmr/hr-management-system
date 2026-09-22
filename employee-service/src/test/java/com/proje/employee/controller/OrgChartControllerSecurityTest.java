package com.proje.employee.controller;

import com.proje.employee.config.JwtAuthenticationFilter;
import com.proje.employee.config.JwtService;
import com.proje.employee.config.SecurityConfig;
import com.proje.employee.config.SecurityProblemWriter;
import com.proje.employee.dto.OrgChartResponse;
import com.proje.employee.exception.GlobalExceptionHandler;
import com.proje.employee.service.OrgChartService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Organizasyon semasi TOPLU yapisal veridir.
 *
 * Panelle ayni gerekce: kapsami sinirli bir kullaniciya acilmasi, tek tek
 * goremedigi kisileri toplu halde gostermek olurdu.
 */
@WebMvcTest(OrgChartController.class)
// JwtAuthenticationFilter GERCEK bean olarak alinir, @MockitoBean DEGIL.
// Sahte bir filtre doFilter'i cagirmaz, yani zincir hic ilerlemez ve
// yetkilendirmeye ULASILMAZ: her istek bos bir 200 doner ve butun guvenlik
// testleri sessizce gecer. Olculdu -- ilk halinde anonim istek bile 200 aldi.
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityProblemWriter.class,
        GlobalExceptionHandler.class})
class OrgChartControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrgChartService orgChartService;

    @MockitoBean
    private JwtService jwtService;

    private void serviceReturnsEmptyChart() {
        when(orgChartService.build()).thenReturn(new OrgChartResponse(List.of(), 0, 0, false));
    }

    @Test
    @WithMockUser(roles = "HR_SPECIALIST")
    @DisplayName("An HR specialist may read the org chart")
    void hrSpecialistMayRead() throws Exception {
        serviceReturnsEmptyChart();
        mockMvc.perform(get("/api/org-chart")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DisplayName("A system administrator may read the org chart")
    void systemAdminMayRead() throws Exception {
        serviceReturnsEmptyChart();
        mockMvc.perform(get("/api/org-chart")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("A manager may not read the whole org chart")
    void managerMayNotRead() throws Exception {
        // Yonetici kendi ekibini gorebilir ama BUTUN raporlama cizgilerini
        // degil: bu, kapsaminin disindaki kisileri toplu halde gostermek olurdu.
        mockMvc.perform(get("/api/org-chart")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("A plain employee may not read the org chart")
    void employeeMayNotRead() throws Exception {
        mockMvc.perform(get("/api/org-chart")).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("An anonymous caller may not read the org chart")
    void anonymousMayNotRead() throws Exception {
        mockMvc.perform(get("/api/org-chart")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("HEAD is covered too, not only GET")
    void headIsCovered() throws Exception {
        // Kural metot belirtmiyor. Belirtseydi HEAD kapsam disinda kalirdi:
        // Spring Security HEAD'i GET saymaz ama Spring MVC HEAD istegini
        // @GetMapping metoduna yonlendirir -- maas ucunda tam olarak bu olculdu.
        mockMvc.perform(head("/api/org-chart")).andExpect(status().isForbidden());
    }
}
