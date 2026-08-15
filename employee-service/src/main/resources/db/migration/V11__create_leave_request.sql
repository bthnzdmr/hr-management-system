-- GiST index'i araliklar icin && operatorunu bilir ama BIGINT icin = operatorunu
-- bilmez; o btree'nin isidir. btree_gist, btree operatorlerini GiST'e ogretir ve
-- boylece asagidaki kisitta ikisi tek index'te yasayabilir.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE leave_request (
    id           BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- Iznin SAHIBI. Kaydi giren kisi ayri bir kolonda tutulur; ileride oz
    -- servise gecilirse sema degismesin diye ikisi bastan ayrildi.
    employee_id  BIGINT      NOT NULL,
    created_by   BIGINT      NOT NULL,

    leave_type   VARCHAR(20) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- Tarihler iki SADE kolonda tutulur: Hibernate'in daterange diye bir tipi
    -- yoktur ve ozel bir UserType yazmak, JDBC seviyesinde elle serilestirme
    -- bakimi demekti.
    start_date   DATE        NOT NULL,
    end_date     DATE        NOT NULL,

    -- Cakisma kisiti bir ARALIK ister; aralik iki tarihten TURETILIR.
    --
    -- Boylece iki temsil arasinda tutarsizlik yapisal olarak imkansiz: span'i
    -- kimse yazmaz, PostgreSQL uretir. Ayni bilgiyi iki yerde tutmanin projede
    -- tekrarlayan bedeli -- ikisinin zamanla ayrilmasi -- burada odenmiyor.
    --
    -- DIKKAT: daterange varsayilani '[)' -- alt sinir dahil, ust sinir HARIC.
    -- Bu yuzden end_date + 1: "10-15 Mart dahil" [2026-03-10, 2026-03-16) olur.
    -- `+1` ayrintisi YALNIZCA burada; hicbir servis onu hatirlamak zorunda degil.
    span         DATERANGE   GENERATED ALWAYS AS (daterange(start_date, end_date + 1)) STORED,

    note         VARCHAR(500),

    decided_by   BIGINT,
    decided_at   TIMESTAMPTZ,

    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_leave_employee   FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_leave_created_by FOREIGN KEY (created_by)  REFERENCES users (id),
    CONSTRAINT fk_leave_decided_by FOREIGN KEY (decided_by)  REFERENCES users (id),

    -- Kapali kume: serbest metin olsaydi "izin", "Izin" ve "yillik" ayri
    -- seyler sayilir ve raporlama anlamsizlasirdi. Ayrilma sebebindeki karar.
    CONSTRAINT ck_leave_type   CHECK (leave_type IN ('ANNUAL', 'SICK', 'UNPAID', 'PARENTAL')),
    CONSTRAINT ck_leave_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),

    -- Tek gunluk izin gecerlidir (start = end), ters aralik degildir.
    -- Bu kisit ayni zamanda BOS araligi imkansiz kilar: end + 1 daima
    -- start'tan buyuk oldugu icin span hicbir zaman bos olamaz -- bos bir
    -- aralik hicbir seyle kesismez ve cakisma kisitini SESSIZCE atlardi.
    CONSTRAINT ck_leave_dates CHECK (end_date >= start_date),

    -- Karar bilgisi ya tamamen vardir ya hic yoktur; yarim bir duruma
    -- (kim karar verdi belli, ne zaman belli degil) girmek imkansiz olmali.
    CONSTRAINT ck_leave_decision CHECK (
        (decided_by IS NULL AND decided_at IS NULL)
        OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)
    ),

    -- Yalnizca sonuclanmis bir istegin karari olabilir.
    CONSTRAINT ck_leave_decision_status CHECK (
        (status IN ('PENDING', 'CANCELLED') AND decided_by IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND decided_by IS NOT NULL)
    ),

    -- ASIL KISIT: ayni kisinin tarihleri KESISEN iki izni olamaz.
    --
    -- UNIQUE burada ise yaramaz cunku o yalnizca = operatorunu bilir: 10-15 ile
    -- 12-18 farkli degerlerdir, UNIQUE ikisini de kabul ederdi. EXCLUDE
    -- operatoru sectirir ve && "kesisiyor" demektir.
    --
    -- Servis katmaninda "once oku, cakisiyor mu bak, sonra yaz" yazsaydik
    -- klasik check-then-act olusurdu: iki eszamanli istek de "cakisma yok"
    -- gorur ve ikisi de yazardi.
    --
    -- KISMI: reddedilmis veya iptal edilmis bir izin yeni bir izni engellemez.
    CONSTRAINT ex_leave_no_overlap EXCLUDE USING gist (
        employee_id WITH =,
        span WITH &&
    ) WHERE (status IN ('PENDING', 'APPROVED'))
);

-- Kisit yalnizca PENDING/APPROVED satirlarini indeksler; bir personelin BUTUN
-- izinlerini listelemek (reddedilmisler dahil) icin ayri bir index gerekir.
CREATE INDEX idx_leave_employee_id ON leave_request (employee_id);

-- Bekleyen istekleri listeleme ekraninin sorgusu; kismi cunku sonuclanmis
-- satirlar o ekranda hic gorunmez.
CREATE INDEX idx_leave_pending ON leave_request (created_at) WHERE status = 'PENDING';
