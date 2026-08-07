-- Demo para poder probar de extremo a extremo la personalización de un
-- ítem (RF-026): una opción obligatoria (tamaño) y dos adicionales,
-- asignados a un par de productos reales. No son datos confirmados por
-- Mr. Elote — reemplázalos vía POST /opciones, /adicionales y
-- POST /productos/{id}/opciones|/adicionales cuando se confirmen los
-- reales.

INSERT INTO opcion (nombre, tipo) VALUES ('Tamaño', 'unica');

INSERT INTO valor_opcion (opcion_id, nombre, precio_adicional)
SELECT id, 'Normal', 0 FROM opcion WHERE nombre = 'Tamaño'
UNION ALL
SELECT id, 'Grande', 3000 FROM opcion WHERE nombre = 'Tamaño';

INSERT INTO adicional (nombre, precio, disponible) VALUES
    ('Queso extra', 2000, true),
    ('Tocineta extra', 3000, true);

INSERT INTO producto_opcion (producto_id, opcion_id, obligatoria)
SELECT p.id, o.id, true
FROM producto p, opcion o
WHERE p.codigo = 'NACHOS-2P' AND o.nombre = 'Tamaño';

INSERT INTO producto_adicional (producto_id, adicional_id)
SELECT p.id, a.id
FROM producto p, adicional a
WHERE p.codigo IN ('NACHOS-2P', 'BURRITO-1P') AND a.nombre IN ('Queso extra', 'Tocineta extra');
