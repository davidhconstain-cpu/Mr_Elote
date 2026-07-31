import { formatPrice } from '../utils/formatPrice'

export default function DrinksSection({ drinks }) {
  return (
    <section id="bebidas" className="drinks-section">
      <h2 className="section-title">Bebidas</h2>
      <ul className="drinks-list">
        {drinks.map((drink) => (
          <li key={drink.id} className="drink-item">
            <span className="drink-name">{drink.name}</span>
            <span className="price">{formatPrice(drink.price)}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
