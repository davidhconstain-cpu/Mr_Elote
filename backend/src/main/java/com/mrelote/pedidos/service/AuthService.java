package com.mrelote.pedidos.service;

import com.mrelote.pedidos.dto.request.LoginRequest;
import com.mrelote.pedidos.dto.request.RegistroClienteRequest;
import com.mrelote.pedidos.dto.response.LoginResponse;
import com.mrelote.pedidos.dto.response.UsuarioResponse;
import com.mrelote.pedidos.entity.Cliente;
import com.mrelote.pedidos.entity.Rol;
import com.mrelote.pedidos.entity.Usuario;
import com.mrelote.pedidos.exception.ConflictException;
import com.mrelote.pedidos.repository.ClienteRepository;
import com.mrelote.pedidos.repository.RolRepository;
import com.mrelote.pedidos.repository.UsuarioRepository;
import com.mrelote.pedidos.security.JwtService;
import com.mrelote.pedidos.security.RoleNames;
import com.mrelote.pedidos.security.UsuarioPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public LoginResponse registrarCliente(RegistroClienteRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new ConflictException("Ya existe una cuenta con ese email.");
        }
        if (usuarioRepository.existsByNumeroDocumento(request.numeroDocumento())) {
            throw new ConflictException("Ya existe una cuenta con ese número de documento.");
        }
        Rol rolCliente = rolRepository.findByNombre(RoleNames.CLIENTE)
                .orElseThrow(() -> new IllegalStateException("El rol Cliente no está sembrado en la base de datos"));

        Usuario usuario = Usuario.builder()
                .rol(rolCliente)
                .tipoDocumento(request.tipoDocumento())
                .numeroDocumento(request.numeroDocumento())
                .nombre(request.nombre())
                .apellidos(request.apellidos())
                .email(request.email())
                .telefono(request.telefono())
                .passwordHash(passwordEncoder.encode(request.password()))
                .aceptaPromociones(Boolean.TRUE.equals(request.aceptaPromociones()))
                // La validación @AssertTrue del request ya garantiza que aceptó.
                .terminosAceptadosEn(OffsetDateTime.now())
                .activo(true)
                .build();
        // saveAndFlush (no solo save): con GenerationType.IDENTITY en Usuario,
        // Cliente necesita el id del padre ya confirmado en el mismo flush por
        // culpa de @MapsId — si no, Hibernate lanza
        // "AssertionFailure: null identifier (Cliente)" al intentar derivarlo.
        usuario = usuarioRepository.saveAndFlush(usuario);

        Cliente cliente = Cliente.builder().usuario(usuario).usuarioId(usuario.getId()).build();
        clienteRepository.save(cliente);

        return emitirTokens(new UsuarioPrincipal(usuario));
    }

    public LoginResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.identificador(), request.password()));
        UsuarioPrincipal principal = (UsuarioPrincipal) authentication.getPrincipal();
        return emitirTokens(principal);
    }

    /** Inicio de sesión con código de un solo uso, sin contraseña. */
    public LoginResponse loginConCodigo(Usuario usuario) {
        return emitirTokens(new UsuarioPrincipal(usuario));
    }

    public LoginResponse refrescar(UsuarioPrincipal principal) {
        return emitirTokens(principal);
    }

    private LoginResponse emitirTokens(UsuarioPrincipal principal) {
        String accessToken = jwtService.generarAccessToken(principal);
        String refreshToken = jwtService.generarRefreshToken(principal);
        return new LoginResponse(accessToken, refreshToken, UsuarioResponse.from(principal.usuario()));
    }
}
