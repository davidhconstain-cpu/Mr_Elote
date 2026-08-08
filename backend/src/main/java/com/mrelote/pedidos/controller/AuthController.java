package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.request.ConfirmarResetRequest;
import com.mrelote.pedidos.dto.request.LoginRequest;
import com.mrelote.pedidos.dto.request.RegistroClienteRequest;
import com.mrelote.pedidos.dto.request.SolicitarResetRequest;
import com.mrelote.pedidos.dto.response.LoginResponse;
import com.mrelote.pedidos.security.JwtService;
import com.mrelote.pedidos.security.UsuarioDetailsService;
import com.mrelote.pedidos.security.UsuarioPrincipal;
import com.mrelote.pedidos.service.AuthService;
import com.mrelote.pedidos.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final UsuarioDetailsService usuarioDetailsService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/registro")
    public ResponseEntity<LoginResponse> registro(@Valid @RequestBody RegistroClienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrarCliente(request));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@RequestHeader("Authorization") String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BadCredentialsException("Falta el refresh token");
        }
        String token = authorization.substring("Bearer ".length());
        if (!jwtService.tokenValido(token) || jwtService.esAccessToken(token)) {
            throw new BadCredentialsException("Refresh token inválido o expirado");
        }
        String email = jwtService.extraerEmail(token);
        UsuarioPrincipal principal = (UsuarioPrincipal) usuarioDetailsService.loadUserByUsername(email);
        return authService.refrescar(principal);
    }

    /**
     * Siempre 202, exista o no el email — evita que alguien pueda usar este
     * endpoint para averiguar qué correos tienen cuenta (ver SecurityConfig,
     * es público). El envío real del link queda documentado como pendiente
     * de proveedor de email (por ahora se loguea, ver PasswordResetService).
     */
    @PostMapping("/recuperar")
    public ResponseEntity<Void> recuperar(@Valid @RequestBody SolicitarResetRequest request) {
        passwordResetService.solicitar(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/recuperar/confirmar")
    public ResponseEntity<Void> confirmarRecuperacion(@Valid @RequestBody ConfirmarResetRequest request) {
        passwordResetService.confirmar(request.token(), request.nuevaPassword());
        return ResponseEntity.noContent().build();
    }
}
