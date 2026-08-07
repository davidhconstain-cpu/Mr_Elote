package com.mrelote.pedidos.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTokenMinutes;
    private final long refreshTokenDays;

    public JwtService(
            @Value("${mrelote.jwt.secret}") String secret,
            @Value("${mrelote.jwt.access-token-minutes}") long accessTokenMinutes,
            @Value("${mrelote.jwt.refresh-token-days}") long refreshTokenDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenMinutes = accessTokenMinutes;
        this.refreshTokenDays = refreshTokenDays;
    }

    public String generarAccessToken(UsuarioPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("uid", principal.id())
                .claim("rol", principal.usuario().getRol().getNombre())
                .claim("tipo", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public String generarRefreshToken(UsuarioPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("uid", principal.id())
                .claim("tipo", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTokenDays, ChronoUnit.DAYS)))
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extraerEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean esAccessToken(String token) {
        return "access".equals(parseClaims(token).get("tipo", String.class));
    }

    public boolean tokenValido(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
