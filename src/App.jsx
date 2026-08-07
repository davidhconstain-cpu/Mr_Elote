import Header from './components/Header'
import QuickNav from './components/QuickNav'
import ProductSection from './components/ProductSection'
import DrinksSection from './components/DrinksSection'
import AdicionesSection from './components/AdicionesSection'
import Footer from './components/Footer'
import { drinks, adiciones, quickLinks } from './data/products'
import { useCatalog } from './hooks/useCatalog'
import './App.css'

function App() {
  const { loading, error, products } = useCatalog()

  return (
    <div className="catalog">
      <Header />
      <QuickNav links={quickLinks} />
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
        <DrinksSection drinks={drinks} />
        <AdicionesSection adiciones={adiciones} />
      </main>
      <Footer />
    </div>
  )
}

export default App
