import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { useAuth } from './AuthContext'

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
  const { isAuthenticated, openAuthModal } = useAuth()
  // Ítem que el cliente intentó agregar sin sesión: se guarda, se le pide
  // iniciar sesión, y al autenticarse entra solo al carrito.
  const [pendiente, setPendiente] = useState(null)

  const pushItem = useCallback((item) => {
    setItems((prev) => {
      const existing = prev.find((i) => i.key === item.key)
      if (existing) {
        return prev.map((i) => (i.key === item.key ? { ...i, cantidad: i.cantidad + 1 } : i))
      }
      return [...prev, { ...item, cantidad: 1 }]
    })
  }, [])

  // Regla del negocio: pedir siempre con cuenta, incluso para recoger en el
  // local, para que todo pedido quede asociado a un cliente identificable.
  const addItem = useCallback(
    (item) => {
      if (!isAuthenticated) {
        setPendiente(item)
        openAuthModal()
        return false
      }
      pushItem(item)
      return true
    },
    [isAuthenticated, openAuthModal, pushItem],
  )

  useEffect(() => {
    if (isAuthenticated && pendiente) {
      pushItem(pendiente)
      setPendiente(null)
    }
  }, [isAuthenticated, pendiente, pushItem])

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
