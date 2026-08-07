import { API_BASE_URL } from './config'

// MVP: solo pedidos "recoger" (anónimos, sin login ni mesa QR) — el
// catálogo público todavía no tiene inicio de sesión de cliente para
// ofrecer domicilio, ni lectura de QR de mesa para pedidos "local".
export async function crearPedido({ items, observaciones }) {
  const response = await fetch(`${API_BASE_URL}/pedidos`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      tipo: 'recoger',
      items: items.map((item) => ({ productoId: item.productoId, cantidad: item.cantidad })),
      observaciones: observaciones || null,
    }),
  })

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.mensaje || `No se pudo enviar el pedido (${response.status})`)
  }

  return response.json()
}
