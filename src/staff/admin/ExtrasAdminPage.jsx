import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { staffFetch } from '../staffApi'
import { formatPrice } from '../../utils/formatPrice'

export default function ExtrasAdminPage() {
  const { accessToken } = useAuth()
  const [opciones, setOpciones] = useState([])
  const [adicionales, setAdicionales] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [opcionForm, setOpcionForm] = useState({ nombre: '', tipo: 'unica' })
  const [valorForm, setValorForm] = useState({ opcionId: '', nombre: '', precioAdicional: '' })
  const [adicionalForm, setAdicionalForm] = useState({ nombre: '', precio: '' })

  const cargar = useCallback(() => {
    setLoading(true)
    Promise.all([staffFetch(accessToken, '/opciones'), staffFetch(accessToken, '/adicionales')])
      .then(([ops, ads]) => {
        setOpciones(ops)
        setAdicionales(ads)
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

  async function crearOpcion(e) {
    e.preventDefault()
    setError(null)
    try {
      await staffFetch(accessToken, '/opciones', { method: 'POST', body: JSON.stringify(opcionForm) })
      setOpcionForm({ nombre: '', tipo: 'unica' })
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  async function crearValor(e) {
    e.preventDefault()
    if (!valorForm.opcionId) return
    setError(null)
    try {
      await staffFetch(accessToken, `/opciones/${valorForm.opcionId}/valores`, {
        method: 'POST',
        body: JSON.stringify({
          nombre: valorForm.nombre,
          precioAdicional: valorForm.precioAdicional ? Number(valorForm.precioAdicional) : 0,
        }),
      })
      setValorForm({ opcionId: valorForm.opcionId, nombre: '', precioAdicional: '' })
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  async function crearAdicional(e) {
    e.preventDefault()
    setError(null)
    try {
      await staffFetch(accessToken, '/adicionales', {
        method: 'POST',
        body: JSON.stringify({ nombre: adicionalForm.nombre, precio: Number(adicionalForm.precio) }),
      })
      setAdicionalForm({ nombre: '', precio: '' })
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  if (loading) return <p className="staff-empty">Cargando…</p>

  return (
    <div>
      {error && <p className="staff-error">{error}</p>}

      <div className="staff-section">
        <p className="staff-section-title">Opciones (ej. Tamaño)</p>
        <form className="staff-form" onSubmit={crearOpcion}>
          <input
            required
            placeholder="Nombre de la opción"
            value={opcionForm.nombre}
            onChange={(e) => setOpcionForm({ ...opcionForm, nombre: e.target.value })}
          />
          <select value={opcionForm.tipo} onChange={(e) => setOpcionForm({ ...opcionForm, tipo: e.target.value })}>
            <option value="unica">Selección única</option>
            <option value="multiple">Selección múltiple</option>
          </select>
          <button type="submit" className="staff-button">
            Crear opción
          </button>
        </form>

        <form className="staff-form" onSubmit={crearValor}>
          <select value={valorForm.opcionId} onChange={(e) => setValorForm({ ...valorForm, opcionId: e.target.value })}>
            <option value="">Agregar valor a…</option>
            {opciones.map((o) => (
              <option key={o.id} value={o.id}>
                {o.nombre}
              </option>
            ))}
          </select>
          <input
            required
            placeholder="Nombre del valor (ej. Grande)"
            value={valorForm.nombre}
            onChange={(e) => setValorForm({ ...valorForm, nombre: e.target.value })}
          />
          <input
            type="number"
            min="0"
            step="0.01"
            placeholder="Precio adicional"
            value={valorForm.precioAdicional}
            onChange={(e) => setValorForm({ ...valorForm, precioAdicional: e.target.value })}
          />
          <button type="submit" className="staff-button secondary">
            Agregar valor
          </button>
        </form>

        <div className="staff-card-grid">
          {opciones.map((o) => (
            <div key={o.id} className="staff-card">
              <span className="staff-card-title">
                {o.nombre} · {o.tipo}
              </span>
              <ul className="staff-card-items">
                {o.valores.map((v) => (
                  <li key={v.id}>
                    {v.nombre} — {formatPrice(v.precioAdicional)}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </div>

      <div className="staff-section">
        <p className="staff-section-title">Adicionales (ej. Queso extra)</p>
        <form className="staff-form" onSubmit={crearAdicional}>
          <input
            required
            placeholder="Nombre"
            value={adicionalForm.nombre}
            onChange={(e) => setAdicionalForm({ ...adicionalForm, nombre: e.target.value })}
          />
          <input
            type="number"
            min="0"
            step="0.01"
            required
            placeholder="Precio"
            value={adicionalForm.precio}
            onChange={(e) => setAdicionalForm({ ...adicionalForm, precio: e.target.value })}
          />
          <button type="submit" className="staff-button">
            Crear adicional
          </button>
        </form>
        <table className="staff-table">
          <thead>
            <tr>
              <th>Nombre</th>
              <th>Precio</th>
              <th>Disponible</th>
            </tr>
          </thead>
          <tbody>
            {adicionales.map((a) => (
              <tr key={a.id}>
                <td>{a.nombre}</td>
                <td>{formatPrice(a.precio)}</td>
                <td>{a.disponible ? 'Sí' : 'No'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
