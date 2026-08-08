import { useCombos } from '../hooks/useCombos'
import { useCart } from '../context/CartContext'
import { formatPrice } from '../utils/formatPrice'

export default function CombosSection() {
  const { loading, error, combos } = useCombos()
  const { addItem, quantityOf } = useCart()

  if (loading || error || combos.length === 0) {
    return null
  }

  return (
    <section id="combos" className="combos-section">
      <h2 className="section-title accent">Combos</h2>
      <div className="combos-list">
        {combos.map((combo) => {
          const cantidad = quantityOf(`c-${combo.id}`)
          return (
            <div key={combo.id} className={`combo-card${combo.disponible ? '' : ' agotado'}`}>
              <div className="combo-info">
                <span className="combo-name">{combo.nombre}</span>
                {combo.descripcion && <span className="combo-description">{combo.descripcion}</span>}
                <span className="combo-componentes">
                  {combo.componentes.map((c) => c.productoNombre).join(' + ')}
                </span>
              </div>
              <div className="combo-action">
                <span className="price">{formatPrice(combo.precio)}</span>
                {combo.disponible ? (
                  <button
                    type="button"
                    className="add-button"
                    onClick={() =>
                      addItem({
                        key: `c-${combo.id}`,
                        tipo: 'combo',
                        comboId: combo.id,
                        nombre: combo.nombre,
                        price: combo.precio,
                      })
                    }
                    aria-label={`Agregar ${combo.nombre} al carrito`}
                  >
                    {cantidad > 0 ? `Agregado (${cantidad})` : 'Agregar'}
                  </button>
                ) : (
                  <span className="combo-agotado-badge">Agotado</span>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </section>
  )
}
