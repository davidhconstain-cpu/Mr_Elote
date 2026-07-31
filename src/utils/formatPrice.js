export function formatPrice(price) {
  if (price == null) return 'Consultar'
  return `$${price.toLocaleString('es-CO')}`
}
