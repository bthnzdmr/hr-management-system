package com.proje.employee.controller;

import com.proje.employee.config.JwtAuthenticationFilter;
import com.proje.employee.config.JwtService;
import com.proje.employee.config.SecurityConfig;
import com.proje.employee.config.SecurityProblemWriter;
import com.proje.employee.exception.GlobalExceptionHandler;
import com.proje.employee.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hesap yonetimi uclarinin yetki kurallari.
 *
 * Kural SIRASI burada tasiyicidir: "/api/users/me/password" kurali genel
 * "/api/users/**" kuralindan once yazilmazsa, Spring Security ilk eslesen kurali
 * uyguladigi icin yalnizca yoneticiler parolasini degistirebilir.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityProblemWriter.class,
        GlobalExceptionHandler.class})
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    private static final String PASSWORD_BODY = """
            {"currentPassword":"current-password","newPassword":"a-brand-new-password"}
            """;

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("A plain user may change their own password")
    void plainUserMayChangeOwnPassword() throws Exception {
        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PASSWORD_BODY))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("A plain user may not list the accounts")
    void plainUserMayNotListAccounts() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("A plain user may not create an account")
    void plainUserMayNotCreateAccounts() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new@example.com","password":"a-long-password-x",
                                 "roles":["EMPLOYEE"]}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("A plain user may not change anyone's roles")
    void plainUserMayNotChangeRoles() throws Exception {
        mockMvc.perform(put("/api/users/1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["SYSTEM_ADMIN"]}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR_SPECIALIST")
    @DisplayName("An HR specialist may not manage accounts")
    void hrSpecialistMayNotManageAccounts() throws Exception {
        // Gorevler ayrildi: Ik verisini yoneten kisi erisim de yonetemez.
        // Ayni kisi ikisini birden yapacaksa IKI role birden sahip olur.
        mockMvc.perform(get("/api/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("A plain user may not read the accounts with HEAD either")
    void plainUserMayNotProbeWithHead() throws Exception {
        // Daha once olculmus bir kusur: metot belirtilen bir kural HEAD'i
        // kapsamiyor ve Spring MVC HEAD'i @GetMapping metoduna yonlendiriyor.
        mockMvc.perform(head("/api/users")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("An anonymous visitor may not change any password")
    void anonymousMayNotChangePassword() throws Exception {
        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PASSWORD_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    @DisplayName("A system administrator may list the accounts")
    void systemAdministratorMayListAccounts() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isOk());
    }
}
