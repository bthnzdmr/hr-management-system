package com.proje.employee.exception;

import com.proje.employee.config.JwtAuthenticationFilter;
import com.proje.employee.config.JwtService;
import com.proje.employee.config.SecurityConfig;
import com.proje.employee.config.SecurityProblemWriter;
import com.proje.employee.controller.EmployeeController;
import com.proje.employee.service.AccessScopeResolver;
import com.proje.employee.service.EmployeeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Istemci hatalari 4xx donmelidir.
 *
 * Bu testler gercek bir hatadan dogdu: catch-all handler Spring'in cozucusunden
 * once calisiyor ve asagidaki dort durumu da 500'e ceviriyordu.
 */
@WebMvcTest(EmployeeController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityProblemWriter.class,
        GlobalExceptionHandler.class})
class ClientErrorStatusTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmployeeService employeeService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AccessScopeResolver accessScopeResolver;

    @Test
    @WithMockUser(roles = "HR_SPECIALIST")
    @DisplayName("A malformed request body is a client error, not a server error")
    void malformedBodyIsClientError() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "HR_SPECIALIST")
    @DisplayName("A path variable of the wrong type is a client error")
    void wrongTypePathVariableIsClientError() throws Exception {
        mockMvc.perform(get("/api/employees/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "HR_SPECIALIST")
    @DisplayName("An unsupported HTTP method returns 405")
    void unsupportedMethodReturns405() throws Exception {
        mockMvc.perform(patch("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @WithMockUser(roles = "HR_SPECIALIST")
    @DisplayName("An unknown sort field is a client error, not a server error")
    void unknownSortFieldIsClientError() throws Exception {
        // Olculdu: bu istek 500 donuyordu. Istemcinin yazdigi bir alan adi
        // sunucu hatasi degildir; ResponseEntityExceptionHandler bu istisnayi
        // kapsamadigi icin acikca ele alinmasi gerekiyor.
        when(employeeService.getAll(ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenThrow(new InvalidDataAccessApiUsageException("Unknown sort property"));

        mockMvc.perform(get("/api/employees?sort=noSuchField,asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "HR_SPECIALIST")
    @DisplayName("A body that violates validation still reports the offending fields")
    void validationStillReportsFields() throws Exception {
        // Ust sinifi turetirken bu davranisi kaybetmedigimizi dogrular.
        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"\",\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.errors").isArray());
    }
}
