/**
 * Licenciamiento local: prueba gratuita de 14 días sin medio de pago (IDD §2.4, decisión APROBADA) y detección
 * básica de reloj retrocedido (R-09).
 * <p>
 * PENDIENTE DP-07: no hay validación en línea, activación, renovación, tolerancia sin conexión ni política de
 * reinstalación. Por eso {@link com.vivanetpay.licensing.LicenseService} solo INFORMA el estado; no bloquea la
 * captura salvo que se configure {@code license.enforce=true}. Implementado por: agente núcleo.
 */
package com.vivanetpay.licensing;
