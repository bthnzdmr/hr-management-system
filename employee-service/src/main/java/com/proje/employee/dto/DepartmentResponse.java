package com.proje.employee.dto;

import com.proje.employee.audit.AuditDetail;
import com.proje.employee.audit.AuditLabel;

/**
 * Departman.
 *
 * Tek bir sekil kullanilir: hem secim listesi hem yonetim ekrani bunu okur.
 * Iki ayri cevap tanimlansaydi biri digerinin gerisinde kalirdi -- projede
 * ayni hata bos durum metinlerinde ve rol seciciyde yasandi.
 *
 * @param activeEmployeeCount kapatmanin mumkun olup olmadigini gosterir;
 *                            arayuz bunu okuyup dugmeyi devre disi birakir
 */
public record DepartmentResponse(Long id, String name, boolean active, long activeEmployeeCount)
        implements AuditLabel, AuditDetail {

    @Override
    public String auditLabel() {
        return name;
    }

    /** Kadro sayisi da yaziliyor: bir departmanin kapatilmasi onu bagimli kilar. */
    @Override
    public String auditDetail() {
        String people = activeEmployeeCount + (activeEmployeeCount == 1 ? " person" : " people");

        return (active ? "Open" : "Closed") + " · " + people;
    }
}
