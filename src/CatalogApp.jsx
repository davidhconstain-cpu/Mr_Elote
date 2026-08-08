import { useMemo, useState } from 'react'
import Header from './components/Header'
import CategoryFilter from './components/CategoryFilter'
import MesaBanner from './components/MesaBanner'
import ProductCard from './components/ProductCard'
import CombosSection from './components/CombosSection'
import DrinksSection from './components/DrinksSection'
import AdicionesSection from './components/AdicionesSection'
import Footer from './components/Footer'
import Cart from './components/Cart'
import { drinks, adiciones } from './data/products'
import { useCatalog } from './hooks/useCatalog'
import { CartProvider } from './context/CartContext'
import { MesaProvider } from './context/MesaContext'

const TODAS = 'todas'
const COMBOS = 'combos'
const BEBIDAS = 'bebidas'
const ADICIONES = 'adiciones'

export default function CatalogApp() {
  const { loading, error, products } = useCatalog()
  const [categoria, setCategoria] = useState(TODAS)

  // Las categorías salen del catálogo real (un chip por plato), así que un
  // producto nuevo creado desde el panel de administración aparece solo.
  const categorias = useMemo(
    () => [
      { id: TODAS, label: 'Todas', image: products[0]?.image ?? null },
      ...products.map((p) => ({ id: p.id, label: p.name, image: p.image })),
      { id: COMBOS, label: 'Combos', image: null },
      { id: BEBIDAS, label: 'Bebidas', image: null },
      { id: ADICIONES, label: 'Adiciones', image: null },
    ],
    [products],
  )

  const esTodas = categoria === TODAS
  const productosVisibles = esTodas ? products : products.filter((p) => p.id === categoria)
  const mostrarCombos = esTodas || categoria === COMBOS
  const mostrarBebidas = esTodas || categoria === BEBIDAS
  const mostrarAdiciones = esTodas || categoria === ADICIONES

  return (
    <MesaProvider>
      <CartProvider>
        <div className="catalog">
          {/* Header + filtro viajan juntos como un solo bloque fijo, para que
              el menú siga visible al hacer scroll sin depender de una altura
              de header fija (que cambia entre móvil y escritorio). */}
          <div className="sticky-top">
            <Header />
            <CategoryFilter categorias={categorias} activa={categoria} onChange={setCategoria} />
          </div>
          <MesaBanner />

          <main className="catalog-main">
            <div className="container">
              {loading && <p className="catalog-status">Cargando el menú…</p>}
              {error && (
                <p className="catalog-status catalog-status-error">
                  No se pudo cargar el menú desde el servidor. Verifica que el backend esté corriendo.
                </p>
              )}

              {!loading && !error && productosVisibles.length > 0 && (
                <section className="catalog-section">
                  {!esTodas && <h2 className="section-title">{productosVisibles[0].name}</h2>}
                  <div className="product-grid">
                    {productosVisibles.map((product) => (
                      <ProductCard key={product.id} product={product} />
                    ))}
                  </div>
                </section>
              )}

              {mostrarCombos && <CombosSection />}
              {mostrarBebidas && <DrinksSection drinks={drinks} />}
              {mostrarAdiciones && <AdicionesSection adiciones={adiciones} />}
            </div>
          </main>

          <Footer />
          <Cart />
        </div>
      </CartProvider>
    </MesaProvider>
  )
}
