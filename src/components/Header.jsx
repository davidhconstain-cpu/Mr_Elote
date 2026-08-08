import { useState } from 'react'
import { useAuth } from '../context/AuthContext'
import MisPedidosPanel from './MisPedidosPanel'

export default function Header() {
  const { usuario, isAuthenticated, logout, openAuthModal } = useAuth()
  const [misPedidosOpen, setMisPedidosOpen] = useState(false)

  return (
    <header className="site-header">
      <div className="container site-header-inner">
        <div className="site-brand">
          <div className="logo">
            <span className="logo-mr">MR</span>
            <span className="logo-elote">ELOTE</span>
          </div>
          <p className="tagline">Comida callejera artesanal</p>
        </div>

        <div className="account-bar">
          {isAuthenticated ? (
            <>
              <span className="account-name">Hola, {usuario.nombre}</span>
              <button type="button" className="account-link" onClick={() => setMisPedidosOpen(true)}>
                Mis pedidos
              </button>
              <button type="button" className="account-link" onClick={logout}>
                Salir
              </button>
            </>
          ) : (
            <button type="button" className="account-button" onClick={openAuthModal}>
              Iniciar sesión
            </button>
          )}
        </div>
      </div>

      {misPedidosOpen && <MisPedidosPanel onClose={() => setMisPedidosOpen(false)} />}
    </header>
  )
}
