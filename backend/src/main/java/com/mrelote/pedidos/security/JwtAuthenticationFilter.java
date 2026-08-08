package com.mrelote.pedidos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Autenticación opcional por diseño: si no hay header Authorization, la
 * petición sigue sin autenticar y es SecurityConfig quien decide si la ruta
 * es pública, de auth opcional (ej. POST /pedidos, ver api/openapi.yaml) o
 * exige un rol concreto.
 *
 * Ojo con la diferencia entre "sin header" y "header con token inválido":
 * un token vencido o corrupto NO se degrada a anónimo, se rechaza con 401.
 * Antes se ignoraba en silencio, y en las rutas de auth opcional eso hacía
 * que el pedido de un cliente con la sesión vencida se creara como pedido
 * anónimo (sin cliente_id) — el cliente lo daba por hecho pero nunca le
 * aparecía en "Mis pedidos".
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioDetailsService usuarioDetailsService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    /**
     * Las rutas de /auth son públicas y algunas usan el header Authorization
     * para otra cosa: /auth/refresh manda ahí el refresh token, que este
     * filtro rechazaría por no ser un access token — dejando la renovación
     * de sesión permanentemente rota.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring("Bearer ".length());

        if (!jwtService.tokenValido(token) || !jwtService.esAccessToken(token)) {
            // El cliente cree que tiene sesión pero no la tiene: hay que
            // decírselo (401) para que renueve el token o vuelva a entrar,
            // no seguir como si fuera un anónimo.
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response,
                    new BadCredentialsException("Token inválido o expirado"));
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String email = jwtService.extraerEmail(token);
            UserDetails userDetails = usuarioDetailsService.loadUserByUsername(email);

            var authToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }
}
