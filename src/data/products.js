// Navegación y secciones que todavía no vienen del backend: los platos
// (con precios confirmados) ahora se cargan en vivo desde la API vía
// src/hooks/useCatalog.js — ver src/App.jsx. Bebidas y adiciones se quedan
// aquí como datos estáticos porque sus precios reales aún no se han
// confirmado (ver TODO abajo).

export const quickLinks = [
  { id: 'desgranado', label: 'Desgranado' },
  { id: 'salchipapa', label: 'Salchipapa' },
  { id: 'mechada', label: 'Mechada' },
  { id: 'aborrajada', label: 'Aborrajada' },
  { id: 'callejera', label: 'Callejera' },
  { id: 'chicharronuda', label: 'Chicharronuda' },
  { id: 'bebidas', label: 'Bebidas' },
  { id: 'adiciones', label: 'Adiciones' },
]

export const drinks = [
  { id: 'limonada-natural', name: 'Limonada Natural', price: null },
  { id: 'limonada-fresa', name: 'Limonada de Fresa', price: null },
]

// Placeholder: en el sitio original esta sección solo se veía como un enlace
// ("ADICIONES"); no se alcanzó a leer el contenido real. Reemplaza estos
// ítems con la lista y precios reales.
export const adiciones = [
  { name: 'Queso extra', price: null },
  { name: 'Carne extra', price: null },
  { name: 'Tocineta extra', price: null },
  { name: 'Salsas adicionales', price: null },
]
