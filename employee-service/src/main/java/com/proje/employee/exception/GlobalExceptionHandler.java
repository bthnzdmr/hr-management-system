package com.proje.employee.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * ResponseEntityExceptionHandler'dan turetilmesi zorunludur.
 *
 * Turetilmeseydi asagidaki @ExceptionHandler(Exception.class) Spring'in kendi
 * cozucusunden ONCE calisir ve cercevenin urettigi ISTEMCI hatalarini da yutardi:
 * bozuk JSON, yanlis tipte yol degiskeni, olmayan yol ve yanlis HTTP metodu
 * 400/404/405 yerine 500 donerdi. Kullanici hatasina 5xx donmek izlemeyi
 * anlamsizlastirir.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({EmployeeNotFoundException.class, DepartmentNotFoundException.class,
            UserNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage());
    }

    /**
     * Kayit, istemci onu okuduktan sonra degismis.
     *
     * IKI kaynak tek yerde karsilaniyor:
     *  - `StaleRecordException`: istemcinin gonderdigi surum eski (bayat form).
     *  - `OptimisticLockingFailureException`: Hibernate'in UPDATE ... WHERE
     *    version = ? cumlesi sifir satir etkilemis (es zamanli iki yazma).
     *
     * Ikisi ayni olguyu farkli olceklerde yakalar ve kullaniciya soylenecek
     * sey aynidir: yeniden yukle, tekrar dene.
     *
     * 409 doner: istegin kendisi gecerli, sistemin durumu degismis.
     */
    @ExceptionHandler({StaleRecordException.class, OptimisticLockingFailureException.class})
    public ProblemDetail handleStaleRecord(Exception ex) {
        // Hibernate'in mesaji entity sinifi ve id icerir -- ic detaydir ve
        // disari verilmez.
        log.warn("Concurrent modification rejected: {}", ex.getMessage());

        String message = ex instanceof StaleRecordException
                ? ex.getMessage()
                : "This record was changed by someone else. Reload and try again.";

        return problem(HttpStatus.CONFLICT, "Record was modified", message);
    }

    // Istek gecerli ama sistemin durumu izin vermiyor: son yoneticiyi dusurmek,
    // kendi hesabini kapatmak. Mesaj is kuralidir, ic detay degil.
    @ExceptionHandler(UserRuleViolationException.class)
    public ProblemDetail handleUserRule(UserRuleViolationException ex) {
        return problem(HttpStatus.CONFLICT, "Operation not allowed", ex.getMessage());
    }

    // 401 DEGIL 400: 401 arayuzdeki interceptor'a "oturum bitti" der ve kullanici
    // parolasini yanlis yazdi diye sistemden atilirdi.
    @ExceptionHandler(InvalidPasswordException.class)
    public ProblemDetail handleInvalidPassword(InvalidPasswordException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid password", ex.getMessage());
    }

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    public ProblemDetail handleInvalidPasswordResetToken(InvalidPasswordResetTokenException ex) {
        // 400, 401 degil: arayuzdeki interceptor 401'i "oturum bitti" sayip
        // kullaniciyi disari atardi -- oysa burada henuz bir oturum yok.
        return problem(HttpStatus.BAD_REQUEST, "Invalid reset link", ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ProblemDetail handleEmailExists(EmailAlreadyExistsException ex) {
        return problem(HttpStatus.CONFLICT, "Email already registered", ex.getMessage());
    }

    // Hangi kismin yanlis oldugu SOYLENMEZ: "bu email kayitli degil" demek,
    // saldirgana gecerli hesaplari kesfetme imkani verir.
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        // Yigin izi YAZILMAZ: yanlis parola beklenen bir istemci durumudur,
        // sunucu arizasi degil. Izi basmak her denemede yuzlerce satir uretir
        // ve parola tarayan biri log hacmini kendi silahina cevirir.
        log.warn("Authentication failed: {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "Authentication failed", "Invalid credentials");
    }

    // Sebep istemciye SOYLENMEZ: "bu jeton iptal edilmisti" demek, saldirgana
    // elindekinin bir zamanlar gecerli oldugunu dogrulardi. Ayrinti loga gider.
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        log.warn("Refresh rejected: {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "Authentication failed", "Invalid refresh token");
    }

    @ExceptionHandler({ManagerCycleException.class, InactiveManagerException.class})
    public ProblemDetail handleInvalidManager(RuntimeException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid manager assignment", ex.getMessage());
    }

    // Ayri bir baslik: "Invalid manager assignment" altina konsaydi istemci
    // yanlis alani duzeltmeye calisirdi.
    @ExceptionHandler(MissingTerminationReasonException.class)
    public ProblemDetail handleMissingTerminationReason(MissingTerminationReasonException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Termination reason required", ex.getMessage());
    }

    // Ust siniftaki metot override edilir; ayri bir @ExceptionHandler yazmak
    // ayni istisna icin iki eslesme uretir ve uygulama acilista hata verir.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {

        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldErrorDetail(e.getField(), e.getDefaultMessage()))
                .toList();

        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST,
                "Validation failed", "Request contains invalid fields");
        detail.setProperty("errors", errors);

        return handleExceptionInternal(ex, detail, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Gecersiz sayfalama/siralama parametresi.
     *
     * ResponseEntityExceptionHandler bu ikisini kapsamaz; kapsamasaydi
     * "?sort=olmayanAlan" catch-all'a dusup 500 donerdi -- olculdu. Istemcinin
     * yazdigi bir alan adi sunucu hatasi degildir.
     */
    /**
     * Siralamasina izin verilmeyen alan.
     *
     * Reddedilen alanin adi YALNIZCA loga yazilir; cevaba konsaydi
     * "salary siralanamaz" mesaji boyle bir alanin varligini dogrulardi.
     */
    /**
     * Hiz siniri asildi.
     *
     * 429 doner, 401 DEGIL: arayuzdeki interceptor 401'i "oturum bitti" sayip
     * kullaniciyi sistemden atardi -- oysa burada henuz bir oturum bile yok.
     * 429 ayrica standart olarak "sonra tekrar dene" anlamini tasir.
     */
    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ProblemDetail handleTooManyLoginAttempts(TooManyLoginAttemptsException ex) {
        // Sayac zaten LoginAttemptService icinde loglandi; burada tekrarlanmaz.
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts", ex.getMessage());
    }

    @ExceptionHandler(InactiveDepartmentException.class)
    public ProblemDetail handleInactiveDepartment(InactiveDepartmentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid department", ex.getMessage());
    }

    @ExceptionHandler(DepartmentRuleViolationException.class)
    public ProblemDetail handleDepartmentRule(DepartmentRuleViolationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Department rule violated", ex.getMessage());
    }

    @ExceptionHandler(DepartmentNameAlreadyExistsException.class)
    public ProblemDetail handleDuplicateDepartmentName(DepartmentNameAlreadyExistsException ex) {
        return problem(HttpStatus.CONFLICT, "Department already exists", ex.getMessage());
    }

    /**
     * Jetonun arkasindaki hesap yok.
     *
     * Mesaj kasitli olarak belirsiz: hesabin silinmis mi yoksa hic var olmamis
     * mi oldugunu soylemek, gecerli e-posta cikarmaya yarardi.
     */
    @ExceptionHandler(StaleCredentialsException.class)
    public ProblemDetail handleStaleCredentials(StaleCredentialsException ex) {
        log.warn("Token presented for an account that no longer exists: {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "Authentication failed", "Invalid credentials");
    }

    @ExceptionHandler(InvalidSortPropertyException.class)
    public ProblemDetail handleInvalidSortProperty(InvalidSortPropertyException ex) {
        log.warn("Rejected sorting on a field that is not allowed: {}", ex.getProperty());
        return problem(HttpStatus.BAD_REQUEST, "Invalid sort field", ex.getMessage());
    }

    @ExceptionHandler({PropertyReferenceException.class, InvalidDataAccessApiUsageException.class})
    public ProblemDetail handleInvalidQueryParameter(Exception ex) {
        log.warn("Rejected an invalid query parameter: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Invalid request parameter",
                "One of the paging or sorting parameters is not valid");
    }

    // Cakisan izin: kural veritabaninda yasiyor, burada yalnizca kullanicinin
    // anlayacagi hale ceviriliyor.
    @ExceptionHandler(OverlappingLeaveException.class)
    public ProblemDetail handleOverlappingLeave(OverlappingLeaveException ex) {
        return problem(HttpStatus.CONFLICT, "Overlapping leave", ex.getMessage());
    }

    @ExceptionHandler(LeaveRuleViolationException.class)
    public ProblemDetail handleLeaveRule(LeaveRuleViolationException ex) {
        return problem(HttpStatus.CONFLICT, "Operation not allowed", ex.getMessage());
    }

    @ExceptionHandler(LeaveRequestNotFoundException.class)
    public ProblemDetail handleLeaveNotFound(LeaveRequestNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Leave request not found", ex.getMessage());
    }

    // Kod tarafindaki kontrol ile kayit arasindaki yaris durumunda veritabani
    // kisiti devreye girer ve bu istisna firlar.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Database constraint violated", ex);
        return problem(HttpStatus.CONFLICT, "Constraint violation",
                "The request conflicts with existing data");
    }

    // Beklenmeyen her sey. Detay loga yazilir, istemciye genel mesaj doner:
    // ic hata mesajlari ve yigin izleri disariya sizmamalidir.
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        return problemDetail;
    }
}
