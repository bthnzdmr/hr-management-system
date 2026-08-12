package com.proje.employee.controller;

import com.proje.employee.config.JwtAuthenticationFilter;
import com.proje.employee.config.JwtService;
import com.proje.employee.config.SecurityConfig;
import com.proje.employee.config.SecurityProblemWriter;
import com.proje.employee.exception.GlobalExceptionHandler;
import com.proje.employee.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Toplu veri kimlere acik?
 *
 * Kapsami sinirli bir kullanici tek tek goremedigi kisilerin toplamini da
 * gormemelidir. "Pazarlama 5 kisi" masum gorunur ama ayni mantikla ayrilma
 * sayilari da sizar ve kucuk bir departmanda toplam, bireyi ele verir.
 */
@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityProblemWriter.class,
        GlobalExceptionHandler.class})
class DashboardControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private JwtService jwtService;

    @Test
    @WithMockUser(roles = "HR_SPECIALIST")
    @DisplayName("An HR specialist may read the overview")
    void hrSpecialistMayRead() throws Exception {
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DisplayName("A system administrator may read the overview")
    void systemAdministratorMayRead() throws Exception {
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("A plain employee may not read the overview")
    void employeeMayNotRead() throws Exception {
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("A manager may not read the overview either")
    void managerMayNotRead() throws Exception {
        // Yoneticinin kapsami kendi ekibiyle sinirli; sirket geneli toplamlar
        // o sinirin disindadir.
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("HEAD does not slip past the rule either")
    void headIsCoveredToo() throws Exception {
        // Daha once olculmus bir kusur: metot belirtilen kural HEAD'i
        // kapsamiyordu ve Spring MVC HEAD'i @GetMapping metoduna yonlendiriyor.
        mockMvc.perform(head("/api/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("An anonymous visitor gets 401, not 403")
    void anonymousGetsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    }
}
