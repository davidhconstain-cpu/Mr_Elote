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
  dentro de un grupo, no). `GET /productos/{id}/opciones` +
  `POST/DELETE /productos/{id}/opciones|adicionales` (admin) exponen y
  asignan qué opciones/adicionales aplican a cada producto.
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
- **Catálogo React conectado**: el frontend (`../src`) ya consume
  `GET /api/v1/productos` en vivo en vez de datos estáticos — ver
  "Catálogo React" más abajo.

## Catálogo React

El catálogo (`../src/hooks/useCatalog.js`) reemplazó los datos estáticos de
`../src/data/products.js` por una llamada real a `GET /api/v1/productos`.
Cada porción de un plato (1/2/4/6 personas) es una fila de `producto`
distinta con el mismo `nombre` y su propio `precio`/`porcion_personas`
(columna agregada en `V5__add_porcion_personas.sql`); el hook las agrupa
por `nombre` para reconstruir la vista de "un plato, varias porciones" del
sitio original. El menú real de Mr. Elote (16 filas de producto, con las
imágenes ya existentes en `../src/public/images`) se siembra en
`V6__seed_menu_mrelote.sql`. Bebidas y adiciones siguen estáticas en el
frontend porque sus precios reales aún no están confirmados.

Para correr ambos juntos en desarrollo:

```bash
# backend (puerto 8080)
export DB_USER=mrelote DB_PASSWORD=mrelote
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# frontend (puerto 5173) — en otra terminal, desde la raíz del repo
npm run dev
```

Por defecto el frontend apunta a `http://localhost:8080/api/v1`
(`VITE_API_BASE_URL`, ver `.env.example` en la raíz) y el backend permite
CORS desde `http://localhost:5173` (`mrelote.cors.allowed-origins`, ver
`application.yml`).

El catálogo también arma pedidos reales, en cualquiera de los tres tipos
(recoger anónimo, domicilio con cliente autenticado, local vía QR de
mesa) — ver el commit "Agregar carrito...", "...login de cliente..." y
"...flujo de pedido local...". Desde la última ronda además: combos
(`CombosSection`, RF-027), personalizar un ítem con opciones/adicionales
antes de agregarlo al carrito (`ItemCustomizeModal`, cada combinación es
una línea distinta del carrito), historial de pedidos del cliente
(`MisPedidosPanel`, con el detalle y el historial de estados de cada
uno), y elegir método de pago al confirmar — solo para cliente
autenticado, ya que `POST /pedidos/{id}/pagos` solo lo permite a
Cliente/Mesero/Caja; un pedido anónimo se paga en persona.

## Panel de staff

Sección aparte del catálogo (`../src/staff/`, rutas bajo `/staff`, usa
`react-router-dom`), con su propio login (reutiliza `AuthContext` — es la
misma sesión JWT, cualquier rol puede loguearse ahí) y navegación según el
rol de la cuenta:

- **Cocina** (`/staff/cocina`): cola de pedidos habilitados, filtrable por
  tipo, con "iniciar preparación" / "marcar listo".
- **Caja** (`/staff/caja`): pedidos pendientes de confirmar pago,
  apertura/movimientos/cierre de caja, y pedidos "listos" para marcar
  recogidos (recoger) o entregados (local) — domicilio lo maneja
  Despachos.
- **Despachos** (`/staff/despachos`): cola de pedidos despachados,
  asignarse uno, marcar en camino y entregado.
- **Administración** (`/staff/admin/*`, 11 pestañas): productos
  (crear/editar/disponibilidad/asignar opciones y adicionales),
  categorías, combos (crear y definir componentes), opciones y
  adicionales, usuarios de staff (crear/desactivar), roles (editar
  permisos finos — `GET /permisos` es nuevo, no existía ningún endpoint
  para listar los permisos disponibles), zonas y tarifas de domicilio,
  mesas (crear y regenerar QR), informes/dashboard, auditoría, y
  configuración general.

No hay ningún usuario de staff sembrado en producción — los 5 usuarios de
demo (uno por rol, contraseña `password123`) están en
`V11__seed_usuarios_staff_demo.sql`, pensados solo para desarrollo/pruebas.

## Notificaciones y recuperación de contraseña

