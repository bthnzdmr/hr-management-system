-- Korelasyon kimligi olayla birlikte tasinir.
--
-- CorrelationIdFilter kimligi yalnizca HTTP katmaninda tutuyordu: MDC istegin
-- ipliginde yasar ve cevap donunce temizlenir. OutboxRelay ise ZAMANLAYICI
-- ipliginde calisir ve onun MDC'si bostur -- yani uretici kendi yayin
-- loglarinda bile kimligi basamiyordu, tuketici hic goremiyordu.
--
-- Kimlik satira yazilirsa, isteği yapan iplik ile mesaji yayinlayan iplik
-- arasindaki bosluk kapanir.
ALTER TABLE outbox ADD COLUMN correlation_id VARCHAR(64);

-- NULL olabilir: bu migration'dan ONCE yazilmis satirlarin kimligi yok ve
-- geriye donuk uretilemez. Zorunlu yapmak, mevcut veriyi gecersiz kilardi.
COMMENT ON COLUMN outbox.correlation_id IS
    'Olayi ureten HTTP istegin korelasyon kimligi; migration oncesi satirlarda NULL';
