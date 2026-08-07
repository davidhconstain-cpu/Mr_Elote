import { useState } from 'react'
import { useAuth } from '../context/AuthContext'

export default function AuthModal() {
  const { login, registro, closeAuthModal } = useAuth()
  const [tab, setTab] = useState('login')
  const [loginForm, setLoginForm] = useState({ email: '', password: '' })
  const [registroForm, setRegistroForm] = useState({ nombre: '', email: '', telefono: '', password: '' })
  const [status, setStatus] = useState('idle')
  const [error, setError] = useState(null)

  async function handleLogin(e) {
    e.preventDefault()
    setStatus('sending')
    setError(null)
    try {
      await login(loginForm.email, loginForm.password)
    } catch (err) {
      setError(err.message)
      setStatus('idle')
    }
  }

  async function handleRegistro(e) {
    e.preventDefault()
    setStatus('sending')
    setError(null)
    try {
      await registro(registroForm)
    } catch (err) {
      setError(err.message)
      setStatus('idle')
    }
  }

  return (
    <div className="auth-overlay" onClick={closeAuthModal}>
      <div className="auth-modal" onClick={(e) => e.stopPropagation()}>
        <div className="auth-modal-header">
          <div className="auth-tabs">
            <button type="button" className={tab === 'login' ? 'active' : ''} onClick={() => setTab('login')}>
              Iniciar sesión
            </button>
            <button type="button" className={tab === 'registro' ? 'active' : ''} onClick={() => setTab('registro')}>
              Crear cuenta
            </button>
          </div>
          <button type="button" className="cart-close" onClick={closeAuthModal} aria-label="Cerrar">
            ✕
          </button>
        </div>

        {tab === 'login' ? (
          <form className="auth-form" onSubmit={handleLogin}>
            <input
              type="email"
              required
              placeholder="Correo electrónico"
              value={loginForm.email}
              onChange={(e) => setLoginForm({ ...loginForm, email: e.target.value })}
            />
            <input
              type="password"
              required
              placeholder="Contraseña"
              value={loginForm.password}
              onChange={(e) => setLoginForm({ ...loginForm, password: e.target.value })}
            />
            {error && <p className="cart-error">{error}</p>}
            <button type="submit" className="cart-confirm-button" disabled={status === 'sending'}>
              {status === 'sending' ? 'Entrando...' : 'Entrar'}
            </button>
          </form>
        ) : (
          <form className="auth-form" onSubmit={handleRegistro}>
            <input
              required
              placeholder="Nombre"
              value={registroForm.nombre}
              onChange={(e) => setRegistroForm({ ...registroForm, nombre: e.target.value })}
            />
            <input
              type="email"
              required
              placeholder="Correo electrónico"
              value={registroForm.email}
              onChange={(e) => setRegistroForm({ ...registroForm, email: e.target.value })}
            />
            <input
              placeholder="Teléfono (opcional)"
              value={registroForm.telefono}
              onChange={(e) => setRegistroForm({ ...registroForm, telefono: e.target.value })}
            />
            <input
              type="password"
              required
              minLength={8}
              placeholder="Contraseña (mínimo 8 caracteres)"
              value={registroForm.password}
              onChange={(e) => setRegistroForm({ ...registroForm, password: e.target.value })}
            />
            {error && <p className="cart-error">{error}</p>}
            <button type="submit" className="cart-confirm-button" disabled={status === 'sending'}>
              {status === 'sending' ? 'Creando cuenta...' : 'Crear cuenta'}
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
