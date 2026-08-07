package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.ZonaDomicilio;

import java.math.BigDecimal;

public record ZonaDomicilioResponse(
        Long id, String nombre, Boolean activa, BigDecimal tarifaVigente, Integer tiempoEstimadoMinutos) {

    public static ZonaDomicilioResponse from(ZonaDomicilio z, BigDecimal tarifaVigente, Integer tiempoEstimado) {
        return new ZonaDomicilioResponse(z.getId(), z.getNombre(), z.getActiva(), tarifaVigente, tiempoEstimado);
    }
}
