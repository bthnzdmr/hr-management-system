INSERT INTO department (name) VALUES
    ('Software Development'),
    ('Human Resources'),
    ('Accounting'),
    ('Sales'),
    ('Marketing')
ON CONFLICT (name) DO NOTHING;
