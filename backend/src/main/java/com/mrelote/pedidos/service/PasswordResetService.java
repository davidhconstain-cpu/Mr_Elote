package com.mrelote.pedidos.service;

import com.mrelote.pedidos.entity.PasswordResetToken;
import com.mrelote.pedidos.entity.Usuario;
import com.mrelote.pedidos.exception.BusinessRuleException;
import com.mrelote.pedidos.repository.PasswordResetTokenRepository;
import com.mrelote.pedidos.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * RF-002: recuperar contraseña. La lógica es real de punta a punta —
 * generación de un token de un solo uso con expiración, hash del token en
 * base de datos (nunca en claro), y consumo atómico al confirmar. Lo único
 * que falta para producción es el transporte del link por email/SMS, que
 * depende de un proveedor externo con credenciales que este entorno no
 * tiene (sección 28 del documento de requisitos); por ahora se entrega por
 * el mismo canal "log" real que usa {@code LogNotificacionSender}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final int EXPIRACION_MINUTOS = 30;

    private final UsuarioRepository usuarioRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Nunca revela si el email existe o no (evita enumeración de cuentas):
     * siempre "tiene éxito" desde el punto de vista del cliente HTTP.
     */
    @Transactional
    public void solicitar(String email) {
        usuarioRepository.findByEmail(email).ifPresent(usuario -> {
            String token = generarToken();
            passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .usuario(usuario)
                    .tokenHash(hash(token))
                    .expiraEn(OffsetDateTime.now().plusMinutes(EXPIRACION_MINUTOS))
                    .build());
            log.info("[password-reset] link para {} <{}>: /restablecer?token={} (expira en {} min)",
                    usuario.getNombre(), usuario.getEmail(), token, EXPIRACION_MINUTOS);
        });
    }

    @Transactional
    public void confirmar(String token, String nuevaPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new BusinessRuleException("El enlace de recuperación no es válido"));
        if (resetToken.getUsadoEn() != null) {
            throw new BusinessRuleException("Este enlace de recuperación ya fue usado");
        }
        if (resetToken.getExpiraEn().isBefore(OffsetDateTime.now())) {
            throw new BusinessRuleException("Este enlace de recuperación expiró");
        }
        Usuario usuario = resetToken.getUsuario();
        usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        usuarioRepository.save(usuario);
        resetToken.setUsadoEn(OffsetDateTime.now());
        passwordResetTokenRepository.save(resetToken);
    }

    private String generarToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
