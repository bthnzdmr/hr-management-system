package com.proje.employee.controller;

import com.proje.employee.dto.DashboardResponse;
import com.proje.employee.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gosterge paneli verisi.
 *
 * Yetki SecurityConfig'te: yalnizca HR_SPECIALIST ve SYSTEM_ADMIN.
 *
 * Sebep, toplu verinin bireysel veriden FARKLI olmasidir: kapsami sinirli bir
 * kullanici tek tek goremedigi kisilerin toplamini da gormemelidir. "Pazarlama
 * 5 kisi" masum gorunur ama ayni mantikla ayrilma sayilari da sizardi ve
 * kucuk bir departmanda toplam, bireyi ele verir.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardResponse overview() {
        return dashboardService.overview();
    }
}
