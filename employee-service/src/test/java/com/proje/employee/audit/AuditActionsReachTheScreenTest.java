package com.proje.employee.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sunucuya eklenen her denetim eylemi ARAYUZDE de tanimli mi?
 *
 * <p>Bu hata UC KEZ tekrarlandi: `EMPLOYEES_EXPORTED`, `LEAVE_ENTITLEMENT_SET`
 * ve `EMPLOYEES_IMPORTED` eklendiginde arayuzdeki tip ve etiket haritasi
 * guncellenmedi. Sonucu sessiz: eylem suzgecten SECILEMIYOR ve tabloda ham
 * enum adiyla gorunuyor -- hicbir test kirilmadan.
 *
 * <p>Uc tekrardan sonra "dikkat ederim" bir cozum degil; kapinin MEKANIK
 * olmasi gerekiyor. Diller farkli oldugu icin tek yol arayuz dosyasini
 * okumak.
 *
 * <p>Dosya bulunamazsa test DUSER, atlanmaz: bulunamayan bir dosya sessiz bir
 * "her sey yolunda" uretirdi ve kapi tam da o gun ise yaramaz hale gelirdi.
 * Ayni ders `AuditSecretLeakTest`te de yazili -- sifir metot tarayan bir
 * kontrol, gecmesine ragmen hicbir sey kanitlamaz.
 */
class AuditActionsReachTheScreenTest {

    /** Modul her zaman `frontend/` ile YAN YANA durur. */
    private static final Path FRONTEND = Path.of("..", "frontend", "src");

    private String read(Path relative) throws IOException {
        Path path = FRONTEND.resolve(relative);

        assertThat(Files.exists(path))
                .describedAs("frontend file %s should exist; the guard is useless without it",
                        path.toAbsolutePath())
                .isTrue();

        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Every audit action is declared in the frontend type")
    void everyActionIsDeclaredInTheFrontendType() throws IOException {
        String types = read(Path.of("api", "audit.ts"));

        assertThat(names())
                .allSatisfy(action -> assertThat(types)
                        .describedAs("AuditAction '%s' is missing from the frontend union", action)
                        .contains("'" + action + "'"));
    }

    @Test
    @DisplayName("Every audit action has a label on the audit screen")
    void everyActionHasALabel() throws IOException {
        // Etiketi olmayan eylem, filtre listesinde HIC gorunmez ve tabloda ham
        // enum adiyla ("EMPLOYEES_IMPORTED") basilir.
        String page = read(Path.of("pages", "AuditPage.tsx"));

        assertThat(names())
                .allSatisfy(action -> assertThat(page)
                        .describedAs("AuditAction '%s' has no label on the audit screen", action)
                        .contains("'" + action + "'"));
    }

    private List<String> names() {
        return Arrays.stream(AuditAction.values()).map(Enum::name).toList();
    }
}
