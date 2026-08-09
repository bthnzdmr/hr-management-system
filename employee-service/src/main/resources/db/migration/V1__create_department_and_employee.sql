CREATE TABLE department (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_department_name UNIQUE (name)
);

CREATE TABLE employee (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    email          VARCHAR(255) NOT NULL,
    phone          VARCHAR(20),
    department_id  BIGINT       NOT NULL,
    manager_id     BIGINT,
    job_title      VARCHAR(100) NOT NULL,
    hire_date      DATE         NOT NULL,
    salary         NUMERIC(12,2),
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_employee_email UNIQUE (email),

    CONSTRAINT fk_employee_department
        FOREIGN KEY (department_id) REFERENCES department (id),

    CONSTRAINT fk_employee_manager
        FOREIGN KEY (manager_id) REFERENCES employee (id) ON DELETE SET NULL,

    CONSTRAINT ck_employee_salary
        CHECK (salary IS NULL OR salary >= 0),

    CONSTRAINT ck_employee_manager_not_self
        CHECK (manager_id IS NULL OR manager_id <> id)
);

CREATE INDEX idx_employee_department_id ON employee (department_id);
CREATE INDEX idx_employee_manager_id    ON employee (manager_id);
CREATE INDEX idx_employee_last_name     ON employee (last_name);
