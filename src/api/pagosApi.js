import { API_BASE_URL } from './config'

// Solo Cliente/Mesero/Caja pueden autorregistrar un pago (ver
// PagoController#registrar); un pedido anónimo (recoger/local sin login)
// se paga en persona y lo registra el staff desde el panel de Caja.
export async function registrarPago(token, pedidoId, { metodoPagoId, monto }) {
  const response = await fetch(`${API_BASE_URL}/pedidos/${pedidoId}/pagos`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ metodoPagoId, monto }),
  })

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.mensaje || `No se pudo registrar el pago (${response.status})`)
  }

  return response.json()
}
