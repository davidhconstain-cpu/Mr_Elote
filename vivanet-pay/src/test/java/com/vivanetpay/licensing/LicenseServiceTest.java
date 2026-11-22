package com.vivanetpay.licensing;

import com.vivanetpay.persistence.AuditRepository;
import com.vivanetpay.persistence.Database;
import com.vivanetpay.persistence.LicenseRepository;
import com.vivanetpay.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LicenseServiceTest {
    @TempDir
    Path dir;

    private LicenseService service(MutableClock clock) {
        Database db = Database.openAndMigrate(dir.resolve("lic.db"));
        return new LicenseService(db, new LicenseRepository(), new AuditRepository(db), clock);
    }

    @Test
    void pruebaDe14DiasYVencimiento() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-22T15:00:00Z"));
        LicenseService s = service(clock);
        LicenseInfo first = s.check();
        assertThat(first.status()).isEqualTo(LicenseStatus.PRUEBA_ACTIVA);
        assertThat(first.daysRemaining()).isEqualTo(14);
        assertThat(first.trialEndsAt()).isEqualTo(Instant.parse("2026-12-06T15:00:00Z"));

        clock.advance(Duration.ofDays(13));
        assertThat(s.check().daysRemaining()).isEqualTo(1);
        clock.advance(Duration.ofDays(1).plusSeconds(1));
        LicenseInfo expired = s.check();
        assertThat(expired.status()).isEqualTo(LicenseStatus.PRUEBA_VENCIDA);
        assertThat(expired.status().allowsCapture()).isFalse();
        assertThat(expired.trialStartedAt()).as("reiniciar no reinicia la prueba").isEqualTo(first.trialStartedAt());
    }

    @Test
    void relojHaciaAtrasMarcaSospechosoYPersiste() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-22T15:00:00Z"));
        LicenseService s = service(clock);
        s.check();
        clock.advance(Duration.ofDays(5));
        s.check();
        clock.set(Instant.parse("2026-11-23T15:00:00Z")); // retrocede 4 días
        assertThat(s.check().status()).isEqualTo(LicenseStatus.SOSPECHOSO);
        clock.set(Instant.parse("2026-11-28T15:00:00Z")); // vuelve a la fecha correcta
        assertThat(s.check().status()).as("SOSPECHOSO es persistente hasta DP-07").isEqualTo(LicenseStatus.SOSPECHOSO);
    }

    @Test
    void pequenosAjustesDeRelojSeTolerean() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-22T15:00:00Z"));
        LicenseService s = service(clock);
        s.check();
        clock.advance(Duration.ofMinutes(-3));
        assertThat(s.check().status()).isEqualTo(LicenseStatus.PRUEBA_ACTIVA);
    }
}
