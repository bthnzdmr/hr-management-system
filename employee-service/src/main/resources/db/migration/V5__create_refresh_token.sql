CREATE TABLE refresh_token (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Jetonun KENDISI degil, SHA-256 ozeti saklanir: veritabani sizarsa
    -- her oturum ele gecirilmis olmasin. Ozet 64 onaltilik karakterdir.
    -- CHAR(64) degil: PostgreSQL'de char(n) hiz kazandirmaz, degeri bosluklarla
    -- doldurur ve belgeleri kullanilmamasini onerir.
    token_hash VARCHAR(64) NOT NULL,
    user_id    BIGINT      NOT NULL,
    issued_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    -- Zaman damgasi, boolean degil: iptalin NE ZAMAN oldugu incelemede gerekir.
    revoked_at TIMESTAMPTZ,

    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),

    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Kullanicinin tum oturumlarini kapatmak icin gereken sorgu: tekrar kullanim
-- tespit edildiginde o kullanicinin butun jetonlari iptal edilir.
CREATE INDEX idx_refresh_token_user_active
    ON refresh_token (user_id)
    WHERE revoked_at IS NULL;
