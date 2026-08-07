import { API_BASE_URL } from './config'

function authHeaders(token) {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }
}

async function handle(response) {
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.mensaje || `No se pudo completar la solicitud (${response.status})`)
  }
  return response.status === 204 ? null : response.json()
}

export function listarDirecciones(token) {
  return fetch(`${API_BASE_URL}/clientes/me/direcciones`, { headers: authHeaders(token) }).then(handle)
}

export function crearDireccion(token, direccion) {
  return fetch(`${API_BASE_URL}/clientes/me/direcciones`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(direccion),
  }).then(handle)
}

export function misPedidos(token, page = 0) {
  return fetch(`${API_BASE_URL}/clientes/me/pedidos?page=${page}&size=10`, {
    headers: authHeaders(token),
  }).then(handle)
}

export function verPedido(token, id) {
  return fetch(`${API_BASE_URL}/pedidos/${id}`, { headers: authHeaders(token) }).then(handle)
}

export function historialPedido(token, id) {
  return fetch(`${API_BASE_URL}/pedidos/${id}/historial`, { headers: authHeaders(token) }).then(handle)
}
