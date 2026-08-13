package com.proje.employee.dto;

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
public record DepartmentResponse(Long id, String name, boolean active, long activeEmployeeCount) {
}
