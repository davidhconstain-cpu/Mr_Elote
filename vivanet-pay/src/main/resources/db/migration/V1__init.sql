-- Vivanet Pay - esquema inicial (IDD §7).
-- Convenciones:
--   * Todos los *_at son INTEGER = milisegundos epoch UTC (IDD §7.2). Presentación en America/Bogota.
--   * Importes: INTEGER en pesos COP enteros (RN-007). Nunca REAL.
--   * Sin borrado físico de eventos, movimientos ni auditoría (RN-008): lo impiden triggers.
--   * La entidad "transaction" del IDD se llama payment_transaction (TRANSACTION es palabra reservada SQL).

CREATE TABLE payment_event (
    id                              INTEGER PRIMARY KEY AUTOINCREMENT,
    source                          TEXT    NOT NULL,
    connector_id                    TEXT    NOT NULL,
    external_id                     TEXT,
    source_timestamp                INTEGER,
    received_at                     INTEGER NOT NULL,
    -- contenido original (nunca se sobrescribe)
    raw_subject                     TEXT,
    raw_sender                      TEXT,
    raw_text                        TEXT    NOT NULL DEFAULT '',
    raw_headers_json                TEXT,
    schema_version                  INTEGER NOT NULL DEFAULT 1,
    -- datos extraídos por el parser (JSON) y metadatos de análisis
    extracted_json                  TEXT,
    parser_id                       TEXT,
    parse_confidence                REAL,
    -- estado e idempotencia
    status                          TEXT    NOT NULL CHECK (status IN ('RECIBIDO','PARSEADO','PENDIENTE_REVISION',
                                        'VALIDADO_POR_FUENTE','ANUNCIADO','DUPLICADO','RECHAZADO','ERROR')),
    status_reason                   TEXT,
    fingerprint                     TEXT    NOT NULL,
    idempotency_key                 TEXT,
    duplicate_of_event_id           INTEGER REFERENCES payment_event(id),
    possible_duplicate_of_event_id  INTEGER REFERENCES payment_event(id),
    correlation_id                  TEXT,
    historical                      INTEGER NOT NULL DEFAULT 0,
    announced_at                    INTEGER,
    created_at                      INTEGER NOT NULL,
    updated_at                      INTEGER NOT NULL,
    CHECK ((status = 'DUPLICADO') = (duplicate_of_event_id IS NOT NULL)),
    CHECK (status = 'DUPLICADO' OR idempotency_key IS NOT NULL)
);

-- Los eventos en ERROR usan idempotency_key 'err:<uuid>' y quedan fuera de los índices parciales,
-- para que el reintento del conector pueda procesarse de nuevo (RNF-005).
-- RN-004: el mismo identificador externo no produce más de un evento original.
CREATE UNIQUE INDEX ux_event_idempotency_key ON payment_event(idempotency_key);
CREATE UNIQUE INDEX ux_event_source_external ON payment_event(source, external_id)
    WHERE external_id IS NOT NULL AND duplicate_of_event_id IS NULL AND status <> 'ERROR';
-- RN-005: huella única entre eventos originales sin identificador externo.
CREATE UNIQUE INDEX ux_event_fingerprint ON payment_event(fingerprint)
    WHERE external_id IS NULL AND duplicate_of_event_id IS NULL AND status <> 'ERROR';
CREATE INDEX ix_event_fingerprint ON payment_event(fingerprint);
CREATE INDEX ix_event_external_id ON payment_event(external_id);
CREATE INDEX ix_event_status ON payment_event(status);
CREATE INDEX ix_event_received_at ON payment_event(received_at);

CREATE TABLE payment_transaction (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id        INTEGER NOT NULL UNIQUE REFERENCES payment_event(id),
    bank            TEXT,
    amount          INTEGER NOT NULL CHECK (amount > 0),
    currency        TEXT    NOT NULL DEFAULT 'COP' CHECK (currency = 'COP'),
    sender          TEXT,
    reference       TEXT,
    occurred_at     INTEGER NOT NULL,
    created_at      INTEGER NOT NULL
);
CREATE INDEX ix_tx_occurred_at ON payment_transaction(occurred_at);
CREATE INDEX ix_tx_bank ON payment_transaction(bank);
CREATE INDEX ix_tx_amount ON payment_transaction(amount);

