package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.response.ConfiguracionResponse;
import com.mrelote.pedidos.entity.Configuracion;
import com.mrelote.pedidos.exception.NotFoundException;
import com.mrelote.pedidos.repository.ConfiguracionRepository;
import com.mrelote.pedidos.service.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** RF-045: incluye modulo_domicilios_activo (RF-032). */
@RestController
@RequestMapping("/api/v1/configuracion")
@RequiredArgsConstructor
@PreAuthorize("hasRole('Administrador')")
public class ConfiguracionController {

    private final ConfiguracionRepository configuracionRepository;
    private final AuditoriaService auditoriaService;

    @GetMapping
    public List<ConfiguracionResponse> ver() {
        return configuracionRepository.findAll().stream().map(ConfiguracionResponse::from).toList();
    }

    @PatchMapping("/{clave}")
    public ConfiguracionResponse actualizar(@PathVariable String clave, @RequestBody Map<String, String> body, Authentication auth) {
        String nuevoValor = body.get("valor");
        Configuracion configuracion = configuracionRepository.findById(clave)
                .orElseThrow(() -> new NotFoundException("No existe el parámetro " + clave));
        String anterior = configuracion.getValor();
        configuracion.setValor(nuevoValor);
        configuracion = configuracionRepository.save(configuracion);
        auditoriaService.registrar(auth, "editar", "configuracion", clave, anterior, nuevoValor);
        return ConfiguracionResponse.from(configuracion);
    }
}
