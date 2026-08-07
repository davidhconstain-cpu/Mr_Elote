package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CategoriaRequest(@NotBlank String nombre, Integer orden, Boolean activa) {
}
