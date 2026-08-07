import { createContext, useCallback, useContext, useMemo, useState } from 'react'

const CartContext = createContext(null)

// Item: { productoId, nombre, porcion, price, cantidad }
export function CartProvider({ children }) {
  const [items, setItems] = useState([])

  const addItem = useCallback((product) => {
    setItems((prev) => {
      const existing = prev.find((item) => item.productoId === product.productoId)
      if (existing) {
        return prev.map((item) =>
          item.productoId === product.productoId ? { ...item, cantidad: item.cantidad + 1 } : item,
        )
      }
      return [...prev, { ...product, cantidad: 1 }]
    })
  }, [])

  const removeItem = useCallback((productoId) => {
    setItems((prev) => prev.filter((item) => item.productoId !== productoId))
  }, [])

  const setQuantity = useCallback((productoId, cantidad) => {
    setItems((prev) => {
      if (cantidad <= 0) return prev.filter((item) => item.productoId !== productoId)
      return prev.map((item) => (item.productoId === productoId ? { ...item, cantidad } : item))
    })
  }, [])

  const clear = useCallback(() => setItems([]), [])

  const quantityOf = useCallback(
    (productoId) => items.find((item) => item.productoId === productoId)?.cantidad ?? 0,
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
