import { API_BASE_URL } from './config'

export async function fetchMetodosPago() {
  const response = await fetch(`${API_BASE_URL}/metodos-pago`)
  if (!response.ok) {
    throw new Error(`${response.status}`)
  }
  const metodos = await response.json()
  return metodos.filter((m) => m.activo)
}
