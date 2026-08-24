package com.proje.employee.export;

import java.util.ArrayList;
import java.util.List;

/**
 * RFC 4180 okuyucusu.
 *
 * Duz bir `split(",")` YETMEZ: tirnak icindeki virgul bir ayrac degildir ve
 * tirnak icinde satir sonu bile bulunabilir. `CsvWriter` bu ikisini de
 * uretebiliyor, dolayisiyla kendi yazdigimiz dosyayi kendi okuyucumuz
 * okuyamasaydi dis aktarma ile ic aktarma birbirini tutmazdi.
 *
 * Kutuphane EKLENMEDI: ihtiyacimiz olan sey bir durum makinesi ve bunun icin
 * zaten dil var. Ayni olcut `CsvWriter`da ve grafik kutuphanesi kararinda da
 * uygulanmisti -- ama org chart'ta ters sonuc vermisti, yani bu bir kural
 * degil her seferinde yapilan bir olcum.
 */
public final class CsvReader {

    /** Excel'in UTF-8 isareti; kendi yazdigimiz dosya bunu tasiyor. */
    private static final char BOM = '﻿';

    private CsvReader() {
    }

    /**
     * Satirlari ve hucreleri cozer. Bos satirlar ATLANIR.
     *
     * Bos satir bir kayit degildir: dosya sonundaki tek bir satir sonu, aksi
     * halde butun alanlari bos olan sahte bir satir uretirdi ve kullanici
     * anlamadigi bir "1. satir gecersiz" hatasi alirdi.
     */
    public static List<List<String>> parse(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();

        boolean quoted = false;
        int i = 0;

        // BOM yalnizca dosyanin BASINDA anlamlidir; ilerideki ayni karakter
        // gercek veridir.
        if (!content.isEmpty() && content.charAt(0) == BOM) {
            i = 1;
        }

        while (i < content.length()) {
            char c = content.charAt(i);

            if (quoted) {
                if (c == '"') {
                    // RFC 4180: tirnak icinde cift tirnak, tek tirnak demektir.
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        cell.append('"');
                        i += 2;
                        continue;
                    }
                    quoted = false;
                } else {
                    cell.append(c);
                }
                i++;
                continue;
            }

            switch (c) {
                case '"' -> quoted = true;
                case ',' -> {
                    row.add(cell.toString());
                    cell.setLength(0);
                }
                case '\r' -> {
                    // CRLF tek satir sonudur; LF'i bir sonraki tur yutar.
                }
                case '\n' -> {
                    row.add(cell.toString());
                    cell.setLength(0);
                    addIfMeaningful(rows, row);
                    row = new ArrayList<>();
                }
                default -> cell.append(c);
            }
            i++;
        }

        // Son satir yeni satirla bitmemis olabilir.
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            addIfMeaningful(rows, row);
        }

        return rows;
    }

    private static void addIfMeaningful(List<List<String>> rows, List<String> row) {
        boolean empty = row.stream().allMatch(value -> value.trim().isEmpty());

        if (!empty) {
            rows.add(row);
        }
    }
}
