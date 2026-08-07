-- Todavía no hay mesas reales de Mr. Elote registradas (RF-004). Se
-- siembran unas mesas de ejemplo con su QR activo para que el flujo de
-- pedido "local" (RF-005, escanear QR en la mesa y pedir sin login) sea
-- probable de extremo a extremo; reemplázalas con las mesas reales vía
-- POST /api/v1/mesas (el código QR se regenera con
-- POST /api/v1/mesas/{id}/qr/regenerar).
INSERT INTO mesa (numero, capacidad, activa) VALUES
    ('1', 4, true),
    ('2', 4, true),
    ('3', 6, true);

INSERT INTO qr_mesa (mesa_id, codigo, activo)
SELECT id, 'demo-mesa-' || numero, true FROM mesa WHERE numero IN ('1', '2', '3');
