-- Guvenlik acisindan anlamli yazmalarin denetim izi.
--
-- Denetim raporundaki iki bulgu -- "SYSTEM_ADMIN kendine HR_SPECIALIST
-- verebiliyor" ve "ayrilmis personele hesap acilabiliyor" -- yalnizca MUMKUN
-- olduklari icin degil, gerceklestiklerinde KIMSENIN FARK EDEMEYECEGI icin
-- ciddiydi. "Kim, kime, ne zaman rol verdi" sorusunun cevabi hicbir yerde yoktu.
--
-- Gercek bir Ik sisteminde denetim izi ozellik degil zorunluluktur: KVKK/GDPR
-- "kisisel veriye kim eristi" sorusunu sorar.
CREATE TABLE audit_entry
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- Islemi yapan. E-POSTA saklanir, kullanici id'si degil: hesap silinse
    -- veya e-posta degisse bile kaydin anlami korunmalidir. Denetim izi
    -- gecmisin fotografidir, canli veriye referans degil.
    actor         VARCHAR(255) NOT NULL,

    -- Ne yapildi: ROLES_CHANGED, ACCOUNT_CREATED, EMPLOYEE_TERMINATED...
    action        VARCHAR(50)  NOT NULL,

    -- Neye yapildi. Tur + kimlik ayri tutulur ki "su personelin gecmisi"
    -- sorgusu index'ten faydalanabilsin.
    target_type   VARCHAR(50)  NOT NULL,
    target_id     VARCHAR(64),

    -- Insan tarafindan okunacak ozet: "EMPLOYEE -> EMPLOYEE, HR_SPECIALIST".
    -- Yapisal JSON degil cunku bu tablo SORGULANMAK icin degil OKUNMAK icin.
    detail        VARCHAR(500),

    -- Istegin izi. Denetim kaydi ile uygulama loglari bu kolon uzerinden
    -- birlestirilir.
    correlation_id VARCHAR(64),

    occurred_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- "Bu kaydi kim degistirdi" en sik sorulan soru.
CREATE INDEX idx_audit_entry_target ON audit_entry (target_type, target_id, occurred_at DESC);

-- "Son ne oldu" -- panelde hareket akisi ve olay incelemesi icin.
CREATE INDEX idx_audit_entry_occurred_at ON audit_entry (occurred_at DESC);

-- "Bu kullanici neler yapti" -- bir hesabin ele gecirildigi suphesinde ilk sorgu.
CREATE INDEX idx_audit_entry_actor ON audit_entry (actor, occurred_at DESC);
