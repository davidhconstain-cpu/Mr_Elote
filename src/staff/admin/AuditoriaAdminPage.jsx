import { useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { staffFetch } from '../staffApi'

export default function AuditoriaAdminPage() {
  const { accessToken } = useAuth()
  const [registros, setRegistros] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    setLoading(true)
    staffFetch(accessToken, '/auditoria?size=50&sort=creadoEn,desc')
      .then((page) => {
        setRegistros(page.content ?? [])
        setLoading(false)
      })
      .catch((err) => {
        setError(err.message)
        setLoading(false)
      })
  }, [accessToken])

  if (loading) return <p className="staff-empty">Cargando…</p>

  return (
    <div>
      {error && <p className="staff-error">{error}</p>}
      {registros.length === 0 ? (
        <p className="staff-empty">Sin registros de auditoría todavía.</p>
      ) : (
        <table className="staff-table">
          <thead>
            <tr>
              <th>Fecha</th>
              <th>Usuario</th>
              <th>Acción</th>
              <th>Entidad</th>
              <th>Motivo</th>
            </tr>
          </thead>
          <tbody>
            {registros.map((r) => (
              <tr key={r.id}>
                <td>{new Date(r.creadoEn).toLocaleString('es-CO')}</td>
                <td>{r.usuarioNombre ?? '—'}</td>
                <td>{r.accion}</td>
                <td>
                  {r.entidad} #{r.entidadId}
                </td>
                <td>{r.motivo ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
