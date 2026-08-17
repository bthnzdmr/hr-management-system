package com.proje.employee.dto;

/**
 * Bir personelin bir yila ait yillik izin bakiyesi.
 *
 * YALNIZCA yillik izin sayilir. Hastalik, ucretsiz ve ebeveyn izni bu haktan
 * dusmez -- farkli haklardir ve tek bir sayida toplanmalari, olmayan bir
 * kesinlik uydurmak olurdu.
 */
public record LeaveBalanceResponse(
        Long employeeId,
        int year,

        /** O yil icin verilen gun. */
        int entitledDays,

        /** Onceki yildan devreden gun. */
        int carriedOverDays,

        /** Onaylanmis yillik izin gunleri. */
        int usedDays,

        /**
         * Karar bekleyen yillik izin gunleri.
         *
         * Bakiyeden DUSULUR: dusulmeseydi iki gunu kalan biri uc ayri iki
         * gunluk talep acabilir ve ucu de onaylanabilir gorunurdu.
         */
        int reservedDays,

        /** entitled + carriedOver - used - reserved. Negatif olabilir. */
        int availableDays,

        /**
         * Hakkin nereden geldigi.
         *
         * "Verilmis hak" ile "yapilandirmadaki varsayilan" ayni ekranda ayni
         * gorunmemeli: ikincisi bir KARAR degil, bir tahmindir.
         */
        Source source
) {

    public enum Source {
        /** Ik tarafindan bu yil icin acikca verilmis. */
        GRANTED,

        /** Kayit yok; yapilandirmadaki varsayilan uygulandi. */
        DEFAULT
    }
}
