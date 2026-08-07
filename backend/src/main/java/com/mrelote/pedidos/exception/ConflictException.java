package com.mrelote.pedidos.exception;

/** 409: por ejemplo, una transición de estado de pedido no permitida. */
public class ConflictException extends RuntimeException {
    public ConflictException(String mensaje) {
        super(mensaje);
    }
}
