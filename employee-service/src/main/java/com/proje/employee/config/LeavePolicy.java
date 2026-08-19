package com.proje.employee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Comparator;
import java.util.List;

/**
 * Yillik izin politikasi: kac gun hak edilir, kac gun devreder.
 *
 * Projedeki diger yapilandirmalar `@Value` ile okunuyor ve bu, skaler degerler
 * icin dogru araç. Merdiven skaler DEGIL, sirali bir liste: `@Value` ile
 * okunabilmesi icin "0:0,1:14,5:20,15:26" gibi bir metni elle ayristirmak
 * gerekirdi. Yapilandirmanin SEKLI koda degil yapilandirmaya ait.
 *
 * Merdiven `application.yml`'de ACIKCA yazili, Java varsayilani olarak gizli
 * degil: bu bir POLITIKA ve politikanin gozden gecirilebilir olmasi gerekir.
 */
@ConfigurationProperties(prefix = "app.leave")
public record LeavePolicy(int defaultAnnualDays, int carryOverCap, List<Tier> seniorityLadder) {

    /** Kidem `fromYears` yila ulastiginda hak `days` olur. */
    public record Tier(int fromYears, int days) {
    }

    /**
     * BOZUK bir merdiven uygulamayi acmaz; EKSIK olan bir merdiven acar.
     *
     * Ayrim ilk denemede yanlis kuruldu: merdiven zorunlu yazilmisti ve
     * yapilandirmasinda o blok bulunmayan her baglam -- butun test yigini
     * dahil -- acilmaz oldu. Olculdu.
     *
     * Dogru olcut sudur: `JWT_SECRET` gurultulu patlar cunku bir sirrin
     * GUVENLI VARSAYILANI YOKTUR. Merdivenin var ve zararsiz: "merdiven yoksa
     * herkese varsayilan gun" tam olarak bu ozellik yazilmadan onceki
     * davranistir, yani surpriz uretmez.
     *
     * Bozuk merdiven baska: sifirdan baslamayan bir merdiven HERKESIN hakkini
     * yanlis hesaplar ve kimsenin bakmadigi bir sayida gorunmez kalirdi.
     */
    public LeavePolicy {
        if (seniorityLadder == null || seniorityLadder.isEmpty()) {
            seniorityLadder = List.of(new Tier(0, defaultAnnualDays));
        }

        seniorityLadder = seniorityLadder.stream()
                .sorted(Comparator.comparingInt(Tier::fromYears))
                .toList();

        // Ilk basamak SIFIRDAN baslamak zorunda: baslamasaydi kidemi
        // merdivenin altinda kalan biri icin cevap OLMAZDI ve o kisi -- yeni
        // ise alinan herkes -- sessizce disarida kalirdi.
        if (seniorityLadder.get(0).fromYears() != 0) {
            throw new IllegalStateException(
                    "app.leave.seniority-ladder must start at 0 years");
        }

        if (carryOverCap < 0 || defaultAnnualDays < 0) {
            throw new IllegalStateException("Leave policy days cannot be negative");
        }
    }

    /**
     * Verilen kidem icin hak edilen gun sayisi.
     *
     * Kidemin ULASTIGI en yuksek basamak kazanir. Basamaklar sirali oldugu
     * icin sondan basa ilk eslesme dogru cevaptir.
     */
    public int entitledDaysFor(int completedYears) {
        for (int i = seniorityLadder.size() - 1; i >= 0; i--) {
            Tier tier = seniorityLadder.get(i);
            if (completedYears >= tier.fromYears()) {
                return tier.days();
            }
        }

        // Yapici sifirdan baslamayi zorunlu kildigi icin buraya dusulemez.
        throw new IllegalStateException("No ladder tier matches " + completedYears + " years");
    }

    /** Devreden gun tavani asamaz ve eksiye inemez. */
    public int carryOverFrom(int remainingDays) {
        return Math.max(0, Math.min(remainingDays, carryOverCap));
    }
}
