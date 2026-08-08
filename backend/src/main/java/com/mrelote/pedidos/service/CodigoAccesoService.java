package com.mrelote.pedidos.service;

import com.mrelote.pedidos.entity.CodigoAcceso;
import com.mrelote.pedidos.entity.Usuario;
import com.mrelote.pedidos.exception.BusinessRuleException;
import com.mrelote.pedidos.repository.CodigoAccesoRepository;
import com.mrelote.pedidos.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * Inicio de sesión con código de un solo uso, como alternativa a la
 * contraseña ("recibe un código"). La lógica es real: código de 6 dígitos
 * generado con SecureRandom, guardado solo como hash SHA-256, con
 * expiración, límite de intentos y consumo de un solo uso.
 *
 * El transporte por SMS/email depende de un proveedor externo que este
 * entorno no tiene (igual que {@link PasswordResetService}); mientras
 * tanto el código se entrega por el mismo canal "log" real que usa
 * {@code LogNotificacionSender}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CodigoAccesoService {

    private static final int EXPIRACION_MINUTOS = 10;
    private static final int MAX_INTENTOS = 5;

    private final UsuarioRepository usuarioRepository;
    private final CodigoAccesoRepository codigoAccesoRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Nunca revela si la cuenta existe (evita enumeración), igual que la
     * recuperación de contraseña: siempre responde como si hubiera enviado.
     */
    @Transactional
    public void solicitar(String identificador) {
        usuarioRepository.findByIdentificador(identificador).ifPresent(usuario -> {
            String codigo = generarCodigo();
            codigoAccesoRepository.save(CodigoAcceso.builder()
                    .usuario(usuario)
                    .codigoHash(hash(codigo))
                    .expiraEn(OffsetDateTime.now().plusMinutes(EXPIRACION_MINUTOS))
                    .build());
            log.info("[codigo-acceso] código para {} <{}>: {} (expira en {} min)",
                    usuario.getNombre(), usuario.getEmail(), codigo, EXPIRACION_MINUTOS);
        });
    }

    /**
     * Valida el código y devuelve el usuario, o falla con motivo claro.
     *
     * Deliberadamente NO es @Transactional: los caminos de fallo lanzan
     * excepción, y si el incremento de `intentos` viviera dentro de esa
     * transacción el rollback lo descartaría — el límite de intentos nunca
     * se alcanzaría y el código quedaría expuesto a fuerza bruta. Cada
     * escritura va en su propia transacción (ver el repositorio).
     */
    public Usuario validar(String identificador, String codigo) {
        Usuario usuario = usuarioRepository.findByIdentificador(identificador)
                .orElseThrow(() -> new BusinessRuleException("El código no es válido"));

        CodigoAcceso codigoAcceso = codigoAccesoRepository
                .findFirstByUsuarioIdAndUsadoEnIsNullOrderByCreadoEnDesc(usuario.getId())
                .orElseThrow(() -> new BusinessRuleException("El código no es válido"));

        if (codigoAcceso.getExpiraEn().isBefore(OffsetDateTime.now())) {
            throw new BusinessRuleException("El código expiró, solicita uno nuevo");
        }
        if (codigoAcceso.getIntentos() >= MAX_INTENTOS) {
            throw new BusinessRuleException("Demasiados intentos fallidos, solicita un código nuevo");
        }
        if (!codigoAcceso.getCodigoHash().equals(hash(codigo))) {
            codigoAccesoRepository.registrarIntentoFallido(codigoAcceso.getId());
            throw new BusinessRuleException("El código no es válido");
        }

        // Consumo atómico: si otra petición ya lo usó, esta no entra.
        if (codigoAccesoRepository.marcarUsado(codigoAcceso.getId(), OffsetDateTime.now()) == 0) {
            throw new BusinessRuleException("El código no es válido");
        }
        return usuario;
    }

    /** 6 dígitos, con ceros a la izquierda si hacen falta. */
    private String generarCodigo() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String hash(String codigo) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(codigo.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
