package com.proje.employee.controller;

import com.proje.employee.config.JwtAuthenticationFilter;
import com.proje.employee.config.JwtService;
import com.proje.employee.config.SecurityConfig;
import com.proje.employee.config.SecurityProblemWriter;
import com.proje.employee.dto.DepartmentResponse;
import com.proje.employee.service.DepartmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DepartmentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityProblemWriter.class})
class DepartmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DepartmentService departmentService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("Returns the departments an authenticated user can choose from")
    void returnsDepartmentsForAuthenticatedUser() throws Exception {
        when(departmentService.getAll(false)).thenReturn(List.of(
                new DepartmentResponse(2L, "Finance", true, 3),
                new DepartmentResponse(1L, "Software Development", true, 12)));

        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Finance"))
                .andExpect(jsonPath("$[1].name").value("Software Development"));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("Rejects an anonymous request with 401")
    void rejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("A plain user may not open a department")
    void plainUserMayNotCreate() throws Exception {
        // Bu kural olmasaydi uc "anyRequest().authenticated()" agina duser ve
        // GIRIS YAPAN HERKES departman acabilirdi.
        mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anything\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DisplayName("Even a system administrator may not open a department")
    void systemAdminMayNotCreate() throws Exception {
        // Departman IS verisidir, kimlik verisi degil: Ik uzmanina aittir.
        mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anything\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("A plain user may not close a department")
    void plainUserMayNotChangeStatus() throws Exception {
        mockMvc.perform(put("/api/departments/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }
}
