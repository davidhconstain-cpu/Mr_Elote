package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotNull;

public record AsignarOpcionRequest(@NotNull Long opcionId, Boolean obligatoria) {
}
