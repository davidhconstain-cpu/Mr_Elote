import { useState } from 'react'
import { useCart } from '../context/CartContext'
import { useAuth } from '../context/AuthContext'
import { crearPedido } from '../api/pedidosApi'
import { formatPrice } from '../utils/formatPrice'
import AddressPicker from './AddressPicker'

export default function Cart() {
  const { items, setQuantity, removeItem, clear, total, count } = useCart()
  const { isAuthenticated, accessToken, openAuthModal } = useAuth()
  const [open, setOpen] = useState(false)
  const [tipo, setTipo] = useState('recoger')
  const [direccionId, setDireccionId] = useState(null)
  const [observaciones, setObservaciones] = useState('')
  const [status, setStatus] = useState('idle') // idle | sending | success | error
  const [error, setError] = useState(null)
  const [confirmedOrder, setConfirmedOrder] = useState(null)

  if (count === 0 && status !== 'success') {
    return null
  }

  const puedeConfirmar = tipo === 'recoger' || (tipo === 'domicilio' && isAuthenticated && direccionId != null)

  async function handleConfirmar() {
    setStatus('sending')
    setError(null)
    try {
      const pedido = await crearPedido({
        tipo,
        direccionId: tipo === 'domicilio' ? direccionId : undefined,
        items,
        observaciones,
        token: accessToken,
      })
      setConfirmedOrder(pedido)
      setStatus('success')
      clear()
      setObservaciones('')
      setDireccionId(null)
    } catch (err) {
      setError(err.message)
      setStatus('error')
    }
  }

  function handleCerrar() {
    setOpen(false)
    if (status === 'success') {
      setStatus('idle')
      setConfirmedOrder(null)
      setTipo('recoger')
    }
  }

  return (
    <>
      <button
        type="button"
        className="cart-button"
        onClick={() => setOpen(true)}
        aria-label={`Ver carrito (${count} ítems)`}
      >
        🛒
        {count > 0 && <span className="cart-count">{count}</span>}
      </button>

      {open && (
        <div className="cart-overlay" onClick={handleCerrar}>
          <aside className="cart-drawer" onClick={(e) => e.stopPropagation()}>
            <div className="cart-drawer-header">
              <h2>Tu pedido</h2>
              <button type="button" className="cart-close" onClick={handleCerrar} aria-label="Cerrar carrito">
                ✕
              </button>
            </div>

            {status === 'success' && confirmedOrder ? (
              <div className="cart-confirmation">
                <p className="cart-confirmation-title">¡Pedido recibido!</p>
                <p className="cart-confirmation-detail">
                  Pedido #{confirmedOrder.id} — {formatPrice(confirmedOrder.total)}
                </p>
                <p className="cart-confirmation-note">
                  {confirmedOrder.tipo === 'domicilio'
                    ? 'Te avisamos cuando salga hacia tu dirección.'
                    : 'Te avisamos cuando esté listo para recoger.'}
                </p>
                <button type="button" className="cart-confirm-button" onClick={handleCerrar}>
                  Cerrar
                </button>
              </div>
            ) : (
              <>
                {items.length === 0 ? (
                  <p className="cart-empty">Tu carrito está vacío.</p>
                ) : (
                  <ul className="cart-items">
                    {items.map((item) => (
                      <li key={item.productoId} className="cart-item">
                        <div className="cart-item-info">
                          <span className="cart-item-name">{item.nombre}</span>
                          <span className="cart-item-porcion">
                            {item.porcion} persona{item.porcion > 1 ? 's' : ''}
                          </span>
                        </div>
                        <div className="cart-item-controls">
                          <button
                            type="button"
                            onClick={() => setQuantity(item.productoId, item.cantidad - 1)}
                            aria-label={`Quitar una unidad de ${item.nombre}`}
                          >
                            −
                          </button>
                          <span className="cart-item-qty">{item.cantidad}</span>
                          <button
                            type="button"
                            onClick={() => setQuantity(item.productoId, item.cantidad + 1)}
                            aria-label={`Agregar una unidad de ${item.nombre}`}
                          >
                            +
                          </button>
                        </div>
                        <span className="cart-item-price">{formatPrice(item.price * item.cantidad)}</span>
                        <button
                          type="button"
                          className="cart-item-remove"
                          onClick={() => removeItem(item.productoId)}
                          aria-label={`Quitar ${item.nombre} del carrito`}
                        >
                          🗑
                        </button>
                      </li>
                    ))}
                  </ul>
                )}

                {items.length > 0 && (
                  <>
                    <div className="order-type-toggle">
                      <button
                        type="button"
                        className={tipo === 'recoger' ? 'active' : ''}
                        onClick={() => setTipo('recoger')}
                      >
                        Recoger
                      </button>
                      <button
                        type="button"
                        className={tipo === 'domicilio' ? 'active' : ''}
                        onClick={() => setTipo('domicilio')}
                      >
                        Domicilio
                      </button>
                    </div>

                    {tipo === 'domicilio' && !isAuthenticated && (
                      <div className="cart-login-prompt">
                        <p>Inicia sesión para pedir a domicilio.</p>
                        <button type="button" className="cart-confirm-button" onClick={openAuthModal}>
                          Iniciar sesión
                        </button>
                      </div>
                    )}

                    {tipo === 'domicilio' && isAuthenticated && (
                      <AddressPicker value={direccionId} onChange={setDireccionId} />
                    )}

                    <textarea
                      className="cart-observaciones"
                      placeholder="Observaciones (opcional): sin cebolla, para las 7pm, etc."
                      value={observaciones}
                      onChange={(e) => setObservaciones(e.target.value)}
                    />

                    <div className="cart-total">
                      <span>Total</span>
                      <span>{formatPrice(total)}</span>
                    </div>
                    {tipo === 'domicilio' && (
                      <p className="cart-domicilio-note">El costo de domicilio se agrega según tu zona.</p>
                    )}

                    {status === 'error' && <p className="cart-error">{error}</p>}
                    <button
                      type="button"
                      className="cart-confirm-button"
                      onClick={handleConfirmar}
                      disabled={status === 'sending' || !puedeConfirmar}
                    >
                      {status === 'sending'
                        ? 'Enviando...'
                        : tipo === 'domicilio'
                          ? 'Confirmar pedido a domicilio'
                          : 'Confirmar pedido para recoger'}
                    </button>
                  </>
                )}
              </>
            )}
          </aside>
        </div>
      )}
    </>
  )
}
