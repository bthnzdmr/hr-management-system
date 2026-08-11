package com.proje.employee.config;

import com.proje.employee.controller.EmployeeController;
import com.proje.employee.service.AccessScopeResolver;
import com.proje.employee.service.EmployeeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeeController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityProblemWriter.class})
class CorsConfigurationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String FOREIGN_ORIGIN = "http://evil.example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmployeeService employeeService;

    @MockBean
    private JwtService jwtService;

    // Controller kapsami bu bilesenden aliyor.
    @MockBean
    private AccessScopeResolver accessScopeResolver;

    @Test
    @DisplayName("Answers the preflight request of the allowed origin without authentication")
    void allowsPreflightFromConfiguredOrigin() throws Exception {
        // Preflight kimlik bilgisi tasimaz; kimlik dogrulamasina takilsaydi
        // tarayici asil istegi hic gondermezdi.
        mockMvc.perform(options("/api/employees")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    @Test
    @DisplayName("Rejects the preflight request of an origin that is not configured")
    void rejectsPreflightFromUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/employees")
                        .header("Origin", FOREIGN_ORIGIN)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("Does not allow a method that is outside the configured list")
    void rejectsMethodOutsideConfiguredList() throws Exception {
        mockMvc.perform(options("/api/employees")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isForbidden());
    }
}
