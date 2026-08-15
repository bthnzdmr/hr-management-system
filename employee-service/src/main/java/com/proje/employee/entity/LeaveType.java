package com.proje.employee.entity;

/**
 * Izin turu.
 *
 * Serbest metin DEGIL kapali bir kume: "yillik", "Yillik" ve "yillik izin" ayri
 * seyler sayilirdi ve tur bazli hicbir rapor anlamli olmazdi. Ayrilma sebebinde
 * verilen kararin aynisi; kume veritabaninda CHECK kisitiyla da yazili.
 */
public enum LeaveType {

    /** Yillik ucretli izin. */
    ANNUAL,

    /** Hastalik izni. */
    SICK,

    /** Ucretsiz izin. */
    UNPAID,

    /** Dogum / ebeveyn izni. */
    PARENTAL
}
