/**
 * Foto del producto. Cuando todavía no hay foto real cargada (Desgranado,
 * Salchipapa, Mechada), en vez de un recuadro vacío se dibuja una ilustración
 * genérica de comida callejera en los colores de la marca — no es una foto de
 * stock (evita problemas de licencia) y se reemplaza sola en cuanto se suba la
 * imagen real del producto vía POST /api/v1/productos/{id}/imagenes.
 */
export default function ProductPhoto({ image, name, variant = 'card' }) {
  const className = `product-photo product-photo-${variant}`

  if (image) {
    return (
      <div className={className}>
        <img src={image} alt={name} loading="lazy" />
      </div>
    )
  }

  return (
    <div className={`${className} placeholder`} role="img" aria-label={`Ilustración de ${name}`}>
      <svg viewBox="0 0 120 90" aria-hidden="true" focusable="false">
        <defs>
          <linearGradient id="mp-bg" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#241416" />
            <stop offset="100%" stopColor="#140c0d" />
          </linearGradient>
        </defs>
        <rect width="120" height="90" fill="url(#mp-bg)" />
        {/* vaso de comida callejera */}
        <path d="M42 34 h36 l-5 40 a4 4 0 0 1 -4 3 h-18 a4 4 0 0 1 -4 -3 z" fill="#e0212b" opacity="0.9" />
        <rect x="40" y="29" width="40" height="7" rx="3.5" fill="#f5f5f5" opacity="0.92" />
        {/* papas / mazorca asomando */}
        <rect x="50" y="16" width="5" height="15" rx="2.5" fill="#f0b429" transform="rotate(-12 52 24)" />
        <rect x="58" y="13" width="5" height="18" rx="2.5" fill="#ffd166" />
        <rect x="66" y="16" width="5" height="15" rx="2.5" fill="#f0b429" transform="rotate(12 68 24)" />
        {/* granos de maíz */}
        <circle cx="53" cy="47" r="2.4" fill="#ffd166" opacity="0.85" />
        <circle cx="62" cy="53" r="2.4" fill="#ffd166" opacity="0.7" />
        <circle cx="70" cy="45" r="2.4" fill="#ffd166" opacity="0.85" />
      </svg>
    </div>
  )
}
