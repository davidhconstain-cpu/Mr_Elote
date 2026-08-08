import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { staffFetch } from '../staffApi'

export default function ConfiguracionAdminPage() {
  const { accessToken } = useAuth()
  const [configuracion, setConfiguracion] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const cargar = useCallback(() => {
    setLoading(true)
    staffFetch(accessToken, '/configuracion')
      .then((data) => {
        setConfiguracion(data)
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

  async function actualizar(clave, valor) {
    setError(null)
    try {
      await staffFetch(accessToken, `/configuracion/${clave}`, { method: 'PATCH', body: JSON.stringify({ valor }) })
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
        {configuracion.map((c) => (
          <div key={c.clave} className="staff-card">
            <span className="staff-card-title">{c.clave}</span>
            {c.descripcion && <span className="staff-card-meta">{c.descripcion}</span>}
            {c.valor === 'true' || c.valor === 'false' ? (
              <button
                type="button"
                className="staff-button secondary"
                onClick={() => actualizar(c.clave, c.valor === 'true' ? 'false' : 'true')}
              >
                Valor actual: {c.valor} (clic para cambiar)
              </button>
            ) : (
              <ConfigValorForm valor={c.valor} onGuardar={(v) => actualizar(c.clave, v)} />
            )}
          </div>
        ))}
      </div>
    </div>
  )
}

function ConfigValorForm({ valor, onGuardar }) {
  const [texto, setTexto] = useState(valor ?? '')
  return (
    <div className="staff-form-row">
      <input value={texto} onChange={(e) => setTexto(e.target.value)} />
      <button type="button" className="staff-button secondary" onClick={() => onGuardar(texto)}>
        Guardar
      </button>
    </div>
  )
}
