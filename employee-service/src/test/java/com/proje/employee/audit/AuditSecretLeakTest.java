package com.proje.employee.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Denetim izine sir sizmasini YAPISAL olarak engelleyen test.
 *
 * <p>Bu test bir olcumden dogdu: denetim izi ilk eklendiginde
 * {@code UserService.create(UserCreateRequest)} isaretlendi ve aspect'in
 * arguman ozeti record'un uretilmis {@code toString}'ini cagirdi. Sonuc:
 * acilan hesabin parolasi {@code audit_entry.detail} kolonuna DUZ METIN
 * yazildi -- ustelik aspect'in kendi yorumu "buraya hicbir sir yazilmaz"
 * diyordu. Yorum bir iddiaydi, kod onu tutmuyordu.
 *
 * <p>Tek bir DTO'yu duzeltmek yetmez: yarin sir tasiyan baska bir istek
 * isaretlenirse ayni sizinti tekrarlanir. Bu test o gelecegi kapatir --
 * {@code @Auditable} ile isaretli ve argumanlarini kaydeden HER metodun
 * parametrelerini tarar ve sir benzeri bir bilesenin metinsel temsile
 * girmedigini dogrular.
 */
class AuditSecretLeakTest {

    private static final String BASE_PACKAGE = "com.proje.employee";

    /** Sir tasidigi varsayilan bilesen adlari. */
    private static final List<String> SECRET_NAMES =
            List.of("password", "secret", "token", "hash", "credential");

    private List<Method> auditedMethodsThatRecordArguments() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));

        List<Method> methods = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE + ".service")) {
            Class<?> type = classOf(definition.getBeanClassName());
            if (type == null || type.getAnnotation(Service.class) == null) {
                continue;
            }
            for (Method method : type.getDeclaredMethods()) {
                Auditable auditable = method.getAnnotation(Auditable.class);
                if (auditable != null && auditable.includeArguments()) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    private Class<?> classOf(String name) {
        try {
            return name == null ? null : Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static boolean looksSecret(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return SECRET_NAMES.stream().anyMatch(lower::contains);
    }

    @Test
    @DisplayName("No audited request type exposes a secret through its text form")
    void auditedArgumentsNeverExposeSecrets() {
        List<Method> audited = auditedMethodsThatRecordArguments();

        // Test kendi bosluguna karsi savunur: hicbir metot bulunamadiysa
        // tarama bozulmus demektir ve yesil olmasi anlamsizdir.
        assertThat(audited)
                .as("the scan must find the audited service methods")
                .isNotEmpty();

        List<String> leaks = new ArrayList<>();

        for (Method method : audited) {
            for (Class<?> parameter : method.getParameterTypes()) {
                if (!parameter.isRecord()) {
                    continue;
                }
                boolean hasSecret = false;
                for (RecordComponent component : parameter.getRecordComponents()) {
                    if (looksSecret(component.getName())) {
                        hasSecret = true;
                        break;
                    }
                }
                if (!hasSecret) {
                    continue;
                }
                // Sir tasiyan bir tip isaretlenmisse, toString'i ELLE
                // yazilmis olmali: record'un uretilmis hali her bileseni basar.
                boolean masksItsOwnText = declaresOwnToString(parameter);
                if (!masksItsOwnText) {
                    leaks.add(method.getDeclaringClass().getSimpleName() + "." + method.getName()
                            + " -> " + parameter.getSimpleName());
                }
            }
        }

        assertThat(leaks)
                .as("an audited request that carries a secret must mask it in toString(), "
                        + "otherwise the audit trail records it in plain text")
                .isEmpty();
    }

    private boolean declaresOwnToString(Class<?> type) {
        try {
            return type.getDeclaredMethod("toString").getDeclaringClass() == type;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @Test
    @DisplayName("The account request keeps its password out of its own text form")
    void userCreateRequestMasksPassword() {
        // Yukaridaki tarama yapisal koruma; bu test somut kanit.
        var request = new com.proje.employee.dto.UserCreateRequest(
                "ada@example.com", "a-very-secret-password",
                java.util.Set.of(com.proje.employee.entity.Role.EMPLOYEE), null);

        assertThat(request.toString())
                .doesNotContain("a-very-secret-password")
                .contains("ada@example.com")
                .contains("***");
    }
}
