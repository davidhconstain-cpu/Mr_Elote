package com.vivanetpay.parsing;

/** Parser con versión de plantilla explícita (R-03: parsers versionados). */
public interface VersionedParser extends PaymentParser {

    /** Versión semántica de las plantillas que soporta este parser, p. ej. "1.0.0". */
    String version();
}
