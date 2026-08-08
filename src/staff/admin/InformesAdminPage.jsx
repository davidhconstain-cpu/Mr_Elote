import { useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { staffFetch } from '../staffApi'
import { formatPrice } from '../../utils/formatPrice'

export default function InformesAdminPage() {
  const { accessToken } = useAuth()
  const [resumen, setResumen] = useState(null)
  const [ventas, setVentas] = useState([])
  const [masVendidos, setMasVendidos] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [agrupar, setAgrupar] = useState('dia')

  useEffect(() => {
    setLoading(true)
    Promise.all([
      staffFetch(accessToken, '/dashboard/resumen'),
      staffFetch(accessToken, `/informes/ventas?agrupar=${agrupar}`),
      staffFetch(accessToken, '/informes/productos-mas-vendidos'),
    ])
      .then(([r, v, mv]) => {
        setResumen(r)
        setVentas(v)
        setMasVendidos(mv)
        setLoading(false)
      })
      .catch((err) => {
        setError(err.message)
        setLoading(false)
      })
  }, [accessToken, agrupar])

  if (loading) return <p className="staff-empty">Cargando…</p>

  return (
    <div>
      {error && <p className="staff-error">{error}</p>}

      {resumen && (
        <div className="staff-kpis">
          <div className="staff-kpi">
            <span className="staff-kpi-label">Pedidos hoy</span>
            <span className="staff-kpi-value">{resumen.numeroPedidos}</span>
          </div>
          <div className="staff-kpi">
            <span className="staff-kpi-label">Ventas hoy</span>
            <span className="staff-kpi-value">{formatPrice(resumen.ventasTotal)}</span>
          </div>
          <div className="staff-kpi">
            <span className="staff-kpi-label">Ticket promedio</span>
            <span className="staff-kpi-value">{formatPrice(resumen.ticketPromedio)}</span>
          </div>
        </div>
      )}

      <div className="staff-section">
        <div className="staff-toolbar">
          <p className="staff-section-title" style={{ margin: 0 }}>
            Ventas por periodo
          </p>
          <select value={agrupar} onChange={(e) => setAgrupar(e.target.value)}>
            <option value="dia">Por día</option>
            <option value="semana">Por semana</option>
            <option value="mes">Por mes</option>
          </select>
        </div>
        <table className="staff-table">
          <thead>
            <tr>
              <th>Periodo</th>
              <th>Pedidos</th>
              <th>Ventas</th>
              <th>Ticket promedio</th>
            </tr>
          </thead>
          <tbody>
            {ventas.map((v) => (
              <tr key={v.periodo}>
                <td>{v.periodo}</td>
                <td>{v.numeroPedidos}</td>
                <td>{formatPrice(v.ventasTotal)}</td>
                <td>{formatPrice(v.ticketPromedio)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="staff-section">
        <p className="staff-section-title">Productos más vendidos</p>
        <table className="staff-table">
          <thead>
            <tr>
              <th>Producto</th>
              <th>Unidades</th>
              <th>Ventas</th>
            </tr>
          </thead>
          <tbody>
            {masVendidos.map((p) => (
              <tr key={p.productoId}>
                <td>{p.nombre}</td>
                <td>{p.unidadesVendidas}</td>
                <td>{formatPrice(p.ventasTotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
