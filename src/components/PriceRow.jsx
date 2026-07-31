import { formatPrice } from '../utils/formatPrice'

function PersonIcon() {
  return (
    <svg className="person-icon" viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="7" r="4" fill="currentColor" />
      <path
        d="M4 21c0-4.4 3.6-8 8-8s8 3.6 8 8"
        fill="none"
        stroke="currentColor"
        strokeWidth="2.5"
        strokeLinecap="round"
      />
    </svg>
  )
}

export default function PriceRow({ people, price }) {
  return (
    <div className="price-row">
      <span
        className="people-icons"
        aria-label={`Porción para ${people} persona${people > 1 ? 's' : ''}`}
      >
        {Array.from({ length: people }, (_, i) => (
          <PersonIcon key={i} />
        ))}
      </span>
      <span className="price">{formatPrice(price)}</span>
    </div>
  )
}
