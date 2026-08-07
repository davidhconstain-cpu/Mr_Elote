package com.mrelote.pedidos.service;

import com.mrelote.pedidos.dto.request.AccionPedidoRequest;
import com.mrelote.pedidos.dto.request.RegistrarPagoRequest;
import com.mrelote.pedidos.dto.request.WebhookPagoRequest;
import com.mrelote.pedidos.dto.response.PagoResponse;
import com.mrelote.pedidos.entity.MetodoPago;
import com.mrelote.pedidos.entity.Pago;
import com.mrelote.pedidos.entity.Pedido;
import com.mrelote.pedidos.exception.BusinessRuleException;
import com.mrelote.pedidos.exception.NotFoundException;
import com.mrelote.pedidos.repository.MetodoPagoRepository;
import com.mrelote.pedidos.repository.PagoRepository;
import com.mrelote.pedidos.repository.PedidoRepository;
import com.mrelote.pedidos.security.UsuarioPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * RF-014 a RF-017, RNF-007, RNF-008: el pago vive separado del pedido con su
 * propio estado; el webhook se procesa de forma idempotente por
 * referenciaExterna. La verificación real de firma HMAC del proveedor queda
 * pendiente de la decisión de pasarela (sección 28 del documento de
 * requisitos) — aquí solo se exige que venga presente.
 */
@Service
@RequiredArgsConstructor
public class PagoService {

    private final PagoRepository pagoRepository;
    private final PedidoRepository pedidoRepository;
    private final MetodoPagoRepository metodoPagoRepository;
    private final PedidoService pedidoService;

    @Transactional
    public PagoResponse registrar(Long pedidoId, RegistrarPagoRequest request) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new NotFoundException("No existe el pedido " + pedidoId));
        MetodoPago metodo = metodoPagoRepository.findById(request.metodoPagoId())
                .orElseThrow(() -> new NotFoundException("No existe el método de pago " + request.metodoPagoId()));

        Pago pago = Pago.builder()
                .pedido(pedido)
                .metodoPago(metodo)
                .monto(request.monto())
                .estado("pendiente")
                .referenciaExterna(request.referenciaExterna())
                .build();
        return PagoResponse.from(pagoRepository.save(pago));
    }

    /** RNF-007/RNF-008: idempotente — un webhook repetido para la misma referencia no hace nada. */
    @Transactional
    public void procesarWebhook(WebhookPagoRequest request) {
        Pago pago = pagoRepository.findByReferenciaExterna(request.referenciaExterna())
                .orElseThrow(() -> new NotFoundException(
                        "No hay un pago registrado con la referencia " + request.referenciaExterna()));

        if (!"pendiente".equals(pago.getEstado())) {
            return; // ya procesado antes: no-op idempotente
        }

        pago.setEstado(request.estado());
        pago.setValidadoEn(OffsetDateTime.now());
        pagoRepository.save(pago);

        if ("validado".equals(request.estado())) {
            pedidoService.confirmarPago(pago.getPedido().getId(), "Confirmación automática de pasarela", null);
        }
    }

    @Transactional
    public PagoResponse validar(Long pagoId, Authentication auth) {
        Pago pago = buscar(pagoId);
        if (!"pendiente".equals(pago.getEstado())) {
            throw new BusinessRuleException("El pago ya fue procesado (" + pago.getEstado() + ")");
        }
        pago.setEstado("validado");
        pago.setValidadoEn(OffsetDateTime.now());
        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal) {
            pago.setValidadoPor(principal.usuario());
        }
        pago = pagoRepository.save(pago);
        pedidoService.confirmarPago(pago.getPedido().getId(), "Validado manualmente por caja", auth);
        return PagoResponse.from(pago);
    }

    @Transactional
    public PagoResponse rechazar(Long pagoId, AccionPedidoRequest body) {
        Pago pago = buscar(pagoId);
        pago.setEstado("rechazado");
        pago.setValidadoEn(OffsetDateTime.now());
        return PagoResponse.from(pagoRepository.save(pago));
    }

    private Pago buscar(Long id) {
        return pagoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe el pago " + id));
    }
}
