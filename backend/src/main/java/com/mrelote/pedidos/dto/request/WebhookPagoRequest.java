package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WebhookPagoRequest(
        @NotBlank String referenciaExterna,
        @Pattern(regexp = "validado|rechazado") String estado,
        @NotBlank String firma) {
}
