-- Talebin gerekcesi ile kararin gerekcesi IKI AYRI OLGUDUR.
--
-- Tek kolon vardi ve reddetme onu UZERINE YAZIYORDU: "hastane randevusu" diye
-- girilen talep, reddedildikten sonra bos kaliyordu. Olculdu -- karar notu
-- verilmediginde alan NULL oluyor ve talep gerekcesi geri donussuz kayboluyordu.
--
-- NULL olabilir: karar verilmemis ya da gerekce yazilmamis istekler gecerlidir
-- ve mevcut satirlarin hepsi bu durumda.
ALTER TABLE leave_request
    ADD COLUMN decision_note VARCHAR(500);

COMMENT ON COLUMN leave_request.note IS
    'Talebi acan kisinin gerekcesi; karar bunu DEGISTIRMEZ.';

COMMENT ON COLUMN leave_request.decision_note IS
    'Karari verenin gerekcesi. Reddetme aciklamasi buraya yazilir.';
