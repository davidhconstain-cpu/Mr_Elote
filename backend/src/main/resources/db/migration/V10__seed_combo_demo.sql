-- Demo para poder probar de extremo a extremo la sección de combos del
-- catálogo (RF-027). No es un combo real confirmado por Mr. Elote —
-- reemplázalo vía POST /combos y PUT /combos/{id}/componentes cuando se
-- confirme la oferta real.
INSERT INTO combo (nombre, descripcion, precio, activo) VALUES
    ('Combo Callejera + Chicharrón', 'Una Callejera para 2 personas más una porción de Chicharrón.', 60000.00, true);

INSERT INTO combo_detalle (combo_id, producto_id, grupo, cantidad, permite_extras)
SELECT co.id, p.id, NULL, 1, false
FROM combo co, producto p
WHERE co.nombre = 'Combo Callejera + Chicharrón' AND p.codigo = 'CALLEJERA-2P'
UNION ALL
SELECT co.id, p.id, NULL, 1, false
FROM combo co, producto p
WHERE co.nombre = 'Combo Callejera + Chicharrón' AND p.codigo = 'PORCION-CHICHARRON-1P';