Ambas tenían lógica real pendiente de un proveedor externo con
credenciales que este entorno no tiene (sección 28 del documento de
requisitos); en vez de dejarlas como stubs, se implementó todo el camino
real hasta el punto exacto donde hace falta ese proveedor:

- **Notificaciones** (`src/main/java/.../notificacion/`): `NotificacionDeliveryJob`
  corre cada `mrelote.notificaciones.intervalo-ms` (5s por defecto),
  toma las notificaciones "pendiente" y las entrega vía `NotificacionSender`
  — `LogNotificacionSender` es una implementación real sobre el canal que
  no depende de ningún tercero, el log de la aplicación. Cambiar a
  SMS/email/WhatsApp real es agregar otra implementación de
  `NotificacionSender`; el resto (el job, los estados "enviada"/"fallida")
  no cambia.
- **Recuperar contraseña** (`PasswordResetService`): `POST /auth/recuperar`
  genera un token de un solo uso (256 bits, aleatorio), lo guarda con su
  hash SHA-256 y una expiración de 30 minutos — nunca el token en claro.
  `POST /auth/recuperar/confirmar` lo consume atómicamente y cambia la
  contraseña. Responde 202 exista o no el email, para no permitir
  enumeración de cuentas. Lo único que falta es el transporte real del
  link (por ahora se loguea, igual que las notificaciones).
- **Código de acceso de un solo uso** (`CodigoAccesoService`), la
  alternativa a la contraseña que ofrece el formulario de login:
  `POST /auth/codigo` genera 6 dígitos con `SecureRandom`, guarda solo su
  hash SHA-256 y expira a los 10 minutos; `POST /auth/codigo/login` lo
  valida y emite los tokens. Responde 202 exista o no la cuenta, igual que
  la recuperación.

  Dos detalles que no son obvios y están así a propósito:
  `CodigoAccesoService.validar` **no** lleva `@Transactional` — los caminos
  de fallo lanzan excepción, y si el incremento del contador de intentos
  viviera dentro de esa transacción el rollback lo descartaría, dejando el
  límite de 5 intentos sin efecto (verificado: sin el arreglo el contador
  se quedaba en 0 tras seis intentos fallidos). Y el consumo del código es
  un `UPDATE ... WHERE usado_en IS NULL` condicional, para que dos
  peticiones simultáneas con el mismo código no puedan ambas entrar.

## Registro e inicio de sesión del cliente

El registro público (`POST /auth/registro`) pide tipo y número de
documento, nombres, apellidos, correo con confirmación, teléfono
opcional, contraseña con confirmación y los consentimientos. Las
confirmaciones y la aceptación de términos se validan con `@AssertTrue`
en `RegistroClienteRequest`, no solo en el navegador: el endpoint es
público y cualquiera puede llamarlo directo. El número de documento es
único entre quienes lo tienen (índice único parcial en `V13`, para que
los usuarios de staff sembrados puedan quedar en NULL).

Para entrar, `LoginRequest.identificador` acepta el correo **o** el
número de documento — `UsuarioDetailsService` los resuelve con
`UsuarioRepository.findByIdentificador`. Los tokens JWT se siguen
emitiendo y resolviendo por correo.

## Tests automatizados

`backend/src/test/java` — 17 tests de integración reales (JUnit 5 +
`@SpringBootTest` con servidor HTTP en un puerto aleatorio), sin mocks:
auth (registro/login/refresh/recuperar contraseña), catálogo (crear
producto, disponibilidad, RN-004), el ciclo de vida completo de un pedido
"recoger" a través de sus 4 transiciones con verificación de permisos por
rol, pedido a domicilio con cálculo de tarifa, y disponibilidad dinámica
de combos (componente fijo agotado vs. grupo de alternativas).

No usan Testcontainers: este entorno de desarrollo no tiene salida de red
hacia Docker Hub (`docker pull postgres` falla con 403 al bajar la
imagen), así que corren contra una base Postgres real y persistente
(`mrelote_test`) en vez de un contenedor efímero — ver el javadoc de
`IntegrationTestBase` para el detalle y cómo migrar a Testcontainers en un
entorno donde sí haya salida a Docker Hub.

```bash
createdb mrelote_test --owner mrelote   # una sola vez
export DB_USER=mrelote DB_PASSWORD=mrelote
mvn test -Dspring.profiles.active=test
```

