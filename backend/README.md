# Mr. Elote — API de Pedidos (backend)

Spring Boot 3 / Java 21, implementado directamente a partir de:

- `../database/schema.sql` — modelo de datos (33 tablas, verificado con PostgreSQL 16 real).
- `../api/openapi.yaml` — contrato de la API (60 rutas, verificado con un mock server real).
- `../docs/api-rest.md` — convenciones y matriz de permisos.

Los 15 módulos de la especificación de requisitos están implementados con
lógica real (no stubs) y verificados con peticiones HTTP reales contra
PostgreSQL — ver "Verificación end-to-end" más abajo.

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

Flyway aplica el esquema y los datos semilla (roles, métodos de pago,
permisos base, configuración) al arrancar. La app queda en
`http://localhost:8080`, prefijo `/api/v1`.

No hay ningún usuario administrador sembrado por defecto (el registro
público solo crea clientes, RF-002); para crear el primer Administrador,
insértalo directamente en la base de datos la primera vez:

```sql
INSERT INTO usuario (rol_id, nombre, email, password_hash, activo)
SELECT id, 'Admin', 'admin@tudominio.com', '<hash-bcrypt>', true
FROM rol WHERE nombre = 'Administrador';
```

## Qué está implementado (con lógica real, no stubs)

- **Identidad**: registro de clientes, login, refresh — JWT con roles.
- **Usuarios, roles y permisos**: CRUD de usuarios de staff (alta,
  edición, baja lógica — nunca se borran, RN-001), listado de roles y
  asignación de permisos finos por rol.
- **Clientes**: direcciones propias (CRUD, scoped al dueño) e historial de
  pedidos paginado.
- **RBAC**: `SecurityConfig` (rutas públicas/opcionales/protegidas) +
  `@PreAuthorize` por rol en cada controlador, según la matriz de permisos
  de `docs/api-rest.md`.
- **Catálogo**: categorías, productos (con disponibilidad), opciones y sus
  valores, adicionales, imágenes de producto (subida multipart a disco
  local, servidas en `/uploads/**`), y combos con **disponibilidad
  calculada dinámicamente** contra la vista SQL `combo_disponibilidad` (un
  componente obligatorio agotado agota el combo; una alternativa agotada
  dentro de un grupo, no).
- **Mesas y QR**: regeneración de QR sin perder el histórico.
- **Pedidos**: creación con snapshot de precios (incluye opciones y
  adicionales), cálculo de domicilio por tarifa vigente, modificación de
  ítems antes de validar el pago (RF-012), y **un endpoint por transición
  de estado** (`confirmar-pago`, `iniciar-preparacion`, `marcar-listo`,
  `entregar`, `recoger`, `despachar`, `anular`), cada uno validando su
  propio conjunto de estados de origen permitidos y dejando rastro en
  `historial_pedido`.
- **Pagos**: registro, validación manual (caja) y webhook idempotente por
  `referenciaExterna` — ambos caminos confluyen en la misma transición de
  pedido.
- **Caja**: apertura, movimientos manuales, cierre con cálculo de
  esperado/real/diferencia.
- **Cocina**: cola filtrable por tipo/estado, ordenada por hora de
  llegada.
- **Domicilios**: zonas con tarifa versionada en el tiempo (RN-005 — un
  pedido ya despachado conserva la tarifa con la que se calculó).
- **Despachos**: cola, asignación (autoasignación si el domiciliario no se
  especifica), en camino, entregado.
- **Notificaciones**: se disparan automáticamente en cada transición
  relevante del pedido (confirmación, pago confirmado, en preparación,
  listo para recoger/entregar, despachado, entregado). El envío real por
  SMS/email/WhatsApp queda pendiente de la decisión de proveedor (sección
  28 del documento de requisitos); por ahora quedan en estado "pendiente".
- **Auditoría general**: bitácora conectada a las mutaciones administrativas
  más sensibles (productos, usuarios, roles/permisos, tarifas de
  domicilio, configuración), con valor anterior/nuevo en JSON.
- **Informes y dashboard**: ventas agrupadas por día/semana/mes, ranking
  de productos/combos más vendidos, resumen del día.
- **Configuración**: parámetros generales (incluye
  `modulo_domicilios_activo`, RF-032).

## Qué queda pendiente (siguiente fase)

- Envío real de notificaciones (job/listener asíncrono que tome las
  notificaciones "pendiente" y las entregue por el canal correspondiente).
- Password reset real para `/auth/recuperar` (hoy es un 202 sin efecto,
  pendiente de proveedor de email).
- Almacenamiento de imágenes en un servicio tipo S3 en vez de disco local
  (documentado como simplificación en `WebConfig`/`ImagenProductoService`).
- Tests automatizados (JUnit/Testcontainers) — hasta ahora la verificación
  fue manual con scripts de curl contra Postgres real; formalizarla como
  suite de integración es el siguiente paso natural.

## Verificación end-to-end

Dos rondas de pruebas HTTP reales (no mocks) contra una instancia real de
PostgreSQL 16, reiniciando la base entre cada ronda:

**Ronda 1 — flujo núcleo**: registro → login → catálogo público →
pedido anónimo por QR de mesa → pago → validación → cola de cocina →
preparación → listo → entrega → historial de 4 transiciones → bloqueo de
anular un pedido en estado final → disponibilidad dinámica de combos al
agotar un componente obligatorio.

**Ronda 2 — módulos restantes**: alta/baja de usuarios de staff →
asignación de permisos a un rol → direcciones de cliente → zona de
domicilio con tarifa versionada → opciones/valores y adicionales → subida
y descarga pública de imagen de producto → pedido a domicilio con precio
correcto (base + opción + adicional + tarifa) → modificación de ítems con
recálculo de total → notificación automática de confirmación → webhook de
pago idempotente (repetirlo no duplica el efecto) → flujo completo de
despacho (cola → asignar → en camino → entregado) → auditoría con
registros reales → informes y dashboard → toggle de configuración.

Ambas rondas encontraron y corrigieron bugs reales (no solo confirmaron
que todo funcionaba):

- Hibernate `@MapsId` + `GenerationType.IDENTITY` hacía que Spring Data
  eligiera `merge()` en vez de `persist()` para un `Cliente` nuevo →
  solucionado implementando `Persistable<Long>`.
- Los forwards internos de error de Spring quedaban bloqueados por el
  filtro de seguridad, enmascarando excepciones reales como falsos 401 →
  se permitió `/error` explícitamente.
- `POST /pedidos/{id}/despachar` solo autorizaba el rol Despachos, en
  contra de la propia matriz de permisos documentada (que también incluye
  Administrador) → corregido el `@PreAuthorize`.
- El informe de `dashboard/resumen` fallaba con "function to_char(unknown,
  unknown) is not unique" por un parámetro de fecha sin cast explícito en
  una consulta nativa → se agregó `CAST(:hoy AS timestamptz)`.

En ambas rondas, las únicas discrepancias no corregidas fueron artefactos
de formato del script de prueba en Python (`4000.0` vs `4000`), no errores
de la API.
