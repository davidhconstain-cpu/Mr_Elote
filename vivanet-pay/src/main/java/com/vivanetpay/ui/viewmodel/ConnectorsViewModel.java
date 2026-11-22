package com.vivanetpay.ui.viewmodel;

import com.vivanetpay.domain.ConnectorState;
import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.integration.AuthorizableConnector;
import com.vivanetpay.integration.Connector;
import com.vivanetpay.ui.Formats;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/** Fila de la pantalla Conectores (RF-017, RF-025), con acciones disponibles según capacidades del conector. */
public record ConnectorsViewModel(String id, String name, String status, String detail, String action,
                                  String lastSuccess, boolean paused, boolean canAuthorize, boolean canTestReception,
                                  boolean authorized) {
    /**
     * Métodos opcionales para "Probar recepción" (descubiertos por reflexión, en este orden):
     * {@code sendTestEvent()} (conector Android) y {@code testReception()}.
     */
    public static final List<String> TEST_METHODS = List.of("sendTestEvent", "testReception");

    public static ConnectorsViewModel of(Connector c) {
        ConnectorState s = c.state();
        boolean authorizable = c instanceof AuthorizableConnector;
        boolean authorized = authorizable && ((AuthorizableConnector) c).isAuthorized();
        return new ConnectorsViewModel(c.id(), c.displayName(), Formats.connectorStatus(s.status()),
                s.detail(), Formats.recommendedAction(s.status()), Formats.time(s.lastSuccessAt()),
                s.status() == ConnectorStatus.PAUSADO, authorizable, testMethod(c).isPresent(), authorized);
    }

    /** Método público sin argumentos de prueba de recepción, si el conector lo ofrece. */
    public static Optional<Method> testMethod(Connector c) {
        for (String name : TEST_METHODS) {
            try {
                return Optional.of(c.getClass().getMethod(name));
            } catch (NoSuchMethodException e) {
                // probar el siguiente nombre
            }
        }
        return Optional.empty();
    }

    /** Ejecuta "Probar recepción" y devuelve un texto para el usuario. Bloqueante. */
    public static String runTest(Connector c) throws Exception {
        Method m = testMethod(c).orElseThrow(() -> new IllegalStateException("El conector no ofrece prueba de recepción"));
        Object r;
        try {
            r = m.invoke(c);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause() instanceof Exception ex ? ex : e;
        }
        if (r == null || m.getReturnType() == void.class) {
            return "Prueba enviada. Revisa el Historial.";
        }
        try { // registros con message() (p. ej. WebhookTestResult)
            Object msg = r.getClass().getMethod("message").invoke(r);
            if (msg != null) {
                return String.valueOf(msg);
            }
        } catch (ReflectiveOperationException ignored) {
            // se usa toString
        }
        return String.valueOf(r);
    }
}