Un detalle real que encontraron estos tests y que las pruebas manuales
con curl no habían revelado: el `TestRestTemplate` autoconfigurado por
Spring Boot usa `HttpURLConnection`, que no soporta el método PATCH y
falla al leer el cuerpo de respuestas de error en modo streaming
(401/409/422) — no es un bug de la API, es una limitación del cliente HTTP
de prueba por defecto. Se resolvió construyendo el `TestRestTemplate` con
`JdkClientHttpRequestFactory` (java.net.http.HttpClient, incluido en el
JDK), que no tiene ninguno de los dos problemas.

## Qué queda pendiente (siguiente fase)

- Almacenamiento de imágenes en un servicio tipo S3 en vez de disco local
  (documentado como simplificación en `WebConfig`/`ImagenProductoService`)
  — bloqueado por no tener credenciales de un proveedor real (AWS u otro)
  en este entorno.
- Envío real de notificaciones y del link de recuperación de contraseña
  por SMS/email/WhatsApp — la lógica ya está completa (ver arriba), solo
  falta contratar un proveedor y agregar su `NotificacionSender`.
- Ampliar la suite de tests a los módulos de staff que hoy solo se
  verificaron manualmente (caja, despachos, informes, auditoría,
  usuarios/roles).

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

**Ronda 3 — integración con el catálogo React**: base de datos recreada
desde cero → Flyway aplica las 6 migraciones (incluida la nueva
`porcion_personas` y el seed del menú real) → `GET /api/v1/productos`
verificado con curl (16 filas, agrupables por nombre, imágenes correctas)
→ CORS verificado con `curl -H "Origin: http://localhost:5173"` (responde
`Access-Control-Allow-Origin` correcto) → frontend (`npm run dev`)
verificado con una captura de pantalla real renderizando los datos del
backend. `ProductoResponse` ahora expone `producto.imagenes`, una
colección `@OneToMany` LAZY; como `spring.jpa.open-in-view` está en
`false`, se anotó `ProductoController#listar/ver` con
`@Transactional(readOnly = true)` para evitar un
`LazyInitializationException` fuera de la transacción — verificado
directamente contra la API, no solo por inspección de código.

**Ronda 4 — flujo de pedidos desde el catálogo**: backend + frontend
corriendo juntos, automatizado con Playwright — agregar 2x Callejera y 1x
Burrito al carrito desde la UI, abrir el panel, escribir observaciones,
confirmar → `POST /api/v1/pedidos` real → pedido #1 creado con
`tipo=recoger`, `total=$115.000` correcto, verificado tanto en la pantalla
de confirmación como directamente en Postgres (`pedido` y `pedido_detalle`).

**Ronda 5 — login de cliente y pedidos a domicilio**: base de datos
recreada desde cero → Flyway aplica las 7 migraciones (incluida la nueva
zona de domicilio placeholder) → automatizado con Playwright: registro de
cliente desde el modal → sesión persistida en `localStorage` (verificada
con un reload real de la página) → login por separado con las mismas
credenciales (no solo registro-y-auto-login) → agregar productos al
carrito → elegir "Domicilio" → crear una dirección nueva con zona →
confirmar pedido → `POST /api/v1/pedidos` con `tipo=domicilio` y el JWT
del cliente → verificado en Postgres: `pedido.cliente_id`, `canal=web`,
`direccion_texto_snapshot`, `zona_domicilio_snapshot` y `total` ($60.000
de productos + $5.000 de domicilio = $65.000) todos correctos.

**Ronda 6 — pedido "local" desde el QR de la mesa**: base de datos
recreada desde cero → Flyway aplica las 8 migraciones (incluidas las
mesas placeholder con QR activo) → `GET /api/v1/mesas/qr/demo-mesa-1`
verificado con curl (200 con la mesa correcta; código inválido → 404) →
Playwright visitando `?mesa=demo-mesa-1`: banner "Pedido en mesa — Mesa 1"
visible, la pestaña "Mesa 1" del carrito preseleccionada automáticamente,
pedido confirmado sin login → verificado en Postgres: `tipo=local`,
`canal=qr`, `mesa_id` correcto, `cliente_id` vacío, total correcto
($62.000). Caso borde probado aparte: un código QR inválido en la URL no
rompe el catálogo — no muestra el banner, no ofrece la pestaña "Mesa", y
el resto del carrito (recoger/domicilio) sigue funcionando normal.

