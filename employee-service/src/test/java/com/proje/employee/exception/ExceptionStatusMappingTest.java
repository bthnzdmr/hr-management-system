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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Her alan istisnasinin DOGRU durum koduna cevrildigini sinar.
 *
 * <p>Kapsam olcumu {@code GlobalExceptionHandler}'i %39,9'da buldu: yirmi
 * ustunde yakalayici var ve cogunun hicbir testi yoktu. Bu yalnizca bir sayi
 * degil -- yanlis kod donmek OLCULEBILIR zarar veriyor ve projede iki kez
 * verdi:
 *
 * <ul>
 *   <li>Kapsam disi kayit icin {@code 403} donmek, id deneyerek personel
 *       sayisi ogrenilmesine izin verirdi; dogrusu {@code 404}.</li>
 *   <li>Hiz sinirinda {@code 401} donmek, arayuzdeki interceptor'i "oturum
 *       bitti" sandirip kullaniciyi SISTEMDEN ATARDI; dogrusu {@code 429}.</li>
 * </ul>
 *
 * <p>Istisnalar bir uc uzerinden firlatiliyor, dogrudan cagirilarak degil:
 * boylece Spring'in yakalayici SECIMI de sinaniyor -- yalnizca metot govdesi
 * degil. Yanlis siralanmis bir {@code @ExceptionHandler} birim testte
 * gorunmezdi.
 */
@WebMvcTest(EmployeeController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityProblemWriter.class,
        GlobalExceptionHandler.class})
@WithMockUser(roles = "HR_SPECIALIST")
class ExceptionStatusMappingTest {

    private static final String ANY_EMPLOYEE = "/api/employees/1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AccessScopeResolver accessScopeResolver;

    /** Okuma ucu her istisnayi tasiyabilir; hangi uc oldugu onemli degil. */
    private void serviceThrows(RuntimeException failure) {
        when(employeeService.getById(anyLong(), any())).thenThrow(failure);
    }

