package com.proje.employee.dto;

import java.util.List;

/**
 * Organizasyon agaci.
 *
 * MAAS YOK ve olmayacak: bu uc toplu yapisal veridir ve panelle ayni
 * kitleye acik. Ucret bilgisi ayri bir yetki cemberinde durur.
 *
 * @param roots       yoneticisi olmayan aktif personel ve altlari
 * @param placed      agacta gorunen kisi sayisi
 * @param unreachable aktif olup agaca giremeyen kisi sayisi -- yoneticisi
 *                    pasiflesmis personel koke baglanamaz ve agactan duser.
 *                    Sessizce kaybetmek yerine SAYILIR: eksik bir sema,
 *                    eksik oldugunu soylemeyen bir semadan iyidir.
 *                    <b>Sinir asildiysa hesaplanmaz (0)</b>: o durumda
 *                    cizilmeyen kisilerin hangisinin veri kusuru, hangisinin
 *                    kirpma yuzunden disarida kaldigi AYIRT EDILEMEZ ve
 *                    tahmini bir sayi vermek yanlis olurdu.
 * @param truncated   dugum siniri asildi mi. `unreachable`den AYRI bir alan
 *                    cunku ayri bir olgu: biri VERI KUSURU, digeri CIZIM
 *                    SINIRI. Tek alanda toplansalardi arayuz hangi mesaji
 *                    gosterecegini bilemezdi -- ayni hata izin modulunde
 *                    talep notu ile karar notu tek kolonda tutulurken bir
 *                    kez yasandi.
 */
public record OrgChartResponse(
        List<Node> roots,
        long placed,
        long unreachable,
        boolean truncated
) {

    public record Node(
            Long id,
            String firstName,
            String lastName,
            String jobTitle,
            String departmentName,
            int depth,
            List<Node> reports
    ) {
    }
}
