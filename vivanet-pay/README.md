# Vivanet Pay

Aplicación de escritorio para Windows (Java 21 + JavaFX) que detecta transferencias entrantes, las registra y las anuncia por voz. Recibe avisos de Nequi desde un celular Android (MacroDroid) y de correos de Gmail.

Una notificación o un correo **no es prueba de que el dinero llegó**. La app distingue "Confirmado" de "Por verificar" y no confirma ningún pago por su cuenta (ver `docs/DECISIONES_PENDIENTES.md`, DP-01).

## Estado

MVP en construcción. El núcleo, los conectores, la interfaz y las pruebas existen en el repositorio. Quedan decisiones abiertas del [IDD v1.0 §16](docs/IDD_v1.0.md) (DP-01 a DP-12); el detalle y los valores provisionales están en [`docs/DECISIONES_PENDIENTES.md`](docs/DECISIONES_PENDIENTES.md). La cobertura de requisitos está en [`docs/TRAZABILIDAD.md`](docs/TRAZABILIDAD.md).

## Requisitos

- JDK 21 (incluye `jpackage` para empaquetar).
- Maven 3.9 o superior.
- Para ejecutar la interfaz: un entorno gráfico. Para empaquetar el `.exe`/`.msi`: Windows 10/11 x64 y WiX Toolset 3.x.

## Comandos

Las pruebas usan `-DbuildDir` para no colisionar con otros directorios de compilación (por defecto `target/`):

```bash
mvn -q -DbuildDir=target-dev test                                  # pruebas (zona horaria UTC)
mvn -q -DbuildDir=target-dev compile exec:java -Dexec.args=--once  # núcleo sin interfaz (HeadlessMain)
mvn javafx:run                                                     # interfaz (com.vivanetpay.ui.MainApp)
mvn javafx:run -Djavafx.args=--minimized                           # interfaz, arrancando en bandeja
```

Empaquetado para Windows (`jpackage`, `.exe`/`.msi`): ver [`docs/EMPAQUETADO.md`](docs/EMPAQUETADO.md). Usa `-Djavafx.platform=win` para resolver las dependencias de JavaFX de Windows.

## Estructura de paquetes (`com.vivanetpay`)

| Paquete | Responsabilidad |
|---|---|
| `domain` | Modelo: `EventStatus` (estados y transiciones), `RawEvent`, `PaymentEvent`, `Transaction`, `ConnectorStatus`, `CopAmount`, `AppTime` (zona America/Bogota). |
| `parsing` | Parsers de Nequi (push y correo) y Bancolombia (correo), y el fallback de formato desconocido. |
| `validation` | Normalización de importes, idempotencia (`IdempotencyEngine`, `Fingerprint`) y política de validación (`ConservativeValidationPolicy`). |
| `application` | Casos de uso: ingesta (`IngestionService`), revisión manual (`ReviewService`), tablero, historial, monitor de conectores. |
| `persistence` | SQLite: repositorios, migraciones (`MigrationRunner`), mantenimiento (respaldo, restauración, integridad). |
| `integration` | Contrato de conectores (`Connector`, `ConnectorProvider` por SPI, `ConnectorRegistry`). |
| `integration.gmail` | Conector Gmail: OAuth de solo lectura, sincronización incremental, revocación. |
| `integration.android` | Receptor HTTP(S) para MacroDroid: autenticación HMAC o token, nonce, límite de tasa, rotación de secreto. |
| `voice` | Cola de anuncios (`AnnouncementQueue`), mensajes en español, motor de voz de Windows (`WindowsSpeechEngine`). |
| `reporting` | Resúmenes del tablero, filtros del historial y exportación CSV. |
| `security` | Almacén de secretos en `<datos>/secrets`: cifrado con DPAPI en Windows (`WindowsSecretStore`); en otros sistemas, archivos con permisos 600 **sin cifrar** (`FileSecretStore`, solo desarrollo). |
| `licensing` | Prueba local de 14 días (`LicenseService`). |
| `observability` | Redacción de tokens, claves y correos en logs y diagnóstico. |
| `app` | `AppConfig`, `AppContext` (composición manual), `Bootstrap`, `HeadlessMain`. |
| `ui` | JavaFX: `MainApp`, `MainWindow`, bandeja (`TrayController`), pantallas en `ui/view/`, modelos de vista en `ui/viewmodel/`. |

Migraciones SQL: `src/main/resources/db/migration/` (listadas en `migrations.idx`; añadir al final, no editar las ya aplicadas).

## Datos, logs y respaldos

Directorio de datos (no está en la carpeta de instalación):

1. Propiedad de sistema `-Dvivanet.data.dir=<ruta>`, si se indica.
2. En Windows: `%APPDATA%\VivanetPay`.
3. En otros sistemas: `~/.vivanetpay`.

Dentro de ese directorio:

- `vivanetpay.db`: base SQLite (importes en pesos enteros; tiempos en epoch ms UTC).
- `config.properties`: configuración no secreta (opcional).
- `logs/vivanetpay.log`: logs rotativos, redactados.
- `backups/`: respaldos de la base (uno al iniciar si `backup.onStart=true`, y los creados desde Diagnóstico).
- `gmail/client_secret.json`: credenciales OAuth de Google (por defecto; se puede cambiar con `connector.gmail.clientSecretsPath`). Nunca al repositorio (ya está en `.gitignore`).

Los secretos (tokens de Gmail, secreto del receptor Android, contraseña del keystore TLS) se guardan **solo** en el `SecretStore`, nunca en `config.properties` ni en la base.

## Configuración (`config.properties`)

