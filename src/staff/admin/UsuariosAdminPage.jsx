import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { staffFetch } from '../staffApi'

export default function UsuariosAdminPage() {
  const { accessToken } = useAuth()
  const [usuarios, setUsuarios] = useState([])
  const [roles, setRoles] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [form, setForm] = useState({ nombre: '', email: '', telefono: '', password: '', rolId: '' })

  const cargar = useCallback(() => {
    setLoading(true)
    Promise.all([staffFetch(accessToken, '/usuarios?size=100'), staffFetch(accessToken, '/roles')])
      .then(([usuariosPage, rolesData]) => {
        setUsuarios(usuariosPage.content ?? [])
        setRoles(rolesData)
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
      await staffFetch(accessToken, '/usuarios', {
        method: 'POST',
        body: JSON.stringify({ ...form, rolId: Number(form.rolId) }),
      })
      setForm({ nombre: '', email: '', telefono: '', password: '', rolId: '' })
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  async function desactivar(usuario) {
    setError(null)
    try {
      await staffFetch(accessToken, `/usuarios/${usuario.id}`, { method: 'DELETE' })
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
        <p className="staff-section-title">Nuevo usuario de staff</p>
        <form className="staff-form" onSubmit={crear}>
          <input required placeholder="Nombre" value={form.nombre} onChange={(e) => setForm({ ...form, nombre: e.target.value })} />
          <input
            type="email"
            required
            placeholder="Correo"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
          />
          <input
            placeholder="Teléfono (opcional)"
            value={form.telefono}
            onChange={(e) => setForm({ ...form, telefono: e.target.value })}
          />
          <input
            type="password"
            required
            minLength={8}
            placeholder="Contraseña"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
          />
          <select required value={form.rolId} onChange={(e) => setForm({ ...form, rolId: e.target.value })}>
            <option value="">Rol…</option>
            {roles.map((r) => (
              <option key={r.id} value={r.id}>
                {r.nombre}
              </option>
            ))}
          </select>
          <button type="submit" className="staff-button">
            Crear usuario
          </button>
        </form>
      </div>

      <table className="staff-table">
        <thead>
          <tr>
            <th>Nombre</th>
            <th>Email</th>
            <th>Rol</th>
            <th>Activo</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {usuarios.map((u) => (
            <tr key={u.id}>
              <td>{u.nombre}</td>
              <td>{u.email}</td>
              <td>{u.rol}</td>
              <td>{u.activo ? 'Sí' : 'No'}</td>
              <td>
                {u.activo && (
                  <button type="button" className="staff-button danger" onClick={() => desactivar(u)}>
                    Desactivar
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
