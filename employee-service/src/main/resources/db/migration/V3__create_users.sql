CREATE TABLE users (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(72)  NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    employee_id    BIGINT,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_users_email       UNIQUE (email),
    CONSTRAINT uk_users_employee_id UNIQUE (employee_id),

    CONSTRAINT fk_users_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id),

    CONSTRAINT ck_users_role
        CHECK (role IN ('ADMIN', 'USER'))
);
