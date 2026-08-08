import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { configurarSesionHttp } from '../api/http'
import {
  login as apiLogin,
  registro as apiRegistro,
  loginConCodigo as apiLoginConCodigo,
} from '../api/authApi'
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

  /** `identificador` puede ser el correo o el número de documento. */
  const login = useCallback(
    async (identificador, password) => {
      const data = await apiLogin(identificador, password)
      persist(data)
      setAuthModalOpen(false)
    },
    [persist],
  )

  const loginConCodigo = useCallback(
    async (identificador, codigo) => {
      const data = await apiLoginConCodigo(identificador, codigo)
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

  // El cliente HTTP necesita leer la sesión más reciente sin volver a
  // registrarse en cada render; una ref evita ese baile de dependencias.
  const sessionRef = useRef(session)
  sessionRef.current = session

  useEffect(() => {
    configurarSesionHttp({
      obtenerSesion: () => sessionRef.current,
      // Renovación silenciosa: solo cambian los tokens, el usuario sigue igual.
      onRenovada: (data) => persist({ ...sessionRef.current, ...data }),
      // Ni el refresh sirvió: se cierra la sesión y se pide entrar de nuevo,
      // en vez de dejar la interfaz mostrando un usuario que ya no lo está.
      onExpirada: () => {
        persist(null)
        setAuthModalOpen(true)
      },
    })
  }, [persist])

  const value = useMemo(
    () => ({
      usuario: session?.usuario ?? null,
      accessToken: session?.accessToken ?? null,
      isAuthenticated: Boolean(session?.accessToken),
      login,
      loginConCodigo,
      registro,
      logout,
      authModalOpen,
      openAuthModal: () => setAuthModalOpen(true),
      closeAuthModal: () => setAuthModalOpen(false),
    }),
    [session, login, loginConCodigo, registro, logout, authModalOpen],
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
