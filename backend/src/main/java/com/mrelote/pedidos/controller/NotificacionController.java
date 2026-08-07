package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.response.NotificacionResponse;
import com.mrelote.pedidos.exception.BusinessRuleException;
import com.mrelote.pedidos.repository.NotificacionRepository;
import com.mrelote.pedidos.security.UsuarioPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sección 14: las notificaciones se generan automáticamente en las transiciones de pedido. */
@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionRepository notificacionRepository;

    @GetMapping
    public Page<NotificacionResponse> misNotificaciones(Authentication auth, Pageable pageable) {
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioPrincipal principal)) {
            throw new BusinessRuleException("Se requiere un usuario autenticado");
        }
        return notificacionRepository.findByUsuarioIdOrderByCreadoEnDesc(principal.id(), pageable)
                .map(NotificacionResponse::from);
    }
}
