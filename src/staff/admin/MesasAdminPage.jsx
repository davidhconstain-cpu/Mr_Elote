import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { staffFetch } from '../staffApi'

export default function MesasAdminPage() {
  const { accessToken } = useAuth()
  const [mesas, setMesas] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [form, setForm] = useState({ numero: '', capacidad: '' })
  const [qrGenerado, setQrGenerado] = useState(null)

  const cargar = useCallback(() => {
    setLoading(true)
    staffFetch(accessToken, '/mesas')
      .then((data) => {
        setMesas(data)
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

  async function crear(e) {
    e.preventDefault()
    setError(null)
    try {
      await staffFetch(accessToken, '/mesas', {
        method: 'POST',
        body: JSON.stringify({ numero: form.numero, capacidad: form.capacidad ? Number(form.capacidad) : null }),
      })
      setForm({ numero: '', capacidad: '' })
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  async function regenerarQr(mesa) {
    setError(null)
    try {
      const qr = await staffFetch(accessToken, `/mesas/${mesa.id}/qr/regenerar`, { method: 'POST' })
      setQrGenerado({ mesaId: mesa.id, ...qr })
    } catch (err) {
      setError(err.message)
    }
  }

  if (loading) return <p className="staff-empty">Cargando…</p>

  return (
    <div>
      {error && <p className="staff-error">{error}</p>}
      <form className="staff-form" onSubmit={crear}>
        <div className="staff-form-row">
          <input required placeholder="Número" value={form.numero} onChange={(e) => setForm({ ...form, numero: e.target.value })} />
          <input
            type="number"
            placeholder="Capacidad"
            value={form.capacidad}
            onChange={(e) => setForm({ ...form, capacidad: e.target.value })}
          />
        </div>
        <button type="submit" className="staff-button">
          Crear mesa
        </button>
      </form>

      <table className="staff-table">
        <thead>
          <tr>
            <th>Número</th>
            <th>Capacidad</th>
            <th>Activa</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {mesas.map((m) => (
            <tr key={m.id}>
              <td>{m.numero}</td>
              <td>{m.capacidad ?? '—'}</td>
              <td>{m.activa ? 'Sí' : 'No'}</td>
              <td>
                <button type="button" className="staff-button secondary" onClick={() => regenerarQr(m)}>
                  Regenerar QR
                </button>
                {qrGenerado?.mesaId === m.id && (
                  <p className="staff-card-meta">
                    Nueva URL: <code>/?mesa={qrGenerado.codigo}</code>
                  </p>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
