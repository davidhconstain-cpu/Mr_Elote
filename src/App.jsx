import Header from './components/Header'
import QuickNav from './components/QuickNav'
import ProductSection from './components/ProductSection'
import DrinksSection from './components/DrinksSection'
import AdicionesSection from './components/AdicionesSection'
import Footer from './components/Footer'
import { products, drinks, adiciones, quickLinks } from './data/products'
import './App.css'

function App() {
  return (
    <div className="catalog">
      <Header />
      <QuickNav links={quickLinks} />
      <main>
        {products.map((product) => (
          <ProductSection key={product.id} product={product} />
        ))}
        <DrinksSection drinks={drinks} />
        <AdicionesSection adiciones={adiciones} />
      </main>
      <Footer />
    </div>
  )
}

export default App
