package com.proje.employee.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeCreateRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private EmployeeCreateRequest gecerliIstek() {
        return new EmployeeCreateRequest(
                "Ada", "Lovelace", "ada@example.com", "+90 555 123 45 67",
                1L, null, "Software Engineer",
                LocalDate.of(2024, 1, 15), new BigDecimal("85000.00"));
    }

    private Set<String> ihlalAlanlari(EmployeeCreateRequest istek) {
        return validator.validate(istek).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("Gecerli istek hicbir ihlal uretmez")
    void gecerliIstekGecer() {
        Set<ConstraintViolation<EmployeeCreateRequest>> ihlaller =
                validator.validate(gecerliIstek());

        assertThat(ihlaller).isEmpty();
    }

    @Test
    @DisplayName("Bosluklardan ibaret isim reddedilir (@NotBlank, @NotEmpty degil)")
    void bosluktanIbaretIsimReddedilir() {
        EmployeeCreateRequest istek = new EmployeeCreateRequest(
                "   ", "Lovelace", "ada@example.com", null,
                1L, null, "Software Engineer",
                LocalDate.of(2024, 1, 15), null);

        assertThat(ihlalAlanlari(istek)).contains("firstName");
    }

    @Test
    @DisplayName("Gecersiz email bicimi reddedilir")
    void gecersizEmailReddedilir() {
        EmployeeCreateRequest istek = new EmployeeCreateRequest(
                "Ada", "Lovelace", "asdf", null,
                1L, null, "Software Engineer",
                LocalDate.of(2024, 1, 15), null);

        assertThat(ihlalAlanlari(istek)).contains("email");
    }

    @Test
    @DisplayName("Departman ve ise giris tarihi zorunludur")
    void zorunluAlanlarEksikOlamaz() {
        EmployeeCreateRequest istek = new EmployeeCreateRequest(
                "Ada", "Lovelace", "ada@example.com", null,
                null, null, "Software Engineer",
                null, null);

        assertThat(ihlalAlanlari(istek)).contains("departmentId", "hireDate");
    }

    @Test
    @DisplayName("Negatif maas reddedilir")
    void negatifMaasReddedilir() {
        EmployeeCreateRequest istek = new EmployeeCreateRequest(
                "Ada", "Lovelace", "ada@example.com", null,
                1L, null, "Software Engineer",
                LocalDate.of(2024, 1, 15), new BigDecimal("-1"));

        assertThat(ihlalAlanlari(istek)).contains("salary");
    }

    @Test
    @DisplayName("Maas NUMERIC(12,2) sinirini asamaz")
    void asiriBuyukMaasReddedilir() {
        EmployeeCreateRequest istek = new EmployeeCreateRequest(
                "Ada", "Lovelace", "ada@example.com", null,
                1L, null, "Software Engineer",
                LocalDate.of(2024, 1, 15), new BigDecimal("99999999999.00"));

        assertThat(ihlalAlanlari(istek)).contains("salary");
    }
}
