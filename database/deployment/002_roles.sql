INSERT INTO public.roles (name, description)
VALUES
    ('ADMIN', 'System administrator'),
    ('TECHNICIAN', 'Repair technician'),
    ('FRONT_DESK', 'Front desk staff')
ON CONFLICT (name) DO NOTHING;