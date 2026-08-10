-- Ayni olay birden fazla kez teslim edilebilir (en az bir kez teslim garantisi).
-- Mukerrer mail gitmemesini saglayan sey buradaki birincil anahtardir: ikinci
-- kayit denemesi veritabani tarafindan reddedilir. "Once sorgula, yoksa ekle"
-- yaklasimi iki tuketici ayni anda calistiginda yarisa acik kalirdi.
CREATE TABLE processed_event (
    event_id     UUID        PRIMARY KEY,
    event_type   VARCHAR(30) NOT NULL,
    employee_id  BIGINT      NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
