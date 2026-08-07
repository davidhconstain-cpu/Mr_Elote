package com.mrelote.pedidos.service;

import com.mrelote.pedidos.dto.request.CrearUsuarioRequest;
import com.mrelote.pedidos.dto.request.EditarUsuarioRequest;
import com.mrelote.pedidos.dto.response.UsuarioResponse;
import com.mrelote.pedidos.entity.Rol;
import com.mrelote.pedidos.entity.Usuario;
import com.mrelote.pedidos.exception.ConflictException;
import com.mrelote.pedidos.exception.NotFoundException;
import com.mrelote.pedidos.repository.RolRepository;
import com.mrelote.pedidos.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-038: administración de usuarios de staff (el alta de clientes va por AuthService#registrarCliente). */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public Page<UsuarioResponse> listar(Long rolId, Pageable pageable) {
        Page<Usuario> page = rolId != null
                ? usuarioRepository.findByRolId(rolId, pageable)
                : usuarioRepository.findAll(pageable);
        return page.map(UsuarioResponse::from);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse ver(Long id) {
        return UsuarioResponse.from(buscar(id));
    }

    @Transactional
    public UsuarioResponse crear(CrearUsuarioRequest request, Authentication auth) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new ConflictException("Ya existe una cuenta con ese email.");
        }
        Rol rol = rolRepository.findById(request.rolId())
                .orElseThrow(() -> new NotFoundException("No existe el rol " + request.rolId()));
        Usuario usuario = Usuario.builder()
                .rol(rol)
                .nombre(request.nombre())
                .email(request.email())
                .telefono(request.telefono())
                .passwordHash(passwordEncoder.encode(request.password()))
                .activo(true)
                .build();
        usuario = usuarioRepository.save(usuario);
        auditoriaService.registrar(auth, "crear", "usuario", usuario.getId(), null, UsuarioResponse.from(usuario));
        return UsuarioResponse.from(usuario);
    }

    @Transactional
    public UsuarioResponse editar(Long id, EditarUsuarioRequest request, Authentication auth) {
        Usuario usuario = buscar(id);
        UsuarioResponse anterior = UsuarioResponse.from(usuario);
        if (request.nombre() != null) usuario.setNombre(request.nombre());
        if (request.telefono() != null) usuario.setTelefono(request.telefono());
        if (request.activo() != null) usuario.setActivo(request.activo());
        if (request.rolId() != null) {
            usuario.setRol(rolRepository.findById(request.rolId())
                    .orElseThrow(() -> new NotFoundException("No existe el rol " + request.rolId())));
        }
        usuario = usuarioRepository.save(usuario);
        auditoriaService.registrar(auth, "editar", "usuario", id, anterior, UsuarioResponse.from(usuario));
        return UsuarioResponse.from(usuario);
    }

    /** Baja lógica: RN-001 aplica también a usuarios, nunca se borran físicamente. */
    @Transactional
    public void desactivar(Long id, Authentication auth) {
        Usuario usuario = buscar(id);
        UsuarioResponse anterior = UsuarioResponse.from(usuario);
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
        auditoriaService.registrar(auth, "desactivar", "usuario", id, anterior, UsuarioResponse.from(usuario));
    }

    private Usuario buscar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe el usuario " + id));
    }
}
