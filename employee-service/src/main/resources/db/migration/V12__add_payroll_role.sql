-- Ucret yazma yetkisi HR_SPECIALIST'ten ayriliyor.
--
-- Ayni rol hem personel kaydi aciyor hem ucret yaziyordu; bu, SAP'nin gorevler
-- ayriligi katalogundaki HCM-01 deseninin ta kendisi: bir kullanici sahte bir
-- personel acip ucret atayabilir. Kural, bir rolun KENDI ICINDE catisma
-- barindirmamasidir.
ALTER TABLE user_role DROP CONSTRAINT ck_user_role_role;

ALTER TABLE user_role ADD CONSTRAINT ck_user_role_role
    CHECK (role IN ('EMPLOYEE', 'MANAGER', 'HR_SPECIALIST', 'PAYROLL_SPECIALIST',
                    'SYSTEM_ADMIN', 'SERVICE'));