**Ronda 7 — combos, personalización de ítems, mis pedidos y pago**: base
de datos recreada desde cero → Flyway aplica las 10 migraciones (incluidos
un combo, una opción con dos valores y dos adicionales de demo) →
Playwright: registro → personalizar Nachos (opción "Tamaño: Grande" +
adicional "Queso extra") desde el nuevo modal → agregar el combo →
elegir método de pago "Efectivo" → confirmar pedido para recoger →
abrir "Mis pedidos" y expandir el detalle. Verificado en Postgres: el
pedido, sus dos líneas (`pedido_detalle`), la opción elegida
(`pedido_detalle_opcion`), el adicional elegido
(`pedido_detalle_adicional`) y el pago (`pago`, método Efectivo, monto
$100.000) — todos correctos y consistentes con el total mostrado en
pantalla. Esta ronda encontró y corrigió un bug real preexistente:
`GET /clientes/me/pedidos` (`ClienteController#misPedidos`) nunca tuvo
`@Transactional`, así que mapear `pedido.items` (colección LAZY) fallaba
con `LazyInitializationException` — nunca se había probado esta ruta con
una petición HTTP real hasta esta ronda. Corregido con
`@Transactional(readOnly = true)`, igual que los demás casos ya conocidos
de este mismo patrón.

**Ronda 8 — panel de staff completo (Cocina, Caja, Despachos,
Administración)**: base de datos recreada desde cero → Flyway aplica las
11 migraciones (incluidos 5 usuarios de staff de demo, uno por rol) →
flujo de negocio real encadenado con Playwright a través de los cuatro
roles: cliente anónimo pide para recoger → Caja confirma el pago → Cocina
inicia preparación y marca listo → Caja marca recogido; por separado, un
pedido a domicilio se lleva hasta despachado y Despachos lo asigna, marca
en camino y entregado. Se recorrieron las 11 pestañas de Administración y
se probaron acciones de escritura reales en la mayoría (crear producto,
marcar agotado, asignar/quitar opción y adicional, crear combo y
agregarle un componente, crear categoría, crear mesa y regenerar su QR,
crear usuario de staff y desactivarlo, editar permisos de un rol, crear
zona de domicilio y actualizar su tarifa). Todo verificado contra
Postgres, no solo contra la respuesta HTTP.

Esta ronda encontró y corrigió tres bugs reales:
- El índice de `/staff` redirigía siempre a Cocina sin importar el rol
  del usuario logueado, así que Caja/Despachos/Administrador aterrizaban
  en una pantalla sin permiso (403) en vez de su propio módulo. Corregido
  calculando el primer módulo visible según el rol real.
- `POST /informes/ventas` — el frontend mandaba `agrupar=day/week/month`
  (inglés); el backend solo acepta `'dia'|'semana'|'mes'` (español).
  Corregido el `<select>` del panel de Informes.
- `POST /zonas-domicilio` — el formulario de crear zona solo mandaba
  `nombre`, pero `ZonaDomicilioRequest.tarifa` es `@NotNull`: crea la
  zona y su primera tarifa en la misma operación. El backend devolvía
  422 y la zona nunca se creaba. Corregido agregando los campos de
  tarifa inicial y tiempo estimado al formulario.

También se confirmó, comparando con el estado real en Postgres, que dos
resultados "en false" durante las pruebas fueron falsos negativos del
script de prueba (tiempo de espera insuficiente antes de leer el DOM
recién re-renderizado), no bugs de la aplicación — se repitieron con más
espera y el estado en base de datos ya era correcto desde la primera vez.

**Ronda 9 — notificaciones reales, recuperación de contraseña y suite de
tests**: con curl real (no la suite de tests) — un pedido nuevo deja su
notificación "pendiente" y a los pocos segundos `NotificacionDeliveryJob`
la deja "enviada" con el log real de `LogNotificacionSender`; el flujo
completo de recuperación de contraseña (solicitar con email existente y
con uno inexistente, ambos 202 idénticos; confirmar con el token real
extraído del log; login con la contraseña vieja falla, con la nueva
funciona; reusar el mismo token falla) — los 5 pasos correctos. Luego,
formalizados como los primeros 13 tests automatizados de
`backend/src/test/java`, corriendo los 13 en verde contra Postgres real.
