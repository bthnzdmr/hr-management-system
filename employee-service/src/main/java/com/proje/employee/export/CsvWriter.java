package com.proje.employee.export;

import java.util.List;

/**
 * RFC 4180 bicimi ve formul enjeksiyonuna karsi koruma.
 *
 * CSV masum bir metin bicimi gibi gorunur ama elektronik tablo programlari
 * `=`, `+`, `-` veya `@` ile baslayan bir hucreyi FORMUL sayar. Bir personelin
 * adi `=HYPERLINK(...)` olsaydi, dosyayi acan kisinin makinesinde calisirdi --
 * yani veri, kodun disari cikma yolu olurdu.
 *
 * Bu yuzden disari yazma da bir GUVEN SINIRIDIR: iceri giren veri
 * dogrulanirken disari cikan veri de notrlenir.
 */
public final class CsvWriter {

    private static final String LINE_SEPARATOR = "\r\n";

    /**
     * Elektronik tabloda formul baslatan karakterler.
     *
     * Sekme ve satir basi de burada: bazi programlar onlari atlayip SONRAKI
     * karakterden basliyor, yani "\t=cmd" da bir formuldur.
     */
    private static final String FORMULA_STARTERS = "=+-@\t\r";

    private CsvWriter() {
    }

    public static String toCsv(List<String> header, List<List<String>> rows) {
        StringBuilder csv = new StringBuilder();

        // Excel UTF-8'i ancak BOM ile taniyor; olmadan aksanli harfler bozuk
        // acilir. Bedeli: BOM'u beklemeyen ayristiricilar ilk sutun adinin
        // basinda gorunmez bir karakter gorur.
        csv.append('﻿');

        appendRow(csv, header);
        rows.forEach(row -> appendRow(csv, row));

        return csv.toString();
    }

    private static void appendRow(StringBuilder csv, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(cell(cells.get(i)));
        }
        csv.append(LINE_SEPARATOR);
    }

    /**
     * Tek bir hucre: once formul notrlemesi, sonra tirnaklama.
     *
     * Sira ONEMLI. Once tirnaklansaydi notrleme tirnagin ICINDEKI metne
     * bakmak zorunda kalirdi; simdi tek bir kural var.
     */
    static String cell(String value) {
        String text = value == null ? "" : value;

        if (!text.isEmpty() && FORMULA_STARTERS.indexOf(text.charAt(0)) >= 0) {
            // Tek tirnak, elektronik tabloya "bu METIN" der ve hucrede
            // gorunmez. Degeri SILMEK yerine notrlemek tercih edildi: veri
            // kaybolmamali, yalnizca calismamali.
            text = "'" + text;
        }

        boolean needsQuotes = text.indexOf(',') >= 0
                || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0;

        if (!needsQuotes) {
            return text;
        }

        // RFC 4180: tirnak icinde tirnak, iki tirnak yazilarak kacirilir.
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
