package com.proje.employee.controller;

import com.proje.employee.config.JwtAuthenticationFilter;
import com.proje.employee.config.JwtService;
import com.proje.employee.config.SecurityConfig;
import com.proje.employee.config.SecurityProblemWriter;
import com.proje.employee.repository.UserRepository;
import com.proje.employee.service.AccessScopeResolver;
import com.proje.employee.service.AuditService;
import com.proje.employee.service.LeaveRequestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Siralama, degeri gostermeden buyukluk iliskisi sizdirir.
 *
 * Olculdu: Ik ucret ucunda 403 aliyordu ama
 * "/api/leave-requests?sort=employee.salary,desc" butun kadronun ucret
 * siralamasini birebir veriyordu. Ayni sizinti bir kez /api/employees ve
 * /api/users icin kapatilmisti; sonradan yazilan iki uc korumayi almamisti.
 */
@WebMvcTest(controllers = {LeaveRequestController.class, AuditController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityProblemWriter.class})
class SortSideChannelTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeaveRequestService leaveRequestService;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private AccessScopeResolver accessScopeResolver;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @WithMockUser(roles = "HR_SPECIALIST")
    @DisplayName("Refuses to sort leave requests through the employee relation")
    void refusesSortingThroughTheRelation() throws Exception {
        // Iliski uzerinden siralama, cevapta hic donmeyen bir alani okunabilir
        // kilar. Istek SERVISE HIC ULASMAMALI.
        mockMvc.perform(get("/api/leave-requests").param("sort", "employee.salary,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid sort field"));

        verify(leaveRequestService, never()).list(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "HR_SPECIALIST")
    @DisplayName("Does not name the rejected leave sort field")
    void doesNotEchoTheRejectedField() throws Exception {
        // Adi geri yazmak, boyle bir alanin VAR OLDUGUNU dogrulardi.
        mockMvc.perform(get("/api/leave-requests").param("sort", "employee.salary,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("salary"))));
    }

    @Test
    @WithMockUser(roles = "HR_SPECIALIST")
    @DisplayName("Still allows sorting leave requests by their own dates")
    void allowsSortingByOwnFields() throws Exception {
        when(leaveRequestService.list(any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/leave-requests").param("sort", "startDate,asc"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DisplayName("Ignores any sort the caller asks for on the audit trail")
    void ignoresAuditSorting() throws Exception {
        // Yorum eskiden "siralama parametreye baglanmaz" diyordu ama Pageable
        // onu da tasiyordu: yorum bir iddiaydi, kod tutmuyordu.
        when(auditService.search(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/audit").param("sort", "detail,desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captured = ArgumentCaptor.forClass(Pageable.class);
        verify(auditService).search(any(), any(), any(), any(), captured.capture());
        assertThat(captured.getValue().getSort().isSorted()).isFalse();
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("The audit trail stays closed to everyone but the system admin")
    void auditStaysClosed() throws Exception {
        mockMvc.perform(get("/api/audit"))
                .andExpect(status().isForbidden());

        verify(auditService, never()).search(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DisplayName("Leave requests stay closed to the system admin")
    void leaveStaysClosedToSystemAdmin() throws Exception {
        // Izin IS verisidir, kimlik verisi degil: erisimi yoneten kisinin
        // calisanin izin gecmisine ihtiyaci yoktur.
        mockMvc.perform(get("/api/leave-requests"))
                .andExpect(status().isForbidden());

        verify(leaveRequestService, never()).list(any(), any(), any(), any(), any(), any(), any());
    }
}
