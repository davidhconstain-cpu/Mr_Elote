package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Caja;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CajaResponse(
        Long id, String usuarioAperturaNombre, BigDecimal montoApertura, String estado,
        BigDecimal montoEsperado, BigDecimal montoReal, BigDecimal diferencia,
        OffsetDateTime abiertaEn, OffsetDateTime cerradaEn) {

    public static CajaResponse from(Caja c) {
        return new CajaResponse(
                c.getId(), c.getUsuarioApertura().getNombre(), c.getMontoApertura(), c.getEstado(),
                c.getMontoEsperado(), c.getMontoReal(), c.getDiferencia(), c.getAbiertaEn(), c.getCerradaEn());
    }
}
