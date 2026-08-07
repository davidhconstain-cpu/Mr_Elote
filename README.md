# Mr. Elote — Catálogo y pedidos

Catálogo web (React + Vite), inspirado originalmente en el sitio de Canva
[catalogo-vivanta.my.canva.site/mr-elote](https://catalogo-vivanta.my.canva.site/mr-elote):
fondo negro, tipografía bold, navegación rápida por categorías y precios por
número de porciones. El menú ya no es texto estático: se carga en vivo desde
el backend real (`backend/`, Spring Boot) y permite armar un carrito y enviar
el pedido para recoger.

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
- `src/api/pedidosApi.js` — envía el pedido armado a `POST /api/v1/pedidos`
  (tipo `recoger`, sin necesidad de login).
- `src/components/Cart.jsx` — botón flotante + panel del carrito y
  confirmación del pedido.
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
- El flujo de pedidos solo cubre "recoger" (anónimo, sin login). Domicilio
  requiere cliente autenticado y "local" requiere QR de mesa — ninguno de
  los dos tiene todavía interfaz en el catálogo.
