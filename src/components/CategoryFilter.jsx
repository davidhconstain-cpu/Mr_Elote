import ProductPhoto from './ProductPhoto'

/**
 * Barra de categorías tipo "filtro": al elegir una, el catálogo muestra solo
 * esa categoría en vez de hacer scroll hasta ella (antes eran anclas `#id`).
 * Queda fija bajo el header para que el menú siempre esté visible.
 */
export default function CategoryFilter({ categorias, activa, onChange }) {
  return (
    <nav className="category-filter" aria-label="Categorías del menú">
      <div className="category-filter-track">
        {categorias.map((cat) => {
          const seleccionada = cat.id === activa
          return (
            <button
              key={cat.id}
              type="button"
              className={`category-chip${seleccionada ? ' active' : ''}`}
              onClick={() => onChange(cat.id)}
              aria-pressed={seleccionada}
            >
              <span className="category-chip-thumb">
                <ProductPhoto image={cat.image} name={cat.label} variant="thumb" />
              </span>
              <span className="category-chip-label">{cat.label}</span>
            </button>
          )
        })}
      </div>
    </nav>
  )
}
