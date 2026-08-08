import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { staffFetch } from '../staffApi'

export default function RolesAdminPage() {
  const { accessToken } = useAuth()
  const [roles, setRoles] = useState([])
  const [permisos, setPermisos] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [editandoId, setEditandoId] = useState(null)
  const [seleccion, setSeleccion] = useState(new Set())

  const cargar = useCallback(() => {
    setLoading(true)
    Promise.all([staffFetch(accessToken, '/roles'), staffFetch(accessToken, '/permisos')])
      .then(([rolesData, permisosData]) => {
        setRoles(rolesData)
        setPermisos(permisosData)
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

  function editar(rol) {
    setEditandoId(rol.id)
    const idsAsignados = permisos.filter((p) => rol.permisos.includes(p.codigo)).map((p) => p.id)
    setSeleccion(new Set(idsAsignados))
  }

  function alternar(permisoId) {
    setSeleccion((prev) => {
      const next = new Set(prev)
      if (next.has(permisoId)) next.delete(permisoId)
      else next.add(permisoId)
      return next
    })
  }

  async function guardar(rolId) {
    setError(null)
    try {
      await staffFetch(accessToken, `/roles/${rolId}/permisos`, {
        method: 'PUT',
        body: JSON.stringify(Array.from(seleccion)),
      })
      setEditandoId(null)
      cargar()
    } catch (err) {
      setError(err.message)
    }
  }

  if (loading) return <p className="staff-empty">Cargando…</p>

  return (
    <div>
      {error && <p className="staff-error">{error}</p>}
      <div className="staff-card-grid">
        {roles.map((rol) => (
          <div key={rol.id} className="staff-card">
            <span className="staff-card-title">{rol.nombre}</span>
            {editandoId === rol.id ? (
              <>
                <div className="staff-card-items">
                  {permisos.map((p) => (
                    <label key={p.id} style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                      <input type="checkbox" checked={seleccion.has(p.id)} onChange={() => alternar(p.id)} />
                      {p.codigo}
                    </label>
                  ))}
                </div>
                <div className="staff-card-actions">
                  <button type="button" className="staff-button" onClick={() => guardar(rol.id)}>
                    Guardar
                  </button>
                  <button type="button" className="staff-button secondary" onClick={() => setEditandoId(null)}>
                    Cancelar
                  </button>
                </div>
              </>
            ) : (
              <>
                <ul className="staff-card-items">
                  {rol.permisos.length === 0 ? <li>Sin permisos finos asignados</li> : rol.permisos.map((c) => <li key={c}>{c}</li>)}
                </ul>
                <button type="button" className="staff-button secondary" onClick={() => editar(rol)}>
                  Editar permisos
                </button>
              </>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
