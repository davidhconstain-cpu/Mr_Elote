-- Todavía no hay zonas ni tarifas reales de domicilio confirmadas para
-- Mr. Elote (RF-030). Se siembra una zona de ejemplo para que el flujo de
-- domicilio del catálogo sea funcional y probable de extremo a extremo;
-- reemplázala con las zonas y tarifas reales vía
-- POST /api/v1/zonas-domicilio y PATCH /api/v1/zonas-domicilio/{id}/tarifa.
INSERT INTO zona_domicilio (nombre, activa) VALUES ('Zona local (por confirmar)', true);

INSERT INTO tarifa_domicilio (zona_domicilio_id, tarifa, tiempo_estimado_minutos, vigente_desde)
SELECT id, 5000.00, 30, now() FROM zona_domicilio WHERE nombre = 'Zona local (por confirmar)';
