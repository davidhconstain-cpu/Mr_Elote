import { authFetch } from './http'

// El token ya no se pasa por parámetro: authFetch lo toma de la sesión
// activa y lo renueva solo si venció (ver src/api/http.js).

export function listarDirecciones() {
  return authFetch('/clientes/me/direcciones')
}

export function crearDireccion(direccion) {
  return authFetch('/clientes/me/direcciones', {
    method: 'POST',
    body: JSON.stringify(direccion),
  })
}

export function misPedidos(page = 0) {
  return authFetch(`/clientes/me/pedidos?page=${page}&size=10`)
}

export function verPedido(id) {
  return authFetch(`/pedidos/${id}`)
}

export function historialPedido(id) {
  return authFetch(`/pedidos/${id}/historial`)
}
