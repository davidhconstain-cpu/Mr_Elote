package com.vivanetpay.qa;

import com.vivanetpay.domain.ConnectorState;
import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.domain.ConnectorType;
import com.vivanetpay.support.TestApp;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.time.Instant;

/**
 * Proceso hijo de CP-012: escribe eventos y cursor y luego MUERE de golpe ({@code Runtime.halt}, equivalente a un
 * corte de energía o "Terminar proceso"): sin close(), sin shutdown hooks, con una transacción a medias.
 */
public final class AbruptWriterMain {
    private AbruptWriterMain() {
    }

    public static void main(String[] args) throws Exception {
        TestApp app = TestApp.create(Path.of(args[0]));
        app.ctx.connectorRepository().saveState(new ConnectorState("gmail-principal", ConnectorType.GMAIL,
                ConnectorStatus.CONECTADO, "ok", Instant.parse("2026-11-22T15:00:00Z"), Instant.parse("2026-11-22T15:00:00Z")));
        for (int i = 1; i <= 3; i++) {
            app.ctx.ingestionService().ingest(app.nequi("abrupt-" + i, "Ana te envió $" + i + "0.000"));
            app.ctx.connectorRepository().commitCursor("gmail-principal", "hist-" + i);
        }
        // transacción abierta sin confirmar cuando el proceso muere
        var c = app.ctx.database().connect();
        c.setAutoCommit(false);
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO app_setting (key, value, is_secret_ref, updated_at) VALUES (?,?,0,?)")) {
            ps.setString(1, "no.debe.persistir");
            ps.setString(2, "x");
            ps.setLong(3, 1L);
            ps.executeUpdate();
        }
        System.out.println("LISTO");
        System.out.flush();
        Runtime.getRuntime().halt(137);
    }
}
