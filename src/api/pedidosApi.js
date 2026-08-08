import { authFetch } from './http'

// tipo: 'recoger', 'domicilio' (requiere una dirección propia del cliente —
// ver AddressPicker) o 'local' (requiere el código del QR de la mesa
// escaneada — ver MesaContext). Todos van con la sesión del cliente: armar
// el carrito ya exige cuenta (ver CartContext), y authFetch renueva el token
// si venció, para que el pedido no termine creándose sin dueño.
export function crearPedido({ tipo, direccionId, mesaCodigoQr, items, observaciones }) {
  return authFetch('/pedidos', {
    method: 'POST',
    body: JSON.stringify({
      tipo,
      direccionId: tipo === 'domicilio' ? direccionId : undefined,
      mesaCodigoQr: tipo === 'local' ? mesaCodigoQr : undefined,
      items: items.map((item) => ({
        productoId: item.tipo === 'combo' ? undefined : item.productoId,
        comboId: item.tipo === 'combo' ? item.comboId : undefined,
        cantidad: item.cantidad,
        valoresOpcionId: item.opciones?.map((o) => o.valorOpcionId),
        adicionalesId: item.adicionales?.map((a) => a.adicionalId),
      })),
      observaciones: observaciones || null,
    }),
  })
}
