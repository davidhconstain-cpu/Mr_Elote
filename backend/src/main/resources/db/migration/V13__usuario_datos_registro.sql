-- Datos de registro del cliente (documento, apellidos, consentimientos).
-- Todas las columnas son opcionales a nivel de esquema porque los usuarios
-- de staff ya existentes no tienen documento; la obligatoriedad para el
-- registro público se valida en RegistroClienteRequest.
ALTER TABLE usuario
    ADD COLUMN tipo_documento        VARCHAR(20),
    ADD COLUMN numero_documento      VARCHAR(30),
    ADD COLUMN apellidos             VARCHAR(150),
    ADD COLUMN acepta_promociones    BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN terminos_aceptados_en TIMESTAMPTZ;

-- Índice único parcial: el documento no se puede repetir entre quienes lo
-- tienen, pero varios usuarios de staff pueden quedar con NULL.
CREATE UNIQUE INDEX ux_usuario_numero_documento
    ON usuario (numero_documento)
    WHERE numero_documento IS NOT NULL;

-- Inicio de sesión con código de un solo uso ("recibe un código"), como
-- alternativa a la contraseña. Igual que password_reset_token, el código
-- nunca se guarda en claro: solo su hash SHA-256.
CREATE TABLE codigo_acceso (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  BIGINT NOT NULL REFERENCES usuario (id),
    codigo_hash TEXT        NOT NULL,
    expira_en   TIMESTAMPTZ NOT NULL,
    usado_en    TIMESTAMPTZ,
    intentos    INT         NOT NULL DEFAULT 0,
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_codigo_acceso_usuario ON codigo_acceso (usuario_id);
