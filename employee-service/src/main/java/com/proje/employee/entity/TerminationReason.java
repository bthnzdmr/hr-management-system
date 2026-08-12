package com.proje.employee.entity;

/**
 * Ayrilma sebebi.
 *
 * Serbest metin DEGIL kapali bir kume: devir oraninin en anlamli kirilimi
 * "istege bagli mi zorunlu mu" ayrimidir ve serbest metinle bu ayrim
 * yapilamaz -- "istifa", "Istifa", "kendi istegiyle" ayri seyler sayilirdi.
 */
public enum TerminationReason {

    /** Kendi istegiyle ayrildi. */
    RESIGNED,

    /** Isveren tarafindan cikarildi. */
    DISMISSED,

    RETIRED,

    /** Belirli sureli sozlesme doldu. */
    END_OF_CONTRACT,

    OTHER
}
