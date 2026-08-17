-- Yillik izin hakkı.
--
-- NEDEN SAKLANIYOR, HESAPLANMIYOR?
--
-- Hak, ise giris tarihi ve kidem kurallarindan TURETILEBILIRDI. Turetilseydi
-- kurallar degistigi gun GECMIS YILLARIN bakiyesi de sessizce degisirdi:
-- 2024'te 14 gun hakki olan biri, 2026'da kural degisince geriye donuk 20 gun
-- hakli gorunurdu. Gecmis bir olgudur ve yerinde durmalidir -- ayni gerekceyle
-- `terminated_at` de saklaniyor, "pasif" olmasindan cikarilmiyor.
--
-- Satir yoksa yapilandirmadaki varsayilan uygulanir ve cevap hangi kaynagin
-- kullanildigini ACIKCA soyler; "verilmis hak" ile "varsayilan" ayni ekranda
-- ayni gorunmez.
CREATE TABLE leave_entitlement (
    id                BIGSERIAL PRIMARY KEY,
    employee_id       BIGINT   NOT NULL REFERENCES employee (id),
    year              INTEGER  NOT NULL,

    -- O yil icin verilen gun sayisi.
    entitled_days     INTEGER  NOT NULL CHECK (entitled_days >= 0),

    -- Onceki yildan devreden. Ayri kolon, cunku "bu yil kac gun hak ettin" ile
    -- "gecen yildan kac gun kaldi" farkli sorulardir ve raporlamada ayrilir.
    carried_over_days INTEGER  NOT NULL DEFAULT 0 CHECK (carried_over_days >= 0),

    note              VARCHAR(255),

    version           BIGINT   NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,

    -- Bir kisiye ayni yil icin iki hak satiri yazilamaz. Garanti uygulamada
    -- degil VERITABANINDA: "once oku, var mi bak, sonra yaz" iki es zamanli
    -- istekte iki satir uretirdi.
    CONSTRAINT uk_leave_entitlement_employee_year UNIQUE (employee_id, year)
);

-- Bakiye sorgusu daima (employee_id, year) ile geliyor; benzersizlik kisiti
-- zaten bu index'i uretiyor, ayrica bir index eklenmiyor.
