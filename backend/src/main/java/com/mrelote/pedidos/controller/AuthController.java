package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.request.LoginRequest;
import com.mrelote.pedidos.dto.request.RegistroClienteRequest;
import com.mrelote.pedidos.dto.response.LoginResponse;
import com.mrelote.pedidos.security.JwtService;
import com.mrelote.pedidos.security.UsuarioDetailsService;
import com.mrelote.pedidos.security.UsuarioPrincipal;
import com.mrelote.pedidos.service.AuthService;
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
}
