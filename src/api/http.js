import { API_BASE_URL } from './config'

/**
 * Cliente HTTP para las llamadas autenticadas.
 *
 * Existe por un problema concreto: el access token vive 30 minutos, y antes
 * nadie lo renovaba. Pasado ese rato la interfaz seguía mostrando la sesión
 * ("Hola, Sandra") pero toda llamada devolvía 401 — "Mis pedidos" se quedaba
 * vacío y el pedido se creaba sin dueño. Aquí, ante un 401 se renueva el
 * token con el refresh token y se reintenta la llamada una sola vez; si el
 * refresh tampoco sirve, se cierra la sesión de verdad para que la interfaz
 * deje de mentir y el cliente vuelva a entrar.
 */

let sesion = null // { accessToken, refreshToken, usuario }
let alRenovar = () => {}
let alExpirar = () => {}

export function configurarSesionHttp({ obtenerSesion, onRenovada, onExpirada }) {
  sesion = obtenerSesion
  alRenovar = onRenovada
  alExpirar = onExpirada
}

function sesionActual() {
  return typeof sesion === 'function' ? sesion() : sesion
}

async function parsear(response) {
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    const detalle = Array.isArray(body?.detalles) && body.detalles.length > 0
      ? body.detalles.map((d) => d.replace(/^[a-zA-Z]+:\s*/, '')).join('. ')
      : null
    throw new Error(detalle || body?.mensaje || `No se pudo completar la solicitud (${response.status})`)
  }
  return response.status === 204 ? null : response.json()
}

function conToken(options, token) {
  const headers = { ...(options.headers ?? {}) }
  if (options.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`
  return { ...options, headers }
}

/** Pide un access token nuevo. Devuelve el token, o null si ya no se puede. */
async function renovar() {
  const actual = sesionActual()
  if (!actual?.refreshToken) return null
  try {
    const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${actual.refreshToken}` },
    })
    if (!response.ok) return null
    const data = await response.json()
    alRenovar(data)
    return data.accessToken
  } catch {
    return null
  }
}

/**
 * fetch autenticado con renovación transparente. `path` es relativo a
 * API_BASE_URL (ej. '/clientes/me/pedidos').
 */
export async function authFetch(path, options = {}) {
  const actual = sesionActual()
  let response = await fetch(`${API_BASE_URL}${path}`, conToken(options, actual?.accessToken))

  if (response.status === 401) {
    const tokenNuevo = await renovar()
    if (!tokenNuevo) {
      alExpirar()
      throw new Error('Tu sesión expiró. Vuelve a iniciar sesión.')
    }
    response = await fetch(`${API_BASE_URL}${path}`, conToken(options, tokenNuevo))
  }

  return parsear(response)
}

/** Igual que authFetch pero para rutas públicas (sin token). */
export async function publicFetch(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, conToken(options, null))
  return parsear(response)
}
