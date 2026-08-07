-- El catálogo/menú digital muestra un mismo plato en varias porciones
-- (1, 2, 4, 6 personas), cada una con su propio precio. El modelo de
-- requisitos representa esto como una fila de producto por porción
-- (mismo nombre, codigo distinto); esta columna guarda cuántas personas
-- rinde esa fila para que el frontend pueda agrupar por nombre y mostrar
-- las porciones juntas, igual que el sitio original.
ALTER TABLE producto ADD COLUMN porcion_personas INTEGER;
