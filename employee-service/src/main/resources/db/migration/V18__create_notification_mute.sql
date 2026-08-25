-- Bildirim tercihleri.
--
-- SAKLANAN SEY YALNIZCA SESSIZE ALMALARDIR: satirin VARLIGI "bu bildirimi
-- gonderme" demektir, yoklugu "gonder". Her personel icin bir tercih satiri
-- tutup icine boolean yazmak da mumkundu ama o zaman "hic dokunmadi" ile
-- "acikca acik birakti" ayni sey olurdu ve varsayilani degistirmek gecmis
-- satirlari da elden gecirmeyi gerektirirdi. Varsayilan ACIK ve kodda yazili;
-- tabloda yalnizca ondan SAPMALAR duruyor.
--
-- MUTABLE OLMAYAN BILDIRIMLER BILEREK BURADA YOK: personel kaydinin
-- degistigini haber veren mail bir SEFFAFLIK sinyalidir -- "senin kaydinda
-- biri degisiklik yapti". Kapatilabilseydi, erisimi olan biri once bildirimi
-- susturup sonra kaydi degistirebilirdi. Her bildirim kapatilabilir olmak
-- zorunda degildir.
CREATE TABLE notification_mute (
    employee_id BIGINT      NOT NULL,
    kind        VARCHAR(32) NOT NULL,

    -- Birlesik birincil anahtar: ayni bildirimi ayni kisi icin iki kez
    -- susturmak veritabani seviyesinde imkansiz. Uygulama kodundaki bir
    -- kontrole guvenmek yaris durumuna aciktir -- `user_role`daki ayni karar.
    CONSTRAINT pk_notification_mute PRIMARY KEY (employee_id, kind),

    -- Personel silinirse tercihleri de gider; tercih tek basina anlamsizdir.
    CONSTRAINT fk_notification_mute_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id) ON DELETE CASCADE,

    -- Kapali kume veritabaninda da yazili: uygulama disindan yazilan bir satir
    -- da kurala uymak zorunda. Enum'a yeni bir deger eklendiginde bu kisit da
    -- degismeli ve bir test bunu tutuyor.
    CONSTRAINT ck_notification_mute_kind
        CHECK (kind IN ('LEAVE_REQUEST', 'LEAVE_DECISION'))
);
