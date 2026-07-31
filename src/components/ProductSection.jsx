import PriceRow from './PriceRow'
import ProductPhoto from './ProductPhoto'

export default function ProductSection({ product }) {
  return (
    <section id={product.id} className="product-section">
      <h2 className="product-name">{product.name}</h2>
      <p className="product-description">{product.description}</p>
      <div className="price-list">
        {product.prices.map((p) => (
          <PriceRow key={p.people} people={p.people} price={p.price} />
        ))}
      </div>
      <ProductPhoto image={product.image} name={product.name} />
    </section>
  )
}
