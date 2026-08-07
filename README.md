# Mr. Elote — Catálogo y pedidos

Catálogo web (React + Vite), inspirado originalmente en el sitio de Canva
[catalogo-vivanta.my.canva.site/mr-elote](https://catalogo-vivanta.my.canva.site/mr-elote):
fondo negro, tipografía bold, navegación rápida por categorías y precios por
número de porciones. El menú ya no es texto estático: se carga en vivo desde
el backend real (`backend/`, Spring Boot), permite armar un carrito, crear
cuenta / iniciar sesión, y enviar el pedido para recoger, a domicilio o en
mesa (escaneando el QR de la mesa).

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
  cambiar cantidad, total).
- `src/context/AuthContext.jsx` + `src/api/authApi.js` — login/registro de
  cliente, sesión (JWT) persistida en `localStorage`.
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
- `src/data/products.js` — navegación rápida, y bebidas/adiciones (todavía
  estáticas, ver más abajo).
- `src/components/` — Header, QuickNav, ProductSection, PriceRow,
  DrinksSection, AdicionesSection, Footer.
- `backend/` — API REST en Java/Spring Boot (ver `backend/README.md`).

## Pendiente por revisar

- Precios reales de la sección **Bebidas** (hoy muestra "Consultar").
- Lista y precios reales de **Adiciones** (en el sitio original solo se veía
  el enlace, no el contenido) — tampoco existen todavía en el backend.
- Fotos de Desgranado, Salchipapa y Mechada (muestran un placeholder).
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
