package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.QrMesa;

public record QrMesaResponse(Long id, Long mesaId, String codigo, Boolean activo) {
    public static QrMesaResponse from(QrMesa qr) {
        return new QrMesaResponse(qr.getId(), qr.getMesa().getId(), qr.getCodigo(), qr.getActivo());
    }
}
