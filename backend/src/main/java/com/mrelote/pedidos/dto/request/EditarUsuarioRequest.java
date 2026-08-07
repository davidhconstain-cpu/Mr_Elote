package com.mrelote.pedidos.dto.request;

public record EditarUsuarioRequest(String nombre, String telefono, Long rolId, Boolean activo) {
}
