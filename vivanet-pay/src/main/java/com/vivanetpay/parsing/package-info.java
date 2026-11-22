/**
 * Analizadores de notificaciones (extracción de banco, importe, remitente, referencia, fecha; RF-007/RF-008).
 * <p>
 * Contratos estables: {@link com.vivanetpay.parsing.PaymentParser}, {@link com.vivanetpay.parsing.ParseResult},
 * {@link com.vivanetpay.parsing.ParserRegistry}. El núcleo solo incluye
 * {@link com.vivanetpay.parsing.UnrecognizedFallbackParser}.
 * <p>
 * Los parsers reales (Nequi Android, plantillas Gmail verificadas) los implementa el AGENTE DE PARSERS,
 * preferiblemente en subpaquetes {@code com.vivanetpay.parsing.nequi}, {@code com.vivanetpay.parsing.gmail}, etc.,
 * usando {@link com.vivanetpay.validation.AmountNormalizer} para convertir importes a pesos enteros.
 * Deben ser versionados (R-03) y no lanzar excepciones ante formatos desconocidos.
 */
package com.vivanetpay.parsing;