Archivo en el directorio de datos. Las claves no secretas se leen en `app/AppConfig.java`, `integration/android/AndroidWebhookConfig.java` e `integration/gmail/GmailSettings.java`. Por defecto arrancan **Gmail y Android**: Gmail sin credenciales queda en "Requiere autorización" (no usa la red) y el receptor Android escucha solo en `127.0.0.1`. Para desactivarlos use `connectors.enabled=none`.

| Clave | Por defecto | Descripción |
|---|---|---|
| `connectors.enabled` | `gmail,android` | Conectores a arrancar: `gmail`, `android` o ambos separados por coma; `none` no arranca ninguno. |
| `policy.automaticValidation` | `false` | Permite confirmar automáticamente según la política (DP-01). |
| `policy.trustedSources` | (vacío) | Fuentes de confianza para la validación automática (p. ej. `ANDROID_NEQUI`). |
| `policy.minConfidence` | `0.9` | Confianza mínima del parser; por debajo, el evento va a revisión. |
| `policy.announceUnconfirmed` | `true` | Anuncia los posibles pagos como "Verifica el pago". |
| `policy.announceOnManualValidation` | `false` | Anuncia por voz al validar manualmente. |
| `idempotency.similarityWindowMinutes` | `10` | Ventana para detectar el mismo pago desde dos fuentes. |
| `voice.enabled` | `true` | Activa la voz. |
| `voice.volume` | `80` | Volumen (0 a 100). Solo en Windows. |
| `voice.rate` | `0` | Velocidad (-10 a 10; 0 es normal). Solo en Windows. |
| `voice.name` | (vacío) | Nombre de la voz de Windows; vacío usa la predeterminada. |
| `voice.includeSender` | `false` | Incluye el remitente en el anuncio (DP-09). |
| `voice.maxAgeMinutes` | `15` | No anuncia eventos más antiguos que esto. |
| `voice.connectorAlerts` | `true` | Anuncia cuando un conector se desconecta. |
| `backup.onStart` | `true` | Respaldo de la base al iniciar. |
| `license.enforce` | `false` | Si es `true`, la captura no arranca con la prueba vencida o sospechosa (DP-07). |
| `connector.gmail.clientSecretsPath` | `<datos>/gmail/client_secret.json` | Archivo de credenciales de Google Cloud. |
| `connector.gmail.senders` | `nequi.com.co,bancolombia.com.co` | Remitentes permitidos (DP-02). |
| `connector.gmail.query` | `from:(<remitentes>) newer_than:2d` | Búsqueda de la sincronización inicial. |
| `connector.gmail.pollSeconds` | `60` | Intervalo de sondeo (mínimo 5). |
| `connector.gmail.initialMax` | `50` | Máximo de correos en la sincronización inicial. |
| `connector.gmail.backoffBaseSeconds` / `backoffMaxSeconds` | `5` / `900` | Backoff exponencial ante fallos. |
| `connector.android.bindAddress` | `127.0.0.1` | Direcciones de escucha, separadas por coma. Para el celular, añadir la IP LAN del PC (DP-03). |
| `connector.android.port` | `47390` | Puerto del receptor (0 = efímero, solo pruebas). |
| `connector.android.auth` | `hmac` | `hmac` o `token`. MacroDroid necesita `token` (ver `DECISIONES_PENDIENTES.md`). |
| `connector.android.tls.keystore` | (vacío) | Ruta a un PKCS12 para HTTPS. Contraseña en el `SecretStore`. |
| `connector.android.maxSkewSeconds` | `300` | Vigencia del timestamp en modo `hmac`. |
| `connector.android.maxBodyBytes` | `16384` | Tamaño máximo del cuerpo (tope duro 65536). |
| `connector.android.rateLimit.burst` / `rateLimit.perMinute` | `20` / `60` | Límite de solicitudes por IP. |
| `connector.android.nonceCacheSize` | `10000` | Nonces recordados contra reenvíos (`hmac`). |
| `connector.android.rotationGraceMinutes` | `10` | Validez del secreto anterior tras rotarlo. |
| `connector.android.ingestTimeoutMs` | `5000` | Espera máxima por el núcleo antes de responder 202 `processing`. |
| `connector.android.allowWildcardBind` | `false` | Permite `0.0.0.0` o `::`. No recomendado. |

Propiedades de sistema útiles: `vivanet.data.dir` (directorio de datos), `vivanet.log.dir` (logs), `vivanet.log.level` (`INFO` por defecto).

## Documentación

- [`docs/IDD_v1.0.md`](docs/IDD_v1.0.md): especificación (requisitos, arquitectura, pruebas, decisiones pendientes).
- [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md): mapa de paquetes, contratos para conectores y parsers, decisiones provisionales.
- [`docs/TRAZABILIDAD.md`](docs/TRAZABILIDAD.md): requisitos RF-001 a RF-025 con sus clases y tests.
- [`docs/DECISIONES_PENDIENTES.md`](docs/DECISIONES_PENDIENTES.md): DP-01 a DP-12 con su valor provisional.
- [`docs/GMAIL_OAUTH.md`](docs/GMAIL_OAUTH.md): configuración de OAuth para Gmail.
- [`docs/MACRODROID.md`](docs/MACRODROID.md): puente Android (guía paso a paso) y contrato HTTP.
- [`docs/EMPAQUETADO.md`](docs/EMPAQUETADO.md): instalador para Windows.
- [`docs/MANUAL_USUARIO.md`](docs/MANUAL_USUARIO.md): manual para el propietario del negocio.
- [`tools/macrodroid/README.md`](tools/macrodroid/README.md): plantillas de MacroDroid y contrato HTTP.
