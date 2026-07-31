export default function ProductPhoto({ image, name }) {
  if (image) {
    return (
      <div className="product-photo">
        <img src={image} alt={name} loading="lazy" />
      </div>
    )
  }

  return (
    <div className="product-photo placeholder">
      <span className="placeholder-icon" aria-hidden="true">
        📷
      </span>
      <span className="placeholder-text">Foto de {name}</span>
    </div>
  )
}
