package com.vivanetpay.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Entrada del registro de auditoría (tabla {@code audit_log}), de solo inserción (RN-008, RF-024).
 *
 * @param id            id interno
 * @param entityType    tipo de entidad (ver constantes)
 * @param entityId      id de la entidad
 * @param action        acción (p. ej. "STATUS_CHANGE", "CREATE", "REVIEW_KEEP")
 * @param beforeValue   valor anterior (nullable)
 * @param afterValue    valor posterior (nullable)
 * @param actor         quién lo hizo
 * @param reason        motivo (nullable)
 * @param correlationId correlación (nullable)
 * @param createdAt     instante UTC
 */
public record AuditEntry(Long id, String entityType, String entityId, String action,
                         String beforeValue, String afterValue, String actor, String reason,
                         String correlationId, Instant createdAt) {
    public static final String ENTITY_EVENT = "payment_event";
    public static final String ENTITY_TRANSACTION = "payment_transaction";
    public static final String ENTITY_CONNECTOR = "connector";
    public static final String ENTITY_LICENSE = "license_state";
    public static final String ENTITY_SETTING = "app_setting";

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_STATUS_CHANGE = "STATUS_CHANGE";
    public static final String ACTION_REVIEW_KEEP = "REVIEW_KEEP";

    public AuditEntry {
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
