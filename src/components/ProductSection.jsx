import PriceRow from './PriceRow'
import ProductPhoto from './ProductPhoto'
import { useCart } from '../context/CartContext'

export default function ProductSection({ product }) {
  const { addItem, quantityOf } = useCart()

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
            quantity={p.productoId ? quantityOf(p.productoId) : 0}
            onAdd={
              p.productoId
                ? () =>
                    addItem({
                      productoId: p.productoId,
                      nombre: product.name,
                      porcion: p.people,
                      price: p.price,
                    })
                : undefined
            }
          />
        ))}
      </div>
      <ProductPhoto image={product.image} name={product.name} />
    </section>
  )
}
