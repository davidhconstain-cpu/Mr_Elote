-- Menú real de Mr. Elote (RF-025). Datos verificados contra capturas de
-- pantalla del sitio original — ver ../../../../../../src/data/products.js
-- en el frontend, de donde salen estos mismos textos y precios.
--
-- Cada plato con varias porciones (1/2/4/6 personas) se modela como una
-- fila de producto por porción, todas con el mismo nombre y distinto
-- codigo/precio/porcion_personas; el frontend las agrupa por nombre.
--
-- Las imágenes reutilizan las rutas estáticas ya publicadas por el
-- frontend (public/images/*.jpg) en vez de duplicarlas en el backend:
-- no hay razón para subirlas de nuevo por el endpoint de imágenes solo
-- para servir el mismo archivo.

INSERT INTO categoria (nombre, orden, activa) VALUES ('Comida', 1, true);

INSERT INTO producto (categoria_id, codigo, nombre, descripcion, precio, porcion_personas, disponible, activo)
SELECT c.id, v.codigo, v.nombre, v.descripcion, v.precio, v.porcion_personas, true, true
FROM categoria c, (VALUES
    ('DESGRANADO-1P', 'Desgranado', 'Papas a la Francesa, Salchicha Americana, Queso, Maíz, Carne, Pollo, Pepitas y Salsas Artesanales.', 23000.00, 1),
    ('DESGRANADO-2P', 'Desgranado', 'Papas a la Francesa, Salchicha Americana, Queso, Maíz, Carne, Pollo, Pepitas y Salsas Artesanales.', 30000.00, 2),
    ('DESGRANADO-4P', 'Desgranado', 'Papas a la Francesa, Salchicha Americana, Queso, Maíz, Carne, Pollo, Pepitas y Salsas Artesanales.', 50000.00, 4),
    ('SALCHIPAPA-1P', 'Salchipapa', 'Papas a la Francesa, Salchicha Americana, Tocineta, Queso, Maíz, Pollo, Pepitas y Salsas Artesanales.', 18000.00, 1),
    ('SALCHIPAPA-2P', 'Salchipapa', 'Papas a la Francesa, Salchicha Americana, Tocineta, Queso, Maíz, Pollo, Pepitas y Salsas Artesanales.', 25000.00, 2),
    ('SALCHIPAPA-4P', 'Salchipapa', 'Papas a la Francesa, Salchicha Americana, Tocineta, Queso, Maíz, Pollo, Pepitas y Salsas Artesanales.', 40000.00, 4),
    ('MECHADA-2P', 'Mechada', 'Carne Desmechada, Maduro, Salchicha Americana, Papas a la Francesa, Pico de Gallo y Salsas Artesanales.', 45000.00, 2),
    ('CALLEJERA-2P', 'Callejera', 'Chorizo, Chicharrón, Yuca, Queso, Papas a la Francesa y Salsas Artesanales.', 45000.00, 2),
    ('ABORRAJADA-2P', 'Aborrajada', 'Papas a la Francesa, Salchicha Ranchera, Carne Angus, Madurito, Queso y Salsas Artesanales.', 42000.00, 2),
    ('CHICHARRONUDA-2P', 'Chicharronuda', 'Papas a la Francesa, Queso, Salchicha Americana, Chicharrón Carnudo y Salsas Artesanales.', 40000.00, 2),
    ('RANCHI-NUGGETS-2P', 'Ranchi Nuggets', 'Papas a la Francesa, Queso, Salchicha Ranchera, Nuggets de Pollo y Salsas Artesanales.', 35000.00, 2),
    ('NACHOS-2P', 'Nachos', 'Nachos con Queso, Pollo, Carne, Pico de Gallo y Salsas Artesanales.', 35000.00, 2),
    ('DESGRANADO-ESP-6P', 'Desgranado Especial', 'Carne, Pollo, Salchicha, Maduro, Maicitos, Papas a la Francesa, Queso, Ripio, Pollo Apanado, Chicharrón y Salsas Artesanales.', 80000.00, 6),
    ('PORCION-CHICHARRON-1P', 'Porción de Chicharrón', 'Papas a la Francesa y Chicharrón Carnudo.', 22000.00, 1),
    ('MADURITA-1P', 'Madurita', 'Maduro Guayabo, Queso, Carne Desmechada y Salchicha Ranchera.', 22000.00, 1),
    ('BURRITO-1P', 'Burrito', 'Pollo, Carne, Maíz, Tocineta, Lechuga, Tortillas, Salsas Artesanales y Porción de Papas.', 25000.00, 1)
) AS v(codigo, nombre, descripcion, precio, porcion_personas)
WHERE c.nombre = 'Comida';

INSERT INTO imagen_producto (producto_id, url, orden)
SELECT p.id, v.url, 0
FROM producto p, (VALUES
    ('CALLEJERA-2P', '/images/callejera.jpg'),
    ('ABORRAJADA-2P', '/images/aborrajada.jpg'),
    ('CHICHARRONUDA-2P', '/images/chicharronuda.jpg'),
    ('RANCHI-NUGGETS-2P', '/images/ranchi-nuggets.jpg'),
    ('NACHOS-2P', '/images/nachos.jpg'),
    ('DESGRANADO-ESP-6P', '/images/desgranado-especial.jpg'),
    ('PORCION-CHICHARRON-1P', '/images/porcion-chicharron.jpg'),
    ('MADURITA-1P', '/images/madurita.jpg'),
    ('BURRITO-1P', '/images/burrito.jpg')
) AS v(codigo, url)
WHERE p.codigo = v.codigo;
