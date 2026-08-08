import Header from './components/Header'
import QuickNav from './components/QuickNav'
import MesaBanner from './components/MesaBanner'
import ProductSection from './components/ProductSection'
import CombosSection from './components/CombosSection'
import DrinksSection from './components/DrinksSection'
import AdicionesSection from './components/AdicionesSection'
import Footer from './components/Footer'
import Cart from './components/Cart'
import { drinks, adiciones, quickLinks } from './data/products'
import { useCatalog } from './hooks/useCatalog'
import { CartProvider } from './context/CartContext'
import { MesaProvider } from './context/MesaContext'

export default function CatalogApp() {
  const { loading, error, products } = useCatalog()

  return (
    <MesaProvider>
      <CartProvider>
        <div className="catalog">
          <Header />
          <QuickNav links={quickLinks} />
          <MesaBanner />
          <main>
            {loading && <p className="catalog-status">Cargando el menú…</p>}
            {error && (
              <p className="catalog-status catalog-status-error">
                No se pudo cargar el menú desde el servidor. Verifica que el backend esté corriendo.
              </p>
            )}
            {!loading &&
              !error &&
              products.map((product) => <ProductSection key={product.id} product={product} />)}
            <CombosSection />
            <DrinksSection drinks={drinks} />
            <AdicionesSection adiciones={adiciones} />
          </main>
          <Footer />
          <Cart />
        </div>
      </CartProvider>
    </MesaProvider>
  )
}
