import { API_BASE_URL } from './config'

export async function resolverQrMesa(codigo) {
  const response = await fetch(`${API_BASE_URL}/mesas/qr/${encodeURIComponent(codigo)}`)
  if (!response.ok) {
    throw new Error(`${response.status}`)
  }
  return response.json()
}
