import { useEffect, useState } from 'react'
import { fetchOpcionesDeProducto } from '../api/catalogApi'
import { formatPrice } from '../utils/formatPrice'

export default function ItemCustomizeModal({ nombre, priceTier, onClose, onAdd }) {
  const [opciones, setOpciones] = useState(priceTier.tieneOpciones ? null : [])
  const [error, setError] = useState(null)
  const [seleccionOpciones, setSeleccionOpciones] = useState({}) // { [opcionId]: valorOpcionId }
  const [seleccionAdicionales, setSeleccionAdicionales] = useState(new Set())

  useEffect(() => {
    if (!priceTier.tieneOpciones) return
    let cancelled = false
    fetchOpcionesDeProducto(priceTier.productoId)
      .then((data) => {
        if (cancelled) return
        setOpciones(data)
      })
      .catch((err) => {
        if (cancelled) return
        setError(err.message)
        setOpciones([])
      })
    return () => {
      cancelled = true
    }
  }, [priceTier.productoId, priceTier.tieneOpciones])

  const adicionales = priceTier.adicionales ?? []

  function elegirValor(opcionId, valorOpcionId) {
    setSeleccionOpciones((prev) => ({ ...prev, [opcionId]: valorOpcionId }))
  }

  function alternarAdicional(adicionalId) {
    setSeleccionAdicionales((prev) => {
      const next = new Set(prev)
      if (next.has(adicionalId)) next.delete(adicionalId)
      else next.add(adicionalId)
      return next
    })
  }

  const cargando = opciones === null
  const obligatoriasSinElegir = (opciones ?? []).some(
    (o) => o.obligatoria && !seleccionOpciones[o.opcionId],
  )

  const opcionesElegidas = (opciones ?? [])
    .map((o) => {
      const valorId = seleccionOpciones[o.opcionId]
      if (!valorId) return null
      const valor = o.valores.find((v) => v.id === valorId)
      return valor ? { valorOpcionId: valor.id, nombre: `${o.nombre}: ${valor.nombre}`, precioAdicional: valor.precioAdicional } : null
    })
    .filter(Boolean)

  const adicionalesElegidos = adicionales.filter((a) => seleccionAdicionales.has(a.id))

  const precioUnitario =
    priceTier.price +
    opcionesElegidas.reduce((sum, o) => sum + o.precioAdicional, 0) +
    adicionalesElegidos.reduce((sum, a) => sum + a.precio, 0)

  function handleAgregar() {
    const key = [
      `p-${priceTier.productoId}`,
      opcionesElegidas.map((o) => o.valorOpcionId).sort().join('.'),
      adicionalesElegidos.map((a) => a.id).sort().join('.'),
    ].join('|')

    onAdd({
      key,
      tipo: 'producto',
      productoId: priceTier.productoId,
      nombre,
      porcion: priceTier.people,
      opciones: opcionesElegidas,
      adicionales: adicionalesElegidos.map((a) => ({ adicionalId: a.id, nombre: a.nombre, precio: a.precio })),
      price: precioUnitario,
    })
    onClose()
  }

  return (
    <div className="auth-overlay" onClick={onClose}>
      <div className="auth-modal" onClick={(e) => e.stopPropagation()}>
        <div className="auth-modal-header">
          <h2 className="customize-title">{nombre}</h2>
          <button type="button" className="cart-close" onClick={onClose} aria-label="Cerrar">
            ✕
          </button>
        </div>

        {error && <p className="cart-error">{error}</p>}

        {cargando ? (
          <p className="cart-status">Cargando opciones…</p>
        ) : (
          <div className="customize-body">
            {opciones.map((o) => (
              <fieldset key={o.opcionId} className="customize-group">
                <legend>
                  {o.nombre} {o.obligatoria && <span className="customize-required">(obligatorio)</span>}
                </legend>
                {o.valores.map((v) => (
                  <label key={v.id} className="customize-option">
                    <input
                      type="radio"
                      name={`opcion-${o.opcionId}`}
                      checked={seleccionOpciones[o.opcionId] === v.id}
                      onChange={() => elegirValor(o.opcionId, v.id)}
                    />
                    <span>
                      {v.nombre}
                      {v.precioAdicional > 0 && <em> (+{formatPrice(v.precioAdicional)})</em>}
                    </span>
                  </label>
                ))}
              </fieldset>
            ))}

            {adicionales.length > 0 && (
              <fieldset className="customize-group">
                <legend>Adicionales</legend>
                {adicionales.map((a) => (
                  <label key={a.id} className="customize-option">
                    <input
                      type="checkbox"
                      checked={seleccionAdicionales.has(a.id)}
                      onChange={() => alternarAdicional(a.id)}
                    />
                    <span>
                      {a.nombre} <em>(+{formatPrice(a.precio)})</em>
                    </span>
                  </label>
                ))}
              </fieldset>
            )}

            <div className="cart-total">
              <span>Total</span>
              <span>{formatPrice(precioUnitario)}</span>
            </div>

            <button
              type="button"
              className="cart-confirm-button"
              onClick={handleAgregar}
              disabled={obligatoriasSinElegir}
            >
              Agregar al carrito
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
