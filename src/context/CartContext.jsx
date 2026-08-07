import { createContext, useCallback, useContext, useMemo, useState } from 'react'

const CartContext = createContext(null)

// Item: { key, tipo: 'producto'|'combo', productoId?, comboId?, nombre,
// porcion?, opciones?: [{valorOpcionId,nombre,precioAdicional}],
// adicionales?: [{adicionalId,nombre,precio}], price, cantidad }
//
// `key` identifica la línea del carrito: dos veces el mismo producto con
// distintas opciones/adicionales son líneas separadas, por eso no basta con
// productoId/comboId como antes.
export function CartProvider({ children }) {
  const [items, setItems] = useState([])

  const addItem = useCallback((item) => {
    setItems((prev) => {
      const existing = prev.find((i) => i.key === item.key)
      if (existing) {
        return prev.map((i) => (i.key === item.key ? { ...i, cantidad: i.cantidad + 1 } : i))
      }
      return [...prev, { ...item, cantidad: 1 }]
    })
  }, [])

  const removeItem = useCallback((key) => {
    setItems((prev) => prev.filter((i) => i.key !== key))
  }, [])

  const setQuantity = useCallback((key, cantidad) => {
    setItems((prev) => {
      if (cantidad <= 0) return prev.filter((i) => i.key !== key)
      return prev.map((i) => (i.key === key ? { ...i, cantidad } : i))
    })
  }, [])

  const clear = useCallback(() => setItems([]), [])

  const quantityOf = useCallback(
    (key) => items.find((i) => i.key === key)?.cantidad ?? 0,
    [items],
  )

  const total = useMemo(() => items.reduce((sum, item) => sum + item.price * item.cantidad, 0), [items])
  const count = useMemo(() => items.reduce((sum, item) => sum + item.cantidad, 0), [items])

  const value = useMemo(
    () => ({ items, addItem, removeItem, setQuantity, clear, quantityOf, total, count }),
    [items, addItem, removeItem, setQuantity, clear, quantityOf, total, count],
  )

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

export function useCart() {
  const context = useContext(CartContext)
  if (!context) {
    throw new Error('useCart debe usarse dentro de un CartProvider')
  }
  return context
}
