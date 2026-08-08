import { API_BASE_URL } from '../api/config'

async function handle(response) {
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.mensaje || `No se pudo completar la solicitud (${response.status})`)
  }
  return response.status === 204 ? null : response.json()
}

// Wrapper genérico autenticado para todo el panel de staff: son muchos
// endpoints de solo CRUD/transiciones, no vale la pena un archivo por cada
// uno como en el catálogo de cliente.
export function staffFetch(token, path, options = {}) {
  const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, ...options.headers }
  return fetch(`${API_BASE_URL}${path}`, { ...options, headers }).then(handle)
}
