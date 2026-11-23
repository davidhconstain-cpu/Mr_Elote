package com.vivanetpay.ui;

import com.vivanetpay.domain.ConnectorState;
import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.domain.ConnectorType;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.reporting.BankBreakdown;
import com.vivanetpay.reporting.DashboardSummary;
import com.vivanetpay.ui.viewmodel.DashboardModel;
import com.vivanetpay.ui.viewmodel.DashboardViewModel;
import com.vivanetpay.ui.viewmodel.HistoryViewModel;
import javafx.application.ColorScheme;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas sin pantalla: modelo observable del Tablero, tema y contraste de la hoja de estilos. */
class UiModernizationTest {

    private static DashboardViewModel sample(int pending) {
        DashboardSummary s = new DashboardSummary(LocalDate.of(2025, 3, 1), 300000, 2, 50000, 1, pending,
                List.of(new BankBreakdown("Nequi", 100000, 1, 50000, 1)),
                UiTestData.row(1, "Nequi", 50000L, "x", EventStatus.PARSEADO), Instant.parse("2025-03-01T20:00:00Z"));
        ConnectorState c = new ConnectorState("g", ConnectorType.GMAIL, ConnectorStatus.CONECTADO, "ok",
                Instant.parse("2025-03-01T19:00:00Z"), Instant.parse("2025-03-01T19:00:00Z"));
        return DashboardViewModel.of(s, List.of(c));
    }

    @Test
    void dashboardModelBindingsFollowUpdatesWithoutDisplay() {
        DashboardModel m = new DashboardModel();
        var total = m.text(DashboardViewModel::confirmedTotal, "");
        var pending = m.hasPending();
        var day = m.day();
        assertThat(total.get()).isEmpty();
        assertThat(pending.get()).isFalse();
        assertThat(day.get()).isEmpty();

        m.update(sample(3));
        assertThat(total.get()).isEqualTo("$300.000");
        assertThat(pending.get()).isTrue();
        assertThat(day.get()).isEqualTo("Hoy 2025-03-01 (hora de Colombia)");
        assertThat(m.banks()).hasSize(1);
        assertThat(m.connectors()).hasSize(1);

        m.update(sample(0));
        assertThat(pending.get()).isFalse();
    }

    @Test
    void systemColorSchemeMapsToDarkOnlyForDark() {
        assertThat(Theme.isDark(ColorScheme.DARK)).isTrue();
        assertThat(Theme.isDark(ColorScheme.LIGHT)).isFalse();
        assertThat(Theme.isDark(null)).isFalse();
    }

    @Test
    void historySummaryWarnsWhenLimitReached() {
        assertThat(HistoryViewModel.summary(3, 500)).isEqualTo("3 resultado(s). Horas en America/Bogota.");
        assertThat(HistoryViewModel.summary(500, 500)).contains("se muestran los primeros 500");
    }

    // ---- contraste WCAG AA (RNF accesibilidad, IDD 9.4) ----

    private static final Pattern DEF = Pattern.compile("(-vp-[a-z-]+):\\s*(#[0-9a-fA-F]{6});");

    private static Map<String, String> palette(String css, String selector) {
        int a = css.indexOf(selector + " {");
        int b = css.indexOf('}', a);
        Map<String, String> m = new HashMap<>();
        Matcher mt = DEF.matcher(css.substring(a, b));
        while (mt.find()) {
            m.put(mt.group(1), mt.group(2));
        }
        return m;
    }

    private static double luminance(String hex) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = Integer.parseInt(hex.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
            c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    static double contrast(String a, String b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    @Test
    void textColorsMeetWcagAaInBothThemes() throws IOException {
        String css;
        try (InputStream in = getClass().getResourceAsStream("/ui/vivanet.css")) {
            css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, String> light = palette(css, ".root");
        Map<String, String> dark = new HashMap<>(light);
        dark.putAll(palette(css, ".root.dark"));
        String[][] textOnBackground = {
                {"-vp-text", "-vp-bg"}, {"-vp-text", "-vp-surface"}, {"-vp-muted", "-vp-bg"},
                {"-vp-muted", "-vp-surface"}, {"-vp-primary", "-vp-bg"}, {"-vp-primary", "-vp-surface"},
                {"-vp-nav-text", "-vp-nav-bg"}, {"-vp-nav-text", "-vp-nav-hover"},
                {"-vp-nav-selected-text", "-vp-nav-selected-bg"}, {"-vp-banner-text", "-vp-banner-bg"},
                {"-vp-ok", "-vp-surface"}, {"-vp-warn", "-vp-surface"}};
        for (Map<String, String> theme : List.of(light, dark)) {
            for (String[] p : textOnBackground) {
                assertThat(theme).containsKeys(p[0], p[1]);
                assertThat(contrast(theme.get(p[0]), theme.get(p[1])))
                        .as("%s sobre %s (%s / %s)", p[0], p[1], theme.get(p[0]), theme.get(p[1]))
                        .isGreaterThanOrEqualTo(4.5);
            }
            // bordes y foco: componentes no textuales, mínimo 3:1 (WCAG 1.4.11)
            assertThat(contrast(theme.get("-vp-neutral"), theme.get("-vp-surface"))).isGreaterThanOrEqualTo(3.0);
            assertThat(contrast(theme.get("-vp-focus"), theme.get("-vp-nav-bg"))).isGreaterThanOrEqualTo(3.0);
        }
    }
}
