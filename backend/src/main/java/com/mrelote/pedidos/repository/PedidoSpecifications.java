package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.Pedido;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.ZoneOffset;

public final class PedidoSpecifications {

    private PedidoSpecifications() {
    }

    public static Specification<Pedido> conFiltros(String estado, String tipo, LocalDate desde, LocalDate hasta) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (estado != null) {
                predicate = cb.and(predicate, cb.equal(root.get("estado"), estado));
            }
            if (tipo != null) {
                predicate = cb.and(predicate, cb.equal(root.get("tipo"), tipo));
            }
            if (desde != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(
                        root.get("creadoEn"), desde.atStartOfDay().atOffset(ZoneOffset.UTC)));
            }
            if (hasta != null) {
                predicate = cb.and(predicate, cb.lessThan(
                        root.get("creadoEn"), hasta.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)));
            }
            return predicate;
        };
    }
}
