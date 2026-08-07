import { useAuth } from '../context/AuthContext'

export default function Header() {
  const { usuario, isAuthenticated, logout, openAuthModal } = useAuth()

  return (
    <header className="site-header">
      <div className="logo">
        <span className="logo-mr">MR</span>
        <span className="logo-elote">ELOTE</span>
      </div>
      <p className="tagline">Comida callejera artesanal</p>
      <div className="account-bar">
        {isAuthenticated ? (
          <>
            <span className="account-name">Hola, {usuario.nombre}</span>
            <button type="button" className="account-link" onClick={logout}>
              Salir
            </button>
          </>
        ) : (
          <button type="button" className="account-link" onClick={openAuthModal}>
            Iniciar sesión
          </button>
        )}
      </div>
    </header>
  )
}
