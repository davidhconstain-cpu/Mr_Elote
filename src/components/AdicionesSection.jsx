import { formatPrice } from '../utils/formatPrice'

export default function AdicionesSection({ adiciones }) {
  return (
    <section id="adiciones" className="adiciones-section">
      <h2 className="section-title accent">Adiciones</h2>
      <ul className="adiciones-list">
        {adiciones.map((item) => (
          <li key={item.name} className="adicion-item">
            <span>{item.name}</span>
            <span className="price">{formatPrice(item.price)}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
