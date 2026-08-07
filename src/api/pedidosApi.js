import { API_BASE_URL } from './config'

// tipo: 'recoger' (anónimo), 'domicilio' (requiere cliente autenticado con
// una dirección propia — ver AddressPicker) o 'local' (requiere el código
// del QR de la mesa escaneada — ver MesaContext).
export async function crearPedido({ tipo, direccionId, mesaCodigoQr, items, observaciones, token }) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE_URL}/pedidos`, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      tipo,
      direccionId: tipo === 'domicilio' ? direccionId : undefined,
      mesaCodigoQr: tipo === 'local' ? mesaCodigoQr : undefined,
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
