package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.Pedido;
import com.mrelote.pedidos.repository.projection.VentaPorPeriodoProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long>, JpaSpecificationExecutor<Pedido> {

    /** Cola de cocina: solo pedidos habilitados, ordenados por hora de entrada (RF-018 a RF-021). */
    List<Pedido> findByTipoAndEstadoInOrderByCreadoEnAsc(String tipo, List<String> estados);

    List<Pedido> findByEstadoInOrderByCreadoEnAsc(List<String> estados);

    Page<Pedido> findByClienteUsuarioIdOrderByCreadoEnDesc(Long clienteId, Pageable pageable);

    /**
     * RF-042: ventas agrupadas por periodo. granularidad ('day'|'week'|
     * 'month') se valida en InformeService antes de llegar aquí; como
     * parámetro ligado no representa riesgo de inyección.
     */
    @Query(value = """
            SELECT to_char(date_trunc(:granularidad, creado_en), 'YYYY-MM-DD') AS periodo,
                   count(*) AS numeroPedidos,
                   COALESCE(sum(total), 0) AS ventasTotal,
                   COALESCE(avg(total), 0) AS ticketPromedio
            FROM pedido
            WHERE estado <> 'anulado'
              AND (CAST(:desde AS timestamptz) IS NULL OR creado_en >= :desde)
              AND (CAST(:hasta AS timestamptz) IS NULL OR creado_en < :hasta)
            GROUP BY periodo
            ORDER BY periodo
            """, nativeQuery = true)
    List<VentaPorPeriodoProjection> ventasPorPeriodo(
            @Param("granularidad") String granularidad,
            @Param("desde") OffsetDateTime desde,
            @Param("hasta") OffsetDateTime hasta);

    /**
     * RF-041: KPIs del día para el dashboard. to_char necesita el cast
     * explícito a timestamptz: como parámetro ligado sin tipo, Postgres no
     * puede elegir entre sus sobrecargas ("function to_char(unknown,
     * unknown) is not unique").
     */
    @Query(value = """
            SELECT to_char(CAST(:hoy AS timestamptz), 'YYYY-MM-DD') AS periodo,
                   count(*) AS numeroPedidos,
                   COALESCE(sum(total), 0) AS ventasTotal,
                   COALESCE(avg(total), 0) AS ticketPromedio
            FROM pedido
            WHERE estado <> 'anulado'
              AND creado_en >= :hoy AND creado_en < CAST(:hoy AS timestamptz) + interval '1 day'
            """, nativeQuery = true)
    VentaPorPeriodoProjection resumenDelDia(@Param("hoy") OffsetDateTime hoy);
}
