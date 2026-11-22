/**
 * Presentación JavaFX y bandeja del sistema (IDD §9, RF-001..RF-004, RF-013..RF-017, RF-023).
 * <p>
 * Lo implementará el AGENTE UI/VOZ: clase {@code com.vivanetpay.ui.MainApp} (JavaFX {@code Application},
 * referenciada por {@code javafx-maven-plugin}), pantallas Tablero, Historial, Pendientes, Conectores, Voz,
 * Configuración y Diagnóstico, y la bandeja (System Tray). Debe obtener todo de {@code app.AppContext}
 * (creado con {@code app.Bootstrap}), suscribirse a {@code application.AppEventBus} y pasar al hilo de UI con
 * {@code Platform.runLater}. Nunca acceder a la base ni a la red en el hilo de JavaFX (RNF-008).
 */
package com.vivanetpay.ui;
