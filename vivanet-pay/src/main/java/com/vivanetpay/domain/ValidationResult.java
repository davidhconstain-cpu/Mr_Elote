package com.vivanetpay.domain;

/** Resultado de una regla de validación automática o manual. */
public enum ValidationResult {
    /** La regla se cumplió. */
    APROBADO,
    /** La regla falló y obliga a rechazar el evento. */
    FALLIDO,
    /** La regla no es concluyente; envía a revisión. */
    ADVERTENCIA,
    /** Registro informativo (p. ej. "mantener pendiente" en revisión manual). */
    INFORMATIVO
}
