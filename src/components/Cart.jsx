import { useEffect, useState } from 'react'
import { useCart } from '../context/CartContext'
import { useAuth } from '../context/AuthContext'
import { useMesa } from '../context/MesaContext'
import { crearPedido } from '../api/pedidosApi'
import { registrarPago } from '../api/pagosApi'
import { fetchMetodosPago } from '../api/metodosPagoApi'
import { formatPrice } from '../utils/formatPrice'
import AddressPicker from './AddressPicker'

function detalleItem(item) {
  const partes = []
  if (item.porcion) partes.push(`${item.porcion} persona${item.porcion > 1 ? 's' : ''}`)
  for (const o of item.opciones ?? []) partes.push(o.nombre)
  for (const a of item.adicionales ?? []) partes.push(`+ ${a.nombre}`)
  return partes.join(' · ')
}

export default function Cart() {
  const { items, setQuantity, removeItem, clear, total, count } = useCart()
  const { isAuthenticated, accessToken, openAuthModal } = useAuth()
  const { mesa, codigo: mesaCodigo, loading: mesaLoading } = useMesa()
  const [open, setOpen] = useState(false)
  const [tipo, setTipo] = useState('recoger')
  const [tipoTouched, setTipoTouched] = useState(false)
  const [direccionId, setDireccionId] = useState(null)
  const [observaciones, setObservaciones] = useState('')
  const [metodos, setMetodos] = useState([])
  const [metodoPagoId, setMetodoPagoId] = useState('')
  const [status, setStatus] = useState('idle') // idle | sending | success | error
  const [error, setError] = useState(null)
  const [confirmedOrder, setConfirmedOrder] = useState(null)
  const [pagoAviso, setPagoAviso] = useState(null)

  useEffect(() => {
    if (!mesaLoading && mesa && !tipoTouched) {
      setTipo('local')
    }
  }, [mesaLoading, mesa, tipoTouched])

  useEffect(() => {
    if (!isAuthenticated) return
    let cancelled = false
    fetchMetodosPago()
      .then((data) => {
        if (!cancelled) setMetodos(data)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [isAuthenticated])

  if (count === 0 && status !== 'success') {
    return null
  }

  function elegirTipo(nuevoTipo) {
    setTipo(nuevoTipo)
    setTipoTouched(true)
  }

  const puedeConfirmar =
    tipo === 'recoger' ||
    (tipo === 'domicilio' && isAuthenticated && direccionId != null) ||
    (tipo === 'local' && mesa != null)

  async function handleConfirmar() {
    setStatus('sending')
    setError(null)
    setPagoAviso(null)
    try {
      const pedido = await crearPedido({
        tipo,
        direccionId: tipo === 'domicilio' ? direccionId : undefined,
        mesaCodigoQr: tipo === 'local' ? mesaCodigo : undefined,
        items,
        observaciones,
        token: accessToken,
      })

      if (isAuthenticated && metodoPagoId) {
        try {
          await registrarPago(accessToken, pedido.id, { metodoPagoId: Number(metodoPagoId), monto: pedido.total })
        } catch (pagoErr) {
          setPagoAviso(`El pedido se creó, pero el pago no quedó registrado: ${pagoErr.message}`)
        }
      }

      setConfirmedOrder(pedido)
      setStatus('success')
      clear()
      setObservaciones('')
      setDireccionId(null)
      setMetodoPagoId('')
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
      setPagoAviso(null)
      setTipo(mesa ? 'local' : 'recoger')
      setTipoTouched(false)
    }
  }

  const etiquetaConfirmar =
    tipo === 'domicilio'
      ? 'Confirmar pedido a domicilio'
      : tipo === 'local'
        ? 'Confirmar pedido en la mesa'
        : 'Confirmar pedido para recoger'

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
                    : confirmedOrder.tipo === 'local'
                      ? 'Ya va para tu mesa, en un momento te lo llevamos.'
                      : 'Te avisamos cuando esté listo para recoger.'}
                </p>
                {pagoAviso && <p className="cart-error">{pagoAviso}</p>}
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
                      <li key={item.key} className="cart-item">
                        <div className="cart-item-info">
                          <span className="cart-item-name">{item.nombre}</span>
                          {detalleItem(item) && <span className="cart-item-porcion">{detalleItem(item)}</span>}
                        </div>
                        <div className="cart-item-controls">
                          <button
                            type="button"
                            onClick={() => setQuantity(item.key, item.cantidad - 1)}
                            aria-label={`Quitar una unidad de ${item.nombre}`}
                          >
                            −
                          </button>
                          <span className="cart-item-qty">{item.cantidad}</span>
                          <button
                            type="button"
                            onClick={() => setQuantity(item.key, item.cantidad + 1)}
                            aria-label={`Agregar una unidad de ${item.nombre}`}
                          >
                            +
                          </button>
                        </div>
                        <span className="cart-item-price">{formatPrice(item.price * item.cantidad)}</span>
                        <button
                          type="button"
                          className="cart-item-remove"
                          onClick={() => removeItem(item.key)}
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
                        onClick={() => elegirTipo('recoger')}
                      >
                        Recoger
                      </button>
                      <button
                        type="button"
                        className={tipo === 'domicilio' ? 'active' : ''}
                        onClick={() => elegirTipo('domicilio')}
                      >
                        Domicilio
                      </button>
                      {mesa && (
                        <button
                          type="button"
                          className={tipo === 'local' ? 'active' : ''}
                          onClick={() => elegirTipo('local')}
                        >
                          Mesa {mesa.numero}
                        </button>
                      )}
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

                    {isAuthenticated && metodos.length > 0 && (
                      <label className="cart-payment-picker">
                        <span>Método de pago</span>
                        <select value={metodoPagoId} onChange={(e) => setMetodoPagoId(e.target.value)}>
                          <option value="">Pagar en persona</option>
                          {metodos.map((m) => (
                            <option key={m.id} value={m.id}>
                              {m.nombre}
                            </option>
                          ))}
                        </select>
                      </label>
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
                      {status === 'sending' ? 'Enviando...' : etiquetaConfirmar}
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
