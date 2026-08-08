import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { staffFetch } from './staffApi'
import { formatPrice } from '../utils/formatPrice'

export default function CajaPage() {
  const { accessToken } = useAuth()
  const [caja, setCaja] = useState(null)
  const [movimientos, setMovimientos] = useState([])
  const [pendientes, setPendientes] = useState([])
  const [listos, setListos] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [montoApertura, setMontoApertura] = useState('')
  const [montoReal, setMontoReal] = useState('')
  const [movTipo, setMovTipo] = useState('ingreso')
  const [movMonto, setMovMonto] = useState('')
  const [movDescripcion, setMovDescripcion] = useState('')

  const cargarPendientes = useCallback(() => {
    staffFetch(accessToken, '/pedidos?estado=pago_pendiente&size=50')
      .then((page) => setPendientes(page.content ?? []))
      .catch((err) => setError(err.message))
    staffFetch(accessToken, '/pedidos?estado=listo&size=50')
      .then((page) => setListos((page.content ?? []).filter((p) => p.tipo !== 'domicilio')))
      .catch((err) => setError(err.message))
  }, [accessToken])

  const cargarCajaActual = useCallback(() => {
    setLoading(true)
    staffFetch(accessToken, '/caja/actual')
      .then((data) => {
        setCaja(data)
        setLoading(false)
        return staffFetch(accessToken, `/caja/${data.id}/movimientos`)
      })
      .then((movs) => movs && setMovimientos(movs))
      .catch(() => {
        setCaja(null)
        setLoading(false)
      })
  }, [accessToken])

  useEffect(() => {
    cargarCajaActual()
    cargarPendientes()
  }, [cargarCajaActual, cargarPendientes])

  async function confirmarPago(pedido) {
    setError(null)
    try {
      await staffFetch(accessToken, `/pedidos/${pedido.id}/confirmar-pago`, { method: 'POST' })
      cargarPendientes()
    } catch (err) {
      setError(err.message)
    }
  }

  async function entregarOrecoger(pedido) {
    setError(null)
    const accion = pedido.tipo === 'local' ? 'entregar' : 'recoger'
    try {
      await staffFetch(accessToken, `/pedidos/${pedido.id}/${accion}`, { method: 'POST' })
      cargarPendientes()
    } catch (err) {
      setError(err.message)
    }
  }

  async function abrirCaja(e) {
    e.preventDefault()
    setError(null)
    try {
      await staffFetch(accessToken, '/caja/apertura', {
        method: 'POST',
        body: JSON.stringify({ montoApertura: Number(montoApertura) }),
      })
      setMontoApertura('')
      cargarCajaActual()
    } catch (err) {
      setError(err.message)
    }
  }

  async function registrarMovimiento(e) {
    e.preventDefault()
    setError(null)
    try {
      await staffFetch(accessToken, `/caja/${caja.id}/movimientos`, {
        method: 'POST',
        body: JSON.stringify({ tipo: movTipo, monto: Number(movMonto), descripcion: movDescripcion || null }),
      })
      setMovMonto('')
      setMovDescripcion('')
      cargarCajaActual()
    } catch (err) {
      setError(err.message)
    }
  }

  async function cerrarCaja(e) {
    e.preventDefault()
    setError(null)
    try {
      await staffFetch(accessToken, `/caja/${caja.id}/cierre`, {
        method: 'POST',
        body: JSON.stringify({ montoReal: Number(montoReal) }),
      })
      setMontoReal('')
      cargarCajaActual()
    } catch (err) {
      setError(err.message)
    }
  }

  if (loading) return <p className="staff-empty">Cargando…</p>

  return (
    <div>
      <h1 className="staff-page-title">Caja</h1>
      {error && <p className="staff-error">{error}</p>}

      <div className="staff-section">
        <p className="staff-section-title">Pedidos pendientes de pago</p>
        {pendientes.length === 0 ? (
          <p className="staff-empty">No hay pedidos esperando confirmación de pago.</p>
        ) : (
          <table className="staff-table">
            <thead>
              <tr>
                <th>Pedido</th>
                <th>Tipo</th>
                <th>Total</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {pendientes.map((p) => (
                <tr key={p.id}>
                  <td>#{p.id}</td>
                  <td>{p.tipo}</td>
                  <td>{formatPrice(p.total)}</td>
                  <td>
                    <button type="button" className="staff-button" onClick={() => confirmarPago(p)}>
                      Confirmar pago
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="staff-section">
        <p className="staff-section-title">Listos para entregar / recoger</p>
        {listos.length === 0 ? (
          <p className="staff-empty">No hay pedidos listos esperando entrega.</p>
        ) : (
          <table className="staff-table">
            <thead>
              <tr>
                <th>Pedido</th>
                <th>Tipo</th>
                <th>Total</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {listos.map((p) => (
                <tr key={p.id}>
                  <td>#{p.id}</td>
                  <td>{p.tipo}</td>
                  <td>{formatPrice(p.total)}</td>
                  <td>
                    <button type="button" className="staff-button" onClick={() => entregarOrecoger(p)}>
                      {p.tipo === 'local' ? 'Marcar entregado' : 'Marcar recogido'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {!caja ? (
        <form className="staff-form" onSubmit={abrirCaja}>
          <p className="staff-section-title">Abrir caja</p>
          <input
            type="number"
            min="0"
            step="0.01"
            required
            placeholder="Monto de apertura"
            value={montoApertura}
            onChange={(e) => setMontoApertura(e.target.value)}
          />
          <button type="submit" className="staff-button">
            Abrir caja
          </button>
        </form>
      ) : (
        <>
          <div className="staff-kpis">
            <div className="staff-kpi">
              <span className="staff-kpi-label">Estado</span>
              <span className="staff-kpi-value">{caja.estado}</span>
            </div>
            <div className="staff-kpi">
              <span className="staff-kpi-label">Apertura</span>
              <span className="staff-kpi-value">{formatPrice(caja.montoApertura)}</span>
            </div>
            <div className="staff-kpi">
              <span className="staff-kpi-label">Esperado</span>
              <span className="staff-kpi-value">
                {caja.montoEsperado != null ? formatPrice(caja.montoEsperado) : 'Se calcula al cerrar'}
              </span>
            </div>
          </div>

          <div className="staff-section">
            <p className="staff-section-title">Registrar movimiento manual</p>
            <form className="staff-form" onSubmit={registrarMovimiento}>
              <div className="staff-form-row">
                <select value={movTipo} onChange={(e) => setMovTipo(e.target.value)}>
                  <option value="ingreso">Ingreso</option>
                  <option value="egreso">Egreso</option>
                </select>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  required
                  placeholder="Monto"
                  value={movMonto}
                  onChange={(e) => setMovMonto(e.target.value)}
                />
              </div>
              <input
                placeholder="Descripción (opcional)"
                value={movDescripcion}
                onChange={(e) => setMovDescripcion(e.target.value)}
              />
              <button type="submit" className="staff-button secondary">
                Registrar
              </button>
            </form>
          </div>

          <div className="staff-section">
            <p className="staff-section-title">Movimientos</p>
            {movimientos.length === 0 ? (
              <p className="staff-empty">Sin movimientos todavía.</p>
            ) : (
              <table className="staff-table">
                <thead>
                  <tr>
                    <th>Tipo</th>
                    <th>Monto</th>
                    <th>Pedido</th>
                    <th>Descripción</th>
                    <th>Usuario</th>
                  </tr>
                </thead>
                <tbody>
                  {movimientos.map((m) => (
                    <tr key={m.id}>
                      <td>{m.tipo}</td>
                      <td>{formatPrice(m.monto)}</td>
                      <td>{m.pedidoId ?? '—'}</td>
                      <td>{m.descripcion ?? '—'}</td>
                      <td>{m.usuarioNombre}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          <div className="staff-section">
            <p className="staff-section-title">Cerrar caja</p>
            <form className="staff-form" onSubmit={cerrarCaja}>
              <input
                type="number"
                min="0"
                step="0.01"
                required
                placeholder="Monto real contado"
                value={montoReal}
                onChange={(e) => setMontoReal(e.target.value)}
              />
              <button type="submit" className="staff-button danger">
                Cerrar caja
              </button>
            </form>
          </div>
        </>
      )}
    </div>
  )
}
