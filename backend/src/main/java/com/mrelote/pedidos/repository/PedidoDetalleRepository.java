package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.PedidoDetalle;
import com.mrelote.pedidos.repository.projection.ProductoVendidoProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface PedidoDetalleRepository extends JpaRepository<PedidoDetalle, Long> {

    /** RF-043: productos y combos más vendidos entre fechas (agrupa por nombre_snapshot). */
    @Query(value = """
            SELECT COALESCE(pd.producto_id, pd.combo_id) AS productoId,
                   pd.nombre_snapshot AS nombre,
                   sum(pd.cantidad) AS unidadesVendidas,
                   COALESCE(sum(pd.subtotal_linea), 0) AS ventasTotal
            FROM pedido_detalle pd
            JOIN pedido p ON p.id = pd.pedido_id
            WHERE p.estado NOT IN ('anulado', 'rechazado', 'devuelto')
              AND (CAST(:desde AS timestamptz) IS NULL OR p.creado_en >= :desde)
              AND (CAST(:hasta AS timestamptz) IS NULL OR p.creado_en < :hasta)
            GROUP BY COALESCE(pd.producto_id, pd.combo_id), pd.nombre_snapshot
            ORDER BY unidadesVendidas DESC
            LIMIT 20
            """, nativeQuery = true)
    List<ProductoVendidoProjection> productosMasVendidos(
            @Param("desde") OffsetDateTime desde, @Param("hasta") OffsetDateTime hasta);
}
