# Mr. Elote — Catálogo

Catálogo web (React + Vite) inspirado en el sitio de Canva
[catalogo-vivanta.my.canva.site/mr-elote](https://catalogo-vivanta.my.canva.site/mr-elote):
fondo negro, tipografía bold, navegación rápida por categorías y precios por
número de porciones.

## Desarrollo

```bash
npm install
npm run dev
```

## Estructura

- `src/data/products.js` — productos, precios y descripciones del menú.
- `src/components/` — Header, QuickNav, ProductSection, DrinksSection,
  AdicionesSection, Footer.

## Pendiente por revisar

El contenido se transcribió desde capturas de pantalla de baja resolución del
sitio original, así que antes de publicar conviene verificar:

- Textos exactos de las descripciones de cada producto en
  `src/data/products.js`.
- Precios y sabores reales de la sección **Bebidas**.
- Lista y precios reales de **Adiciones** (en el sitio original solo se veía
  el enlace, no el contenido).
- Fotos de producto: cada `ProductSection` muestra un placeholder cuando
  `product.image` es `null`. Para agregar una foto real, coloca el archivo en
  `public/images/` y actualiza el campo `image` del producto correspondiente,
  por ejemplo `image: '/images/desgranado.jpg'`.
