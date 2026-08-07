# Modelo de datos — Sistema de Pedidos Mr. Elote

Basado en la sección 22 (Modelo de datos conceptual) de la
`Especificación de Requisitos del Producto v1.1`. El esquema SQL completo,
ejecutable en PostgreSQL, está en [`database/schema.sql`](../database/schema.sql).

El esquema fue **verificado contra una instancia real de PostgreSQL 16**
(no solo revisado a ojo): se ejecutó `schema.sql` completo sin errores y se
corrieron 8 pruebas dirigidas — creación de tablas, disponibilidad de
combos por grupo de componentes, snapshot de precio histórico, y las
restricciones de integridad (`pedido_detalle` producto-o-combo, único QR
activo por mesa). Las 8 pasaron.

## Diagrama general

Ver el diagrama interactivo (Mermaid) en el artifact publicado, o el mismo
diagrama en `database/schema.sql` como referencia de tablas.

## Módulos

1. **Identidad y acceso** — `rol`, `permiso`, `rol_permiso`, `usuario`, `cliente`, `direccion`.
2. **Mesas y QR** — `mesa`, `qr_mesa`.
3. **Catálogo** — `categoria`, `producto`, `imagen_producto`, `opcion`, `valor_opcion`, `producto_opcion`, `adicional`, `producto_adicional`, `combo`, `combo_detalle`.
4. **Pedidos** — `pedido`, `pedido_detalle`, `pedido_detalle_opcion`, `pedido_detalle_adicional`, `historial_pedido`.
5. **Pagos y caja** — `metodo_pago`, `pago`, `caja`, `movimiento_caja`.
6. **Domicilios y soporte** — `zona_domicilio`, `tarifa_domicilio`, `despacho`, `notificacion`, `auditoria`, `configuracion`.

## Decisiones de diseño clave

| Decisión | Por qué | Referencia |
|---|---|---|
| El pedido nunca se borra físicamente | Trazabilidad y consistencia contable | RN-001 |
| Nombre y precio "congelados" (snapshot) en cada línea de pedido | Un cambio de precio futuro no debe alterar pedidos ya vendidos | RN-003, RF-013 |
| Estado de pago separado del estado de pedido | Cocina no debe preparar algo no pagado; permite pago automático (pasarela) o manual (WhatsApp/caja) | Sección 8 |
| Combo como entidad propia que referencia productos (`combo_detalle`) | Se vende como un ítem del menú pero conserva sus componentes reales | RF-027, sección 11 |
| Disponibilidad de combo calculada por grupo de componentes (vista `combo_disponibilidad`) | Un componente obligatorio agotado agota el combo; una alternativa agotada (ej. una bebida) no, si quedan otras del mismo grupo | Sección 11 (v1.1) |
| Auditoría general (`auditoria`) separada del historial de pedido (`historial_pedido`) | La auditoría cubre todo el sistema (usuarios, precios, permisos); el historial de pedido es específico y se muestra al cliente en su seguimiento | RN-002, sección 20 |
| Tarifa de domicilio versionada (`vigente_desde`/`vigente_hasta`) | Un pedido ya despachado no cambia de precio si la tarifa de la zona se actualiza después | RN-005, sección 18 |
| QR de mesa con histórico (`activo` + índice único parcial) | Permite regenerar un QR comprometido sin perder trazabilidad del anterior | Sección 17 |
| `pedido_detalle` exige producto_id XOR combo_id (constraint `chk_pedido_detalle_producto_o_combo`) | Una línea de pedido es un producto o un combo, nunca ambos ni ninguno | Integridad de datos |

## Próximos pasos sugeridos

- Especificación de la API REST (endpoints por módulo, contratos de entrada/salida).
- Migraciones versionadas (Flyway/Liquibase) a partir de `schema.sql`.
- Semillas de datos (roles, permisos, métodos de pago, categorías) para arrancar el MVP.
- Matriz de permisos por rol (detalle de `rol_permiso`).
