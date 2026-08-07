package com.mrelote.pedidos.domain;

import java.util.Set;

/**
 * Constantes de estado + tabla de transiciones válidas, tomada literalmente
 * de la Especificación de Requisitos v1.1, sección 8.2 ("Transiciones de
 * estado del pedido por rol"). Los valores coinciden con el CHECK
 * constraint de pedido.estado en database/schema.sql.
 */
public final class EstadoPedido {

    public static final String CREADO = "creado";
    public static final String PAGO_PENDIENTE = "pago_pendiente";
    public static final String PAGO_VALIDADO = "pago_validado";
    public static final String EN_PREPARACION = "en_preparacion";
    public static final String LISTO = "listo";
    public static final String ENTREGADO = "entregado";
    public static final String RECOGIDO = "recogido";
    public static final String DESPACHADO = "despachado";
    public static final String RECHAZADO = "rechazado";
    public static final String DEVUELTO = "devuelto";
    public static final String NO_RECIBIDO = "no_recibido";
    public static final String ANULADO = "anulado";

    /** Estados desde los que cocina puede recibir un pedido (RF-018). */
    public static final Set<String> HABILITADOS_PARA_COCINA =
            Set.of(PAGO_VALIDADO, EN_PREPARACION, LISTO);

    /** Estados de origen válidos para cada acción explícita del API. */
    public static final Set<String> ORIGEN_CONFIRMAR_PAGO = Set.of(PAGO_PENDIENTE);
    public static final Set<String> ORIGEN_INICIAR_PREPARACION = Set.of(PAGO_VALIDADO);
    public static final Set<String> ORIGEN_MARCAR_LISTO = Set.of(EN_PREPARACION);
    public static final Set<String> ORIGEN_ENTREGAR = Set.of(LISTO);
    public static final Set<String> ORIGEN_RECOGER = Set.of(LISTO);
    public static final Set<String> ORIGEN_DESPACHAR = Set.of(LISTO);
    /** Anular: cualquier estado que no sea ya un estado final. */
    public static final Set<String> ESTADOS_FINALES =
            Set.of(ENTREGADO, RECOGIDO, DESPACHADO, RECHAZADO, DEVUELTO, NO_RECIBIDO, ANULADO);

    private EstadoPedido() {
    }
}
