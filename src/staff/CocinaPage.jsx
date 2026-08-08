import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { staffFetch } from './staffApi'

const TIPOS = ['', 'local', 'recoger', 'domicilio']

export default function CocinaPage() {
  const { accessToken } = useAuth()
  const [tipo, setTipo] = useState('')
  const [pedidos, setPedidos] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [actuando, setActuando] = useState(null)

  const cargar = useCallback(() => {
    setLoading(true)
    const query = tipo ? `?tipo=${tipo}` : ''
    staffFetch(accessToken, `/cocina/pedidos${query}`)
      .then((data) => {
        setPedidos(data)
        setLoading(false)
      })
      .catch((err) => {
        setError(err.message)
        setLoading(false)
      })
  }, [accessToken, tipo])

  useEffect(() => {
    cargar()
  }, [cargar])

  async function avanzar(pedido) {
    setActuando(pedido.id)
    setError(null)
    const accion = pedido.estado === 'pago_validado' ? 'iniciar-preparacion' : 'marcar-listo'
    try {
      await staffFetch(accessToken, `/pedidos/${pedido.id}/${accion}`, { method: 'POST' })
      cargar()
    } catch (err) {
      setError(err.message)
    } finally {
      setActuando(null)
    }
  }

  return (
    <div>
      <h1 className="staff-page-title">Cocina</h1>

      <div className="staff-toolbar">
        <select value={tipo} onChange={(e) => setTipo(e.target.value)}>
          {TIPOS.map((t) => (
            <option key={t} value={t}>
              {t === '' ? 'Todos los tipos' : t}
            </option>
          ))}
        </select>
        <button type="button" className="staff-button secondary" onClick={cargar}>
          Actualizar
        </button>
      </div>

      {error && <p className="staff-error">{error}</p>}
      {loading ? (
        <p className="staff-empty">Cargando…</p>
      ) : pedidos.length === 0 ? (
        <p className="staff-empty">No hay pedidos en cola.</p>
      ) : (
        <div className="staff-card-grid">
          {pedidos.map((pedido) => (
            <div key={pedido.id} className="staff-card">
              <div className="staff-card-header">
                <span className="staff-card-title">
                  #{pedido.id} · {pedido.tipo}
                </span>
                <span className="staff-badge">{pedido.estado}</span>
              </div>
              <span className="staff-card-meta">{new Date(pedido.creadoEn).toLocaleTimeString('es-CO')}</span>
              <ul className="staff-card-items">
                {pedido.items.map((item) => (
                  <li key={item.id}>
                    {item.cantidad}x {item.nombreSnapshot}
                  </li>
                ))}
              </ul>
              {pedido.observaciones && <span className="staff-card-meta">Obs: {pedido.observaciones}</span>}
              <div className="staff-card-actions">
                <button
                  type="button"
                  className="staff-button"
                  disabled={actuando === pedido.id}
                  onClick={() => avanzar(pedido)}
                >
                  {pedido.estado === 'pago_validado' ? 'Iniciar preparación' : 'Marcar listo'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
