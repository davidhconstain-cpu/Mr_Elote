import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { login as apiLogin, registro as apiRegistro } from '../api/authApi'
import AuthModal from '../components/AuthModal'

const STORAGE_KEY = 'mrelote.auth'
const AuthContext = createContext(null)

function loadStoredSession() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(loadStoredSession)
  const [authModalOpen, setAuthModalOpen] = useState(false)

  const persist = useCallback((data) => {
    setSession(data)
    if (data) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  }, [])

  const login = useCallback(
    async (email, password) => {
      const data = await apiLogin(email, password)
      persist(data)
      setAuthModalOpen(false)
    },
    [persist],
  )

  const registro = useCallback(
    async (payload) => {
      const data = await apiRegistro(payload)
      persist(data)
      setAuthModalOpen(false)
    },
    [persist],
  )

  const logout = useCallback(() => persist(null), [persist])

  const value = useMemo(
    () => ({
      usuario: session?.usuario ?? null,
      accessToken: session?.accessToken ?? null,
      isAuthenticated: Boolean(session?.accessToken),
      login,
      registro,
      logout,
      authModalOpen,
      openAuthModal: () => setAuthModalOpen(true),
      closeAuthModal: () => setAuthModalOpen(false),
    }),
    [session, login, registro, logout, authModalOpen],
  )

  return (
    <AuthContext.Provider value={value}>
      {children}
      {authModalOpen && <AuthModal />}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth debe usarse dentro de un AuthProvider')
  }
  return context
}