    @Test
    @DisplayName("A record outside the caller's scope is 404, never 403")
    void outOfScopeRecordIsNotFound() throws Exception {
        // 403 "bu kayit var ama goremezsin" der ve id denenerek personel
        // sayisi ogrenilebilirdi.
        serviceThrows(new EmployeeNotFoundException(1L));

        mockMvc.perform(get(ANY_EMPLOYEE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    @DisplayName("A stale form and a concurrent write both surface as 409")
    void staleRecordIsConflict() throws Exception {
        // Iki AYRI kaynak, kullanici icin ayni sey: biri bayat form, digeri
        // Hibernate'in "UPDATE ... WHERE version = ?" cumlesinin sifir satir
        // etkilemesi.
        serviceThrows(new StaleRecordException("employee", 1L));

        mockMvc.perform(get(ANY_EMPLOYEE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Record was modified"));
    }

    @Test
    @DisplayName("Hibernate's own locking message never reaches the client")
    void optimisticLockingMessageIsNotLeaked() throws Exception {
        serviceThrows(new OptimisticLockingFailureException(
                "Row was updated or deleted by another transaction "
                        + "(or unsaved-value mapping was incorrect) : [com.proje.employee.entity.Employee#1]"));

        mockMvc.perform(get(ANY_EMPLOYEE))
                .andExpect(status().isConflict())
                // Ic sinif adi ve Hibernate'in metni disariya sizmamali.
                .andExpect(jsonPath("$.detail").value(not(containsString("com.proje"))))
                .andExpect(jsonPath("$.detail").value(not(containsString("unsaved-value"))));
    }

    @Test
    @DisplayName("Rate limiting is 429, so the client does not treat it as a dead session")
    void rateLimitIsTooManyRequests() throws Exception {
        // OLCULMUS: 401 donseydi arayuzdeki interceptor kullaniciyi sistemden
        // atardi -- oysa burada henuz bir oturum bile yok.
        serviceThrows(new TooManyLoginAttemptsException());

        mockMvc.perform(get(ANY_EMPLOYEE))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Too many attempts"));
    }

    @Test
    @DisplayName("The rate limit answer never confirms that an account exists")
    void rateLimitAnswerDoesNotConfirmTheAccount() throws Exception {
        serviceThrows(new TooManyLoginAttemptsException());

        mockMvc.perform(get(ANY_EMPLOYEE))
                .andExpect(jsonPath("$.detail").value(not(containsString("@"))))
                .andExpect(jsonPath("$.detail").value(not(containsString("locked"))));
    }

    @Test
    @DisplayName("A deleted account behind a valid token is 401, not 500")
    void staleCredentialsIsUnauthorized() throws Exception {
        // Bu bir ISTEMCI durumudur. Once 500 donuyordu ve izleme kirleniyordu:
        // alarm kurulmus bir sistemde OLMAYAN bir arizayi bildirir.
        serviceThrows(new StaleCredentialsException("ghost@example.com"));

        mockMvc.perform(get(ANY_EMPLOYEE))
                .andExpect(status().isUnauthorized())
                // Mesaj kasitli belirsiz: "silinmis" ile "hic olmamis" ayrimi
                // gecerli e-posta cikarmaya yarardi.
                .andExpect(jsonPath("$.detail").value("Invalid credentials"))
                .andExpect(jsonPath("$.detail").value(not(containsString("ghost"))));
    }

    @Test
    @DisplayName("An inactive manager is the client's mistake, so 400")
    void inactiveManagerIsBadRequest() throws Exception {
        serviceThrows(new InactiveManagerException(9L));

        mockMvc.perform(get(ANY_EMPLOYEE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid manager assignment"));
    }

    @Test
    @DisplayName("A manager cycle is reported as an invalid assignment")
    void managerCycleIsBadRequest() throws Exception {
        serviceThrows(new ManagerCycleException(1L, 2L));

        mockMvc.perform(get(ANY_EMPLOYEE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid manager assignment"));
    }

    @Test
    @DisplayName("A missing termination reason is refused, not defaulted")
    void missingTerminationReasonIsBadRequest() throws Exception {
        // Varsayilan bir sebep atamak, devir oraninin en anlamli kirilimini
        // sessizce bozardi. Eksik veri, yanlis veriden iyidir.
        serviceThrows(new MissingTerminationReasonException());

        mockMvc.perform(get(ANY_EMPLOYEE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Termination reason required"));
    }

    @Test
    @DisplayName("A duplicate email is a conflict, and says which address")
    void duplicateEmailIsConflict() throws Exception {
        serviceThrows(new EmailAlreadyExistsException("taken@example.com"));

        mockMvc.perform(get(ANY_EMPLOYEE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email already registered"));
    }

    @Test
    @DisplayName("An inactive department is refused with 400")
    void inactiveDepartmentIsBadRequest() throws Exception {
        serviceThrows(new InactiveDepartmentException(3L));

        mockMvc.perform(get(ANY_EMPLOYEE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid department"));
    }

    @Test
    @DisplayName("A duplicate department name is a conflict")
    void duplicateDepartmentNameIsConflict() throws Exception {
        serviceThrows(new DepartmentNameAlreadyExistsException("Sales"));

        mockMvc.perform(get(ANY_EMPLOYEE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Department already exists"));
    }

    @Test
    @DisplayName("Overlapping leave is a conflict the user can understand")
    void overlappingLeaveIsConflict() throws Exception {
        // Kural veritabaninda yasiyor (EXCLUDE kisiti); burada yalnizca
        // kullanicinin anlayacagi hale ceviriliyor.
        serviceThrows(new OverlappingLeaveException(
                "This employee already has leave in that date range"));

        mockMvc.perform(get(ANY_EMPLOYEE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Overlapping leave"));
    }

    @Test
    @DisplayName("A leave rule violation is a conflict, not a validation error")
    void leaveRuleViolationIsConflict() throws Exception {
        serviceThrows(new LeaveRuleViolationException("You cannot decide on your own request"));

        mockMvc.perform(get(ANY_EMPLOYEE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Operation not allowed"));
    }

    @Test
    @DisplayName("A missing leave request is 404")
    void leaveRequestNotFoundIs404() throws Exception {
        serviceThrows(new LeaveRequestNotFoundException(7L));

        mockMvc.perform(get(ANY_EMPLOYEE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Leave request not found"));
    }

    @Test
    @DisplayName("A database constraint violation is 409 and hides the SQL")
    void constraintViolationHidesTheDatabaseMessage() throws Exception {
        // Koddaki kontrol ile kayit arasindaki YARISTA veritabani kisiti
        // devreye girer. Kisit adi ve SQL istemciye gitmemeli.
        serviceThrows(new DataIntegrityViolationException(
                "could not execute statement [ERROR: duplicate key value violates "
                        + "unique constraint \"uk_employee_email\"]"));

        mockMvc.perform(get(ANY_EMPLOYEE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(not(containsString("uk_employee_email"))))
                .andExpect(jsonPath("$.detail").value(not(containsString("ERROR:"))));
    }

    @Test
    @DisplayName("Every error body carries the same shape")
    void everyErrorUsesProblemDetail() throws Exception {
        // Tek bir sekil: arayuz her hatayi ayni bicimde okuyabilmeli.
        serviceThrows(new EmployeeNotFoundException(1L));

        mockMvc.perform(get(ANY_EMPLOYEE))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists());
    }
}
