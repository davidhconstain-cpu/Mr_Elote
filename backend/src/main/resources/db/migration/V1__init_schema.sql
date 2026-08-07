-- Sistema de Pedidos Mr. Elote — esquema relacional (PostgreSQL)
-- Basado en: Especificación de Requisitos del Producto v1.1
-- Los comentarios "RF-xxx" / "RN-xxx" referencian los requisitos funcionales
-- y reglas de negocio del documento que cada tabla/columna implementa.

-- =========================================================
-- 1. IDENTIDAD Y ACCESO
-- =========================================================

CREATE TABLE rol (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre          TEXT NOT NULL UNIQUE, -- Cliente, Mesero, Caja, Cocina, Despachos, Administrador
    descripcion     TEXT
);

CREATE TABLE permiso (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo          TEXT NOT NULL UNIQUE, -- ej. 'pedido.crear', 'producto.editar'
    descripcion     TEXT
);

-- RF-038, RNF-004: permisos finos por rol (RBAC)
CREATE TABLE rol_permiso (
    rol_id          BIGINT NOT NULL REFERENCES rol(id),
    permiso_id      BIGINT NOT NULL REFERENCES permiso(id),
    PRIMARY KEY (rol_id, permiso_id)
);

-- Toda persona con acceso al sistema (cliente o staff) es un usuario.
-- RF-002, RF-003, RNF-003, RNF-004
CREATE TABLE usuario (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rol_id          BIGINT NOT NULL REFERENCES rol(id),
    nombre          TEXT NOT NULL,
    email           TEXT UNIQUE,
    telefono        TEXT,
    password_hash   TEXT, -- NULL para pedidos de QR sin registro (no aplica usuario)
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_usuario_rol ON usuario(rol_id);

-- Datos propios de clientes (1:1 con usuario cuando rol = Cliente).
-- RF-040: historial de pedidos por cliente.
CREATE TABLE cliente (
    usuario_id      BIGINT PRIMARY KEY REFERENCES usuario(id),
    fecha_nacimiento DATE,
    notas           TEXT
);

CREATE TABLE zona_domicilio (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre          TEXT NOT NULL UNIQUE, -- barrio/zona, ej. "Zona 1"
    activa          BOOLEAN NOT NULL DEFAULT TRUE
);

-- RF-030: tarifas de domicilio por zona, versionadas en el tiempo para no
-- perder el histórico cuando cambia el precio (ver RN-005 y el pedido que
-- guarda la tarifa aplicada, no una referencia viva).
CREATE TABLE tarifa_domicilio (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    zona_domicilio_id       BIGINT NOT NULL REFERENCES zona_domicilio(id),
    tarifa                  NUMERIC(12,2) NOT NULL CHECK (tarifa >= 0),
    tiempo_estimado_minutos INTEGER,
    vigente_desde           TIMESTAMPTZ NOT NULL DEFAULT now(),
    vigente_hasta           TIMESTAMPTZ -- NULL = tarifa vigente actual
);
CREATE INDEX idx_tarifa_domicilio_zona ON tarifa_domicilio(zona_domicilio_id);

CREATE TABLE direccion (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cliente_id          BIGINT NOT NULL REFERENCES cliente(usuario_id),
    zona_domicilio_id   BIGINT REFERENCES zona_domicilio(id),
    etiqueta            TEXT, -- "Casa", "Trabajo"
    direccion_texto     TEXT NOT NULL,
    referencia          TEXT,
    predeterminada      BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_direccion_cliente ON direccion(cliente_id);

-- =========================================================
-- 2. MESAS Y QR
-- =========================================================

-- RF-024
CREATE TABLE mesa (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero      TEXT NOT NULL UNIQUE,
    capacidad   INTEGER,
    activa      BOOLEAN NOT NULL DEFAULT TRUE
);

-- RF-004, RF-005: identificar mesa y pedir sin login.
-- Se permite regenerar el QR sin perder el histórico (sección 17): cada
-- regeneración inserta una fila nueva y desactiva la anterior en vez de
-- sobrescribirla.
CREATE TABLE qr_mesa (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mesa_id     BIGINT NOT NULL REFERENCES mesa(id),
    codigo      TEXT NOT NULL UNIQUE, -- token único usado en la URL del QR
    activo      BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_qr_mesa_mesa ON qr_mesa(mesa_id);
-- Solo puede haber un QR activo por mesa a la vez.
CREATE UNIQUE INDEX uq_qr_mesa_activo ON qr_mesa(mesa_id) WHERE activo;

-- =========================================================
-- 3. CATÁLOGO: PRODUCTOS, OPCIONES, ADICIONALES, COMBOS
-- =========================================================

-- RF-025
CREATE TABLE categoria (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre  TEXT NOT NULL UNIQUE,
    orden   INTEGER NOT NULL DEFAULT 0,
    activa  BOOLEAN NOT NULL DEFAULT TRUE
);

-- RF-025, RF-028, RF-029, RN-004: el producto agotado permanece visible
-- (no se borra ni se oculta) pero `disponible = false` impide agregarlo
-- al carrito.
CREATE TABLE producto (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    categoria_id    BIGINT NOT NULL REFERENCES categoria(id),
    codigo          TEXT NOT NULL UNIQUE,
    nombre          TEXT NOT NULL,
    descripcion     TEXT,
    precio          NUMERIC(12,2) NOT NULL CHECK (precio >= 0),
    disponible      BOOLEAN NOT NULL DEFAULT TRUE, -- RN-004
    activo          BOOLEAN NOT NULL DEFAULT TRUE,  -- baja lógica del catálogo
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_producto_categoria ON producto(categoria_id);
CREATE INDEX idx_producto_disponible ON producto(disponible) WHERE activo;

CREATE TABLE imagen_producto (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    producto_id BIGINT NOT NULL REFERENCES producto(id),
    url         TEXT NOT NULL,
    orden       INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_imagen_producto ON imagen_producto(producto_id);

-- RF-026: p.ej. "Término de cocción", "Tamaño".
CREATE TABLE opcion (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre  TEXT NOT NULL,
    tipo    TEXT NOT NULL CHECK (tipo IN ('unica', 'multiple'))
);

-- p.ej. "Término medio" (+0), "Tamaño grande" (+3000).
CREATE TABLE valor_opcion (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    opcion_id           BIGINT NOT NULL REFERENCES opcion(id),
    nombre              TEXT NOT NULL,
    precio_adicional    NUMERIC(12,2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_valor_opcion ON valor_opcion(opcion_id);

CREATE TABLE producto_opcion (
    producto_id BIGINT NOT NULL REFERENCES producto(id),
    opcion_id   BIGINT NOT NULL REFERENCES opcion(id),
    obligatoria BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (producto_id, opcion_id)
);

-- RF-026: adicionales tipo "Extra queso".
CREATE TABLE adicional (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre      TEXT NOT NULL,
    precio      NUMERIC(12,2) NOT NULL CHECK (precio >= 0),
    disponible  BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE producto_adicional (
    producto_id     BIGINT NOT NULL REFERENCES producto(id),
    adicional_id    BIGINT NOT NULL REFERENCES adicional(id),
    PRIMARY KEY (producto_id, adicional_id)
);

-- RF-027: el combo es una entidad propia del catálogo (no un producto
-- duplicado); referencia sus componentes en combo_detalle.
CREATE TABLE combo (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre          TEXT NOT NULL,
    descripcion     TEXT,
    precio          NUMERIC(12,2) NOT NULL CHECK (precio >= 0),
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Regla de disponibilidad de combos (v1.1, sección 11):
-- - Componentes con `grupo` NULL son obligatorios y fijos: si su producto
--   se agota, el combo se agota.
-- - Componentes que comparten el mismo `grupo` (ej. 'bebida') son
--   alternativas entre sí: el combo solo se agota si TODOS los productos
--   de ese grupo están agotados.
-- La disponibilidad del combo se recalcula (vista o trigger) cada vez que
-- cambia `producto.disponible`, no solo al crear el combo.
CREATE TABLE combo_detalle (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    combo_id        BIGINT NOT NULL REFERENCES combo(id),
    producto_id     BIGINT NOT NULL REFERENCES producto(id),
    grupo           TEXT, -- NULL = componente fijo obligatorio
    cantidad        INTEGER NOT NULL DEFAULT 1,
    permite_extras  BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_combo_detalle_combo ON combo_detalle(combo_id);
CREATE INDEX idx_combo_detalle_producto ON combo_detalle(producto_id);

-- Vista de apoyo para RF-028/RN-004 aplicado a combos: un combo está
-- disponible si cada grupo de componentes tiene al menos un producto
-- disponible (los componentes fijos, grupo NULL, se tratan como su propio
-- grupo de un solo elemento).
CREATE VIEW combo_disponibilidad AS
SELECT
    c.id AS combo_id,
    c.activo AND NOT EXISTS (
        SELECT 1
        FROM (
            SELECT COALESCE(cd.grupo, 'fijo-' || cd.id::text) AS grupo_clave,
                   bool_or(p.disponible) AS grupo_tiene_disponible
            FROM combo_detalle cd
            JOIN producto p ON p.id = cd.producto_id
            WHERE cd.combo_id = c.id
            GROUP BY grupo_clave
        ) grupos
        WHERE NOT grupo_tiene_disponible
    ) AS disponible
FROM combo c;

-- =========================================================
-- 4. PEDIDOS
-- =========================================================

-- RF-006, RF-007, RF-014, RN-001, RN-003: el pedido nunca se borra
-- físicamente y conserva sus totales históricos aunque cambie el catálogo.
CREATE TABLE pedido (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo                        TEXT NOT NULL CHECK (tipo IN ('local', 'recoger', 'domicilio')),
    canal                       TEXT NOT NULL CHECK (canal IN ('web', 'qr', 'mesero', 'whatsapp')),
    estado                      TEXT NOT NULL DEFAULT 'creado' CHECK (estado IN (
                                    'creado', 'pago_pendiente', 'pago_validado',
                                    'en_preparacion', 'listo',
                                    'entregado', 'recogido', 'despachado',
                                    'rechazado', 'devuelto', 'no_recibido', 'anulado'
                                )),
    cliente_id                  BIGINT REFERENCES cliente(usuario_id), -- NULL: QR/mesero sin cliente registrado
    mesa_id                     BIGINT REFERENCES mesa(id),            -- solo tipo = 'local'
    direccion_id                BIGINT REFERENCES direccion(id),       -- solo tipo = 'domicilio', referencia informativa
    -- snapshot de domicilio: RN-005, sección 18 — el pedido guarda la
    -- tarifa aplicada, no una referencia viva a tarifa_domicilio.
    direccion_texto_snapshot    TEXT,
    zona_domicilio_snapshot     TEXT,
    domicilio                   NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (domicilio >= 0), -- RN-006: $0 en recoger
    subtotal                    NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_adicionales           NUMERIC(12,2) NOT NULL DEFAULT 0,
    descuento                   NUMERIC(12,2) NOT NULL DEFAULT 0,
    total                       NUMERIC(12,2) NOT NULL DEFAULT 0,
    observaciones               TEXT,
    creado_por                  BIGINT REFERENCES usuario(id), -- cliente, mesero o cuenta "sistema"
    creado_en                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pedido_estado ON pedido(estado);
CREATE INDEX idx_pedido_tipo ON pedido(tipo);
CREATE INDEX idx_pedido_cliente ON pedido(cliente_id);
CREATE INDEX idx_pedido_creado_en ON pedido(creado_en);

-- RF-013, RN-003: cada línea guarda el nombre y precio "congelados" al
-- momento de la compra, independientes de cambios futuros en el catálogo.
CREATE TABLE pedido_detalle (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id                   BIGINT NOT NULL REFERENCES pedido(id),
    producto_id                 BIGINT REFERENCES producto(id), -- NULL si es un combo
    combo_id                    BIGINT REFERENCES combo(id),    -- NULL si es un producto
    nombre_snapshot             TEXT NOT NULL,
    precio_unitario_snapshot    NUMERIC(12,2) NOT NULL,
    cantidad                    INTEGER NOT NULL CHECK (cantidad > 0),
    subtotal_linea              NUMERIC(12,2) NOT NULL,
    CONSTRAINT chk_pedido_detalle_producto_o_combo
        CHECK (num_nonnulls(producto_id, combo_id) = 1)
);
CREATE INDEX idx_pedido_detalle_pedido ON pedido_detalle(pedido_id);

CREATE TABLE pedido_detalle_opcion (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_detalle_id           BIGINT NOT NULL REFERENCES pedido_detalle(id),
    valor_opcion_id             BIGINT REFERENCES valor_opcion(id),
    nombre_snapshot             TEXT NOT NULL,
    precio_adicional_snapshot   NUMERIC(12,2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_pdo_detalle ON pedido_detalle_opcion(pedido_detalle_id);

CREATE TABLE pedido_detalle_adicional (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_detalle_id           BIGINT NOT NULL REFERENCES pedido_detalle(id),
    adicional_id                BIGINT REFERENCES adicional(id),
    nombre_snapshot             TEXT NOT NULL,
    precio_snapshot             NUMERIC(12,2) NOT NULL DEFAULT 0,
    cantidad                    INTEGER NOT NULL DEFAULT 1 CHECK (cantidad > 0)
);
CREATE INDEX idx_pda_detalle ON pedido_detalle_adicional(pedido_detalle_id);

-- RN-002, sección 20: auditoría específica de cambios sobre un pedido
-- (distinta de la auditoría general del sistema, ver `auditoria`).
CREATE TABLE historial_pedido (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id           BIGINT NOT NULL REFERENCES pedido(id),
    usuario_id          BIGINT REFERENCES usuario(id),
    campo_modificado    TEXT NOT NULL,
    valor_anterior      TEXT,
    valor_nuevo         TEXT,
    motivo              TEXT,
    creado_en           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_historial_pedido ON historial_pedido(pedido_id);

-- =========================================================
-- 5. PAGOS Y CAJA
-- =========================================================

-- RF-035
CREATE TABLE metodo_pago (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre  TEXT NOT NULL UNIQUE, -- efectivo, tarjeta, PSE, Nequi, etc.
    activo  BOOLEAN NOT NULL DEFAULT TRUE
);

-- RF-014, RF-015, RF-016, RNF-007: estado del pago independiente del
-- pedido; `referencia_externa` guarda el id de transacción de la pasarela
-- para procesar webhooks de forma idempotente (RNF-008).
CREATE TABLE pago (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id           BIGINT NOT NULL REFERENCES pedido(id),
    metodo_pago_id      BIGINT NOT NULL REFERENCES metodo_pago(id),
    monto               NUMERIC(12,2) NOT NULL CHECK (monto >= 0),
    estado              TEXT NOT NULL DEFAULT 'pendiente' CHECK (estado IN (
                            'pendiente', 'validado', 'rechazado', 'anulado'
                        )),
    referencia_externa  TEXT UNIQUE, -- id de transacción de la pasarela (idempotencia)
    validado_por        BIGINT REFERENCES usuario(id), -- NULL si la confirmación fue automática
    creado_en           TIMESTAMPTZ NOT NULL DEFAULT now(),
    validado_en         TIMESTAMPTZ
);
CREATE INDEX idx_pago_pedido ON pago(pedido_id);
CREATE INDEX idx_pago_estado ON pago(estado);

-- RF-036, RF-037
CREATE TABLE caja (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_apertura_id     BIGINT NOT NULL REFERENCES usuario(id),
    usuario_cierre_id       BIGINT REFERENCES usuario(id),
    monto_apertura          NUMERIC(12,2) NOT NULL,
    estado                  TEXT NOT NULL DEFAULT 'abierta' CHECK (estado IN ('abierta', 'cerrada')),
    monto_esperado          NUMERIC(12,2),
    monto_real              NUMERIC(12,2),
    diferencia              NUMERIC(12,2),
    abierta_en              TIMESTAMPTZ NOT NULL DEFAULT now(),
    cerrada_en              TIMESTAMPTZ
);
CREATE INDEX idx_caja_estado ON caja(estado);

CREATE TABLE movimiento_caja (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    caja_id     BIGINT NOT NULL REFERENCES caja(id),
    tipo        TEXT NOT NULL CHECK (tipo IN ('apertura', 'ingreso', 'egreso', 'pago', 'cierre')),
    monto       NUMERIC(12,2) NOT NULL,
    pedido_id   BIGINT REFERENCES pedido(id), -- NULL si no está ligado a un pedido
    descripcion TEXT,
    usuario_id  BIGINT NOT NULL REFERENCES usuario(id),
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_movimiento_caja_caja ON movimiento_caja(caja_id);

-- =========================================================
-- 6. DESPACHOS, NOTIFICACIONES, AUDITORÍA Y CONFIGURACIÓN
-- =========================================================

-- RF-033, sección 18: solo aplica cuando el módulo de domicilios está activo.
CREATE TABLE despacho (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id       BIGINT NOT NULL UNIQUE REFERENCES pedido(id),
    domiciliario_id BIGINT REFERENCES usuario(id),
    estado          TEXT NOT NULL DEFAULT 'asignado' CHECK (estado IN (
                        'asignado', 'en_camino', 'entregado', 'no_entregado'
                    )),
    asignado_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
    entregado_en    TIMESTAMPTZ
);

-- RF-023, RF-046, sección 14
CREATE TABLE notificacion (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id   BIGINT REFERENCES pedido(id),
    usuario_id  BIGINT REFERENCES usuario(id), -- destinatario
    canal       TEXT NOT NULL CHECK (canal IN ('web', 'sms', 'email', 'whatsapp')),
    tipo        TEXT NOT NULL, -- confirmacion_pedido, pago_confirmado, listo, despachado, ...
    contenido   TEXT,
    estado      TEXT NOT NULL DEFAULT 'pendiente' CHECK (estado IN ('pendiente', 'enviada', 'fallida')),
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
    enviado_en  TIMESTAMPTZ
);
CREATE INDEX idx_notificacion_pedido ON notificacion(pedido_id);

-- RN-002, sección 20, RNF-005: bitácora general del sistema (usuarios,
-- catálogo, precios, permisos, configuración...), no solo pedidos.
-- `entidad` + `entidad_id` identifican el registro afectado sin necesidad
-- de una FK física (la auditoría debe sobrevivir aunque el registro se
-- elimine lógicamente).
CREATE TABLE auditoria (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id      BIGINT REFERENCES usuario(id),
    accion          TEXT NOT NULL, -- crear, editar, eliminar, activar, desactivar...
    entidad         TEXT NOT NULL, -- nombre de la tabla/entidad afectada
    entidad_id      TEXT,
    valor_anterior  JSONB,
    valor_nuevo     JSONB,
    motivo          TEXT,
    origen          TEXT, -- web, api, admin
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_auditoria_entidad ON auditoria(entidad, entidad_id);
CREATE INDEX idx_auditoria_usuario ON auditoria(usuario_id);

-- RF-045
CREATE TABLE configuracion (
    clave           TEXT PRIMARY KEY,
    valor           TEXT NOT NULL,
    descripcion     TEXT,
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Ejemplo de parámetro de configuración referenciado en el documento (RF-032):
-- INSERT INTO configuracion (clave, valor, descripcion)
-- VALUES ('modulo_domicilios_activo', 'true', 'Activa/desactiva el módulo de domicilios');
