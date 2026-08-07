package com.mrelote.pedidos.dto.response;

public record LoginResponse(String accessToken, String refreshToken, UsuarioResponse usuario) {
}
