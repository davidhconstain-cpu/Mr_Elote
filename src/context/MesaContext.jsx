import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { resolverQrMesa } from '../api/mesasApi'

const STORAGE_KEY = 'mrelote.mesa.codigo'
const MesaContext = createContext(null)

function codigoDeLaUrl() {
  return new URLSearchParams(window.location.search).get('mesa')
}

// Un cliente llega aquí escaneando el QR impreso en su mesa
// (?mesa=<codigo> en la URL); se resuelve contra el backend para saber
// qué mesa es y se recuerda en localStorage por si recarga la página.
export function MesaProvider({ children }) {
  const [state, setState] = useState({ loading: true, mesa: null, codigo: null, error: null })

  useEffect(() => {
    const codigo = codigoDeLaUrl() || localStorage.getItem(STORAGE_KEY)
    if (!codigo) {
      setState({ loading: false, mesa: null, codigo: null, error: null })
      return
    }

    let cancelled = false
    resolverQrMesa(codigo)
      .then((mesa) => {
        if (cancelled) return
        localStorage.setItem(STORAGE_KEY, codigo)
        setState({ loading: false, mesa, codigo, error: null })
      })
      .catch((err) => {
        if (cancelled) return
        localStorage.removeItem(STORAGE_KEY)
        setState({ loading: false, mesa: null, codigo: null, error: err.message })
      })

    return () => {
      cancelled = true
    }
  }, [])

  const value = useMemo(() => state, [state])

  return <MesaContext.Provider value={value}>{children}</MesaContext.Provider>
}

export function useMesa() {
  const context = useContext(MesaContext)
  if (!context) {
    throw new Error('useMesa debe usarse dentro de un MesaProvider')
  }
  return context
}
