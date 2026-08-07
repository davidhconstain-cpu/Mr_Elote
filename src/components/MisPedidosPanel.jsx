import { useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { misPedidos, historialPedido } from '../api/clienteApi'
import { formatPrice } from '../utils/formatPrice'

const ESTADO_LABEL = {
  creado: 'Creado',
  pago_pendiente: 'Pago pendiente',
  pago_validado: 'Pago validado',
  en_preparacion: 'En preparación',
  listo: 'Listo',
  entregado: 'Entregado',
  recogido: 'Recogido',
  despachado: 'Despachado',
  rechazado: 'Rechazado',
  devuelto: 'Devuelto',
  no_recibido: 'No recibido',
  anulado: 'Anulado',
}

function formatFecha(iso) {
  return new Date(iso).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' })
}

export default function MisPedidosPanel({ onClose }) {
  const { accessToken } = useAuth()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [pedidos, setPedidos] = useState([])
  const [expandidoId, setExpandidoId] = useState(null)
  const [historial, setHistorial] = useState({})

  useEffect(() => {
    let cancelled = false
    misPedidos(accessToken)
      .then((page) => {
        if (cancelled) return
        setPedidos(page.content ?? [])
        setLoading(false)
      })
      .catch((err) => {
        if (cancelled) return
        setError(err.message)
        setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [accessToken])

  function alternarExpandido(pedido) {
    if (expandidoId === pedido.id) {
      setExpandidoId(null)
      return
    }
    setExpandidoId(pedido.id)
    if (!historial[pedido.id]) {
      historialPedido(accessToken, pedido.id)
        .then((data) => setHistorial((prev) => ({ ...prev, [pedido.id]: data })))
        .catch(() => setHistorial((prev) => ({ ...prev, [pedido.id]: [] })))
    }
  }

  return (
    <div className="cart-overlay" onClick={onClose}>
      <aside className="cart-drawer" onClick={(e) => e.stopPropagation()}>
        <div className="cart-drawer-header">
          <h2>Mis pedidos</h2>
          <button type="button" className="cart-close" onClick={onClose} aria-label="Cerrar">
            ✕
          </button>
        </div>

        {loading && <p className="cart-status">Cargando…</p>}
        {error && <p className="cart-error">{error}</p>}
        {!loading && !error && pedidos.length === 0 && (
          <p className="cart-empty">Todavía no has hecho ningún pedido.</p>
        )}

        <ul className="mis-pedidos-list">
          {pedidos.map((pedido) => (
            <li key={pedido.id} className="mis-pedidos-item">
              <button type="button" className="mis-pedidos-summary" onClick={() => alternarExpandido(pedido)}>
                <div className="mis-pedidos-summary-info">
                  <span className="mis-pedidos-id">
                    Pedido #{pedido.id} · {pedido.tipo}
                  </span>
                  <span className="mis-pedidos-fecha">{formatFecha(pedido.creadoEn)}</span>
                </div>
                <div className="mis-pedidos-summary-right">
                  <span className={`estado-badge estado-${pedido.estado}`}>
                    {ESTADO_LABEL[pedido.estado] ?? pedido.estado}
                  </span>
                  <span className="cart-item-price">{formatPrice(pedido.total)}</span>
                </div>
              </button>

              {expandidoId === pedido.id && (
                <div className="mis-pedidos-detalle">
                  <ul className="mis-pedidos-detalle-items">
                    {pedido.items.map((item) => (
                      <li key={item.id}>
                        {item.cantidad}x {item.nombreSnapshot} — {formatPrice(item.subtotalLinea)}
                      </li>
                    ))}
                  </ul>
                  {pedido.observaciones && (
                    <p className="mis-pedidos-observaciones">Obs: {pedido.observaciones}</p>
                  )}
                  <p className="mis-pedidos-detalle-title">Historial</p>
                  <ul className="mis-pedidos-historial">
                    {(historial[pedido.id] ?? []).map((h) => (
                      <li key={h.id}>
                        {formatFecha(h.creadoEn)} — {h.campoModificado}: {h.valorAnterior} → {h.valorNuevo}
                      </li>
                    ))}
                    {historial[pedido.id]?.length === 0 && <li>Sin cambios registrados.</li>}
                  </ul>
                </div>
              )}
            </li>
          ))}
        </ul>
      </aside>
    </div>
  )
}
