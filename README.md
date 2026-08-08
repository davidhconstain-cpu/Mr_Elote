# Mr. Elote — Catálogo y pedidos

Catálogo web (React + Vite), inspirado originalmente en el sitio de Canva
[catalogo-vivanta.my.canva.site/mr-elote](https://catalogo-vivanta.my.canva.site/mr-elote):
fondo negro, tipografía bold, navegación rápida por categorías y precios por
número de porciones. El menú ya no es texto estático: se carga en vivo desde
el backend real (`backend/`, Spring Boot), permite armar un carrito, crear
cuenta / iniciar sesión, y enviar el pedido para recoger, a domicilio o en
mesa (escaneando el QR de la mesa). Además, un panel de staff separado
(`/staff`) cubre cocina, caja, despachos y administración completa del
negocio.

## Desarrollo

Backend y frontend corren por separado:

```bash
# backend (puerto 8080) — ver backend/README.md para el detalle de Postgres/Flyway
cd backend
export DB_USER=mrelote DB_PASSWORD=mrelote
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# frontend (puerto 5173) — en otra terminal, desde la raíz del repo
npm install
npm run dev
```

El frontend apunta por defecto a `http://localhost:8080/api/v1`
(`VITE_API_BASE_URL`, ver `.env.example`).

## Estructura

- `src/hooks/useCatalog.js` + `src/api/catalogApi.js` — cargan el menú desde
  `GET /api/v1/productos` y agrupan las porciones de cada plato.
- `src/context/CartContext.jsx` — estado del carrito (agregar, quitar,
  cambiar cantidad, total). **Agregar al carrito exige sesión** en todos los
  casos, incluso para recoger en el local, para que todo pedido quede
  asociado a un cliente identificable: si no hay sesión abre el modal de
  login y guarda el ítem, que entra solo al carrito al autenticarse.
- `src/context/AuthContext.jsx` + `src/api/authApi.js` + `src/components/AuthModal.jsx`
  — login/registro de cliente, sesión (JWT) persistida en `localStorage`.
  El registro pide tipo y número de documento, nombres, apellidos, correo
  (con confirmación), teléfono opcional, contraseña (con confirmación) y los
  consentimientos de promociones y de términos y condiciones. Para entrar se
  puede usar **el correo o el número de documento**, y elegir entre
  contraseña o un **código de un solo uso**; también está el enlace de
  "¿Olvidaste tu contraseña?".
- `src/api/http.js` — cliente HTTP de las llamadas autenticadas. El access
  token vive 30 minutos; ante un 401 lo renueva con el refresh token y
  reintenta la llamada, y si el refresh tampoco sirve cierra la sesión y
  abre el login. Antes nadie lo renovaba: pasada media hora la interfaz
  seguía mostrando "Hola, ..." pero "Mis pedidos" se quedaba vacío y el
  pedido se creaba sin dueño.
- `src/api/clienteApi.js` + `src/api/zonasApi.js` + `src/components/AddressPicker.jsx`
  — direcciones del cliente autenticado y zonas de domicilio con su tarifa.
- `src/context/MesaContext.jsx` + `src/api/mesasApi.js` — resuelve el QR de
  la mesa desde `?mesa=<codigo>` en la URL (`GET /api/v1/mesas/qr/{codigo}`,
  público), lo recuerda en `localStorage` y lo expone al resto del catálogo.
- `src/components/MesaBanner.jsx` — aviso "Pedido en mesa — Mesa N" cuando
  hay una mesa resuelta.
- `src/api/pedidosApi.js` — envía el pedido armado a `POST /api/v1/pedidos`
  (tipo `recoger` anónimo, `domicilio` con el cliente autenticado y su
  dirección, o `local` con el código del QR de la mesa).
- `src/components/Cart.jsx` — botón flotante + panel del carrito, selector
  recoger/domicilio/mesa (la pestaña "Mesa N" solo aparece si el QR se
  resolvió) y confirmación del pedido.
- `src/components/AuthModal.jsx` — modal de login/registro, disparado desde
  el `Header` o desde el carrito al elegir domicilio sin sesión.
- `src/hooks/useCombos.js` + `src/components/CombosSection.jsx` — combos
  del backend (`GET /combos`), con su disponibilidad dinámica.
- `src/components/ItemCustomizeModal.jsx` — al agregar un producto con
  opciones (ej. tamaño) o adicionales (ej. queso extra), abre este modal
  en vez de agregarlo directo; cada combinación es una línea distinta del
  carrito.
- `src/components/MisPedidosPanel.jsx` — historial de pedidos del cliente
  autenticado, con detalle e historial de estados de cada uno.
- `src/api/metodosPagoApi.js` + `src/api/pagosApi.js` — selector de método
  de pago al confirmar (solo cliente autenticado; un pedido anónimo se
  paga en persona).
- `src/components/CategoryFilter.jsx` — barra de categorías con miniatura,
  fija bajo el header: **filtra** el catálogo (antes eran anclas `#id` que
  solo hacían scroll). Los chips se arman con el catálogo real, así que un
  producto nuevo creado desde el panel de administración aparece solo.
- `src/components/ProductCard.jsx` — tarjeta de producto (foto grande,
  nombre, descripción y una fila por porción con su botón "Agregar"),
  dispuestas en una grilla a todo el ancho de la página.
- `src/data/products.js` — bebidas/adiciones (todavía estáticas, ver abajo).
- `src/components/` — Header, ProductCard, CategoryFilter, ProductPhoto,
  DrinksSection, AdicionesSection, Footer.
- `src/CatalogApp.jsx` + `src/staff/` — la app se divide en dos por ruta
  (`src/App.jsx`, `react-router-dom`): `/*` es el catálogo de cliente
  (`CatalogApp`), `/staff/*` es el panel de staff (login propio que
  reutiliza `AuthContext`, navegación según el rol). Ver
  `backend/README.md` → "Panel de staff" para el detalle de cada módulo
  (Cocina, Caja, Despachos, Administración).
- `backend/` — API REST en Java/Spring Boot (ver `backend/README.md`).

## Pendiente por revisar

- Precios reales de la sección **Bebidas** (hoy muestra "Consultar").
- Lista y precios reales de **Adiciones** (en el sitio original solo se veía
  el enlace, no el contenido) — tampoco existen todavía en el backend.
- Fotos de Desgranado, Salchipapa y Mechada (muestran una ilustración
  genérica de comida callejera dibujada en SVG, no una foto de stock, para
  no meter imágenes con licencia ajena en el repo; se reemplaza sola al
  subir la foto real).
  Para agregarlas: colócalas en `public/images/` y súbelas al producto
  correspondiente vía `POST /api/v1/productos/{id}/imagenes`, o agrégalas
  directamente en `backend/src/main/resources/db/migration/V6__seed_menu_mrelote.sql`
  antes de la primera vez que se aplique la migración.
- Zonas y tarifas de domicilio reales — hoy solo hay una zona placeholder
  sembrada (`backend/.../V7__seed_zona_domicilio_placeholder.sql`,
  $5.000/30 min) para que el flujo sea funcional; reemplázala con las
  zonas y tarifas reales vía `POST /api/v1/zonas-domicilio`.
- Mesas reales de Mr. Elote — hoy solo hay 3 mesas placeholder sembradas
  (`backend/.../V8__seed_mesas_qr_placeholder.sql`, códigos QR de ejemplo
  `demo-mesa-1/2/3`) para que el flujo "local" sea probable; reemplázalas
  con las mesas reales vía `POST /api/v1/mesas` (regenera el QR con
  `POST /api/v1/mesas/{id}/qr/regenerar`) e imprime la URL
  `https://<tu-dominio>/?mesa=<codigo>` como QR en cada mesa.
- El combo y las opciones/adicionales de demo (Nachos con tamaño y
  adicionales, "Combo Callejera + Chicharrón") son de ejemplo, no ofertas
  reales confirmadas — ver `V9__seed_opciones_adicionales_demo.sql` y
  `V10__seed_combo_demo.sql`.
- Los 5 usuarios de staff (uno por rol) son de demo/desarrollo
  (`V11__seed_usuarios_staff_demo.sql`, contraseña `password123`) — no
  crear usuarios reales con esa contraseña en producción.

## Estado del proyecto

Los 15 módulos de la especificación de requisitos están implementados con
lógica real, verificados de extremo a extremo (curl, Playwright, y una
suite de 19 tests automatizados — ver `backend/README.md`). Lo único que
queda genuinamente bloqueado, no solo pendiente de tiempo, es lo que
depende de credenciales de un proveedor externo que este entorno no
tiene: envío real de notificaciones/recuperación de contraseña por
SMS/email (la lógica ya está completa, solo falta contratar el proveedor)
y almacenamiento de imágenes en S3. Todo lo demás en esta lista es dato
real de Mr. Elote por confirmar, no código por escribir.
