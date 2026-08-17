-- Iyimser kilitleme icin surum kolonu.
--
-- Kapatilan kusur KAYIP GUNCELLEME: iki Ik uzmani ayni personeli acip biri
-- telefonu, digeri unvani degistirdiginde sonra kaydeden digerinin
-- degisikligini SESSIZCE geri aliyordu. Guncelleme butun alanlari istekten
-- yazdigi icin kayip tek alanla da sinirli kalmiyordu.
--
-- Neden iyimser, kotumser degil: personel duzenleme NADIREN cakisir. Kilit
-- tutmak her duzenlemede bedel odemek olurdu; surum kontrolu bedeli yalnizca
-- cakisma aninda oder. Son yonetici kuralinda kotumser kilit dogruydu -- orada
-- cakisma kuraldi, burada istisna.
--
-- DEFAULT 0 sart: dolu bir tabloya varsayilansiz NOT NULL kolon eklenemez.
ALTER TABLE employee   ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE department ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Izin talebi de ayni yaristan etkilenir: iki yonetici ayni talebi ayni anda
-- sonuclandirabilir. Servis "zaten karara baglanmis" kontrolu yapiyor ama o
-- kontrol ile yazma arasinda bir pencere var -- klasik check-then-act.
ALTER TABLE leave_request ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
