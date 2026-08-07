package com.mrelote.pedidos.service;

import com.mrelote.pedidos.domain.EstadoPedido;
import com.mrelote.pedidos.dto.request.CrearPedidoRequest;
import com.mrelote.pedidos.dto.request.ItemPedidoRequest;
import com.mrelote.pedidos.dto.response.HistorialPedidoResponse;
import com.mrelote.pedidos.dto.response.PedidoResponse;
import com.mrelote.pedidos.entity.*;
import com.mrelote.pedidos.exception.BusinessRuleException;
import com.mrelote.pedidos.exception.ConflictException;
import com.mrelote.pedidos.exception.NotFoundException;
import com.mrelote.pedidos.repository.*;
import com.mrelote.pedidos.security.RoleNames;
import com.mrelote.pedidos.security.UsuarioPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final HistorialPedidoRepository historialPedidoRepository;
    private final DespachoRepository despachoRepository;
    private final ProductoRepository productoRepository;
    private final ComboRepository comboRepository;
    private final ComboDisponibilidadRepository comboDisponibilidadRepository;
    private final ValorOpcionRepository valorOpcionRepository;
    private final AdicionalRepository adicionalRepository;
    private final QrMesaRepository qrMesaRepository;
    private final ClienteRepository clienteRepository;
    private final DireccionRepository direccionRepository;
    private final TarifaDomicilioRepository tarifaDomicilioRepository;
    private final UsuarioRepository usuarioRepository;

    // ---------------------------------------------------------------
    // Creación (RF-005, RF-006, RF-007) — autenticación opcional, ver
    // api/openapi.yaml: security: [{}, {bearerAuth: []}] en POST /pedidos.
    // ---------------------------------------------------------------
    @Transactional
    public PedidoResponse crear(CrearPedidoRequest request, Authentication authentication) {
        UsuarioPrincipal principal = principalDe(authentication);

        Mesa mesa = null;
        String canal;
        Cliente cliente = null;
        Usuario creadoPor = null;

        if (principal != null) {
            creadoPor = principal.usuario();
            String rol = principal.usuario().getRol().getNombre();
            if (RoleNames.MESERO.equals(rol)) {
                canal = "mesero";
            } else {
                canal = "web";
                cliente = clienteRepository.findById(principal.id())
                        .orElseThrow(() -> new BusinessRuleException("El usuario autenticado no es un cliente"));
            }
        } else {
            canal = "qr";
        }

        if (request.mesaCodigoQr() != null) {
            QrMesa qr = qrMesaRepository.findByCodigoAndActivoTrue(request.mesaCodigoQr())
                    .orElseThrow(() -> new BusinessRuleException("El código QR no existe o ya no está activo"));
            mesa = qr.getMesa();
            if (principal == null) {
                canal = "qr";
            }
        }

        if ("local".equals(request.tipo()) && mesa == null) {
            throw new BusinessRuleException("Un pedido 'local' debe venir de un QR de mesa válido");
        }

        Direccion direccion = null;
        BigDecimal domicilio = BigDecimal.ZERO;
        String direccionTextoSnapshot = null;
        String zonaSnapshot = null;
        if ("domicilio".equals(request.tipo())) {
            if (cliente == null) {
                throw new BusinessRuleException("Un pedido a domicilio requiere un cliente autenticado con una dirección registrada");
            }
            if (request.direccionId() == null) {
                throw new BusinessRuleException("Falta la dirección de entrega");
            }
            direccion = direccionRepository.findById(request.direccionId())
                    .orElseThrow(() -> new NotFoundException("No existe la dirección " + request.direccionId()));
            direccionTextoSnapshot = direccion.getDireccionTexto();
            if (direccion.getZonaDomicilio() != null) {
                zonaSnapshot = direccion.getZonaDomicilio().getNombre();
                domicilio = tarifaDomicilioRepository
                        .findByZonaDomicilioIdAndVigenteHastaIsNull(direccion.getZonaDomicilio().getId())
                        .map(TarifaDomicilio::getTarifa)
                        .orElse(BigDecimal.ZERO);
            }
        }
        // RN-006: recoger no cobra domicilio (y "local" tampoco): domicilio ya
        // queda en BigDecimal.ZERO salvo que tipo == "domicilio".

        Pedido pedido = Pedido.builder()
                .tipo(request.tipo())
                .canal(canal)
                .estado(EstadoPedido.PAGO_PENDIENTE)
                .cliente(cliente)
                .mesa(mesa)
                .direccion(direccion)
                .direccionTextoSnapshot(direccionTextoSnapshot)
                .zonaDomicilioSnapshot(zonaSnapshot)
                .domicilio(domicilio)
                .observaciones(request.observaciones())
                .creadoPor(creadoPor)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalAdicionales = BigDecimal.ZERO;
        List<PedidoDetalle> items = new ArrayList<>();
        for (ItemPedidoRequest itemReq : request.items()) {
            PedidoDetalle detalle = construirLinea(itemReq, pedido);
            items.add(detalle);
            subtotal = subtotal.add(detalle.getSubtotalLinea());
            totalAdicionales = totalAdicionales.add(
                    detalle.getAdicionales().stream()
                            .map(a -> a.getPrecioSnapshot().multiply(BigDecimal.valueOf(a.getCantidad())))
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        pedido.setItems(items);
        pedido.setSubtotal(subtotal);
        pedido.setTotalAdicionales(totalAdicionales);
        pedido.setTotal(subtotal.add(totalAdicionales).add(domicilio).subtract(pedido.getDescuento()));

        return PedidoResponse.from(pedidoRepository.save(pedido));
    }

    private PedidoDetalle construirLinea(ItemPedidoRequest itemReq, Pedido pedido) {
        if ((itemReq.productoId() == null) == (itemReq.comboId() == null)) {
            throw new BusinessRuleException("Cada línea debe tener productoId o comboId, nunca ambos ni ninguno");
        }
        int cantidad = itemReq.cantidad() != null ? itemReq.cantidad() : 1;
        String nombreSnapshot;
        BigDecimal precioBase;

        if (itemReq.productoId() != null) {
            Producto producto = productoRepository.findById(itemReq.productoId())
                    .orElseThrow(() -> new NotFoundException("No existe el producto " + itemReq.productoId()));
            if (!Boolean.TRUE.equals(producto.getDisponible())) {
                throw new BusinessRuleException("El producto '" + producto.getNombre() + "' está agotado");
            }
            nombreSnapshot = producto.getNombre();
            precioBase = producto.getPrecio();
        } else {
            Combo combo = comboRepository.findById(itemReq.comboId())
                    .orElseThrow(() -> new NotFoundException("No existe el combo " + itemReq.comboId()));
            boolean disponible = comboDisponibilidadRepository.findByComboId(combo.getId())
                    .map(d -> Boolean.TRUE.equals(d.getDisponible())).orElse(false);
            if (!disponible) {
                throw new BusinessRuleException("El combo '" + combo.getNombre() + "' está agotado");
            }
            nombreSnapshot = combo.getNombre();
            precioBase = combo.getPrecio();
        }

        PedidoDetalle detalle = PedidoDetalle.builder()
                .pedido(pedido)
                .producto(itemReq.productoId() != null
                        ? productoRepository.getReferenceById(itemReq.productoId()) : null)
                .combo(itemReq.comboId() != null
                        ? comboRepository.getReferenceById(itemReq.comboId()) : null)
                .nombreSnapshot(nombreSnapshot)
                .cantidad(cantidad)
                .build();

        List<PedidoDetalleOpcion> opciones = new ArrayList<>();
        BigDecimal opcionesTotal = BigDecimal.ZERO;
        if (itemReq.valoresOpcionId() != null) {
            for (Long valorOpcionId : itemReq.valoresOpcionId()) {
                ValorOpcion valor = valorOpcionRepository.findById(valorOpcionId)
                        .orElseThrow(() -> new NotFoundException("No existe la opción " + valorOpcionId));
                opciones.add(PedidoDetalleOpcion.builder()
                        .pedidoDetalle(detalle)
                        .valorOpcion(valor)
                        .nombreSnapshot(valor.getNombre())
                        .precioAdicionalSnapshot(valor.getPrecioAdicional())
                        .build());
                opcionesTotal = opcionesTotal.add(valor.getPrecioAdicional());
            }
        }
        detalle.setOpciones(opciones);

        List<PedidoDetalleAdicional> adicionales = new ArrayList<>();
        if (itemReq.adicionalesId() != null) {
            for (Long adicionalId : itemReq.adicionalesId()) {
                Adicional adicional = adicionalRepository.findById(adicionalId)
                        .orElseThrow(() -> new NotFoundException("No existe el adicional " + adicionalId));
                if (!Boolean.TRUE.equals(adicional.getDisponible())) {
                    throw new BusinessRuleException("El adicional '" + adicional.getNombre() + "' está agotado");
                }
                adicionales.add(PedidoDetalleAdicional.builder()
                        .pedidoDetalle(detalle)
                        .adicional(adicional)
                        .nombreSnapshot(adicional.getNombre())
                        .precioSnapshot(adicional.getPrecio())
                        .cantidad(1)
                        .build());
            }
        }
        detalle.setAdicionales(adicionales);

        BigDecimal precioUnitarioConOpciones = precioBase.add(opcionesTotal);
        detalle.setPrecioUnitarioSnapshot(precioUnitarioConOpciones);
        detalle.setSubtotalLinea(precioUnitarioConOpciones.multiply(BigDecimal.valueOf(cantidad)));
        return detalle;
    }

    // ---------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public Page<PedidoResponse> buscar(String estado, String tipo, LocalDate desde, LocalDate hasta, Pageable pageable) {
        return pedidoRepository.findAll(PedidoSpecifications.conFiltros(estado, tipo, desde, hasta), pageable)
                .map(PedidoResponse::from);
    }

    @Transactional(readOnly = true)
    public PedidoResponse ver(Long id) {
        return PedidoResponse.from(buscarEntidad(id));
    }

    /** RF-018 a RF-021: cola de cocina, solo pedidos habilitados, por hora de entrada. */
    @Transactional(readOnly = true)
    public List<PedidoResponse> colaCocina(String tipo, String estado) {
        List<String> estados = estado != null ? List.of(estado) : List.copyOf(EstadoPedido.HABILITADOS_PARA_COCINA);
        List<Pedido> pedidos = tipo != null
                ? pedidoRepository.findByTipoAndEstadoInOrderByCreadoEnAsc(tipo, estados)
                : pedidoRepository.findByEstadoInOrderByCreadoEnAsc(estados);
        return pedidos.stream().map(PedidoResponse::from).toList();
    }

    // ---------------------------------------------------------------
    // Transiciones (sección 8.2 v1.1: un endpoint por transición)
    // ---------------------------------------------------------------
    @Transactional
    public PedidoResponse confirmarPago(Long id, String motivo, Authentication auth) {
        return transicionar(id, EstadoPedido.ORIGEN_CONFIRMAR_PAGO, EstadoPedido.PAGO_VALIDADO, motivo, auth);
    }

    @Transactional
    public PedidoResponse iniciarPreparacion(Long id, String motivo, Authentication auth) {
        return transicionar(id, EstadoPedido.ORIGEN_INICIAR_PREPARACION, EstadoPedido.EN_PREPARACION, motivo, auth);
    }

    @Transactional
    public PedidoResponse marcarListo(Long id, String motivo, Authentication auth) {
        return transicionar(id, EstadoPedido.ORIGEN_MARCAR_LISTO, EstadoPedido.LISTO, motivo, auth);
    }

    @Transactional
    public PedidoResponse entregar(Long id, String motivo, Authentication auth) {
        return transicionar(id, EstadoPedido.ORIGEN_ENTREGAR, EstadoPedido.ENTREGADO, motivo, auth);
    }

    @Transactional
    public PedidoResponse recoger(Long id, String motivo, Authentication auth) {
        return transicionar(id, EstadoPedido.ORIGEN_RECOGER, EstadoPedido.RECOGIDO, motivo, auth);
    }

    @Transactional
    public PedidoResponse despachar(Long id, String motivo, Authentication auth) {
        Pedido pedido = buscarEntidad(id);
        if (!"domicilio".equals(pedido.getTipo())) {
            throw new BusinessRuleException("Solo los pedidos de tipo domicilio pasan a despacho");
        }
        PedidoResponse respuesta = transicionar(id, EstadoPedido.ORIGEN_DESPACHAR, EstadoPedido.DESPACHADO, motivo, auth);
        despachoRepository.save(Despacho.builder().pedido(pedido).estado("asignado").build());
        return respuesta;
    }

    @Transactional
    public PedidoResponse anular(Long id, String motivo, Authentication auth) {
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessRuleException("Anular un pedido requiere un motivo");
        }
        Pedido pedido = buscarEntidad(id);
        if (EstadoPedido.ESTADOS_FINALES.contains(pedido.getEstado())) {
            throw new ConflictException("El pedido ya está en un estado final ('" + pedido.getEstado() + "')");
        }
        return transicionar(id, Set.of(pedido.getEstado()), EstadoPedido.ANULADO, motivo, auth);
    }

    @Transactional(readOnly = true)
    public List<HistorialPedidoResponse> historial(Long id) {
        return historialPedidoRepository.findByPedidoIdOrderByCreadoEnDesc(id).stream()
                .map(HistorialPedidoResponse::from)
                .toList();
    }

    private PedidoResponse transicionar(Long id, Set<String> origenesValidos, String nuevoEstado, String motivo, Authentication auth) {
        Pedido pedido = buscarEntidad(id);
        if (!origenesValidos.contains(pedido.getEstado())) {
            throw new ConflictException(
                    "No se puede pasar de '" + pedido.getEstado() + "' a '" + nuevoEstado + "'");
        }
        String anterior = pedido.getEstado();
        pedido.setEstado(nuevoEstado);
        pedido = pedidoRepository.save(pedido);

        UsuarioPrincipal principal = principalDe(auth);
        historialPedidoRepository.save(HistorialPedido.builder()
                .pedido(pedido)
                .usuario(principal != null ? principal.usuario() : null)
                .campoModificado("estado")
                .valorAnterior(anterior)
                .valorNuevo(nuevoEstado)
                .motivo(motivo)
                .build());
        return PedidoResponse.from(pedido);
    }

    private Pedido buscarEntidad(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe el pedido " + id));
    }

    private UsuarioPrincipal principalDe(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UsuarioPrincipal principal)) {
            return null;
        }
        return principal;
    }
}
