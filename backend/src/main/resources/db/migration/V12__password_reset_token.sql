-- RF-002 (recuperar contraseña). El token nunca se guarda en claro: solo
-- su hash SHA-256, igual que una contraseña — si la tabla se filtra, no
-- sirve para nada sin el token original que el usuario recibió.
CREATE TABLE password_reset_token (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  BIGINT NOT NULL REFERENCES usuario(id),
    token_hash  TEXT NOT NULL UNIQUE,
    expira_en   TIMESTAMPTZ NOT NULL,
    usado_en    TIMESTAMPTZ,
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_reset_token_usuario ON password_reset_token(usuario_id);
