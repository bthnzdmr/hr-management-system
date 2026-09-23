package com.proje.employee;

import com.proje.employee.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * README'nin SAYDIGI seyler gerceklikle uyusuyor mu?
 *
 * <p>Belgeyi guncel tutmak bir disiplin meselesi degil, bir KAPI meselesidir.
 * Olculdu: teknoloji yigini tablosu <b>Spring Boot 3.2.12</b> ve
 * <b>Spring Cloud 2023.0.5</b> diyordu -- iki buyuk goc geride. Uc numarali
 * uc sayisi, migration sayisi ve test sayilari da zamanla kaymisti. Hicbiri
 * bir testi kirmadi cunku hicbirini tutan test yoktu.
 *
 * <p>Ayni kalip {@code AuditActionsReachTheScreenTest}te zaten var: iki ayri
 * dilde yasayan bir bilginin senkron kalmasi ancak MEKANIK olarak
 * dogrulanabilir. Burada iki ayri BICIMDE yasiyor -- biri kod, digeri proza.
 *
 * <p><b>Kasitli olarak DAR:</b> yalnizca sayilabilen ve tek bir dogru cevabi
 * olan iddialar sinanir. "Aciklama guncel mi" sorusu makineye sorulamaz ve
 * sorulmaya calisilsaydi yanlis alarm ureten, zamanla gormezden gelinen bir
 * test olurdu.
 *
 * <p><b>TEST SAYISI BILEREK GATE'LENMEDI</b> ve README de onu artik kesin
 * yazmiyor. Sebebi dongusel: sayiyi olcmenin tek guvenilir yolu testleri
 * KOSTURMAK, dolayisiyla bir testin icinden bakilan sayi her zaman eksik
 * olurdu. Ustelik her yeni test kapiyi kirardi -- test yazmayi cezalandiran
 * bir kapi, kaldirilan bir kapidir. Gate'lenemeyen bir sayi kesin
 * yazilmaz: yalan soyleyen bir sayi, hic olmayandan kotudur.
 *
 * <p>Dosya bulunamazsa test DUSER, atlanmaz -- bulunamayan bir dosya sessiz
 * bir "her sey yolunda" uretirdi.
 */
class ReadmeStaysTrueTest {

    /** Modul her zaman deponun kokuyle YAN YANA durur. */
    private static final Path ROOT = Path.of("..");

    private static final Path README = ROOT.resolve("README.md");

    private String readme() {
        return read(README);
    }

    private String read(Path path) {
        assertThat(Files.exists(path))
                .describedAs("%s should exist; the guard proves nothing without it",
                        path.toAbsolutePath())
                .isTrue();
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** README'de "<sayi> <etiket>" kalibini bulur. */
    private int claimed(String label) {
        Matcher matcher = Pattern.compile("(\\d+)\\s+" + Pattern.quote(label)).matcher(readme());

        assertThat(matcher.find())
                .describedAs("README should state a count for '%s'", label)
                .isTrue();

        return Integer.parseInt(matcher.group(1));
    }

    private String pomProperty(Path pom, String tag) {
        Matcher matcher = Pattern
                .compile("<" + tag + ">([^<]+)</" + tag + ">")
                .matcher(read(pom));

        assertThat(matcher.find())
                .describedAs("<%s> should be declared in %s", tag, pom)
                .isTrue();

        return matcher.group(1);
    }

    @Test
    @DisplayName("The Spring Boot version in the README is the one actually built")
    void bootVersionIsCurrent() {
        // OLCULDU: README iki goc boyunca 3.2.12 yaziyordu. Bir mulakatta
        // acilacak ilk dosyanin en temel satiri yanlisti.
        Matcher parent = Pattern
                .compile("<parent>.*?<version>([^<]+)</version>", Pattern.DOTALL)
                .matcher(read(Path.of("pom.xml")));

        assertThat(parent.find()).describedAs("the parent version should be declared").isTrue();
        String version = parent.group(1);

        assertThat(readme())
                .describedAs("README should name Spring Boot %s", version)
                .contains(version);
    }

    @Test
    @DisplayName("The Spring Cloud version in the README is the one actually built")
    void cloudVersionIsCurrent() {
        String version = pomProperty(Path.of("pom.xml"), "spring-cloud.version");

        assertThat(readme())
                .describedAs("README should name Spring Cloud %s", version)
                .contains(version);
    }

    @Test
    @DisplayName("The endpoint count in the README matches the controllers")
    void endpointCountIsCurrent() {
        long actual = javaFiles(Path.of("src", "main", "java", "com", "proje", "employee",
                        "controller"))
                .mapToLong(file -> Pattern
                        .compile("@(?:Get|Post|Put|Delete|Patch)Mapping")
                        .matcher(read(file))
                        .results()
                        .count())
                .sum();

        assertThat(claimed("REST ucu"))
                .describedAs("README claims a different number of endpoints than the controllers expose")
                .isEqualTo((int) actual);
    }

    @Test
    @DisplayName("The migration count in the README covers both services")
    void migrationCountIsCurrent() {
        long actual = Stream
                .of(Path.of("src", "main", "resources", "db", "migration"),
                        ROOT.resolve(Path.of("notification-service", "src", "main", "resources",
                                "db", "migration")))
                .mapToLong(this::countMigrations)
                .sum();

        assertThat(claimed("Flyway migration"))
                .describedAs("a migration was added without updating the README")
                .isEqualTo((int) actual);
    }

    @Test
    @DisplayName("The role count in the README matches the enum")
    void roleCountIsCurrent() {
        // Rol sayisi bir kez 2'den 6'ya cikti ve o gun butun ekranlari
        // degistirdi; README'nin bunu kacirmasi kolay.
        assertThat(claimed("rol"))
                .describedAs("README claims a different number of roles than Role declares")
                .isEqualTo(Role.values().length);
    }

    @Test
    @DisplayName("Every file the README links to exists")
    void everyLinkedFileExists() {
        // Olu bir baglanti, okuyanin guvenini once bir kez kirar ve sonra
        // butun belgeye yayilir.
        Matcher links = Pattern.compile("\\]\\((?!https?:)([^)#]+)").matcher(readme());

        while (links.find()) {
            String target = links.group(1).trim();
            if (target.isEmpty()) {
                continue;
            }

            assertThat(Files.exists(ROOT.resolve(target)))
                    .describedAs("README links to '%s', which does not exist", target)
                    .isTrue();
        }
    }

    private long countMigrations(Path directory) {
        assertThat(Files.isDirectory(directory))
                .describedAs("%s should be a migration directory", directory.toAbsolutePath())
                .isTrue();

        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> file.getFileName().toString().startsWith("V")).count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Stream<Path> javaFiles(Path directory) {
        assertThat(Files.isDirectory(directory))
                .describedAs("%s should exist", directory.toAbsolutePath())
                .isTrue();

        try (Stream<Path> files = Files.list(directory)) {
            List<Path> collected = files
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .toList();
            return collected.stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
