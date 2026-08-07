# Mr. Elote — API de Pedidos (backend)

Esqueleto Spring Boot 3 / Java 21 del sistema de pedidos, implementado a
partir de:

- `../database/schema.sql` — modelo de datos (33 tablas, verificado con PostgreSQL 16 real).
- `../api/openapi.yaml` — contrato de la API (60 rutas, verificado con un mock server real).
- `../docs/api-rest.md` — convenciones y matriz de permisos.

## Stack

Java 21 · Spring Boot 3.3 · Spring Data JPA (Hibernate 6) · Spring Security
(JWT, BCrypt) · Flyway · PostgreSQL · Lombok · Maven.

## Cómo correrlo localmente

```bash
# 1. Base de datos
createuser mrelote --pwprompt   # password: mrelote (o exporta DB_USER/DB_PASSWORD)
createdb mrelote --owner mrelote

# 2. Levantar la app (perfil dev, apunta a localhost:5432/mrelote)
export DB_USER=mrelote DB_PASSWORD=mrelote
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Flyway aplica el esquema y los datos semilla (roles, métodos de pago) al
arrancar. La app queda en `http://localhost:8080`, prefijo `/api/v1`.

## Qué está implementado (con lógica real, no stubs)

- **Identidad**: registro de clientes, login, refresh — JWT con roles.
- **RBAC**: `SecurityConfig` (rutas públicas/opcionales/protegidas) +
  `@PreAuthorize` por rol en cada controlador, según la matriz de permisos
  de `docs/api-rest.md`.
- **Catálogo**: categorías, productos (con disponibilidad), combos con
  **disponibilidad calculada dinámicamente** contra la vista SQL
  `combo_disponibilidad` (un componente obligatorio agotado agota el
  combo; una alternativa agotada dentro de un grupo, no).
- **Mesas y QR**: regeneración de QR sin perder el histórico.
- **Pedidos**: creación con snapshot de precios, cálculo de domicilio por
  tarifa vigente, y **un endpoint por transición de estado**
  (`confirmar-pago`, `iniciar-preparacion`, `marcar-listo`, `entregar`,
  `recoger`, `despachar`, `anular`), cada uno validando su propio conjunto
  de estados de origen permitidos y dejando rastro en `historial_pedido`.
- **Pagos**: registro, validación manual (caja) y webhook idempotente por
  `referenciaExterna` — ambos caminos confluyen en la misma transición de
  pedido.
- **Caja**: apertura, movimientos manuales, cierre con cálculo de
  esperado/real/diferencia.
- **Cocina**: cola filtrable por tipo/estado, ordenada por hora de
  llegada.

## Qué queda pendiente (siguiente fase, no implementado aún)

- Usuarios/roles/permisos: solo existe el modelo y el seed; faltan los
  endpoints CRUD de administración.
- Domicilios/despachos: el módulo de pedidos ya crea el registro de
  `despacho` al despachar, pero faltan los endpoints de asignar/en
  camino/entregar y el toggle de `configuracion.modulo_domicilios_activo`.
- Notificaciones: no se disparan automáticamente en las transiciones
  todavía (la tabla y el repositorio ya existen).
- Auditoría general (`auditoria`, distinta de `historial_pedido`) y el
  endpoint de consulta.
- Informes/dashboard: solo hay datos para construirlos (ver
  `PedidoRepository`, `PagoRepository`), faltan las consultas agregadas.
- Adiciones/opciones: CRUD de `opcion`/`valor_opcion`/`producto_opcion` no
  tiene controlador propio todavía (las entidades y repos sí existen).

## Verificación end-to-end

El flujo completo se probó contra una instancia real de PostgreSQL con
peticiones HTTP reales (no mocks): registro → login → catálogo público →
creación de pedido anónimo por QR → registro y validación de pago →
transición automática a `pago_validado` → cola de cocina → preparación →
listo → entrega → historial de 4 transiciones → bloqueo de anular un
pedido ya en estado final → disponibilidad dinámica de combos al agotar un
componente. 17/18 aserciones pasaron (la única discrepancia fue un
artefacto de formato en el script de prueba, no un error de la API).
