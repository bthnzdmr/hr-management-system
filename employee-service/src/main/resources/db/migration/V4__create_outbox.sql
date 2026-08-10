CREATE TABLE outbox (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id     UUID         NOT NULL,
    event_type   VARCHAR(30)  NOT NULL,
    routing_key  VARCHAR(100) NOT NULL,
    payload      JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts     INTEGER      NOT NULL DEFAULT 0,
    last_error   TEXT,

    CONSTRAINT uk_outbox_event_id UNIQUE (event_id)
);

-- Kismi index: yalnizca gonderilmemis satirlari icerir. Gonderilen satir
-- index'ten duser, boylece tablo buyuse de relay'in bakacagi yer kucuk kalir.
CREATE INDEX idx_outbox_unpublished
    ON outbox (created_at)
    WHERE published_at IS NULL;
