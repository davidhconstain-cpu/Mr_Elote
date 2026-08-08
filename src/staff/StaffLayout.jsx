import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import StaffLogin from './StaffLogin'
import { MODULOS } from './modulos'

export default function StaffLayout() {
  const { usuario, isAuthenticated, logout } = useAuth()

  if (!isAuthenticated) {
    return <StaffLogin />
  }

  const modulosVisibles = MODULOS.filter((m) => m.roles.includes(usuario.rol))

  if (modulosVisibles.length === 0) {
    return (
      <div className="staff-login">
        <div className="staff-login-form">
          <h1 className="staff-login-title">Sin acceso</h1>
          <p className="cart-status">
            Tu cuenta ({usuario.nombre}, rol {usuario.rol}) no tiene ningún módulo del panel de staff asignado.
          </p>
          <button type="button" className="cart-confirm-button" onClick={logout}>
            Salir
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="staff-shell">
      <aside className="staff-nav">
        <div className="staff-nav-brand">MR ELOTE STAFF</div>
        <nav>
          {modulosVisibles.map((m) => (
            <NavLink
              key={m.path}
              to={`/staff/${m.path}`}
              className={({ isActive }) => `staff-nav-link${isActive ? ' active' : ''}`}
            >
              {m.label}
            </NavLink>
          ))}
        </nav>
        <div className="staff-nav-footer">
          <span>{usuario.nombre}</span>
          <span className="staff-nav-role">{usuario.rol}</span>
          <button type="button" className="account-link" onClick={logout}>
            Salir
          </button>
        </div>
      </aside>
      <main className="staff-content">
        <Outlet />
      </main>
    </div>
  )
}
