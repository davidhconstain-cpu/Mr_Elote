# API REST — Sistema de Pedidos Mr. Elote

Especificación completa en [`api/openapi.yaml`](../api/openapi.yaml) (OpenAPI 3.0,
60 rutas / 77 operaciones / 48 esquemas). Verificada de dos formas, no solo
revisada a ojo:

1. **Validación de esquema** con `openapi-spec-validator` — el documento es
   OpenAPI 3.0 válido, sin referencias rotas.
2. **Servidor mock real** (`@stoplight/prism-cli`): se levantó un servidor
   HTTP a partir del spec y se le hicieron peticiones reales. Confirmó que
   las 60 rutas registran correctamente y, en particular, que el modelo de
   seguridad está bien codificado: los endpoints públicos (`/categorias`,
   `/productos`, `/mesas/qr/{codigo}`, `/pagos/webhook/{proveedor}`)
   responden sin token, y los protegidos (`/pedidos/{id}/confirmar-pago`)
   devuelven `401` sin `Authorization: Bearer`.

## Convenciones

- **Base path:** `/api/v1`.
- **Formato:** JSON, campos en `camelCase`.
- **Autenticación:** JWT Bearer (`Authorization: Bearer <token>`) salvo en
  los endpoints marcados explícitamente como públicos (catálogo de lectura,
  login/registro, resolución de QR de mesa, webhook de pagos).
- **Paginación:** `page` (0-indexado), `size`, `sort` (ej. `creadoEn,desc`).
  Respuesta envuelta en `{ contenido, pagina, tamano, totalElementos, totalPaginas }`.
- **Errores:** siempre `{ codigo, mensaje, detalles[] }` con el código HTTP
  correspondiente (`401` no autenticado, `403` sin permiso, `404` no existe,
  `409` conflicto de estado — ej. transición de pedido no permitida, `422`
  violación de regla de negocio).
- **Idempotencia:** `POST /pedidos` acepta un header `Idempotency-Key` para
  evitar duplicados ante reintentos de red (RNF-007). El webhook de pagos
  es idempotente por `referenciaExterna` (RNF-008).

## Pedidos: un endpoint por transición, no un PATCH genérico de estado

En vez de un único `PATCH /pedidos/{id}/estado`, cada transición de la
tabla "Transiciones de estado del pedido por rol" (documento v1.1, sección
8.2) tiene su propio endpoint de acción: `confirmar-pago`,
`iniciar-preparacion`, `marcar-listo`, `entregar`, `recoger`, `despachar`,
`anular`. Esto permite aplicar el rol autorizado y las validaciones de cada
transición de forma explícita, en vez de tener que replicar esa tabla como
lógica condicional dentro de un solo endpoint genérico.

## Matriz de permisos por grupo de endpoints

| Grupo | Cliente | Mesero | Caja | Cocina | Despachos | Administrador |
|---|---|---|---|---|---|---|
| Catálogo (lectura) | público | público | público | público | público | público |
| Catálogo (escritura) | | | | | | ✓ |
| Pedidos (crear) | ✓ | ✓ | | | | ✓ |
| Pedidos (leer propio / buscar todos) | propio | ✓ | ✓ | ✓ (cola) | ✓ (cola) | ✓ |
| Pedidos (modificar ítems / anular) | | ✓ | ✓ | | | ✓ |
| Confirmar / validar pago | | | ✓ | | | |
| Iniciar preparación / marcar listo | | | | ✓ | | |
| Entregar / recoger | | ✓ | ✓ | | | |
| Despachar y gestionar despacho | | | | | ✓ | ✓ |
| Caja (apertura, movimientos, cierre) | | | ✓ | | | ✓ (historial) |
| Usuarios, roles, permisos | | | | | | ✓ |
| Auditoría | | | | | | ✓ |
| Informes / dashboard | | | | | | ✓ |
| Configuración | | | | | | ✓ |

## Próximos pasos sugeridos

- Generar el esqueleto de controladores Spring Boot a partir de `openapi.yaml`
  (openapi-generator, `spring` generator) para no escribir las firmas a mano.
- Definir los DTOs de validación (`@Valid`) por endpoint según los `required`
  del spec.
- Especificación de eventos asíncronos (colas) para las notificaciones que
  se disparan en cada transición de pedido.
