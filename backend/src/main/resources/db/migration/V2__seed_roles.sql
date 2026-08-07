-- Roles del sistema (sección 5 de la especificación de requisitos).
-- Los nombres son la fuente de verdad para las autoridades de Spring
-- Security (ver com.mrelote.pedidos.security.RoleNames).
INSERT INTO rol (nombre, descripcion) VALUES
    ('Cliente', 'Ve el menú, pide, paga y consulta su historial.'),
    ('Mesero', 'Gestiona mesas, toma pedidos y cambios autorizados.'),
    ('Caja', 'Valida pagos y administra caja y cierre.'),
    ('Cocina', 'Ve pedidos habilitados, prepara y marca listos.'),
    ('Despachos', 'Gestiona pedidos de domicilio y su entrega.'),
    ('Administrador', 'Administra catálogo, usuarios, mesas, domicilios, informes y auditoría.');

INSERT INTO metodo_pago (nombre) VALUES
    ('Efectivo'), ('Tarjeta'), ('PSE'), ('Nequi');
