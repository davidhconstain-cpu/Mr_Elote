package com.mrelote.pedidos.exception;

/** 422: violación de una regla de negocio (ej. producto agotado). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String mensaje) {
        super(mensaje);
    }
}
