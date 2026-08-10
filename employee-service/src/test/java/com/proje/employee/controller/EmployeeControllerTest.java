package com.proje.employee.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import com.proje.employee.config.SecurityConfig;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeeController.class)
@Import(SecurityConfig.class)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmployeeService employeeService;

    private EmployeeCreateRequest gecerliIstek() {
        return new EmployeeCreateRequest(
                "Ada", "Lovelace", "ada@example.com", null,
                1L, null, "Software Engineer",
                LocalDate.of(2024, 1, 15), new BigDecimal("85000.00"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Gecerli kayit 201 ve Location header'i doner")
    void gecerliKayit201Doner() throws Exception {
        EmployeeResponse response = new EmployeeResponse(
                42L, "Ada", "Lovelace", "ada@example.com", null,
                1L, "Software Development", null, "Software Engineer",
                LocalDate.of(2024, 1, 15), true);

        when(employeeService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(gecerliIstek())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/employees/42"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.departmentName").value("Software Development"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Gecersiz istek 400 ve alan bazli hata listesi doner")
    void gecersizIstek400Doner() throws Exception {
        EmployeeCreateRequest bozuk = new EmployeeCreateRequest(
                "   ", "", "asdf", null,
                null, null, "Engineer",
                LocalDate.of(2024, 1, 15), new BigDecimal("-5"));

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bozuk)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(org.hamcrest.Matchers.hasItems(
                                "firstName", "lastName", "email", "departmentId", "salary")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Kayitli email 409 doner")
    void kayitliEmail409Doner() throws Exception {
        when(employeeService.create(any()))
                .thenThrow(new EmailAlreadyExistsException("ada@example.com"));

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(gecerliIstek())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email already registered"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Olmayan kayit 404 doner")
    void olmayanKayit404Doner() throws Exception {
        when(employeeService.getById(99L)).thenThrow(new EmployeeNotFoundException(99L));

        mockMvc.perform(get("/api/employees/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    // ---- Yetkilendirme kurallari ----

    @Test
    @WithAnonymousUser
    @DisplayName("Kimlik dogrulanmadan okuma ucu 401 doner")
    void kimliksizOkuma401Doner() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER rolu okuyabilir")
    void userRoluOkuyabilir() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER rolu YAZAMAZ: POST 403 doner")
    void userRoluYazamaz() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(gecerliIstek())))
                .andExpect(status().isForbidden());

        // Yetki reddi service'e hic ulasmamali.
        org.mockito.Mockito.verify(employeeService, org.mockito.Mockito.never()).create(any());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER rolu SILEMEZ: DELETE 403 doner")
    void userRoluSilemez() throws Exception {
        mockMvc.perform(delete("/api/employees/1"))
                .andExpect(status().isForbidden());

        org.mockito.Mockito.verify(employeeService, org.mockito.Mockito.never()).deactivate(any());
    }
}
