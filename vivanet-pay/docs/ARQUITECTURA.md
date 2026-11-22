# Vivanet Pay — Arquitectura del núcleo (v0.1)

Referencia: `docs/IDD_v1.0.md` (§4–§8, §13.2, Anexo B). Este documento resume lo construido por el agente núcleo y los contratos que deben usar los demás agentes.

## Build y pruebas

```bash
cd vivanet-pay
mvn -q -DbuildDir=target-<agente> test          # cada agente usa su propio directorio de build
mvn -q -DbuildDir=target-<agente> compile exec:java -Dexec.args=--once   # núcleo sin UI (HeadlessMain)
mvn javafx:run                                  # requiere com.vivanetpay.ui.MainApp (agente UI)
```

- Java 21, UTF-8. Las pruebas corren con `-Duser.timezone=UTC` para detectar errores de zona horaria.
- Todas las dependencias del equipo ya están en `pom.xml` (JavaFX 21.0.5, sqlite-jdbc 3.47.1.0, slf4j 2.0.16, logback 1.5.12, Jackson 2.18.2, google-api-client 2.7.0, google-oauth-client-jetty 1.36.0, gmail v1-rev20240520-2.0.0, JUnit 5.11.3, AssertJ 3.26.3). No cambiar versiones sin coordinarlo.
- Las clases de dependencias de JavaFX se resuelven para el SO donde corre Maven; para empaquetar Windows usar `-Djavafx.platform=win`.

## Mapa de paquetes (`com.vivanetpay`)

| Paquete | Responsabilidad | Dueño |
|---|---|---|
| `domain` | `EventStatus` (8 estados + `canTransitionTo`), `EventSource`, `RawEvent`, `PaymentEvent`, `Transaction` (`long` pesos, COP), `Validation`, `VoiceAnnouncement`, `AuditEntry`, `ConnectorState/Status/Type`, `AppTime` (Bogotá), `CopAmount` | núcleo |
| `parsing` | `PaymentParser`, `ParseResult`, `ParserRegistry`, `UnrecognizedFallbackParser` | contratos: núcleo; parsers reales: **agente parsers** |
| `validation` | `AmountNormalizer`, `EventValidator`, `IdempotencyEngine` + `Fingerprint`, `ValidationPolicy` + `ConservativeValidationPolicy` | núcleo |
| `application` | `EventSink`/`IngestionService`, `ReviewService`, `DashboardService`, `HistoryService`, `AppEventBus`/`AppEvent`, `AnnouncementRecorder`, `ConnectorMonitor` | núcleo |
| `persistence` | `Database`, `MigrationRunner`, repositorios, `IngestionWriter`, `ReportingQueries`, `DatabaseMaintenance` (backup/restore/integrity) | núcleo |
| `integration` | `Connector`, `AbstractConnector`, `ConnectorProvider` (SPI), `ConnectorDependencies`, `ConnectorCursorStore`, `ConnectorRegistry` | núcleo |
| `integration.gmail` | OAuth solo lectura, sincronización incremental, revocación | **agente Gmail/seguridad** |
| `integration.android` | receptor webhook (HMAC o token, timestamp, nonce, límites; TLS opcional; por defecto en 127.0.0.1) para MacroDroid | **agente Android** |
| `voice` | `VoiceService`, `AnnouncementQueue` (1 hilo, sin solapamientos), `SpeechEngine`, `LoggingSpeechEngine`, `AnnouncementMessages` | cola: núcleo; motor Windows: **agente UI/voz** |
| `security` | `SecretStore`; `WindowsSecretStore` (DPAPI vía PowerShell, Windows), `FileSecretStore` (archivo con permisos POSIX 600, sin cifrar: solo desarrollo/Linux), `InMemorySecretStore` (pruebas); `SecretStores` elige según el SO | agente seguridad |
| `reporting` | `DashboardSummary`, `BankBreakdown`, `HistoryFilter`, `HistoryRow`, `CsvExporter` (exportación CSV) | agente UI/reportes |
| `observability` | `Redactor`, conversores Logback `%rmsg`/`%rex`, `Correlation` (MDC `cid`) | núcleo |
| `licensing` | `LicenseService` (prueba 14 días, reloj retrocedido ⇒ `SOSPECHOSO`) | núcleo |
| `app` | `AppConfig`, `AppContext` (composición manual), `Bootstrap`, `HeadlessMain` | núcleo |
| `ui` | `MainApp` JavaFX, bandeja, pantallas | **agente UI** |

## Contratos para los otros agentes

