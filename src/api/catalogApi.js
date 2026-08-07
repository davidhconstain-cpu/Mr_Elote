import { API_BASE_URL } from './config'

export async function fetchProductos() {
  const response = await fetch(`${API_BASE_URL}/productos?size=100&sort=id,asc`)
  if (!response.ok) {
    throw new Error(`${response.status}`)
  }
  const page = await response.json()
  return page.content ?? []
}
