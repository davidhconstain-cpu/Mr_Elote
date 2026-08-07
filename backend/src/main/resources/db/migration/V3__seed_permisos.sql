-- Permisos base de ejemplo (RF-038, RNF-004). El control grueso por rol ya
-- lo aplica @PreAuthorize en cada endpoint; esta tabla habilita permisos
-- finos adicionales dentro de un mismo rol si el negocio lo pide más
-- adelante.
INSERT INTO permiso (codigo, descripcion) VALUES
    ('pedido.crear', 'Crear pedidos'),
    ('pedido.anular', 'Anular pedidos'),
    ('pedido.modificar_items', 'Modificar ítems de un pedido existente'),
    ('producto.editar', 'Editar productos y precios'),
    ('producto.cambiar_disponibilidad', 'Marcar productos disponibles/agotados'),
    ('caja.abrir', 'Abrir caja'),
    ('caja.cerrar', 'Cerrar caja'),
    ('usuario.administrar', 'Crear y editar usuarios de staff'),
    ('auditoria.consultar', 'Consultar la bitácora de auditoría'),
    ('informes.consultar', 'Consultar informes y dashboard');
