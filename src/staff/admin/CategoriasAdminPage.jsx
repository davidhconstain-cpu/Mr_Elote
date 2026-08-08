import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { staffFetch } from '../staffApi'

export default function CategoriasAdminPage() {
  const { accessToken } = useAuth()
  const [categorias, setCategorias] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [nombre, setNombre] = useState('')
  const [orden, setOrden] = useState('')

  const cargar = useCallback(() => {
    setLoading(true)
    staffFetch(accessToken, '/categorias')
      .then((data) => {
        setCategorias(data)
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
      await staffFetch(accessToken, '/categorias', {
        method: 'POST',
        body: JSON.stringify({ nombre, orden: orden ? Number(orden) : 0 }),
      })
      setNombre('')
      setOrden('')
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  async function toggleActiva(categoria) {
    setError(null)
    try {
      await staffFetch(accessToken, `/categorias/${categoria.id}`, {
        method: 'PATCH',
        body: JSON.stringify({ activa: !categoria.activa }),
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
      <form className="staff-form" onSubmit={crear}>
        <input required placeholder="Nombre de la categoría" value={nombre} onChange={(e) => setNombre(e.target.value)} />
        <input type="number" placeholder="Orden" value={orden} onChange={(e) => setOrden(e.target.value)} />
        <button type="submit" className="staff-button">
          Crear categoría
        </button>
      </form>

      <table className="staff-table">
        <thead>
          <tr>
            <th>Nombre</th>
            <th>Orden</th>
            <th>Activa</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {categorias.map((c) => (
            <tr key={c.id}>
              <td>{c.nombre}</td>
              <td>{c.orden}</td>
              <td>{c.activa ? 'Sí' : 'No'}</td>
              <td>
                <button type="button" className="staff-button secondary" onClick={() => toggleActiva(c)}>
                  {c.activa ? 'Desactivar' : 'Activar'}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
