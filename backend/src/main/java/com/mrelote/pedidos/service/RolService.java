package com.mrelote.pedidos.service;

import com.mrelote.pedidos.dto.response.RolResponse;
import com.mrelote.pedidos.entity.Permiso;
import com.mrelote.pedidos.entity.Rol;
import com.mrelote.pedidos.exception.NotFoundException;
import com.mrelote.pedidos.repository.PermisoRepository;
import com.mrelote.pedidos.repository.RolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RolService {

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<RolResponse> listar() {
        return rolRepository.findAll().stream().map(RolResponse::from).toList();
    }

    /** RF-038, RNF-004: RBAC fino además del control grueso por rol de @PreAuthorize. */
    @Transactional
    public RolResponse asignarPermisos(Long rolId, List<Long> permisoIds, Authentication auth) {
        Rol rol = rolRepository.findById(rolId)
                .orElseThrow(() -> new NotFoundException("No existe el rol " + rolId));
        List<String> anteriores = rol.getPermisos().stream().map(Permiso::getCodigo).toList();

        var permisos = new HashSet<Permiso>();
        for (Long id : permisoIds) {
            permisos.add(permisoRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("No existe el permiso " + id)));
        }
        rol.setPermisos(permisos);
        rol = rolRepository.save(rol);

        auditoriaService.registrar(auth, "editar_permisos", "rol", rolId,
                anteriores, permisos.stream().map(Permiso::getCodigo).toList());
        return RolResponse.from(rol);
    }
}
