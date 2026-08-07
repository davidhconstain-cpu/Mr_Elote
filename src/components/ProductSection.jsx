import { useState } from 'react'
import PriceRow from './PriceRow'
import ProductPhoto from './ProductPhoto'
import ItemCustomizeModal from './ItemCustomizeModal'
import { useCart } from '../context/CartContext'

export default function ProductSection({ product }) {
  const { addItem, quantityOf } = useCart()
  const [personalizando, setPersonalizando] = useState(null) // priceTier en edición, o null

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
    <section id={product.id} className="product-section">
      <h2 className="product-name">{product.name}</h2>
      <p className="product-description">{product.description}</p>
      <div className="price-list">
        {product.prices.map((p) => (
          <PriceRow
            key={p.people}
            people={p.people}
            price={p.price}
            quantity={p.productoId ? quantityOf(`p-${p.productoId}`) : 0}
            onAdd={p.productoId ? () => handleAdd(p) : undefined}
          />
        ))}
      </div>
      <ProductPhoto image={product.image} name={product.name} />

      {personalizando && (
        <ItemCustomizeModal
          nombre={product.name}
          priceTier={personalizando}
          onClose={() => setPersonalizando(null)}
          onAdd={addItem}
        />
      )}
    </section>
  )
}
