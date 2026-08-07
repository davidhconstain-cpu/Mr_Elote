package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.response.AuditoriaResponse;
import com.mrelote.pedidos.repository.AuditoriaRepository;
import com.mrelote.pedidos.repository.AuditoriaSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF-039. */
@RestController
@RequestMapping("/api/v1/auditoria")
@RequiredArgsConstructor
@PreAuthorize("hasRole('Administrador')")
public class AuditoriaController {

    private final AuditoriaRepository auditoriaRepository;

    @GetMapping
    public Page<AuditoriaResponse> listar(
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) Long usuarioId,
            Pageable pageable) {
        return auditoriaRepository.findAll(AuditoriaSpecifications.conFiltros(entidad, usuarioId), pageable)
                .map(AuditoriaResponse::from);
    }
}