**Conectores (Gmail, Android)**
1. Implementar `Connector` (recomendado: extender `AbstractConnector` y usar `updateStatus`/`markSuccess`).
2. Crear un `ConnectorProvider` y registrarlo en `src/main/resources/META-INF/services/com.vivanetpay.integration.ConnectorProvider`. `Bootstrap` crea los habilitados en `connectors.enabled` (por defecto `gmail,android`; `none` = ninguno) y les pasa `ConnectorDependencies` (sink, cursorStore, secretStore, settings `connector.<tipo>.*`, dataDir, clock). Ejemplo funcional: `src/test/java/com/vivanetpay/app/FakeConnectorProvider.java`.
3. Construir `RawEvent` (Anexo B) con `externalEventId` siempre que exista (messageId de Gmail, eventId de MacroDroid) y llamar **solo** a `EventSink.ingest(raw)` (bloqueante, fuera del hilo UI). No interpretar importes ni decidir estados.
4. Avanzar el cursor con `ConnectorCursorStore.commitCursor` **después** de que `ingest` devuelva `isPersisted()`. Reintentar es seguro: la idempotencia evita duplicados.
5. Sincronizaciones iniciales/de recuperación: marcar `historical(true)` para no anunciarlas.
6. Secretos solo en `SecretStore` (claves tipo `gmail.<conector>.refresh_token`); nunca en logs, `app_setting` ni el repositorio.

**Parsers**: implementar `PaymentParser` (sin estado, sin excepciones, id versionado, importes con `AmountNormalizer`) y registrarlo con `AppContext.Builder#parser`. `ParseResult.confidence` < 0.9 ⇒ revisión.

**UI/Voz**: obtener todo de `AppContext` creado con `Bootstrap.start(AppContext.builder(AppConfig.load()).speechEngine(...).secretStore(...))` en un hilo de fondo. Fijar `System.setProperty("vivanet.log.dir", config.logsDir())` antes del primer log (ver `HeadlessMain`). Suscribirse a `AppEventBus` (`TransactionRecorded`, `EventNeedsReview`, `EventStatusChanged`, `ConnectorStatusChanged`, `AnnouncementNotPlayed`) y pasar al hilo JavaFX con `Platform.runLater`. Lecturas: `DashboardService.today()`, `HistoryService.search(HistoryFilter)`, `ReviewService.pending/validate/discard/keepPending`. Silenciar: `VoiceService.setMuted`. El motor Windows implementa `SpeechEngine` (bloqueante; la cola ya serializa).

## Datos

- SQLite en `<dataDir>/vivanetpay.db`; `dataDir` = `-Dvivanet.data.dir`, o `%APPDATA%\VivanetPay`, o `~/.vivanetpay`. Logs en `<dataDir>/logs`, respaldos en `<dataDir>/backups`.
- Migraciones: `src/main/resources/db/migration/V<n>__<desc>.sql` listadas en `migrations.idx` (añadir al final; no editar las aplicadas).
- Tiempos = epoch ms UTC; importes = INTEGER pesos (`CHECK amount > 0`, `currency = 'COP'`). La entidad `transaction` del IDD es la tabla `payment_transaction` (palabra reservada).
- Idempotencia: `idempotency_key` UNIQUE (`ext:FUENTE:id` o `fp:sha256`), UNIQUE parcial `(source, external_id)` y `fingerprint`; la decisión y la inserción ocurren en la misma transacción IMMEDIATE.
- RN-008: triggers impiden DELETE de eventos/movimientos/auditoría, UPDATE de auditoría y de movimientos, y modificar el contenido original del evento.

## Decisiones provisionales (no resuelven las DP)

| DP | Decisión provisional configurable |
|---|---|
| DP-01 | `ConservativeValidationPolicy`: ningún evento automático queda `VALIDADO_POR_FUENTE`. Eventos completos ⇒ `PARSEADO`; dudosos ⇒ `PENDIENTE_REVISION`; ambos se anuncian como "Nueva notificación… Verifica el pago." Solo `ReviewService.validate` (Flujo D) confirma. Activable: `policy.automaticValidation`, `policy.trustedSources` (vacío por defecto). |
| DP-09 | La voz no dice el remitente (`voice.includeSender=false`); volumen 80, velocidad 0. |
| DP-12 | Revisión exige actor no vacío y motivo para descartar; no se corrigen importes; un evento sin importe no puede confirmarse; un posible duplicado de un evento ya confirmado no se confirma sin motivo explícito de "pago distinto" (R-04). |
| DP-07 | Licencia local de 14 días; reloj retrocedido > 10 min ⇒ `SOSPECHOSO` persistente. Sin validación en línea. `license.enforce=false`: solo informa. |
| DP-05/DP-10 | Respaldo al iniciar (`backup.onStart=true`) en el mismo disco, sin retención automática; logs: 10 MB/archivo, 30 días, tope 300 MB. |
| DP-03/DP-04 | El receptor Android existe (`integration.android`); su ubicación/exposición definitiva sigue pendiente (hoy solo 127.0.0.1 o IP privada concreta; TLS obligatorio si no es privada). |
| DP-08 | Exportación CSV disponible (`reporting.CsvExporter`); formato Excel pendiente. |

Otros: el tablero separa "confirmados" de "por verificar" y excluye DUPLICADO/RECHAZADO/ERROR. `ANUNCIADO` solo se alcanza desde `VALIDADO_POR_FUENTE`; para posibles pagos el anuncio queda en `voice_announcement` y en `payment_event.announced_at`. Coincidencia parcial (mismo importe, remitente compatible, ±10 min, otra fuente o sin id externo) ⇒ `PENDIENTE_REVISION` (CP-014). Importes con centavos distintos de cero no se redondean y van a revisión.
