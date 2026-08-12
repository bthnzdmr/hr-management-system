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
 */
public record OrgChartResponse(
        List<Node> roots,
        long placed,
        long unreachable
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
