const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'

export async function fetchProductos() {
  const response = await fetch(`${API_BASE_URL}/productos?size=100&sort=id,asc`)
  if (!response.ok) {
    throw new Error(`${response.status}`)
  }
  const page = await response.json()
  return page.content ?? []
}
