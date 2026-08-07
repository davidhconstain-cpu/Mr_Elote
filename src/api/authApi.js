import { API_BASE_URL } from './config'

async function handle(response) {
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.mensaje || `No se pudo completar la solicitud (${response.status})`)
  }
  return response.json()
}

export function login(email, password) {
  return fetch(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  }).then(handle)
}

export function registro({ nombre, email, telefono, password }) {
  return fetch(`${API_BASE_URL}/auth/registro`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nombre, email, telefono, password }),
  }).then(handle)
}
