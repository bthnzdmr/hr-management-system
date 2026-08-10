package com.proje.employee.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
        log.warn("Kimlik dogrulama basarisiz", ex);
        return problem(HttpStatus.UNAUTHORIZED, "Authentication failed", "Invalid credentials");
    }

    @ExceptionHandler(ManagerCycleException.class)
    public ProblemDetail handleManagerCycle(ManagerCycleException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid manager assignment", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldErrorDetail(e.getField(), e.getDefaultMessage()))
                .toList();

        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST,
                "Validation failed", "Request contains invalid fields");
        detail.setProperty("errors", errors);
        return detail;
    }

    // Kod tarafindaki kontrol ile kayit arasindaki yaris durumunda veritabani
    // kisiti devreye girer ve bu istisna firlar.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Veritabani kisiti ihlal edildi", ex);
        return problem(HttpStatus.CONFLICT, "Constraint violation",
                "The request conflicts with existing data");
    }

    // Beklenmeyen her sey. Detay loga yazilir, istemciye genel mesaj doner:
    // ic hata mesajlari ve yigin izleri disariya sizmamalidir.
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Beklenmeyen hata", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        return problemDetail;
    }
}
