import { authFetch } from '../api/http'

// Wrapper genérico autenticado para todo el panel de staff: son muchos
// endpoints de solo CRUD/transiciones, no vale la pena un archivo por cada
// uno como en el catálogo de cliente.
//
// El primer parámetro (token) se conserva por compatibilidad con las ~20
// páginas del panel que ya lo pasan, pero se ignora: authFetch toma el token
// de la sesión activa y lo renueva si venció, para que a media jornada no se
// caiga la pantalla de cocina o de caja sin explicación.
export function staffFetch(_token, path, options = {}) {
  return authFetch(path, options)
}
