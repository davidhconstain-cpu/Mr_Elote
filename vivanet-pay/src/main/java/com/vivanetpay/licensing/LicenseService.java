package com.vivanetpay.licensing;

import com.vivanetpay.domain.Actors;
import com.vivanetpay.domain.AppTime;
import com.vivanetpay.domain.AuditEntry;
import com.vivanetpay.persistence.AuditRepository;
import com.vivanetpay.persistence.Database;
import com.vivanetpay.persistence.LicenseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Prueba local de 14 días (IDD §2.4) con registro de {@code trial_started_at}, {@code trial_ends_at},
 * {@code last_check_at} y el máximo instante observado. Si el reloj actual es anterior al máximo observado
 * (menos una tolerancia), el estado pasa a {@link LicenseStatus#SOSPECHOSO} y queda así (R-09).
 * <p>
 * PROVISIONAL — DP-07 pendiente: sin validación en línea; la reinstalación borra el estado local.
 */
public final class LicenseService {
    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);
    public static final String LOCAL_TRIAL_ID = "LOCAL-TRIAL";
    public static final Duration TRIAL_DURATION = Duration.ofDays(14);
    /** Tolerancia de ajustes de reloj (NTP, cambio manual menor) antes de considerar sospechoso. */
    public static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(10);

    private final Database db;
    private final LicenseRepository repo;
    private final AuditRepository audit;
    private final Clock clock;

    public LicenseService(Database db, LicenseRepository repo, AuditRepository audit, Clock clock) {
        this.db = db;
        this.repo = repo;
        this.audit = audit;
        this.clock = clock;
    }

    /** Verifica y actualiza el estado local. Inicia la prueba en la primera ejecución. */
    public LicenseInfo check() {
        Instant now = clock.instant();
        LicenseRepository.Row row = db.inTransaction(c -> {
            var existing = repo.find(c, LOCAL_TRIAL_ID);
            if (existing.isEmpty()) {
                var created = new LicenseRepository.Row(LOCAL_TRIAL_ID, LicenseStatus.PRUEBA_ACTIVA.name(), now,
                        now.plus(TRIAL_DURATION), now, now, now);
                repo.upsert(c, created);
                audit.insert(c, new AuditEntry(null, AuditEntry.ENTITY_LICENSE, LOCAL_TRIAL_ID, AuditEntry.ACTION_CREATE,
                        null, created.status(), Actors.LICENSING, "Inicio de prueba de 14 días", null, now));
                return created;
            }
            var r = existing.get();
            LicenseStatus previous = LicenseStatus.valueOf(r.status());
            LicenseStatus next;
            Instant maxSeen = r.maxSeenAt().isAfter(now) ? r.maxSeenAt() : now;
            if (previous == LicenseStatus.SOSPECHOSO || now.isBefore(r.maxSeenAt().minus(CLOCK_TOLERANCE))) {
                next = LicenseStatus.SOSPECHOSO;
            } else if (now.isBefore(r.trialEndsAt())) {
                next = LicenseStatus.PRUEBA_ACTIVA;
            } else {
                next = LicenseStatus.PRUEBA_VENCIDA;
            }
            var updated = new LicenseRepository.Row(r.licenseId(), next.name(), r.trialStartedAt(), r.trialEndsAt(), now,
                    maxSeen, now);
            repo.upsert(c, updated);
            if (next != previous) {
                String reason = next == LicenseStatus.SOSPECHOSO
                        ? "Reloj del equipo retrocedido: ahora=" + now + ", máximo observado=" + r.maxSeenAt()
                        : null;
                audit.insert(c, new AuditEntry(null, AuditEntry.ENTITY_LICENSE, LOCAL_TRIAL_ID,
                        AuditEntry.ACTION_STATUS_CHANGE, previous.name(), next.name(), Actors.LICENSING, reason, null, now));
                if (next == LicenseStatus.SOSPECHOSO) {
                    log.warn("Licencia marcada SOSPECHOSO por reloj retrocedido");
                }
            }
            return updated;
        });
        return toInfo(row, now);
    }

    private static LicenseInfo toInfo(LicenseRepository.Row r, Instant now) {
        LicenseStatus status = LicenseStatus.valueOf(r.status());
        long remainingMs = Math.max(0, r.trialEndsAt().toEpochMilli() - now.toEpochMilli());
        long days = (remainingMs + Duration.ofDays(1).toMillis() - 1) / Duration.ofDays(1).toMillis();
        if (status != LicenseStatus.PRUEBA_ACTIVA) {
            days = status == LicenseStatus.SOSPECHOSO ? days : 0;
        }
        String message = switch (status) {
            case PRUEBA_ACTIVA -> "Prueba gratuita activa: quedan " + days + " día(s), hasta el "
                    + AppTime.format(r.trialEndsAt()) + ".";
            case PRUEBA_VENCIDA -> "La prueba gratuita terminó. Contacta al proveedor para activar la licencia.";
            case SOSPECHOSO -> "La fecha del equipo parece haber retrocedido. Corrige la fecha y hora de Windows "
                    + "y contacta soporte.";
            case ACTIVA -> "Licencia activa.";
        };
        return new LicenseInfo(status, r.trialStartedAt(), r.trialEndsAt(), r.lastCheckAt(), days, message);
    }
}
