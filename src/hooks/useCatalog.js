import { useEffect, useState } from 'react'
import { fetchProductos } from '../api/catalogApi'
import { slugify } from '../utils/slugify'

// Un plato con varias porciones llega del backend como varias filas de
// producto con el mismo nombre (una por porción); las agrupamos aquí para
// mantener la forma { id, name, description, prices, image } que ya
// consumen ProductSection/PriceRow.
function groupProductos(productos) {
  const groups = new Map()

  for (const p of productos) {
    if (!p.activo) continue
    if (!groups.has(p.nombre)) {
      groups.set(p.nombre, {
        id: slugify(p.nombre),
        name: p.nombre,
        description: p.descripcion,
        prices: [],
        image: p.imagenes?.[0] ?? null,
      })
    }
    if (p.disponible) {
      groups.get(p.nombre).prices.push({
        people: p.porcionPersonas ?? 1,
        price: Number(p.precio),
        productoId: p.id,
        tieneOpciones: Boolean(p.tieneOpciones),
        adicionales: p.adicionales ?? [],
      })
    }
  }

  return Array.from(groups.values())
    .filter((group) => group.prices.length > 0)
    .map((group) => ({
      ...group,
      prices: group.prices.sort((a, b) => a.people - b.people),
    }))
}

export function useCatalog() {
  const [state, setState] = useState({ loading: true, error: null, products: [] })

  useEffect(() => {
    let cancelled = false

    fetchProductos()
      .then((productos) => {
        if (cancelled) return
        setState({ loading: false, error: null, products: groupProductos(productos) })
      })
      .catch((error) => {
        if (cancelled) return
        setState({ loading: false, error: error.message, products: [] })
      })

    return () => {
      cancelled = true
    }
  }, [])

  return state
}
