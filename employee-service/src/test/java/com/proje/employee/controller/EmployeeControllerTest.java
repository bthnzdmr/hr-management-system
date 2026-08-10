package com.proje.employee.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proje.employee.config.JwtAuthenticationFilter;
import com.proje.employee.config.JwtService;
import com.proje.employee.config.SecurityConfig;
import com.proje.employee.config.SecurityProblemWriter;
import com.proje.employee.dto.EmployeeCreateRequest;
import com.proje.employee.dto.EmployeeResponse;
import com.proje.employee.exception.EmailAlreadyExistsException;
import com.proje.employee.exception.EmployeeNotFoundException;
import com.proje.employee.service.EmployeeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest yalnizca web katmanini yukler; SecurityConfig'in ihtiyac duydugu
// filtre ve servis acikca saglanmalidir.
@WebMvcTest(EmployeeController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityProblemWriter.class})
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmployeeService employeeService;

    // Kimlik @WithMockUser ile atandigi icin token cozumlemeye gerek yok.
    @MockBean
    private JwtService jwtService;

    private EmployeeCreateRequest validRequest() {
        return new EmployeeCreateRequest(
                "Ada", "Lovelace", "ada@example.com", null,
                1L, null, "Software Engineer",
                LocalDate.of(2024, 1, 15), new BigDecimal("85000.00"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("A valid create returns 201 with a Location header")
    void createReturns201WithLocation() throws Exception {
        EmployeeResponse response = new EmployeeResponse(
                42L, "Ada", "Lovelace", "ada@example.com", null,
                1L, "Software Development", null, "Software Engineer",
                LocalDate.of(2024, 1, 15), true);

        when(employeeService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/employees/42"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.departmentName").value("Software Development"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("An invalid request returns 400 with per-field errors")
    void invalidRequestReturns400WithFieldErrors() throws Exception {
        EmployeeCreateRequest invalid = new EmployeeCreateRequest(
                "   ", "", "not-an-email", null,
                null, null, "Engineer",
                LocalDate.of(2024, 1, 15), new BigDecimal("-5"));

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(org.hamcrest.Matchers.hasItems(
                                "firstName", "lastName", "email", "departmentId", "salary")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("An already registered email returns 409")
    void duplicateEmailReturns409() throws Exception {
        when(employeeService.create(any()))
                .thenThrow(new EmailAlreadyExistsException("ada@example.com"));

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email already registered"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("A missing record returns 404")
    void missingRecordReturns404() throws Exception {
        when(employeeService.getById(99L)).thenThrow(new EmployeeNotFoundException(99L));

        mockMvc.perform(get("/api/employees/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    // ---- Yetkilendirme kurallari ----

    @Test
    @WithAnonymousUser
    @DisplayName("Unauthenticated read returns 401")
    void unauthenticatedReadReturns401() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER role can read")
    void userRoleCanRead() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER role cannot create: POST returns 403")
    void userRoleCannotCreate() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());

        // Yetki reddi service'e hic ulasmamali.
        verify(employeeService, never()).create(any());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER role cannot delete: DELETE returns 403")
    void userRoleCannotDelete() throws Exception {
        mockMvc.perform(delete("/api/employees/1"))
                .andExpect(status().isForbidden());

        verify(employeeService, never()).deactivate(any());
    }
}