CREATE TABLE connector (
    id                  TEXT PRIMARY KEY,
    type                TEXT    NOT NULL,
    enabled             INTEGER NOT NULL DEFAULT 1,
    connection_status   TEXT    NOT NULL,
    status_detail       TEXT,
    last_success_at     INTEGER,
    last_error_at       INTEGER,
    cursor              TEXT,
    cursor_updated_at   INTEGER,
    settings_ref        TEXT,
    updated_at          INTEGER NOT NULL
);

CREATE TABLE validation (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id        INTEGER NOT NULL REFERENCES payment_event(id),
    rule            TEXT    NOT NULL,
    result          TEXT    NOT NULL,
    reason          TEXT,
    validated_at    INTEGER NOT NULL,
    actor           TEXT    NOT NULL
);
CREATE INDEX ix_validation_event ON validation(event_id);

CREATE TABLE voice_announcement (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id        INTEGER REFERENCES payment_event(id),
    transaction_id  INTEGER REFERENCES payment_transaction(id),
    kind            TEXT    NOT NULL,
    message         TEXT    NOT NULL,
    status          TEXT    NOT NULL,
    queued_at       INTEGER NOT NULL,
    spoken_at       INTEGER,
    error           TEXT
);
CREATE INDEX ix_voice_event ON voice_announcement(event_id);

CREATE TABLE audit_log (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    entity_type     TEXT    NOT NULL,
    entity_id       TEXT    NOT NULL,
    action          TEXT    NOT NULL,
    before_value    TEXT,
    after_value     TEXT,
    actor           TEXT    NOT NULL,
    reason          TEXT,
    correlation_id  TEXT,
    created_at      INTEGER NOT NULL
);
CREATE INDEX ix_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX ix_audit_created ON audit_log(created_at);

CREATE TABLE app_setting (
    key             TEXT PRIMARY KEY,
    value           TEXT,
    -- 1 = value es una REFERENCIA a una clave de SecretStore, nunca el secreto en sí
    is_secret_ref   INTEGER NOT NULL DEFAULT 0,
    updated_at      INTEGER NOT NULL
);

CREATE TABLE license_state (
    license_id        TEXT PRIMARY KEY,
    status            TEXT    NOT NULL,
    trial_started_at  INTEGER NOT NULL,
    trial_ends_at     INTEGER NOT NULL,
    last_check_at     INTEGER NOT NULL,
    max_seen_at       INTEGER NOT NULL,
    updated_at        INTEGER NOT NULL
);

-- RN-008: sin borrado físico.
CREATE TRIGGER trg_no_delete_event BEFORE DELETE ON payment_event
BEGIN
    SELECT RAISE(ABORT, 'RN-008: no se permite borrar eventos');
END;

CREATE TRIGGER trg_no_delete_tx BEFORE DELETE ON payment_transaction
BEGIN
    SELECT RAISE(ABORT, 'RN-008: no se permite borrar movimientos');
END;

CREATE TRIGGER trg_no_delete_audit BEFORE DELETE ON audit_log
BEGIN
    SELECT RAISE(ABORT, 'RN-008: la auditoría es de solo inserción');
END;

CREATE TRIGGER trg_no_update_audit BEFORE UPDATE ON audit_log
BEGIN
    SELECT RAISE(ABORT, 'RN-008: la auditoría es de solo inserción');
END;

-- El contenido original de un evento no se sobrescribe (Flujo D paso 30).
CREATE TRIGGER trg_event_raw_immutable BEFORE UPDATE OF source, connector_id, external_id, source_timestamp,
    received_at, raw_subject, raw_sender, raw_text, raw_headers_json, fingerprint, created_at ON payment_event
BEGIN
    SELECT RAISE(ABORT, 'El contenido original del evento es inmutable');
END;

-- Los movimientos no se modifican: las correcciones se hacen por estado del evento + auditoría.
CREATE TRIGGER trg_tx_immutable BEFORE UPDATE ON payment_transaction
BEGIN
    SELECT RAISE(ABORT, 'Los movimientos son inmutables; use estado del evento y auditoría');
END;
