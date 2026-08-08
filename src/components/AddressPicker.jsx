import { useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { listarDirecciones, crearDireccion } from '../api/clienteApi'
import { fetchZonasDomicilio } from '../api/zonasApi'
import { formatPrice } from '../utils/formatPrice'

export default function AddressPicker({ value, onChange }) {
  const { isAuthenticated } = useAuth()
  const [direcciones, setDirecciones] = useState([])
  const [zonas, setZonas] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({ etiqueta: '', direccionTexto: '', zonaDomicilioId: '', referencia: '' })
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let cancelled = false
    Promise.all([listarDirecciones(), fetchZonasDomicilio()])
      .then(([dirs, zs]) => {
        if (cancelled) return
        setDirecciones(dirs)
        setZonas(zs)
        setLoading(false)
        if (dirs.length === 0) {
          setShowForm(true)
        } else if (!value) {
          onChange((dirs.find((d) => d.predeterminada) ?? dirs[0]).id)
        }
      })
      .catch((err) => {
        if (cancelled) return
        setError(err.message)
        setLoading(false)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated])

  function tarifaDeZona(zonaId) {
    return zonas.find((z) => z.id === zonaId)?.tarifaVigente ?? null
  }

  async function handleGuardar(e) {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const nueva = await crearDireccion({
        etiqueta: form.etiqueta || null,
        direccionTexto: form.direccionTexto,
        zonaDomicilioId: form.zonaDomicilioId ? Number(form.zonaDomicilioId) : null,
        referencia: form.referencia || null,
        predeterminada: direcciones.length === 0,
      })
      setDirecciones((prev) => [...prev, nueva])
      onChange(nueva.id)
      setShowForm(false)
      setForm({ etiqueta: '', direccionTexto: '', zonaDomicilioId: '', referencia: '' })
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return <p className="cart-status">Cargando direcciones…</p>
  }

  return (
    <div className="address-picker">
      {error && <p className="cart-error">{error}</p>}

      {direcciones.length > 0 && (
        <div className="address-list">
          {direcciones.map((d) => (
            <label key={d.id} className="address-option">
              <input type="radio" name="direccion" checked={value === d.id} onChange={() => onChange(d.id)} />
              <span>
                <strong>{d.etiqueta || 'Dirección'}</strong> — {d.direccionTexto}
                {tarifaDeZona(d.zonaDomicilioId) != null && (
                  <em className="address-tarifa"> (domicilio {formatPrice(tarifaDeZona(d.zonaDomicilioId))})</em>
                )}
              </span>
            </label>
          ))}
        </div>
      )}

      {!showForm && (
        <button type="button" className="address-add-toggle" onClick={() => setShowForm(true)}>
          + Agregar dirección
        </button>
      )}

      {showForm && (
        <form className="address-form" onSubmit={handleGuardar}>
          <input
            required
            placeholder="Etiqueta (Casa, Trabajo...)"
            value={form.etiqueta}
            onChange={(e) => setForm({ ...form, etiqueta: e.target.value })}
          />
          <input
            required
            placeholder="Dirección completa"
            value={form.direccionTexto}
            onChange={(e) => setForm({ ...form, direccionTexto: e.target.value })}
          />
          <select
            required
            value={form.zonaDomicilioId}
            onChange={(e) => setForm({ ...form, zonaDomicilioId: e.target.value })}
          >
            <option value="">Selecciona tu zona</option>
            {zonas.map((z) => (
              <option key={z.id} value={z.id}>
                {z.nombre} — {formatPrice(z.tarifaVigente)}
              </option>
            ))}
          </select>
          <input
            placeholder="Referencia (opcional)"
            value={form.referencia}
            onChange={(e) => setForm({ ...form, referencia: e.target.value })}
          />
          <div className="address-form-actions">
            <button type="submit" className="cart-confirm-button" disabled={saving}>
              {saving ? 'Guardando…' : 'Guardar dirección'}
            </button>
            {direcciones.length > 0 && (
              <button type="button" className="address-cancel" onClick={() => setShowForm(false)}>
                Cancelar
              </button>
            )}
          </div>
        </form>
      )}
    </div>
  )
}
