import { useEffect, useState } from 'react'
import { fetchCombos } from '../api/combosApi'

export function useCombos() {
  const [state, setState] = useState({ loading: true, error: null, combos: [] })

  useEffect(() => {
    let cancelled = false

    fetchCombos()
      .then((combos) => {
        if (cancelled) return
        setState({ loading: false, error: null, combos: combos.filter((c) => c.activo) })
      })
      .catch((error) => {
        if (cancelled) return
        setState({ loading: false, error: error.message, combos: [] })
      })

    return () => {
      cancelled = true
    }
  }, [])

  return state
}
