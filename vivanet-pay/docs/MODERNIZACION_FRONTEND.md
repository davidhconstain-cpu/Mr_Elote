# Modernización del frontend (noviembre de 2026)

## 1. Versiones

| Componente | Antes | Después | Motivo |
|---|---|---|---|
| `org.openjfx:javafx-controls/fxml` | 21.0.5 | **23.0.2** | Última JavaFX cuyo bytecode es Java 21 (class file 65, verificado en el jar). JavaFX 24 es class 66 (JDK 22+), 25 y 27 exigen JDK 23/25. 23.x trae `Platform.Preferences` (tema del sistema). |
| `org.openjfx:javafx-maven-plugin` | 0.0.8 | 0.0.8 | Ya es la última en Maven Central. |

Nota: 23.x es una versión no LTS ya sin mantenimiento. La alternativa conservadora es la línea LTS 21.0.13.1 (sin tema del sistema). El proyecto sigue en Java 21 por decisión del propietario.
Otras librerías de UI: ninguna (solo JavaFX; no se usa ControlsFX/FXTrayIcon).

## 2. APIs deprecadas
Con `-Xlint:all` (ya activo en el pom) y 23.0.2 no quedan avisos de deprecación en `ui/**` ni `reporting/**`.
**Bandeja (`java.awt.SystemTray/TrayIcon`)**: sigue siendo la vía soportada (JavaFX no tiene API de bandeja hasta 27). Funciona en Windows 11 (el icono puede quedar en el desbordamiento; `displayMessage` se muestra como notificación). No se cambia; si se quisiera una API más cómoda existe la librería FXTrayIcon, que envuelve la misma API AWT.

## 3. Patrones aplicados
- `Background`: hilos virtuales (`Thread.ofVirtual`) + semáforo de 3 (mismo límite que el pool anterior para no saturar SQLite).
- `DashboardModel` (nuevo): estado observable; `DashboardScreen` enlaza (bind) sus etiquetas, visibilidad del botón y tablas; ya no hay `setText` manual. Se prueba sin display.
- `VoiceScreen`: textos de volumen/velocidad/silencio por `Bindings`; guardado con debounce (`PauseTransition`) que ahora también funciona con el teclado (antes solo al soltar el ratón).
- `MainApp`: `switch` con patrones y `when` para eventos del bus, `Thread.ofPlatform()`, sin casts ni estilos en línea.
- `DiagnosticsScreen`: record `Snapshot` en lugar de `Object[]` con casts; "Abrir carpeta" fuera del hilo FX.
- `HistoryViewModel.summary`: lógica de texto movida al ViewModel (probada).
- Los modelos de filas ya eran `record`.

## 4. Accesibilidad y tema
- `Theme` + `vivanet.css`: colores como *looked-up colors* (`-vp-*`), tema claro/oscuro que sigue `Platform.getPreferences().colorSchemeProperty()` en vivo; los diálogos también reciben el tema.
- Aviso superior ahora con botón "Cerrar aviso" (antes solo clic de ratón); nombre accesible.
- Atajos Ctrl+1..7 entre pantallas, tooltips; `accessibleText` en tablas, filtros, sliders, detalle y totales del Tablero.
- Estados siguen con símbolo y texto; borde "por verificar" discontinuo; foco visible con contorno grueso. Tablas con política de columnas que evita desborde horizontal.
- `UiModernizationTest` verifica WCAG AA (4.5:1 texto, 3:1 bordes/foco) en ambos temas.
- HiDPI: JavaFX escala solo; tamaños en px. El icono de bandeja (32x32) cubre 200 %.

## 5. CSV (`CsvExporter`)
Ya tenía BOM UTF-8, CRLF y RFC 4180. Actualizado a OWASP: comilla simple inicial **y campo entrecomillado**, y detección tras espacios/NBSP/control iniciales y signos de ancho completo (＝＋－＠), además de `= + - @ TAB CR LF`.
Pendiente (DP-08): en Excel con configuración regional es-CO el separador de lista es `;`, así que un CSV con comas abierto con doble clic puede caer en una sola columna; se deja la coma (estándar) y se recomienda decidir entre `;` o importar por Datos > Desde texto.

## 6. Prueba visual
- `mvn javafx:run` bajo xvfb: arranca hasta "Interfaz lista", sin excepciones.
- Capturas de las 7 pantallas en claro y oscuro (herramienta `src/test/.../ScreenshotTool`, no es un test): `/tmp/claude-0/-home-user-Mr-Elote/45cea6e1-3561-5c68-80bd-751c1c818d95/scratchpad/screens/`.
- Hallazgos corregidos: texto de ayuda de campos casi invisible en tema oscuro; botón por defecto con bajo contraste en oscuro. Sin textos cortados salvo la columna "Motivo" de Pendientes (elipsis; el detalle muestra el texto completo).

## 7. Si se subiera a Java 25
Usar JavaFX 25.0.x (LTS). Mejoras disponibles: `Scene.getPreferences`/color scheme por escena, `reducedMotion` y alto contraste en `Platform.Preferences`, mejor rendimiento de CSS. Ninguna es imprescindible: no se requiere migrar.
