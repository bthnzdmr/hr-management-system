package com.proje.employee.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proje.employee.config.JwtAuthenticationFilter;
import com.proje.employee.config.JwtService;
import com.proje.employee.config.SecurityConfig;
import com.proje.employee.config.SecurityProblemWriter;
import com.proje.employee.dto.EmployeeCreateRequest;
import com.proje.employee.dto.EmployeeResponse;
import com.proje.employee.dto.SalaryResponse;
import com.proje.employee.exception.EmailAlreadyExistsException;
import com.proje.employee.exception.EmployeeNotFoundException;
import com.proje.employee.service.AccessScopeResolver;
import com.proje.employee.service.EmployeeService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @MockitoBean
    private EmployeeService employeeService;

    // Kimlik @WithMockUser ile atandigi icin token cozumlemeye gerek yok.
    @MockitoBean
    private JwtService jwtService;

    // Controller kapsami bu bilesenden aliyor; @WebMvcTest yalnizca web
    // katmanini ayaga kaldirdigi icin sahtesi verilmeli.
    @MockitoBean
    private AccessScopeResolver accessScopeResolver;

    private EmployeeCreateRequest validRequest() {
        return new EmployeeCreateRequest(
                "Ada", "Lovelace", "ada@example.com", null,
                1L, null, "Software Engineer",
                LocalDate.of(2024, 1, 15));
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DisplayName("Refuses to sort by a field the response does not carry")
    void refusesSortingBySalary() throws Exception {
        // Olculdu: maas cevapta donmese de "?sort=salary,desc" ucret
        // siralamasini oldugu gibi veriyordu -- deger vermeden buyukluk
        // iliskisi vermek de bir sizintidir. Istek SERVISE HIC ULASMAMALI.
        mockMvc.perform(get("/api/employees").param("sort", "salary,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid sort field"));

        verify(employeeService, never()).getAll(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DisplayName("Does not name the field it rejected")
    void doesNotEchoTheRejectedField() throws Exception {
        // "salary siralanamaz" demek boyle bir alanin VAR OLDUGUNU dogrulardi.
        mockMvc.perform(get("/api/employees").param("sort", "salary,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("salary"))));
    }

    @Test
    @WithMockUser(roles = "HR_SPECIALIST")
    @DisplayName("Still allows sorting by the fields the list actually shows")
    void allowsSortingByVisibleFields() throws Exception {
        when(employeeService.getAll(any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        for (String field : new String[] {"lastName", "firstName", "email", "jobTitle", "hireDate", "id"}) {
            mockMvc.perform(get("/api/employees").param("sort", field + ",asc"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser(roles = "HR_SPECIALIST")
    @DisplayName("A valid create returns 201 with a Location header")
    void createReturns201WithLocation() throws Exception {
        EmployeeResponse response = new EmployeeResponse(
                42L, 0L, "Ada", "Lovelace", "ada@example.com", null,
                1L, "Software Development", null, null, "Software Engineer",
                LocalDate.of(2024, 1, 15), true, null, null);

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
    @WithMockUser(roles = "HR_SPECIALIST")
    @DisplayName("An invalid request returns 400 with per-field errors")
    void invalidRequestReturns400WithFieldErrors() throws Exception {
        EmployeeCreateRequest invalid = new EmployeeCreateRequest(
                "   ", "", "not-an-email", null,
                null, null, "Engineer",
                LocalDate.of(2024, 1, 15));

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(org.hamcrest.Matchers.hasItems(
                                "firstName", "lastName", "email", "departmentId")));
    }

    @Test
    @WithMockUser(roles = "HR_SPECIALIST")
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
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("A missing record returns 404")
    void missingRecordReturns404() throws Exception {
        when(employeeService.getById(eq(99L), any())).thenThrow(new EmployeeNotFoundException(99L));

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
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("USER role can read")
    void userRoleCanRead() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
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
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("USER role cannot change an employee status")
    void userRoleCannotChangeStatus() throws Exception {
        mockMvc.perform(put("/api/employees/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());

        verify(employeeService, never()).changeStatus(any(), anyBoolean(), any());
    }

    @Test
    @WithMockUser(roles = "HR_SPECIALIST")
    @DisplayName("Rejects a status change with no active flag")
    void rejectsStatusChangeWithoutFlag() throws Exception {
        mockMvc.perform(put("/api/employees/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(employeeService, never()).changeStatus(any(), anyBoolean(), any());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("USER role may read who reports to an employee")
    void userRoleMayReadDirectReports() throws Exception {
        // Organizasyon yapisini gormek maasi gormekten farklidir: bu uc
        // bilerek ADMIN'e kisitli DEGIL.
        when(employeeService.getDirectReports(eq(1L), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/employees/1/direct-reports"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DisplayName("system admin cannot read a salary even though it may read employees")
    void systemAdminCannotReadSalary() throws Exception {
        // Erisimi yoneten kisinin ucret bilgisine ihtiyaci yoktur. Kural SIRASI
        // da korunur: maas kurali genel okuma kuralindan once gelmezse acilir.
        mockMvc.perform(get("/api/employees/1/salary"))
                .andExpect(status().isForbidden());

        verify(employeeService, never()).getSalary(any(), any());
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DisplayName("system admin cannot probe a salary with HEAD either")
    void systemAdminCannotProbeSalaryWithHead() throws Exception {
        // Olculdu: kural HttpMethod.GET ile yazildiginda HEAD kapsam disinda
        // kaliyor ve 200 donuyordu. Spring Security HEAD'i GET saymaz, ama
        // Spring MVC HEAD istegini @GetMapping metoduna yonlendirir; cevap
        // govdesiz gitse de Content-Length maasin basamak sayisini sizdirir.
        mockMvc.perform(head("/api/employees/1/salary"))
                .andExpect(status().isForbidden());

        verify(employeeService, never()).getSalary(any(), any());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("an employee may ask for a salary, and the service decides whose")
    void employeeReachesTheSalaryEndpoint() throws Exception {
        // Uc seviyesi artik calisani iceri aliyor; HANGI kaydi gorebilecegine
        // servis karar veriyor. Kendi kaydi disinda 404 doner.
        when(employeeService.getSalary(eq(1L), any()))
                .thenReturn(new SalaryResponse(1L, new BigDecimal("95000.00")));

        mockMvc.perform(get("/api/employees/1/salary"))
                .andExpect(status().isOk());

        verify(employeeService).getSalary(eq(1L), any());
    }

    @Test
    @WithMockUser(roles = "PAYROLL_SPECIALIST")
    @DisplayName("payroll specialist reads the salary through its own endpoint")
    void payrollSpecialistReadsSalary() throws Exception {
        when(employeeService.getSalary(eq(1L), any()))
                .thenReturn(new SalaryResponse(1L, new BigDecimal("95000.00")));

        mockMvc.perform(get("/api/employees/1/salary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(1))
                .andExpect(jsonPath("$.salary").value(95000.00));
    }

    @Test
    @WithMockUser(roles = "PAYROLL_SPECIALIST")
    @DisplayName("payroll specialist can browse employees to find whose pay to set")
    void payrollSpecialistReadsTheDirectory() throws Exception {
        // AccessScope bu role ALL kapsam veriyordu ama uc kurali onu disarida
        // birakmisti: ucretini girecegi kisiyi bulamayan bir rol islevsizdir.
        when(employeeService.getAll(any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PAYROLL_SPECIALIST")
    @DisplayName("Rejects a salary update with a missing amount")
    void rejectsSalaryUpdateWithoutAmount() throws Exception {
        mockMvc.perform(put("/api/employees/1/salary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(employeeService, never()).updateSalary(any(), any());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("USER role cannot update a salary")
    void userRoleCannotUpdateSalary() throws Exception {
        mockMvc.perform(put("/api/employees/1/salary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salary\":1.00}"))
                .andExpect(status().isForbidden());

        verify(employeeService, never()).updateSalary(any(), any());
    }

    @Test
    @WithMockUser(roles = "HR_SPECIALIST")
    @DisplayName("HR cannot touch a salary, because it creates the employee record")
    void hrCannotTouchSalary() throws Exception {
        // Ayni rolun hem kayit acip hem ucret atamasi, sahte personel
        // olusturmanin klasik yolu (SAP gorevler ayriligi katalogu, HCM-01).
        mockMvc.perform(get("/api/employees/1/salary"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/employees/1/salary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salary\": 1}"))
                .andExpect(status().isForbidden());

        verify(employeeService, never()).getSalary(any(), any());
        verify(employeeService, never()).updateSalary(any(), any());
    }
}
