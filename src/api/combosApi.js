import { API_BASE_URL } from './config'

export async function fetchCombos() {
  const response = await fetch(`${API_BASE_URL}/combos?size=50`)
  if (!response.ok) {
    throw new Error(`${response.status}`)
  }
  const page = await response.json()
  return page.content ?? []
}
