import { useState } from 'react'
import { useAuth } from '../context/AuthContext'

export default function StaffLogin() {
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [status, setStatus] = useState('idle')
  const [error, setError] = useState(null)

  async function handleSubmit(e) {
    e.preventDefault()
    setStatus('sending')
    setError(null)
    try {
      await login(email, password)
    } catch (err) {
      setError(err.message)
      setStatus('idle')
    }
  }

  return (
    <div className="staff-login">
      <form className="auth-form staff-login-form" onSubmit={handleSubmit}>
        <h1 className="staff-login-title">Mr. Elote — Panel de staff</h1>
        <input
          type="email"
          required
          placeholder="Correo electrónico"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <input
          type="password"
          required
          placeholder="Contraseña"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        {error && <p className="cart-error">{error}</p>}
        <button type="submit" className="cart-confirm-button" disabled={status === 'sending'}>
          {status === 'sending' ? 'Entrando...' : 'Entrar'}
        </button>
      </form>
    </div>
  )
}
