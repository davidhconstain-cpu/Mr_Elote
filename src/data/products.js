// Catálogo Mr. Elote
// NOTA: la descripción y precios se transcribieron desde capturas de pantalla
// de baja resolución del sitio original en Canva. Revisa y ajusta los textos
// y precios marcados como aproximados antes de publicar.

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

export const products = [
  {
    id: 'desgranado',
    name: 'Desgranado',
    description:
      'Papas a la Francesa, Salchicha Americana, Queso, Maíz, Carne, Pollo, Pepitas y Salsas Artesanales.',
    prices: [
      { people: 1, price: 23000 },
      { people: 2, price: 30000 },
      { people: 4, price: 50000 },
    ],
    image: null,
  },
  {
    id: 'salchipapa',
    name: 'Salchipapa',
    description:
      'Papas a la Francesa, Salchicha Americana, Tocineta, Queso, Maíz, Pollo, Pepitas y Salsas Artesanales.',
    prices: [
      { people: 1, price: 18000 },
      { people: 2, price: 25000 },
      { people: 4, price: 40000 },
    ],
    image: null,
  },
  {
    id: 'mechada',
    name: 'Mechada',
    description:
      'Carne Desmechada, Maduro, Salchicha Americana, Papas a la Francesa, Pico de Gallo y Salsas Artesanales.',
    prices: [{ people: 2, price: 45000 }],
    image: null,
  },
  {
    id: 'callejera',
    name: 'Callejera',
    description:
      'Chorizo, Chicharrón, Yuca, Queso, Papas a la Francesa y Salsas Artesanales.',
    prices: [{ people: 2, price: 45000 }],
    image: null,
  },
  {
    id: 'aborrajada',
    name: 'Aborrajada',
    description:
      'Papas a la Francesa, Salchicha Americana, Carne Angus, Maíz, Frito, Queso y Salsas Artesanales.',
    prices: [{ people: 2, price: 42000 }],
    image: null,
  },
  {
    id: 'chicharronuda',
    name: 'Chicharronuda',
    description:
      'Papas a la Francesa, Queso, Salchicha Americana, Chicharrón Carnudo y Salsas Artesanales.',
    prices: [{ people: 2, price: 40000 }],
    image: null,
  },
  {
    id: 'ranchi-nuggets',
    name: 'Ranchi Nuggets',
    description:
      'Papas a la Francesa, Queso, Salchicha Ranchera, Nuggets de Pollo y Salsas Artesanales.',
    prices: [{ people: 2, price: 35000 }],
    image: null,
  },
  {
    id: 'nachos',
    name: 'Nachos',
    description:
      'Nachos con Queso, Pollo, Carne, Pico de Gallo y Salsas Artesanales.',
    prices: [{ people: 2, price: 35000 }],
    image: null,
  },
  {
    id: 'desgranado-especial',
    name: 'Desgranado Especial',
    description:
      'Carne, Pollo, Salchicha Americana, Papas a la Francesa, Queso, Pollo Asado, Chicharrón y Salsas Artesanales.',
    prices: [{ people: 4, price: 80000 }],
    image: null,
  },
  {
    id: 'porcion-chicharron',
    name: 'Porción de Chicharrón',
    description: 'Papas a la Francesa y Chicharrón Carnudo.',
    prices: [{ people: 1, price: 22000 }],
    image: null,
  },
  {
    id: 'madurita',
    name: 'Madurita',
    description:
      'Maduro, Guayaba, Queso, Carne Desmechada y Salsas Artesanales.',
    prices: [{ people: 1, price: 22000 }],
    image: null,
  },
  {
    id: 'burrito',
    name: 'Burrito',
    description:
      'Pollo, Carne, Maíz, Tortilla, Lechuga, Salsas Artesanales y Porción de Papas.',
    prices: [{ people: 1, price: 25000 }],
    image: null,
  },
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
