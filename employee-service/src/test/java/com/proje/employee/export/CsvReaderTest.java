package com.proje.employee.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RFC 4180 okuyucusu.
 *
 * <p>Duz bir {@code split(",")} yetmez ve bunun somut bedeli var:
 * {@code CsvWriter} tirnak icinde virgul ve satir sonu URETEBILIYOR, yani
 * kendi yazdigimiz dosyayi kendi okuyucumuz okuyamasaydi dis aktarma ile ic
 * aktarma birbirini tutmazdi.
 */
class CsvReaderTest {

    @Test
    @DisplayName("Reads a plain row")
    void readsAPlainRow() {
        assertThat(CsvReader.parse("a,b,c")).containsExactly(List.of("a", "b", "c"));
    }

    @Test
    @DisplayName("A comma inside quotes is data, not a separator")
    void aQuotedCommaIsData() {
        // Duz split burada dort hucre uretir ve sonraki her alan bir kayar.
        assertThat(CsvReader.parse("\"Hopper, Grace\",Engineer"))
                .containsExactly(List.of("Hopper, Grace", "Engineer"));
    }

    @Test
    @DisplayName("A doubled quote inside quotes is a single quote")
    void aDoubledQuoteIsOne() {
        assertThat(CsvReader.parse("\"Grace \"\"Amazing\"\" Hopper\",x"))
                .containsExactly(List.of("Grace \"Amazing\" Hopper", "x"));
    }

    @Test
    @DisplayName("A newline inside quotes does not end the row")
    void aQuotedNewlineDoesNotEndTheRow() {
        assertThat(CsvReader.parse("\"line one\nline two\",x"))
                .containsExactly(List.of("line one\nline two", "x"));
    }

    @Test
    @DisplayName("CRLF is one row break, not two")
    void crlfIsOneBreak() {
        // Iki sayilsaydi her satirin arasinda bos bir satir olusurdu.
        assertThat(CsvReader.parse("a,b\r\nc,d"))
                .containsExactly(List.of("a", "b"), List.of("c", "d"));
    }

    @Test
    @DisplayName("The byte order mark our own export writes is skipped")
    void theByteOrderMarkIsSkipped() {
        // Atlanmasaydi ilk sutun adi gorunmez bir karakterle baslar ve baslik
        // kontrolu HER dosyada basarisiz olurdu -- yani kendi aktardigimiz
        // dosyayi hicbir zaman geri okuyamazdik.
        assertThat(CsvReader.parse("﻿Id,Name").get(0).get(0)).isEqualTo("Id");
    }

    @Test
    @DisplayName("A byte order mark further in is ordinary data")
    void aLaterMarkIsData() {
        // Isaret yalnizca dosyanin BASINDA anlamlidir.
        assertThat(CsvReader.parse("a,﻿b").get(0).get(1)).isEqualTo("﻿b");
    }

    @Test
    @DisplayName("A blank line is not a row")
    void blankLinesAreSkipped() {
        // Dosya sonundaki tek satir sonu, aksi halde butun alanlari bos olan
        // sahte bir satir uretir ve kullanici anlamadigi bir hata alirdi.
        assertThat(CsvReader.parse("a,b\r\n\r\nc,d\r\n")).hasSize(2);
    }

    @Test
    @DisplayName("A row that does not end with a newline is still read")
    void theLastRowNeedsNoNewline() {
        assertThat(CsvReader.parse("a,b\nc,d")).hasSize(2);
    }

    @Test
    @DisplayName("An empty cell stays in place so the columns stay aligned")
    void emptyCellsKeepTheirPlace() {
        // Atlansaydi sonraki her alan bir kayar ve e-posta sutununda unvan
        // gorunurdu.
        assertThat(CsvReader.parse("a,,c")).containsExactly(List.of("a", "", "c"));
    }

    @Test
    @DisplayName("Empty input is no rows, not one empty row")
    void emptyInputIsNoRows() {
        assertThat(CsvReader.parse("")).isEmpty();
    }

    @Test
    @DisplayName("Whatever the writer produces, the reader gets back")
    void whatTheWriterWritesTheReaderReads() {
        // ASIL IDDIA: iki taraf birbirini tutmali. Zor degerlerin hepsi bir
        // arada -- virgul, tirnak, satir sonu ve bos hucre.
        List<String> header = List.of("Name", "Note");
        List<List<String>> rows = List.of(
                List.of("Hopper, Grace", "said \"hello\""),
                List.of("Ada", "line one\nline two"),
                List.of("Empty", ""));

        List<List<String>> read = CsvReader.parse(CsvWriter.toCsv(header, rows));

        assertThat(read.get(0)).isEqualTo(header);
        assertThat(read.subList(1, read.size())).isEqualTo(rows);
    }
}
