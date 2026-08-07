import { API_BASE_URL } from './config'

export async function fetchZonasDomicilio() {
  const response = await fetch(`${API_BASE_URL}/zonas-domicilio`)
  if (!response.ok) {
    throw new Error(`${response.status}`)
  }
  return response.json()
}
