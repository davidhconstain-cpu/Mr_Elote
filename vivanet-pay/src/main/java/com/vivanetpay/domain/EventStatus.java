package com.vivanetpay.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Estados explícitos de un evento de pago (RN-002, IDD §7.3) con su máquina de transiciones.
 * <p>
 * Reglas:
 * <ul>
 *   <li>Solo {@link #VALIDADO_POR_FUENTE} (y {@link #ANUNCIADO}, que proviene de él) habilita el
 *       mensaje "pago confirmado" (RN-003).</li>
 *   <li>{@link #DUPLICADO} y {@link #RECHAZADO} son terminales: no se borran (RN-008), solo se auditan.</li>
 *   <li>Mantener pendiente en revisión manual no es una transición: se registra en auditoría.</li>
 * </ul>
 */
public enum EventStatus {
    /** La fuente entregó el evento. */
    RECIBIDO,
    /** Se extrajeron campos con estructura reconocida (aún NO confirmado). */
    PARSEADO,
    /** Falta evidencia o hay ambigüedad; requiere revisión manual (Flujo D). */
    PENDIENTE_REVISION,
    /** La política de validación (o la validación manual) aceptó la evidencia definida. */
    VALIDADO_POR_FUENTE,
    /** Se reprodujo el anuncio "pago confirmado" de un evento validado. */
    ANUNCIADO,
    /** Coincide con un evento ya procesado (RN-004). Nunca se anuncia. */
    DUPLICADO,
    /** No cumple reglas de aceptación (importe no positivo, moneda distinta de COP, descarte manual). */
    RECHAZADO,
    /** Fallo técnico que requiere reintento o diagnóstico. */
    ERROR;

    private Set<EventStatus> allowedTargets;

    static {
        RECIBIDO.allowedTargets = EnumSet.of(PARSEADO, PENDIENTE_REVISION, VALIDADO_POR_FUENTE, DUPLICADO, RECHAZADO, ERROR);
        PARSEADO.allowedTargets = EnumSet.of(PENDIENTE_REVISION, VALIDADO_POR_FUENTE, DUPLICADO, RECHAZADO, ERROR);
        PENDIENTE_REVISION.allowedTargets = EnumSet.of(VALIDADO_POR_FUENTE, DUPLICADO, RECHAZADO, ERROR);
        VALIDADO_POR_FUENTE.allowedTargets = EnumSet.of(ANUNCIADO, RECHAZADO);
        ANUNCIADO.allowedTargets = EnumSet.of(RECHAZADO);
        DUPLICADO.allowedTargets = EnumSet.noneOf(EventStatus.class);
        RECHAZADO.allowedTargets = EnumSet.noneOf(EventStatus.class);
        ERROR.allowedTargets = EnumSet.of(RECIBIDO, PENDIENTE_REVISION, RECHAZADO);
    }

    /** @return true si la máquina de estados permite pasar de este estado a {@code target}. */
    public boolean canTransitionTo(EventStatus target) {
        return target != null && allowedTargets.contains(target);
    }

    /** @return estados destino permitidos (vista inmutable). */
    public Set<EventStatus> allowedTransitions() {
        return Collections.unmodifiableSet(allowedTargets);
    }

    /**
     * Lanza {@link IllegalStateException} si la transición no está permitida.
     */
    public void requireTransitionTo(EventStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("Transición de estado no permitida: " + this + " -> " + target);
        }
    }

    /** @return true si el estado no admite más transiciones. */
    public boolean isTerminal() {
        return allowedTargets.isEmpty();
    }

    /** @return true si el evento cuenta como pago confirmado (RN-003, RF-011). */
    public boolean isConfirmed() {
        return this == VALIDADO_POR_FUENTE || this == ANUNCIADO;
    }

    /** @return true si el evento es un posible pago aún no confirmado (visible en tablero como "por verificar"). */
    public boolean isUnconfirmedCandidate() {
        return this == PARSEADO || this == PENDIENTE_REVISION;
    }

    /** @return true si el evento debe aparecer en la bandeja de revisión manual (RF-023). */
    public boolean isReviewable() {
        return this == PARSEADO || this == PENDIENTE_REVISION;
    }

    /** Estados que suman al tablero como confirmados. */
    public static Set<EventStatus> confirmedStatuses() {
        return EnumSet.of(VALIDADO_POR_FUENTE, ANUNCIADO);
    }

    /** Estados que suman al tablero como posibles pagos por verificar. */
    public static Set<EventStatus> unconfirmedStatuses() {
        return EnumSet.of(PARSEADO, PENDIENTE_REVISION);
    }
}
