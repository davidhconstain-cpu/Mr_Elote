import { authFetch } from './http'

// Solo Cliente/Mesero/Caja pueden autorregistrar un pago (ver
// PagoController#registrar); si el cliente elige "pagar en persona" no se
// llama a este endpoint y el pago lo registra el staff desde el panel de Caja.
export function registrarPago(pedidoId, { metodoPagoId, monto }) {
  return authFetch(`/pedidos/${pedidoId}/pagos`, {
    method: 'POST',
    body: JSON.stringify({ metodoPagoId, monto }),
  })
}
