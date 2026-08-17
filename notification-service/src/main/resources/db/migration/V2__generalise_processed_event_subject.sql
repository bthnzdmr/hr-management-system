-- Tablo artik yalnizca personel olaylarini tasimiyor: hesap olaylarinin
-- (parola sifirlama) personeli yoktur, kullanicisi vardir. Kolonu NULL'a
-- acmak yerine ADI duzeltiliyor -- bir kullanici id'si tasiyan "employee_id",
-- yalan soyleyen bir isim olurdu.
--
-- Yeniden adlandirma mevcut satirlari korur; NOT NULL kalir cunku her olayin
-- bir oznesi vardir.
ALTER TABLE processed_event RENAME COLUMN employee_id TO subject_id;
