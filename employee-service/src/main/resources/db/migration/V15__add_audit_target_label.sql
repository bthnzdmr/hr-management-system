-- Denetim satirinin gosterdigi kaydin, O ANDAKI insan okunur adi.
--
-- Okurken JOIN yapilsaydi bugunku ad gorunurdu; kayit yeniden adlandirilmissa
-- iz yanlis soyler ve hedef kaybolursa ad tamamen kaybolurdu.
--
-- NULL olabilir ve bu zorunlu: migration oncesi satirlarda ad yok ve geriye
-- donuk URETILEMEZ -- bugunku adi gecmise yazmak, tam da kacindigimiz sey.
-- Arayuz o satirlarda id gostermeye devam eder.
ALTER TABLE audit_entry ADD COLUMN target_label VARCHAR(255);
