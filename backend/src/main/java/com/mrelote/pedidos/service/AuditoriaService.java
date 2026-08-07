package com.mrelote.pedidos.service;

import com.mrelote.pedidos.entity.Auditoria;
import com.mrelote.pedidos.entity.Usuario;
import com.mrelote.pedidos.repository.AuditoriaRepository;
import com.mrelote.pedidos.security.UsuarioPrincipal;
import com.mrelote.pedidos.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bitácora general del sistema (RN-002, sección 20) — distinta de
 * historial_pedido, que es específica de cada pedido. Se llama desde las
 * mutaciones administrativas más sensibles (catálogo, usuarios,
 * configuración).
 */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;

    @Transactional
    public void registrar(Authentication auth, String accion, String entidad, Object entidadId,
                           Object valorAnterior, Object valorNuevo) {
        Usuario actor = usuarioDe(auth);
        Auditoria auditoria = Auditoria.builder()
                .usuario(actor)
                .accion(accion)
                .entidad(entidad)
                .entidadId(entidadId != null ? entidadId.toString() : null)
                .valorAnterior(JsonUtil.toJson(valorAnterior))
                .valorNuevo(JsonUtil.toJson(valorNuevo))
                .origen("api")
                .build();
        auditoriaRepository.save(auditoria);
    }

    private Usuario usuarioDe(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal) {
            return principal.usuario();
        }
        return null;
    }
}
