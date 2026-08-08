-- Usuarios de staff de demo para poder probar el panel de staff de
-- extremo a extremo (no había ninguno sembrado, ver README: "no hay
-- ningún usuario administrador sembrado por defecto"). Contraseña para
-- los 5: "password123". Cámbiala antes de usar esto en un ambiente real.
INSERT INTO usuario (rol_id, nombre, email, password_hash, activo)
SELECT r.id, v.nombre, v.email, '$2a$10$7LRI1VJQbon1xlW/fTF6LOm04K/PwrfsX6CHYddJIE/Mcjw4zJ9n2', true
FROM rol r, (VALUES
    ('Administrador', 'Admin Demo', 'admin@mrelote.com'),
    ('Cocina', 'Cocina Demo', 'cocina@mrelote.com'),
    ('Caja', 'Caja Demo', 'caja@mrelote.com'),
    ('Despachos', 'Despachos Demo', 'despachos@mrelote.com'),
    ('Mesero', 'Mesero Demo', 'mesero@mrelote.com')
) AS v(rol_nombre, nombre, email)
WHERE r.nombre = v.rol_nombre;
