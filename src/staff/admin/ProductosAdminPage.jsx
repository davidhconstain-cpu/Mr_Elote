import { Fragment, useCallback, useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { staffFetch } from '../staffApi'
import { formatPrice } from '../../utils/formatPrice'

export default function ProductosAdminPage() {
  const { accessToken } = useAuth()
  const [productos, setProductos] = useState([])
  const [categorias, setCategorias] = useState([])
  const [opciones, setOpciones] = useState([])
  const [adicionales, setAdicionales] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [expandidoId, setExpandidoId] = useState(null)
  const [productoOpciones, setProductoOpciones] = useState([])

  const [form, setForm] = useState({
    categoriaId: '',
    codigo: '',
    nombre: '',
    descripcion: '',
    precio: '',
    porcionPersonas: '',
  })

  const cargar = useCallback(() => {
    setLoading(true)
    Promise.all([
      staffFetch(accessToken, '/productos?size=200'),
      staffFetch(accessToken, '/categorias'),
      staffFetch(accessToken, '/opciones'),
      staffFetch(accessToken, '/adicionales'),
    ])
      .then(([prods, cats, ops, ads]) => {
        setProductos(prods.content ?? [])
        setCategorias(cats)
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

  async function crear(e) {
    e.preventDefault()
    setError(null)
    try {
      await staffFetch(accessToken, '/productos', {
        method: 'POST',
        body: JSON.stringify({
          categoriaId: Number(form.categoriaId),
          codigo: form.codigo,
          nombre: form.nombre,
          descripcion: form.descripcion || null,
          precio: Number(form.precio),
          porcionPersonas: form.porcionPersonas ? Number(form.porcionPersonas) : null,
        }),
      })
      setForm({ categoriaId: '', codigo: '', nombre: '', descripcion: '', precio: '', porcionPersonas: '' })
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  async function toggleDisponibilidad(producto) {
    setError(null)
    try {
      await staffFetch(accessToken, `/productos/${producto.id}/disponibilidad`, {
        method: 'PATCH',
        body: JSON.stringify({ disponible: !producto.disponible }),
      })
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  function expandir(producto) {
    if (expandidoId === producto.id) {
      setExpandidoId(null)
      return
    }
    setExpandidoId(producto.id)
    staffFetch(accessToken, `/productos/${producto.id}/opciones`)
      .then(setProductoOpciones)
      .catch((err) => setError(err.message))
  }

  async function asignarOpcion(productoId, opcionId, obligatoria) {
    setError(null)
    try {
      await staffFetch(accessToken, `/productos/${productoId}/opciones`, {
        method: 'POST',
        body: JSON.stringify({ opcionId: Number(opcionId), obligatoria }),
      })
      const data = await staffFetch(accessToken, `/productos/${productoId}/opciones`)
      setProductoOpciones(data)
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  async function quitarOpcion(productoId, opcionId) {
    setError(null)
    try {
      await staffFetch(accessToken, `/productos/${productoId}/opciones/${opcionId}`, { method: 'DELETE' })
      setProductoOpciones((prev) => prev.filter((o) => o.opcionId !== opcionId))
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  async function asignarAdicional(productoId, adicionalId) {
    setError(null)
    try {
      await staffFetch(accessToken, `/productos/${productoId}/adicionales`, {
        method: 'POST',
        body: JSON.stringify({ adicionalId: Number(adicionalId) }),
      })
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  async function quitarAdicional(productoId, adicionalId) {
    setError(null)
    try {
      await staffFetch(accessToken, `/productos/${productoId}/adicionales/${adicionalId}`, { method: 'DELETE' })
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
        <p className="staff-section-title">Nuevo producto</p>
        <form className="staff-form" onSubmit={crear}>
          <select required value={form.categoriaId} onChange={(e) => setForm({ ...form, categoriaId: e.target.value })}>
            <option value="">Categoría…</option>
            {categorias.map((c) => (
              <option key={c.id} value={c.id}>
                {c.nombre}
              </option>
            ))}
          </select>
          <input
            required
            placeholder="Código único (ej. NACHOS-2P)"
            value={form.codigo}
            onChange={(e) => setForm({ ...form, codigo: e.target.value })}
          />
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
          <div className="staff-form-row">
            <input
              type="number"
              min="0"
              step="0.01"
              required
              placeholder="Precio"
              value={form.precio}
              onChange={(e) => setForm({ ...form, precio: e.target.value })}
            />
            <input
              type="number"
              min="1"
              placeholder="Porción (personas)"
              value={form.porcionPersonas}
              onChange={(e) => setForm({ ...form, porcionPersonas: e.target.value })}
            />
          </div>
          <button type="submit" className="staff-button">
            Crear producto
          </button>
        </form>
      </div>

      <div className="staff-section">
        <p className="staff-section-title">Productos ({productos.length})</p>
        <table className="staff-table">
          <thead>
            <tr>
              <th>Código</th>
              <th>Nombre</th>
              <th>Precio</th>
              <th>Porción</th>
              <th>Disponible</th>
              <th>Activo</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {productos.map((p) => (
              <Fragment key={p.id}>
                <tr>
                  <td>{p.codigo}</td>
                  <td>{p.nombre}</td>
                  <td>{formatPrice(p.precio)}</td>
                  <td>{p.porcionPersonas ?? '—'}</td>
                  <td>
                    <span className={`staff-badge${p.disponible ? ' ok' : ''}`}>
                      {p.disponible ? 'Sí' : 'Agotado'}
                    </span>
                  </td>
                  <td>{p.activo ? 'Sí' : 'No'}</td>
                  <td>
                    <div className="staff-card-actions">
                      <button type="button" className="staff-button secondary" onClick={() => toggleDisponibilidad(p)}>
                        {p.disponible ? 'Marcar agotado' : 'Marcar disponible'}
                      </button>
                      <button type="button" className="staff-button secondary" onClick={() => expandir(p)}>
                        {expandidoId === p.id ? 'Cerrar' : 'Opciones/adicionales'}
                      </button>
                    </div>
                  </td>
                </tr>
                {expandidoId === p.id && (
                  <tr>
                    <td colSpan={7}>
                      <div className="staff-card-grid">
                        <div className="staff-card">
                          <span className="staff-card-title">Opciones asignadas</span>
                          {productoOpciones.length === 0 && <span className="staff-card-meta">Ninguna</span>}
                          {productoOpciones.map((o) => (
                            <div key={o.opcionId} className="staff-card-actions">
                              <span>
                                {o.nombre} {o.obligatoria ? '(obligatoria)' : ''}
                              </span>
                              <button
                                type="button"
                                className="staff-button danger"
                                onClick={() => quitarOpcion(p.id, o.opcionId)}
                              >
                                Quitar
                              </button>
                            </div>
                          ))}
                          <AsignarOpcionForm opciones={opciones} onAsignar={(id, obl) => asignarOpcion(p.id, id, obl)} />
                        </div>
                        <div className="staff-card">
                          <span className="staff-card-title">Adicionales asignados</span>
                          {p.adicionales.length === 0 && <span className="staff-card-meta">Ninguno</span>}
                          {p.adicionales.map((a) => (
                            <div key={a.id} className="staff-card-actions">
                              <span>{a.nombre}</span>
                              <button
                                type="button"
                                className="staff-button danger"
                                onClick={() => quitarAdicional(p.id, a.id)}
                              >
                                Quitar
                              </button>
                            </div>
                          ))}
                          <AsignarAdicionalForm
                            adicionales={adicionales}
                            asignados={p.adicionales}
                            onAsignar={(id) => asignarAdicional(p.id, id)}
                          />
                        </div>
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function AsignarOpcionForm({ opciones, onAsignar }) {
  const [opcionId, setOpcionId] = useState('')
  const [obligatoria, setObligatoria] = useState(false)
  return (
    <div className="staff-form-row" style={{ marginTop: 8 }}>
      <select value={opcionId} onChange={(e) => setOpcionId(e.target.value)}>
        <option value="">Agregar opción…</option>
        {opciones.map((o) => (
          <option key={o.id} value={o.id}>
            {o.nombre}
          </option>
        ))}
      </select>
      <label style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12 }}>
        <input type="checkbox" checked={obligatoria} onChange={(e) => setObligatoria(e.target.checked)} />
        obligatoria
      </label>
      <button
        type="button"
        className="staff-button secondary"
        disabled={!opcionId}
        onClick={() => {
          onAsignar(opcionId, obligatoria)
          setOpcionId('')
          setObligatoria(false)
        }}
      >
        +
      </button>
    </div>
  )
}

function AsignarAdicionalForm({ adicionales, asignados, onAsignar }) {
  const [adicionalId, setAdicionalId] = useState('')
  const disponibles = adicionales.filter((a) => !asignados.some((x) => x.id === a.id))
  return (
    <div className="staff-form-row" style={{ marginTop: 8 }}>
      <select value={adicionalId} onChange={(e) => setAdicionalId(e.target.value)}>
        <option value="">Agregar adicional…</option>
        {disponibles.map((a) => (
          <option key={a.id} value={a.id}>
            {a.nombre}
          </option>
        ))}
      </select>
      <button
        type="button"
        className="staff-button secondary"
        disabled={!adicionalId}
        onClick={() => {
          onAsignar(adicionalId)
          setAdicionalId('')
        }}
      >
        +
      </button>
    </div>
  )
}
