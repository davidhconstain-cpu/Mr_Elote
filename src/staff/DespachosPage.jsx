import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { staffFetch } from './staffApi'

const SIGUIENTE_ACCION = {
  asignado: { accion: 'en-camino', label: 'Marcar en camino' },
  en_camino: { accion: 'entregar', label: 'Marcar entregado' },
}

export default function DespachosPage() {
  const { accessToken } = useAuth()
  const [despachos, setDespachos] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [actuando, setActuando] = useState(null)

  const cargar = useCallback(() => {
    setLoading(true)
    staffFetch(accessToken, '/despachos')
      .then((data) => {
        setDespachos(data)
        setLoading(false)
      })
      .catch((err) => {
        setError(err.message)
        setLoading(false)
      })
  }, [accessToken])

  useEffect(() => {
    cargar()
  }, [cargar])

  async function asignarme(despacho) {
    setActuando(despacho.id)
    setError(null)
    try {
      await staffFetch(accessToken, `/despachos/${despacho.id}/asignar`, { method: 'POST' })
      cargar()
    } catch (err) {
      setError(err.message)
    } finally {
      setActuando(null)
    }
  }

  async function avanzar(despacho) {
    const siguiente = SIGUIENTE_ACCION[despacho.estado]
    if (!siguiente) return
    setActuando(despacho.id)
    setError(null)
    try {
      await staffFetch(accessToken, `/despachos/${despacho.id}/${siguiente.accion}`, { method: 'POST' })
      cargar()
    } catch (err) {
      setError(err.message)
    } finally {
      setActuando(null)
    }
  }

  return (
    <div>
      <h1 className="staff-page-title">Despachos</h1>
      {error && <p className="staff-error">{error}</p>}

      {loading ? (
        <p className="staff-empty">Cargando…</p>
      ) : despachos.length === 0 ? (
        <p className="staff-empty">No hay despachos pendientes.</p>
      ) : (
        <table className="staff-table">
          <thead>
            <tr>
              <th>Pedido</th>
              <th>Domiciliario</th>
              <th>Estado</th>
              <th>Asignado</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {despachos.map((d) => (
              <tr key={d.id}>
                <td>#{d.pedidoId}</td>
                <td>{d.domiciliarioNombre ?? '—'}</td>
                <td>
                  <span className="staff-badge">{d.estado}</span>
                </td>
                <td>{new Date(d.asignadoEn).toLocaleString('es-CO')}</td>
                <td>
                  <div className="staff-card-actions">
                    {!d.domiciliarioId && (
                      <button
                        type="button"
                        className="staff-button secondary"
                        disabled={actuando === d.id}
                        onClick={() => asignarme(d)}
                      >
                        Asignarme
                      </button>
                    )}
                    {SIGUIENTE_ACCION[d.estado] && (
                      <button
                        type="button"
                        className="staff-button"
                        disabled={actuando === d.id}
                        onClick={() => avanzar(d)}
                      >
                        {SIGUIENTE_ACCION[d.estado].label}
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
