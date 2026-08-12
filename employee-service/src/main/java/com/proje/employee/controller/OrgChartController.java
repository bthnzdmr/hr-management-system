package com.proje.employee.controller;

import com.proje.employee.dto.OrgChartResponse;
import com.proje.employee.service.OrgChartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organizasyon semasi.
 *
 * Yol BILEREK /api/employees altinda DEGIL. Orada olsaydi
 * "GET /api/employees/**" kuralinin ONUNE bir kural yazmak gerekirdi ve
 * Spring Security ilk eslesen kurali uygular -- sonra yazilsaydi sema butun
 * rollere acik kalirdi. Bu tuzak projede maas ucunda bir kez yasandi;
 * ayri bir yol onu tamamen ortadan kaldiriyor.
 */
@Tag(name = "Org chart", description = "Raporlama cizgileri. Panelle ayni kitleye acik.")
@RestController
@RequestMapping("/api/org-chart")
public class OrgChartController {

    private final OrgChartService orgChartService;

    public OrgChartController(OrgChartService orgChartService) {
        this.orgChartService = orgChartService;
    }

    @GetMapping
    @Operation(summary = "Organizasyon agaci",
            description = """
                    Yalnizca AKTIF personel. Yoneticisi pasiflesmis kisiler koke
                    baglanamaz ve agactan duser; sayilari "unreachable" alaninda
                    bildirilir -- eksik bir sema, eksik oldugunu soylemeyen bir
                    semadan iyidir.
                    """)
    public OrgChartResponse get() {
        return orgChartService.build();
    }
}
