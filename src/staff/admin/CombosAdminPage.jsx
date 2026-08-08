import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { staffFetch } from '../staffApi'
import { formatPrice } from '../../utils/formatPrice'

export default function CombosAdminPage() {
  const { accessToken } = useAuth()
  const [combos, setCombos] = useState([])
  const [productos, setProductos] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [form, setForm] = useState({ nombre: '', descripcion: '', precio: '' })
  const [expandidoId, setExpandidoId] = useState(null)
  const [componenteProductoId, setComponenteProductoId] = useState('')
  const [componenteGrupo, setComponenteGrupo] = useState('')

  const cargar = useCallback(() => {
    setLoading(true)
    Promise.all([staffFetch(accessToken, '/combos?size=100'), staffFetch(accessToken, '/productos?size=200')])
      .then(([combosPage, prodsPage]) => {
        setCombos(combosPage.content ?? [])
        setProductos(prodsPage.content ?? [])
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
      await staffFetch(accessToken, '/combos', {
        method: 'POST',
        body: JSON.stringify({ nombre: form.nombre, descripcion: form.descripcion || null, precio: Number(form.precio) }),
      })
      setForm({ nombre: '', descripcion: '', precio: '' })
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  async function agregarComponente(combo) {
    if (!componenteProductoId) return
    setError(null)
    const componentesActuales = combo.componentes.map((c) => ({
      productoId: c.productoId,
      grupo: c.grupo,
      cantidad: c.cantidad,
    }))
    try {
      await staffFetch(accessToken, `/combos/${combo.id}/componentes`, {
        method: 'PUT',
        body: JSON.stringify([
          ...componentesActuales,
          { productoId: Number(componenteProductoId), grupo: componenteGrupo || null, cantidad: 1 },
        ]),
      })
      setComponenteProductoId('')
      setComponenteGrupo('')
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  async function quitarComponente(combo, productoId) {
    setError(null)
    const restantes = combo.componentes
      .filter((c) => c.productoId !== productoId)
      .map((c) => ({ productoId: c.productoId, grupo: c.grupo, cantidad: c.cantidad }))
    try {
      await staffFetch(accessToken, `/combos/${combo.id}/componentes`, {
        method: 'PUT',
        body: JSON.stringify(restantes),
      })
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
        <p className="staff-section-title">Nuevo combo</p>
        <form className="staff-form" onSubmit={crear}>
          <input
            required
            placeholder="Nombre"
            value={form.nombre}
            onChange={(e) => setForm({ ...form, nombre: e.target.value })}
          />
          <textarea
            placeholder="Descripción"
            value={form.descripcion}
            onChange={(e) => setForm({ ...form, descripcion: e.target.value })}
          />
          <input
            type="number"
            min="0"
            step="0.01"
            required
            placeholder="Precio"
            value={form.precio}
            onChange={(e) => setForm({ ...form, precio: e.target.value })}
          />
          <button type="submit" className="staff-button">
            Crear combo
          </button>
        </form>
      </div>

      <div className="staff-card-grid">
        {combos.map((combo) => (
          <div key={combo.id} className="staff-card">
            <div className="staff-card-header">
              <span className="staff-card-title">{combo.nombre}</span>
              <span className={`staff-badge${combo.disponible ? ' ok' : ''}`}>
                {combo.disponible ? 'Disponible' : 'Agotado'}
              </span>
            </div>
            <span className="staff-card-meta">{formatPrice(combo.precio)}</span>
            <ul className="staff-card-items">
              {combo.componentes.map((c) => (
                <li key={c.id}>
                  {c.productoNombre} {c.grupo ? `(grupo: ${c.grupo})` : '(fijo)'}{' '}
                  <button type="button" className="staff-button danger" onClick={() => quitarComponente(combo, c.productoId)}>
                    quitar
                  </button>
                </li>
              ))}
            </ul>
            <button
              type="button"
              className="staff-button secondary"
              onClick={() => setExpandidoId(expandidoId === combo.id ? null : combo.id)}
            >
              {expandidoId === combo.id ? 'Cerrar' : 'Agregar componente'}
            </button>
            {expandidoId === combo.id && (
              <div className="staff-form-row" style={{ marginTop: 8 }}>
                <select value={componenteProductoId} onChange={(e) => setComponenteProductoId(e.target.value)}>
                  <option value="">Producto…</option>
                  {productos.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.nombre} ({p.codigo})
                    </option>
                  ))}
                </select>
                <input
                  placeholder="Grupo (opcional)"
                  value={componenteGrupo}
                  onChange={(e) => setComponenteGrupo(e.target.value)}
                />
                <button type="button" className="staff-button" onClick={() => agregarComponente(combo)}>
                  Agregar
                </button>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
