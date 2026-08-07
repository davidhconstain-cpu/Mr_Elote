package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Auditoria;

import java.time.OffsetDateTime;

public record AuditoriaResponse(
        Long id, String usuarioNombre, String accion, String entidad, String entidadId,
        String valorAnterior, String valorNuevo, String motivo, String origen, OffsetDateTime creadoEn) {

    public static AuditoriaResponse from(Auditoria a) {
        return new AuditoriaResponse(
                a.getId(), a.getUsuario() != null ? a.getUsuario().getNombre() : null,
                a.getAccion(), a.getEntidad(), a.getEntidadId(),
                a.getValorAnterior(), a.getValorNuevo(), a.getMotivo(), a.getOrigen(), a.getCreadoEn());
    }
}
