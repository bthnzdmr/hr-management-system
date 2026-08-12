-- Kimin pasif oldugunu biliyorduk ama NE ZAMAN pasiflestigini bilmiyorduk.
-- Devir orani (turnover) tam olarak bu bilgiye dayanir: "bu ay kac kisi ayrildi".
ALTER TABLE employee
    ADD COLUMN terminated_at      DATE,
    ADD COLUMN termination_reason VARCHAR(30);

-- is_active ile bu iki kolonun birbirini yalanlamasi ONLENIR.
--
-- Kolonlari eklemek yetmez: kod bir gun pasiflestirirken tarihi yazmayi
-- unutursa veri sessizce tutarsizlasir ve devir orani yanlis cikar. Kural
-- veritabaninda dururken unutulamaz.
--
-- Aktif personelin cikis tarihi OLAMAZ; pasif personelin ise OLMAK ZORUNDA
-- degildir -- bu migration'dan onceki kayitlar tarihsizdir ve onlari uydurmak
-- veriyi kirletmek olurdu.
ALTER TABLE employee
    ADD CONSTRAINT ck_employee_termination
        CHECK (is_active = FALSE OR terminated_at IS NULL);

ALTER TABLE employee
    ADD CONSTRAINT ck_employee_termination_reason
        CHECK (termination_reason IS NULL
               OR termination_reason IN ('RESIGNED', 'DISMISSED', 'RETIRED',
                                         'END_OF_CONTRACT', 'OTHER'));

-- Devir orani sorgusu aya gore gruplar; index kismidir cunku aktif personelin
-- bu kolonu daima NULL'dur ve index'te yer kaplamasinin anlami yoktur.
CREATE INDEX idx_employee_terminated_at
    ON employee (terminated_at)
    WHERE terminated_at IS NOT NULL;
