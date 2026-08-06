// Catálogo Mr. Elote
// Textos y precios verificados contra capturas de pantalla en alta
// resolución del sitio original. Los que aún no se han confirmado están
// marcados con un comentario "TODO".

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
    // TODO: confirmar descripción y foto (aún no enviadas)
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
    // TODO: confirmar descripción y foto (aún no enviadas)
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
    // TODO: confirmar descripción y foto (aún no enviadas)
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
    image: '/images/callejera.jpg',
  },
  {
    id: 'aborrajada',
    name: 'Aborrajada',
    description:
      'Papas a la Francesa, Salchicha Ranchera, Carne Angus, Madurito, Queso y Salsas Artesanales.',
    prices: [{ people: 2, price: 42000 }],
    image: '/images/aborrajada.jpg',
  },
  {
    id: 'chicharronuda',
    name: 'Chicharronuda',
    description:
      'Papas a la Francesa, Queso, Salchicha Americana, Chicharrón Carnudo y Salsas Artesanales.',
    prices: [{ people: 2, price: 40000 }],
    image: '/images/chicharronuda.jpg',
  },
  {
    id: 'ranchi-nuggets',
    name: 'Ranchi Nuggets',
    description:
      'Papas a la Francesa, Queso, Salchicha Ranchera, Nuggets de Pollo y Salsas Artesanales.',
    prices: [{ people: 2, price: 35000 }],
    image: '/images/ranchi-nuggets.jpg',
  },
  {
    id: 'nachos',
    name: 'Nachos',
    description:
      'Nachos con Queso, Pollo, Carne, Pico de Gallo y Salsas Artesanales.',
    prices: [{ people: 2, price: 35000 }],
    image: '/images/nachos.jpg',
  },
  {
    id: 'desgranado-especial',
    name: 'Desgranado Especial',
    description:
      'Carne, Pollo, Salchicha, Maduro, Maicitos, Papas a la Francesa, Queso, Ripio, Pollo Apanado, Chicharrón y Salsas Artesanales.',
    prices: [{ people: 6, price: 80000 }],
    image: '/images/desgranado-especial.jpg',
  },
  {
    id: 'porcion-chicharron',
    name: 'Porción de Chicharrón',
    description: 'Papas a la Francesa y Chicharrón Carnudo.',
    prices: [{ people: 1, price: 22000 }],
    image: '/images/porcion-chicharron.jpg',
  },
  {
    id: 'madurita',
    name: 'Madurita',
    description: 'Maduro Guayabo, Queso, Carne Desmechada y Salchicha Ranchera.',
    prices: [{ people: 1, price: 22000 }],
    image: '/images/madurita.jpg',
  },
  {
    id: 'burrito',
    name: 'Burrito',
    description:
      'Pollo, Carne, Maíz, Tocineta, Lechuga, Tortillas, Salsas Artesanales y Porción de Papas.',
    prices: [{ people: 1, price: 25000 }],
    image: '/images/burrito.jpg',
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
