package com.proje.employee.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
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

    @ExceptionHandler({EmployeeNotFoundException.class, DepartmentNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ProblemDetail handleEmailExists(EmailAlreadyExistsException ex) {
        return problem(HttpStatus.CONFLICT, "Email already registered", ex.getMessage());
    }

    // Hangi kismin yanlis oldugu SOYLENMEZ: "bu email kayitli degil" demek,
    // saldirgana gecerli hesaplari kesfetme imkani verir.
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication failed", ex);
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
    @ExceptionHandler({PropertyReferenceException.class, InvalidDataAccessApiUsageException.class})
    public ProblemDetail handleInvalidQueryParameter(Exception ex) {
        log.warn("Rejected an invalid query parameter: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Invalid request parameter",
                "One of the paging or sorting parameters is not valid");
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
