package com.mrelote.pedidos.service;

import com.mrelote.pedidos.dto.response.ProductoVendidoResponse;
import com.mrelote.pedidos.dto.response.ResumenVentasResponse;
import com.mrelote.pedidos.exception.BusinessRuleException;
import com.mrelote.pedidos.repository.PedidoDetalleRepository;
import com.mrelote.pedidos.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/** RF-041, RF-042, RF-043. */
@Service
@RequiredArgsConstructor
public class InformeService {

    private static final Set<String> GRANULARIDADES_VALIDAS = Set.of("dia", "semana", "mes");

    private final PedidoRepository pedidoRepository;
    private final PedidoDetalleRepository pedidoDetalleRepository;

    @Transactional(readOnly = true)
    public List<ResumenVentasResponse> ventas(LocalDate desde, LocalDate hasta, String agrupar) {
        String granularidad = agrupar != null ? agrupar : "dia";
        if (!GRANULARIDADES_VALIDAS.contains(granularidad)) {
            throw new BusinessRuleException("agrupar debe ser 'dia', 'semana' o 'mes'");
        }
        // Postgres usa nombres en inglés para date_trunc.
        String granularidadSql = switch (granularidad) {
            case "dia" -> "day";
            case "semana" -> "week";
            case "mes" -> "month";
            default -> granularidad;
        };
        return pedidoRepository.ventasPorPeriodo(granularidadSql, inicioDe(desde), finDe(hasta)).stream()
                .map(v -> new ResumenVentasResponse(v.getPeriodo(), v.getNumeroPedidos(), v.getVentasTotal(), v.getTicketPromedio()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductoVendidoResponse> productosMasVendidos(LocalDate desde, LocalDate hasta) {
        return pedidoDetalleRepository.productosMasVendidos(inicioDe(desde), finDe(hasta)).stream()
                .map(p -> new ProductoVendidoResponse(p.getProductoId(), p.getNombre(), p.getUnidadesVendidas(), p.getVentasTotal()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ResumenVentasResponse resumenDashboard() {
        var hoy = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC);
        var r = pedidoRepository.resumenDelDia(hoy);
        return new ResumenVentasResponse(r.getPeriodo(), r.getNumeroPedidos(), r.getVentasTotal(), r.getTicketPromedio());
    }

    private OffsetDateTime inicioDe(LocalDate fecha) {
        return fecha != null ? fecha.atStartOfDay().atOffset(ZoneOffset.UTC) : null;
    }

    private OffsetDateTime finDe(LocalDate fecha) {
        return fecha != null ? fecha.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC) : null;
    }
}
