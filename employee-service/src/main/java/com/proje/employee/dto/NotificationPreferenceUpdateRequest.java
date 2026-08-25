package com.proje.employee.dto;

import com.proje.employee.entity.NotificationKind;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * Bildirim tercihlerini gunceller.
 *
 * <p><b>ACIK olanlarin TAMAMI gonderilir</b>, tek tek "sustur/ac" degil. Iki
 * es zamanli istek artimli uclarla birbirinin uzerine yazabilir ve kimsenin
 * istemedigi bir ara duruma dusulebilirdi -- rol kumesinin komple
 * gonderilmesindeki gerekcenin aynisi. Butun kumeyi gondermek istegi
 * idempotent de yapar.
 *
 * <p>Bos kume gecerlidir: "hicbirini isteme" mesru bir tercih. Bu yuzden
 * `@NotNull`, `@NotEmpty` degil.
 */
public record NotificationPreferenceUpdateRequest(
        @NotNull(message = "Enabled notifications are required")
        Set<NotificationKind> enabled
) {
}
