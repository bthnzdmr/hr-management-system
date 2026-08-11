-- Kullanici basina TEK rol yetmiyordu: ayni kisi hem Ik uzmani hem sistem
-- yoneticisi olabilir ve bu iki is birbirinden farklidir.
CREATE TABLE user_role (
    user_id BIGINT      NOT NULL,
    role    VARCHAR(20) NOT NULL,

    -- Birlesik birincil anahtar: ayni rolun ayni kisiye iki kez verilmesi
    -- veritabani seviyesinde imkansiz. Uygulama kodundaki bir kontrole
    -- guvenmek yaris durumuna aciktir.
    CONSTRAINT pk_user_role PRIMARY KEY (user_id, role),

    -- Hesap silinirse rolleri de gider; rol tek basina anlamsizdir.
    CONSTRAINT fk_user_role_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT ck_user_role_role
        CHECK (role IN ('EMPLOYEE', 'MANAGER', 'HR_SPECIALIST', 'SYSTEM_ADMIN', 'SERVICE'))
);

-- Mevcut veri tasiniyor. ADMIN iki isi birden yapiyordu, dolayisiyla IKI
-- role birden karsilik gelir; bolmek bu migration'in asil isidir.
INSERT INTO user_role (user_id, role)
SELECT id, 'HR_SPECIALIST' FROM users WHERE role = 'ADMIN';

INSERT INTO user_role (user_id, role)
SELECT id, 'SYSTEM_ADMIN' FROM users WHERE role = 'ADMIN';

INSERT INTO user_role (user_id, role)
SELECT id, 'EMPLOYEE' FROM users WHERE role = 'USER';

-- Kolon BIRAKILMAZ, silinir: iki kaynak birden dursaydi biri sessizce
-- eskir ve hangisinin dogru oldugu belirsizlesirdi.
ALTER TABLE users DROP COLUMN role;

-- Rol bazli sorgular icin (ornegin "aktif sistem yoneticileri").
CREATE INDEX idx_user_role_role ON user_role (role);
