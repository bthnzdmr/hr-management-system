CREATE TABLE password_reset_token (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- refresh_token ile ayni aile: jetonun KENDISI degil SHA-256 ozeti
    -- saklanir. Ozet 64 onaltilik karakterdir; char(n) degil varchar(64),
    -- gerekcesi V5'te.
    token_hash VARCHAR(64) NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    -- Tek kullanimlik: zaman damgasi, boolean degil. Jetonun NE ZAMAN
    -- kullanildigi incelemede gerekir.
    used_at    TIMESTAMPTZ,

    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash),

    CONSTRAINT fk_password_reset_token_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Sifirlama basarili oldugunda o kullanicinin BEKLEYEN butun jetonlari duser.
CREATE INDEX idx_password_reset_token_user_active
    ON password_reset_token (user_id)
    WHERE used_at IS NULL;

-- Suresi dolan satirlarin temizligi tablonun tamamini tarar; kismi index
-- yazilamaz cunku now() sabit degildir. Ayni karar V10'da alinmisti.
CREATE INDEX idx_password_reset_token_expires_at
    ON password_reset_token (expires_at);
