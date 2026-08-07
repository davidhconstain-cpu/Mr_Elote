package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.Auditoria;
import org.springframework.data.jpa.domain.Specification;

public final class AuditoriaSpecifications {

    private AuditoriaSpecifications() {
    }

    public static Specification<Auditoria> conFiltros(String entidad, Long usuarioId) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (entidad != null) {
                predicate = cb.and(predicate, cb.equal(root.get("entidad"), entidad));
            }
            if (usuarioId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("usuario").get("id"), usuarioId));
            }
            return predicate;
        };
    }
}
