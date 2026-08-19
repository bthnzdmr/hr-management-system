package com.proje.employee.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSV bicimi ve formul enjeksiyonu.
 *
 * <p>Disari yazma da bir GUVEN SINIRIDIR. CSV masum bir metin bicimi gibi
 * gorunur ama elektronik tablo programlari `=` ile baslayan bir hucreyi FORMUL
 * sayar: bir personelin adi {@code =HYPERLINK(...)} olsaydi, dosyayi acan
 * kisinin makinesinde calisirdi.
 *
 * <p>Zararin yeri de onemli: aciga cikan kisi veriyi GIREN degil, dosyayi ACAN
 * kisidir -- yani saldirgan bir alani doldurup baskasinin makinesinde kod
 * calistirmis olur.
 */
class CsvWriterTest {

    private String cellOf(String value) {
        return CsvWriter.cell(value);
    }

    // ------------------------------------------------------ formul enjeksiyonu

    @Test
    @DisplayName("A cell starting with an equals sign is neutralised, not executed")
    void anEqualsSignIsNeutralised() {
        assertThat(cellOf("=1+1")).startsWith("'");
    }

    @Test
    @DisplayName("Every character a spreadsheet treats as a formula start is covered")
    void everyFormulaStarterIsCovered() {
        // Dordu de formul baslatir. Yalnizca "=" dusunulseydi digerleri acikta
        // kalirdi -- ve `@SUM` ile `+cmd` en az `=` kadar tehlikelidir.
        for (String starter : List.of("=", "+", "-", "@")) {
            assertThat(cellOf(starter + "cmd|'/c calc'!A0"))
                    .as("starter %s", starter)
                    .startsWith("'");
        }
    }

    @Test
    @DisplayName("A leading tab or carriage return is covered too")
    void whitespaceLeadersAreCoveredToo() {
        // Bazi programlar bosluk karakterlerini ATLAYIP sonraki karakterden
        // basliyor, yani "\t=cmd" da bir formuldur.
        assertThat(cellOf("\t=cmd")).startsWith("'");

        // Satir basi ayrica TIRNAKLANMAK zorunda, yoksa satiri bolerdi.
        // Yani notrleme tirnagin ICINDE kalir; ayristirici hucreyi okudugunda
        // gordugu deger yine tirnakla basliyor.
        assertThat(cellOf("\r=cmd")).startsWith("\"'");
    }

    @Test
    @DisplayName("The original value is kept, only made inert")
    void theValueIsKeptNotDropped() {
        // Silmek yerine notrlemek tercih edildi: bir personelin adi gercekten
        // "-Reyes" olabilir ve veri kaybolmamali, yalnizca calismamali.
        assertThat(cellOf("-Reyes")).contains("-Reyes");
    }

    @Test
    @DisplayName("An ordinary value is left completely alone")
    void anOrdinaryValueIsUntouched() {
        // Karsi kosul: her hucreye tirnak eklemek de yukaridaki iddialari
        // gecirirdi ama dosyayi okunmaz kilardi.
        assertThat(cellOf("Grace Hopper")).isEqualTo("Grace Hopper");
    }

    // ---------------------------------------------------------- RFC 4180

    @Test
    @DisplayName("A value containing a comma is quoted so it stays one column")
    void aCommaIsQuoted() {
        assertThat(cellOf("Hopper, Grace")).isEqualTo("\"Hopper, Grace\"");
    }

    @Test
    @DisplayName("A quote inside a value is doubled, not dropped")
    void aQuoteIsDoubled() {
        // RFC 4180'in kacis kurali. Tek tirnak birakilsaydi ayristirici hucreyi
        // ORADA bitirir ve satirin geri kalani kayardi.
        assertThat(cellOf("Grace \"Amazing\" Hopper"))
                .isEqualTo("\"Grace \"\"Amazing\"\" Hopper\"");
    }

    @Test
    @DisplayName("A newline inside a value is quoted instead of breaking the row")
    void aNewlineIsQuoted() {
        assertThat(cellOf("line one\nline two")).startsWith("\"").endsWith("\"");
    }

    @Test
    @DisplayName("A missing value becomes an empty cell, not the text null")
    void aMissingValueIsEmpty() {
        // "null" yazilsaydi okuyan kisi bunu bir DEGER sanardi.
        assertThat(cellOf(null)).isEmpty();
    }

    // ------------------------------------------------------------- belge

    @Test
    @DisplayName("Rows end with CRLF as the format requires")
    void rowsEndWithCrlf() {
        String csv = CsvWriter.toCsv(List.of("A"), List.of(List.of("1")));

        assertThat(csv).endsWith("\r\n").contains("A\r\n1\r\n");
    }

    @Test
    @DisplayName("The file opens as UTF-8 in a spreadsheet")
    void theFileCarriesAByteOrderMark() {
        // Excel UTF-8'i ancak BOM ile taniyor; olmadan aksanli harfler bozuk
        // acilir ve kullanici veriyi hatali sanir.
        assertThat(CsvWriter.toCsv(List.of("A"), List.of())).startsWith("﻿");
    }

    @Test
    @DisplayName("An empty result still carries its header")
    void anEmptyResultKeepsTheHeader() {
        // Bos bir dosya "sonuc yok" ile "aktarma bozuk" arasinda ayrim
        // yapmazdi; basliklar bunu soyluyor.
        assertThat(CsvWriter.toCsv(List.of("Id", "Email"), List.of()))
                .contains("Id,Email\r\n");
    }

    @Test
    @DisplayName("A row with missing values keeps its column count")
    void columnsStayAlignedWhenValuesAreMissing() {
        // Sutun sayisi degisseydi sonraki her alan bir kayardi ve e-posta
        // sutununda unvan gorunurdu.
        String csv = CsvWriter.toCsv(List.of("A", "B", "C"),
                List.of(Arrays.asList("1", null, "3")));

        assertThat(csv).contains("1,,3\r\n");
    }
}
