package com.mrelote.pedidos.service;

import com.mrelote.pedidos.dto.request.AbrirCajaRequest;
import com.mrelote.pedidos.dto.request.CerrarCajaRequest;
import com.mrelote.pedidos.dto.request.MovimientoCajaRequest;
import com.mrelote.pedidos.dto.response.CajaResponse;
import com.mrelote.pedidos.dto.response.MovimientoCajaResponse;
import com.mrelote.pedidos.entity.Caja;
import com.mrelote.pedidos.entity.MovimientoCaja;
import com.mrelote.pedidos.exception.BusinessRuleException;
import com.mrelote.pedidos.exception.ConflictException;
import com.mrelote.pedidos.exception.NotFoundException;
import com.mrelote.pedidos.repository.CajaRepository;
import com.mrelote.pedidos.repository.MovimientoCajaRepository;
import com.mrelote.pedidos.security.UsuarioPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * RF-036, RF-037. montoEsperado se calcula solo a partir de los
 * movimientos manuales de esta caja (apertura + ingresos - egresos); una
 * versión posterior debería sumar automáticamente los pagos en efectivo
 * validados durante el turno como movimientos de tipo "pago".
 */
@Service
@RequiredArgsConstructor
public class CajaService {

    private final CajaRepository cajaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;

    @Transactional
    public CajaResponse abrir(AbrirCajaRequest request, Authentication auth) {
        if (cajaRepository.findFirstByEstadoOrderByAbiertaEnDesc("abierta").isPresent()) {
            throw new ConflictException("Ya existe una caja abierta; debe cerrarse antes de abrir otra");
        }
        UsuarioPrincipal principal = principal(auth);
        Caja caja = Caja.builder()
                .usuarioApertura(principal.usuario())
                .montoApertura(request.montoApertura())
                .estado("abierta")
                .build();
        caja = cajaRepository.save(caja);
        movimientoCajaRepository.save(MovimientoCaja.builder()
                .caja(caja).tipo("apertura").monto(request.montoApertura())
                .descripcion("Apertura de caja").usuario(principal.usuario()).build());
        return CajaResponse.from(caja);
    }

    @Transactional(readOnly = true)
    public CajaResponse actual() {
        Caja caja = cajaRepository.findFirstByEstadoOrderByAbiertaEnDesc("abierta")
                .orElseThrow(() -> new NotFoundException("No hay ninguna caja abierta"));
        return CajaResponse.from(caja);
    }

    @Transactional(readOnly = true)
    public List<MovimientoCajaResponse> movimientos(Long cajaId) {
        return movimientoCajaRepository.findByCajaIdOrderByCreadoEnDesc(cajaId).stream()
                .map(MovimientoCajaResponse::from).toList();
    }

    @Transactional
    public MovimientoCajaResponse registrarMovimiento(Long cajaId, MovimientoCajaRequest request, Authentication auth) {
        Caja caja = buscar(cajaId);
        if (!"abierta".equals(caja.getEstado())) {
            throw new BusinessRuleException("La caja ya está cerrada");
        }
        MovimientoCaja movimiento = MovimientoCaja.builder()
                .caja(caja)
                .tipo(request.tipo())
                .monto(request.monto())
                .descripcion(request.descripcion())
                .usuario(principal(auth).usuario())
                .build();
        return MovimientoCajaResponse.from(movimientoCajaRepository.save(movimiento));
    }

    @Transactional
    public CajaResponse cerrar(Long cajaId, CerrarCajaRequest request, Authentication auth) {
        Caja caja = buscar(cajaId);
        if (!"abierta".equals(caja.getEstado())) {
            throw new ConflictException("La caja ya está cerrada");
        }
        BigDecimal esperado = calcularMontoEsperado(caja);
        caja.setMontoEsperado(esperado);
        caja.setMontoReal(request.montoReal());
        caja.setDiferencia(request.montoReal().subtract(esperado));
        caja.setEstado("cerrada");
        caja.setUsuarioCierre(principal(auth).usuario());
        caja.setCerradaEn(OffsetDateTime.now());
        caja = cajaRepository.save(caja);

        movimientoCajaRepository.save(MovimientoCaja.builder()
                .caja(caja).tipo("cierre").monto(request.montoReal())
                .descripcion("Cierre de caja. Diferencia: " + caja.getDiferencia())
                .usuario(principal(auth).usuario()).build());
        return CajaResponse.from(caja);
    }

    private BigDecimal calcularMontoEsperado(Caja caja) {
        BigDecimal total = caja.getMontoApertura();
        for (MovimientoCaja m : movimientoCajaRepository.findByCajaIdOrderByCreadoEnDesc(caja.getId())) {
            switch (m.getTipo()) {
                case "ingreso", "pago" -> total = total.add(m.getMonto());
                case "egreso" -> total = total.subtract(m.getMonto());
                default -> { /* apertura/cierre no se vuelven a sumar */ }
            }
        }
        return total;
    }

    private Caja buscar(Long id) {
        return cajaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe la caja " + id));
    }

    private UsuarioPrincipal principal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioPrincipal principal)) {
            throw new BusinessRuleException("Se requiere un usuario autenticado");
        }
        return principal;
    }
}
