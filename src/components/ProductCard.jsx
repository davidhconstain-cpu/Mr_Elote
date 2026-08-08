import { useState } from 'react'
import ProductPhoto from './ProductPhoto'
import ItemCustomizeModal from './ItemCustomizeModal'
import { useCart } from '../context/CartContext'
import { formatPrice } from '../utils/formatPrice'

/**
 * Tarjeta de producto: foto grande arriba, nombre/descripción, y una fila por
 * porción con su precio y botón de agregar. Reemplaza a la antigua
 * ProductSection (lista vertical a todo lo ancho) por una tarjeta que entra en
 * una grilla.
 */
export default function ProductCard({ product }) {
  const { addItem, quantityOf } = useCart()
  const [personalizando, setPersonalizando] = useState(null)

  function handleAdd(priceTier) {
    const necesitaPersonalizar = priceTier.tieneOpciones || (priceTier.adicionales?.length ?? 0) > 0
    if (necesitaPersonalizar) {
      setPersonalizando(priceTier)
      return
    }
    addItem({
      key: `p-${priceTier.productoId}`,
      tipo: 'producto',
      productoId: priceTier.productoId,
      nombre: product.name,
      porcion: priceTier.people,
      price: priceTier.price,
    })
  }

  return (
    <article className="product-card" id={product.id}>
      <ProductPhoto image={product.image} name={product.name} />

      <div className="product-card-body">
        <h3 className="product-card-name">{product.name}</h3>
        {product.description && <p className="product-card-description">{product.description}</p>}

        <div className="product-card-tiers">
          {product.prices.map((p) => {
            const cantidad = p.productoId ? quantityOf(`p-${p.productoId}`) : 0
            return (
              <div key={p.people} className="product-tier">
                <span className="product-tier-porcion">
                  {p.people} {p.people === 1 ? 'persona' : 'personas'}
                </span>
                <span className="product-tier-price">{formatPrice(p.price)}</span>
                <button
                  type="button"
                  className="add-button"
                  onClick={() => handleAdd(p)}
                  disabled={!p.productoId}
                  aria-label={`Agregar ${product.name} para ${p.people} al carrito`}
                >
                  {cantidad > 0 ? `Agregado (${cantidad})` : 'Agregar'}
                </button>
              </div>
            )
          })}
        </div>
      </div>

      {personalizando && (
        <ItemCustomizeModal
          nombre={product.name}
          priceTier={personalizando}
          onClose={() => setPersonalizando(null)}
          onAdd={addItem}
        />
      )}
    </article>
  )
}
