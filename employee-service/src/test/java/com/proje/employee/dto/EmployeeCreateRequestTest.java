package com.proje.employee.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeCreateRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private EmployeeCreateRequest validRequest() {
        return new EmployeeCreateRequest(
                "Ada", "Lovelace", "ada@example.com", "+90 555 123 45 67",
                1L, null, "Software Engineer",
                LocalDate.of(2024, 1, 15));
    }

    private Set<String> violatedFields(EmployeeCreateRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("A valid request produces no violations")
    void validRequestPasses() {
        Set<ConstraintViolation<EmployeeCreateRequest>> violations =
                validator.validate(validRequest());

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("A whitespace-only name is rejected (@NotBlank, not @NotEmpty)")
    void rejectsWhitespaceOnlyName() {
        EmployeeCreateRequest request = new EmployeeCreateRequest(
                "   ", "Lovelace", "ada@example.com", null,
                1L, null, "Software Engineer",
                LocalDate.of(2024, 1, 15));

        assertThat(violatedFields(request)).contains("firstName");
    }

    @Test
    @DisplayName("An invalid email format is rejected")
    void rejectsInvalidEmailFormat() {
        EmployeeCreateRequest request = new EmployeeCreateRequest(
                "Ada", "Lovelace", "not-an-email", null,
                1L, null, "Software Engineer",
                LocalDate.of(2024, 1, 15));

        assertThat(violatedFields(request)).contains("email");
    }

    @Test
    @DisplayName("Department and hire date are required")
    void rejectsMissingRequiredFields() {
        EmployeeCreateRequest request = new EmployeeCreateRequest(
                "Ada", "Lovelace", "ada@example.com", null,
                null, null, "Software Engineer",
                null);

        assertThat(violatedFields(request)).contains("departmentId", "hireDate");
    }

}
