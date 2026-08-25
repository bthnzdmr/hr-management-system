package com.proje.employee.dto;

import com.proje.employee.audit.AuditDetail;
import com.proje.employee.entity.NotificationKind;

import java.util.List;

/**
 * Bir kisinin bildirim tercihleri.
 *
 * <p>Cevap BUTUN turleri listeler, yalnizca susturulanlari degil: arayuz
 * "hangi secenekler var" sorusunu ayrica sormak zorunda kalmasin ve iki yerde
 * yasayan bir liste olusmasin.
 */
public record NotificationPreferenceResponse(List<Item> items) implements AuditDetail {

    public record Item(NotificationKind kind, String label, boolean enabled) {
    }

    /**
     * Denetim izinde ham dokum degil CUMLE durur.
     *
     * Kapali olanlar yaziliyor cunku ANLAMLI olan sapmadir: varsayilan zaten
     * "hepsi acik" ve butun listeyi her seferinde basmak, gerceklesen degisikligi
     * gurultuye gomerdi.
     */
    @Override
    public String auditDetail() {
        List<String> off = items.stream().filter(item -> !item.enabled())
                .map(Item::label).toList();

        return off.isEmpty()
                ? "All notifications on"
                : "Turned off: " + String.join(", ", off);
    }
}
