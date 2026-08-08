import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { staffFetch } from '../staffApi'
import { formatPrice } from '../../utils/formatPrice'

export default function DomiciliosAdminPage() {
  const { accessToken } = useAuth()
  const [zonas, setZonas] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [nuevaZona, setNuevaZona] = useState('')
  const [nuevaTarifa, setNuevaTarifa] = useState('')
  const [nuevoTiempo, setNuevoTiempo] = useState('')
  const [tarifas, setTarifas] = useState({})

  const cargar = useCallback(() => {
    setLoading(true)
    staffFetch(accessToken, '/zonas-domicilio')
      .then((data) => {
        setZonas(data)
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

  async function crearZona(e) {
    e.preventDefault()
    setError(null)
    try {
      await staffFetch(accessToken, '/zonas-domicilio', {
        method: 'POST',
        body: JSON.stringify({
          nombre: nuevaZona,
          tarifa: Number(nuevaTarifa),
          tiempoEstimadoMinutos: nuevoTiempo ? Number(nuevoTiempo) : null,
        }),
      })
      setNuevaZona('')
      setNuevaTarifa('')
      setNuevoTiempo('')
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  async function actualizarTarifa(zona) {
    const valor = tarifas[zona.id]
    if (!valor) return
    setError(null)
    try {
      await staffFetch(accessToken, `/zonas-domicilio/${zona.id}/tarifa`, {
        method: 'PATCH',
        body: JSON.stringify({ tarifa: Number(valor) }),
      })
      setTarifas((prev) => ({ ...prev, [zona.id]: '' }))
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  if (loading) return <p className="staff-empty">Cargando…</p>

  return (
    <div>
      {error && <p className="staff-error">{error}</p>}
      <form className="staff-form" onSubmit={crearZona}>
        <input required placeholder="Nombre de la zona" value={nuevaZona} onChange={(e) => setNuevaZona(e.target.value)} />
        <div className="staff-form-row">
          <input
            type="number"
            min="0"
            step="0.01"
            required
            placeholder="Tarifa inicial"
            value={nuevaTarifa}
            onChange={(e) => setNuevaTarifa(e.target.value)}
          />
          <input
            type="number"
            min="0"
            placeholder="Tiempo estimado (min)"
            value={nuevoTiempo}
            onChange={(e) => setNuevoTiempo(e.target.value)}
          />
        </div>
        <button type="submit" className="staff-button">
          Crear zona
        </button>
      </form>

      <table className="staff-table">
        <thead>
          <tr>
            <th>Zona</th>
            <th>Tarifa vigente</th>
            <th>Tiempo estimado</th>
            <th>Nueva tarifa</th>
          </tr>
        </thead>
        <tbody>
          {zonas.map((z) => (
            <tr key={z.id}>
              <td>{z.nombre}</td>
              <td>{z.tarifaVigente != null ? formatPrice(z.tarifaVigente) : '—'}</td>
              <td>{z.tiempoEstimadoMinutos ? `${z.tiempoEstimadoMinutos} min` : '—'}</td>
              <td>
                <div className="staff-form-row">
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    placeholder="Nueva tarifa"
                    value={tarifas[z.id] ?? ''}
                    onChange={(e) => setTarifas((prev) => ({ ...prev, [z.id]: e.target.value }))}
                  />
                  <button type="button" className="staff-button secondary" onClick={() => actualizarTarifa(z)}>
                    Actualizar
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
