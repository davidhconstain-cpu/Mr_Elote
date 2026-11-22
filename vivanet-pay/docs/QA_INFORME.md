# Vivanet Pay - Informe de QA y seguridad

Referencia: `docs/IDD_v1.0.md` (RF §4.2, RNF §4.3, RN §4.4, §7.3, §8, §9.3, CP §11.2, aceptación §11.3, DoD §15.3).
Alcance de la auditoría: código de producción (`src/main/java`), pruebas (`src/test/java`), recursos y archivos versionados.
Entorno de las pruebas automatizadas: Linux, Java 21, `mvn -q -DbuildDir=target-qa test`, `-Duser.timezone=UTC`.

## 1. Resultado global

| Concepto | Valor |
|---|---|
| Pruebas ejecutadas (suite completa limpia, `clean test`) | 276 |
| Fallos / errores | 0 / 0 |
| Omitidas | 5 (todas exigen Windows: DPAPI real x2, autoinicio con `reg.exe`, voz de Windows, `WindowsSecretStore` real) |
| Pruebas añadidas por esta auditoría | 43 (6 clases nuevas: 5 en `com.vivanetpay.qa` y `QaWebhookHardeningTest`; ampliadas `RedactorTest`, `GmailOAuthSupportTest`, `BackupRestoreTest`; ajustada `PlatformIntegrationTest`) |
| Defectos críticos o altos de seguridad abiertos | 0 |
| Hallazgos corregidos | 14 (ver §4) |
| Hallazgos pendientes / riesgo aceptado | 4, todos de severidad Baja (ver §4) |

Veredicto: el núcleo cumple los CP automatizables y los criterios de aceptación 11.3 que dependen de software. Quedan por
demostrar en el entorno real (Windows, teléfono Android, cuenta Google) los puntos de la sección 5.

## 2. Casos de prueba CP-001 a CP-015

Estados: **Automatizado-OK** (lo demuestra una prueba que corre en cualquier SO), **Solo Windows** (la prueba existe pero
solo corre en Windows o la verificación final es visual), **Requiere teléfono/cuenta real** (el comportamiento con el
servicio externo real no se puede simular).

| ID | Escenario | Estado | Prueba(s) que lo cubren |
|---|---|---|---|
| CP-001 | Inicio normal | Automatizado-OK (núcleo) y Solo Windows (ventana JavaFX) | `BootstrapTest.arrancaRegistraConectoresYAvanzaCursorTrasPersistir` (base, licencia, conectores con estado real, Gmail sin credenciales = REQUIERE_AUTORIZACION), `ViewModelsTest`, `DashboardAndHistoryTest`, `PlatformIntegrationTest.mainAppClassesLoad`. La apariencia del tablero se valida a mano. |
| CP-002 | Inicio minimizado con Windows | Solo Windows | `PlatformIntegrationTest.regArgumentsAreSeparateListEntries` (argumentos y escape), `windowsStartupRoundTrip` (ahora lee el valor real del registro; omitida fuera de Windows), `startupIsNoOpOutsideWindows`. La bandeja y el arranque con sesión se prueban a mano. |
| CP-003 | Gmail autorizado | Requiere cuenta real | `GmailOAuthSupportTest.scopeIsOnlyReadonly`, `dataStoreKeepsTokensInSecretStoreOnlyAndRoundTrips`, `QaStaticSecurityScanTest.gmailSoloPideElAlcanceDeSoloLectura`, `GmailConnectorTest.*` (cliente simulado). El consentimiento OAuth real se hace a mano. |
| CP-004 | OAuth revocado | Automatizado-OK (simulado) y Requiere cuenta real | `GmailConnectorTest.invalidGrantRequiresAuthorizationWithoutBlockingAndAuthorizeRecovers`, `missingCredentialsReportsRequiresAuthorization`, `revokeClearsStateAndExplainsManualRevocation`, `revokeWhenGoogleUnreachableStillClearsLocalAndReports`. |
| CP-005 | Correo válido, plantilla real | Automatizado-OK (plantillas sintéticas) y Requiere cuenta real | `ParsersFixturesTest` (62 casos, >= 30 plantillas), `EndToEndSmokeTest`, `QaRobustnessTest.importeSeparadoPorEspacioOCaracterInvisibleBajaLaConfianza`. La precisión con correos reales (meta >= 98 %) se mide con muestras reales. |
| CP-006 | Correo desconocido | Automatizado-OK | `IngestionServiceTest.formatoDesconocidoQuedaPendienteYNoSeAnunciaConfirmadoCP006`, `EndToEndSmokeTest.correoBancolombiaDeDominioFalsoVaARevision`, `QaPolicyInvariantsTest.conLaPoliticaPorDefectoNingunCaminoAutomaticoConfirma`, `QaRobustnessTest.correoConDisplayNameQueImitaAlBancoNoVerificaElOrigen`. |
| CP-007 | Webhook válido | Automatizado-OK y Requiere teléfono real | `AndroidWebhookConnectorTest.eventoValidoHmacSeAceptaYSeMapeaARawEvent`, `modoTokenAceptaTokenValidoYRechazaInvalido`, `formularioFirmadoConHmac`, `EndToEndSmokeTest.modoTokenNequiRealisticoDeExtremoAExtremo`, `AndroidWebhookTlsTest.httpsConKeystorePkcs12`. La macro real de MacroDroid se prueba a mano. |
| CP-008 | Webhook inválido | Automatizado-OK | `firmaInvalidaSeRechazaSinCrearPago`, `timestampVencidoOFuturoSeRechaza`, `jsonMalformadoOEsquemaInvalidoDevuelve400`, `metodoYTipoDeContenidoIncorrectos`, `cuerpoGiganteSeRechazaConTopeReal`, `rateLimitPorIpDevuelve429`, `QaWebhookHardeningTest.*`, `EndToEndSmokeTest.modoHmacFirmaValidaYFirmaInvalida`. Rechazo auditado en log sin cuerpo ni firma: `logsNoContienenSecretosFirmasNiCuerpo`. |
| CP-009 | Replay | Automatizado-OK | `replayDeNonceDevuelve409YNoDuplica`, `reenvioMismoEventIdConNuevoNonceEsIdempotente`, `IngestionServiceTest.reintentoConMismoIdExternoNoDuplicaMovimiento`, `ConcurrentIngestionTest.*` (carrera de hilos y de servicios). |
| CP-010 | Monto inválido | Automatizado-OK | `IngestionServiceTest.importeCeroNegativoOMonedaExtranjeraSeRechazanCP010`, `AmountNormalizerTest`, `QaRobustnessTest.montosLimiteNuncaSeContabilizanSilenciosamente` (0, negativos, tope 10.000 millones, `Long.MAX_VALUE`, 40 dígitos, centavos) y `sumaDeMuchosMontosMaximosNoDesbordaElTablero`. |
| CP-011 | Audio silenciado | Automatizado-OK y Solo Windows (audio real) | `AnnouncementQueueTest.noAnunciaDuplicadosHistoricosAntiguosNiSilenciado`, `falloDeAudioSeRegistra`, `reproduceDeUnoEnUnoSinSolapamientos`. Que el sonido sale por los altavoces se comprueba a mano (`PlatformIntegrationTest.windowsSpeechEngineSpeaks` en Windows). |
| CP-012 | Reinicio abrupto | Automatizado-OK | `QaDurabilityTest.procesoMatadoDeGolpeConservaMovimientosCursorEIntegridad` (proceso hijo terminado con `Runtime.halt(137)` con una transacción abierta; al reabrir: `integrity_check` limpio, 3 movimientos, cursor intacto, transacción a medias revertida, reenvío = DUPLICADO), `AtomicityTest.*`. |
| CP-013 | Sin Internet | Automatizado-OK y Requiere teléfono/cuenta real | `QaTimeAndOutageTest.sinInternetElHistorialLocalSigueDisponibleYElConectorQuedaDesconectado` (conector DESCONECTADO, tablero/historial/pendientes operan, validación manual funciona, reinicio sin red conserva datos), `GmailConnectorTest.networkErrorDisconnectsThenRecovers`, `backoffGrowsAndIsCapped`. |
| CP-014 | Mismo pago por Gmail y Android | Automatizado-OK | `IngestionServiceTest.mismoPagoPorGmailYAndroidVaARevisionCP014`, `IdempotencyEngineTest.mismoMontoYRemitenteDesdeOtraFuenteEsAmbiguo`. |
| CP-015 | Zona horaria cerca de medianoche | Automatizado-OK | `DashboardAndHistoryTest.eventoCercaDeMedianocheSeAsignaAlDiaLocalDeColombiaCP015`, `QaTimeAndOutageTest.medianocheDeColombiaEsIndependienteDeLaZonaHorariaDelEquipo` (23:59:59 / 00:00:00 en 5 zonas del PC: UTC, Tokio, Kiritimati, Los Angeles, Pago Pago; tablero, filtro de historial y CSV), `cambioDeDiaMientrasLaAplicacionSigueAbiertaMueveElTablero`. |

## 3. Criterios de aceptación del MVP (IDD 11.3)

| Criterio | Estado | Evidencia |
|---|---|---|
| Instala, inicia y opera en Windows en segundo plano | Solo Windows | `docs/EMPAQUETADO.md`; lista manual en §5. |
| Tablero e historial muestran solo datos persistidos | Cumplido | Todas las lecturas pasan por `ReportingQueries` sobre SQLite (`DashboardAndHistoryTest`, `ViewModelsTest`); no hay estado en memoria que alimente totales. |
| Evento repetido no genera dos movimientos | Cumplido | `IngestionServiceTest`, `ConcurrentIngestionTest`, `AndroidWebhookConnectorTest` (replay y reenvío), `GmailConnectorTest.repeatedMessageIsNotDuplicated`, `MigrationAndSchemaTest.uniqueDeIdExternoEnBaseDeDatos`. |
| Eventos dudosos no se anuncian como confirmados | Cumplido | `QaPolicyInvariantsTest` (8 pruebas: política por defecto, bandera sin fuentes, fuentes sin bandera, opt-in solo para la fuente configurada, validación manual como única vía), `AnnouncementQueueTest.pagoConfirmadoExigeEstadoConfirmadoRN003`. |
| Gmail usa OAuth y puede revocarse | Cumplido (simulado) / Requiere cuenta real | `GmailOAuthSupportTest`, `GmailConnectorTest.revoke*`; PKCE + loopback 127.0.0.1, alcance único `gmail.readonly`. |
| El puente Android rechaza solicitudes no autenticadas | Cumplido | `AndroidWebhookConnectorTest` (firma, token, timestamp, nonce, tamaño, tasa, método), `EndToEndSmokeTest`. |
| La voz se puede activar y silenciar | Cumplido (lógica) / Solo Windows (audio) | `AnnouncementQueueTest`, `ViewModelsTest`; prueba auditiva en §5. |
| La BD sobrevive al reinicio; hay respaldo/restauración | Cumplido | `QaDurabilityTest`, `BackupRestoreTest` (incluye ahora rutas con apóstrofo), `MigrationAndSchemaTest`. |
| Sin defectos críticos o altos de seguridad abiertos | Cumplido | §4: ningún hallazgo Crítico/Alto abierto. |
| El propietario acepta flujos y mensajes de voz con pruebas controladas | Pendiente del propietario | Lista de §5. |

DoD 15.3: "errores y logs revisados; no se exponen secretos" se verifica con `RedactorTest` (13 casos), `AndroidWebhookConnectorTest.logsNoContienenSecretosFirmasNiCuerpo`,
`GmailConnectorTest.logsAndStatusNeverContainTokens` y el escaneo estático `QaStaticSecurityScanTest`.

## 4. Hallazgos de seguridad y robustez

Severidad: Crítica / Alta / Media / Baja. Todos los cambios de código están cubiertos por una prueba nueva o ampliada.

| ID | Sev. | Hallazgo | Estado | Archivo(s) |
|---|---|---|---|---|
| H-01 | Media | **Redactor incompleto (RF-018, RNF-012).** Dejaba pasar: valores entre comillas con espacios (`"password": "mi frase larga"` solo enmascaraba la primera palabra), claves camelCase (`clientSecret`, `refreshToken`), cabecera `Authorization: Token ...` / `Digest ...`, firmas base64url con guion de 40+ caracteres y teléfonos con separadores (`+57 300 123 4567`). | Corregido | `observability/Redactor.java`, `RedactorTest` (+8 casos) |
| H-02 | Baja | **Redactor con coste cuadrático (ReDoS).** El patrón de correo recorría hasta el final de la entrada desde cada posición: 200 000 caracteres de dígitos y puntos tardaban más de 5 minutos, bloqueando el hilo que registra. Ahora la parte local está acotada (64) y el dominio (255). | Corregido | `observability/Redactor.java`, `RedactorTest.esIdempotenteYNoExplotaConEntradasHostiles` |
| H-03 | Media | **Filtro de deserialización demasiado amplio.** `SecretStoreDataStoreFactory` permitía todo `java.lang.*`, `java.util.*` y `java.util.concurrent.locks.*`. Ahora es una lista blanca de clases exactas (`StoredCredential`, `String`, `Long`, `Number`, `ReentrantLock` y sus sincronizadores) con `maxdepth/maxrefs/maxbytes/maxarray` y `!*`. Se prueba que `HashMap`, `ArrayList`, `PriorityQueue`, `URL`, `File`, enums, excepciones y un "gadget" con `readObject` se rechazan y no se ejecutan. | Corregido | `integration/gmail/SecretStoreDataStoreFactory.java`, `GmailOAuthSupportTest` (+3) |
| H-04 | Media | **Ejecutables lanzados por nombre simple.** `powershell.exe` (voz) y `reg.exe` (autoinicio) se resolvían por directorio actual/PATH (secuestro de binarios). Ahora ambos usan la ruta absoluta bajo `%SystemRoot%\System32`, igual que ya hacía DPAPI. | Corregido | nuevo `security/SystemExecutables.java`, `voice/WindowsSpeechEngine.java`, `ui/WindowsStartup.java`, `security/PowerShellDpapiProtector.java`; `QaStaticSecurityScanTest.procesosSeLanzanPorRutaAbsolutaYSinShell` |
| H-05 | Media | **Comillas del valor de autoinicio sin escapar.** Java no escapa las comillas internas al armar la línea de comandos de Windows, por lo que el valor `/d "..."` de `reg add` probablemente se registraba truncado (`C:\Program`), lo que rompe el autoinicio y abre la clase "ruta sin comillas". Se escapan como `\"` (convención de reg.exe). `exec` además esperaba la salida antes del tiempo límite (podía colgarse). | Corregido, **verificar en Windows** (§5, punto 3) | `ui/WindowsStartup.java`, `PlatformIntegrationTest` |
| H-06 | Media | **Respaldo y restauración fallaban con apóstrofo en la ruta** (usuario de Windows como `D'Angelo`: la copia de inicio se omitía y la restauración se rechazaba). Se usa la API nativa de sqlite-jdbc, que recibe la ruta sin analizar SQL. | Corregido | `persistence/DatabaseMaintenance.java`, `BackupRestoreTest.respaldaYRestauraEnRutasConApostrofoYEspacios` |
| H-07 | Media | **Importe truncado con confianza alta.** `$50 000`, `$50<NBSP>000` o `$5<ZWSP>0.000` se extraían como 50 o 5 con confianza 0,98 (un emisor podía mostrar 50.000 y registrar 5). Ahora cualquier separador o carácter invisible seguido de más dígitos baja la confianza a 0,5 y el evento va a revisión. Los formatos normales no cambian (62 fixtures siguen en verde). | Corregido | `parsing/AbstractTemplateParser.java`, `QaRobustnessTest` |
| H-08 | Baja | **Verificación de remitente de correo manipulable.** El parser tomaba el dominio de la última dirección encontrada con regex; `"x@bancolombia.com.co"@evil.example` contaba como verificado. Ahora usa la dirección entre `<...>` o la última `@` y exige un dominio bien formado (misma lógica que `GmailSettings.senderAllowed`, que ya filtraba antes de descargar el cuerpo). | Corregido | `parsing/EmailParserSupport.java`, `QaRobustnessTest.correoConDisplayNameQueImitaAlBancoNoVerificaElOrigen` |
| H-09 | Baja | **Fuga de texto en log de parsers y cola envenenable.** `ParserRegistry` registraba `e.toString()` (mensajes con fragmentos del texto) y no capturaba `StackOverflowError`: un texto hostil podía dejar un correo sin persistir y reintentarse para siempre (el cursor no avanza). | Corregido | `parsing/ParserRegistry.java` |
| H-10 | Baja | **CSV:** un texto que empieza con salto de línea no se neutralizaba. | Corregido | `reporting/CsvExporter.java` |
| H-11 | Baja | `AnnouncementRequest` aceptaba `PAGO_CONFIRMADO` sin importe ("Pago recibido por null pesos"). Ahora exige importe. | Corregido | `voice/AnnouncementRequest.java` |
| H-12 | Baja | Conversiones sensibles a la configuración regional (`toLowerCase()`, `toUpperCase()`, `String.format`) en filtros, moneda, clasificación de errores de Gmail y mensaje de política. Pasan a `Locale.ROOT`. | Corregido | `ReportingQueries.java`, `Transaction.java`, `GmailErrors.java`, `ConservativeValidationPolicy.java` |
| H-13 | Baja | **Receptor Android sin límite de tiempo por solicitud:** el servidor HTTP del JDK no limita cuánto tarda un cliente en enviar la solicitud; con 2-4 hilos, un cliente lento podía agotarlos. Se fijan `sun.net.httpserver.maxReqTime/maxRspTime` = 30 s si el operador no los definió. | Corregido parcialmente (riesgo residual en R-01) | `integration/android/AndroidWebhookConnector.java`, `QaWebhookHardeningTest.tiemposMaximosAntiSlowlorisConfigurados` |
| H-14 | Baja | Cobertura: no existía prueba de reinicio abrupto real, de Unicode raro en el webhook, de errores internos que no filtran trazas al cliente, ni escaneo repetible de secretos/logs/SQL/procesos. | Corregido (pruebas añadidas) | `qa/*`, `QaWebhookHardeningTest` |
| R-01 | Baja | Riesgo residual: el receptor sigue usando un pool pequeño (2-4 hilos); un atacante en la misma red local podría degradar el servicio ~30 s por conexión. Por defecto escucha solo en 127.0.0.1 y con IP LAN advierte "sin HTTPS". | Pendiente (aceptado; mitigación: no abrir el puerto en el router, usar `tls.keystore`) | `AndroidWebhookConnector.java` |
| R-02 | Baja | Modo `auth=token` no tiene nonce ni firma: quien capture el token (HTTP en LAN) puede inyectar eventos falsos; por diseño nunca producen "pago confirmado". El modo por defecto es HMAC. | Pendiente (aceptado, documentado en `AuthMode`) | `integration/android/AuthMode.java` |
| R-03 | Baja | `FileSecretStore` (macOS/Linux/desarrollo) guarda secretos sin cifrar (permisos 600) y lo avisa en el log. En Windows se usa DPAPI. | Pendiente (aceptado: no es la plataforma objetivo) | `security/FileSecretStore.java` |
| R-04 | Baja | En CP-014 el operador podría validar a mano los dos eventos del par Gmail/Android y contar el pago dos veces; la interfaz muestra "Posible duplicado del evento #N" pero no lo bloquea. Además `LoggingSpeechEngine` (solo fuera de Windows) registra el texto hablado (banco e importe). | Pendiente (aceptado; DP-12) | `ui/viewmodel/ReviewViewModel.java`, `voice/LoggingSpeechEngine.java` |

### Revisión dirigida: conclusiones sin hallazgos

- **Logs sin secretos ni cuerpos:** ninguna llamada `log.*` recibe variables de cuerpo, texto, token, secreto, nonce o firma (escaneo `QaStaticSecurityScanTest`); la única excepción documentada es el motor de voz de desarrollo. Los eventos se registran con "externo: sí/no", nunca con el id.
- **Receptor Android:** lectura con tope real (`readCapped`, nunca confía en `Content-Length`), comparación HMAC/token con `MessageDigest.isEqual` sin cortocircuito, nonce de 16-128 caracteres con caché acotada que no expulsa vigentes, límite por IP con penalización por fallos de autenticación, bind por defecto 127.0.0.1, comodín `0.0.0.0` bloqueado, IP pública sin TLS rechazada, esquema estricto (sin campos desconocidos ni duplicados, profundidad 2), errores 4xx/5xx genéricos sin trazas (`erroresInternosNoFiltranTrazasNiRutasAlCliente`).
- **SQL:** todo `PreparedStatement`; las únicas concatenaciones son nombres de enum, constantes y marcadores `?` generados. El escaneo falla si aparece `execute*("..." + variable)`.
- **PowerShell y reg.exe:** argumentos en lista, script constante en `-EncodedCommand`, datos por stdin en Base64 (DPAPI) o campos Base64 (voz); sin interpolación de entrada no confiable.
- **Gmail:** alcance único `gmail.readonly`; sin llamadas de escritura (`send`, `modify`, `trash`, `drafts`); tokens solo en `SecretStore`; `client_secret*.json` está en `.gitignore` y se lee de ruta configurable fuera del repositorio.
- **RN-001 / RN-003 / RF-011:** el único origen de `VALIDADO_POR_FUENTE` es `ConservativeValidationPolicy` con opt-in explícito (`policy.automaticValidation=true` Y fuente en `policy.trustedSources`, ambos vacíos por defecto) o `ReviewService.validate` (manual, con actor y auditoría). `AnnouncementRequest` y `PolicyDecision` lanzan excepción si se intenta "pago confirmado" para un estado no confirmado.
- **RN-008:** ningún `DELETE`/`DROP`/`TRUNCATE` en el código ni en las migraciones; triggers impiden borrar/alterar eventos, movimientos y auditoría; descartar deja el evento RECHAZADO.
- **Archivos sensibles en git:** `git ls-files vivanet-pay | grep -iE 'secret|token|credential|\.db$|keystore|\.p12'` solo devuelve código fuente (`SecretStore*.java`, `WebhookSecretManager.java`, sus pruebas). No hay claves, bases de datos ni keystores versionados; los keystores de prueba se generan en tiempo de ejecución. `QaStaticSecurityScanTest.elRepositorioNoContieneSecretosNiArchivosSensibles` lo vigila. El archivo local `hs_err_pid9189.log` (volcado de fallo de la JVM) está ignorado por `.gitignore` y no contiene secretos; conviene borrarlo.

## 5. Pruebas manuales que debe hacer el propietario

### En Windows 10/11 (equipo real)

1. **Instalación y primer arranque (RF-001, RNF-007, RNF-010):** instalar el paquete, abrir la aplicación; el tablero debe abrir en menos de 3 s y mostrar el estado real de cada conector.
2. **Bandeja y segundo plano (CP-002, RF-002 a RF-004):** cerrar la ventana (debe minimizar a bandeja), abrir desde el icono, pausar/reanudar captura, silenciar voz, salir completamente con confirmación.
3. **Autoinicio (CP-002, H-05):** activar "Iniciar con Windows", abrir `regedit` en `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` y comprobar que el valor `VivanetPay` es exactamente `"C:\...\VivanetPay.exe" --minimized` (con comillas y sin truncar). Cerrar sesión, volver a entrar: debe iniciar minimizada y sin ventanas inesperadas. Desactivar y comprobar que el valor desaparece. Correr `mvn test` en Windows para ejecutar `windowsStartupRoundTrip`.
4. **Voz real (CP-011, RF-015, RF-016):** botón de prueba de voz, volumen y velocidad, silenciar con la ventana y desde la bandeja; confirmar la frase "Nueva notificación de Nequi por 125.000 pesos. Verifica el pago." y que la voz está en español. Con voz silenciada el movimiento sigue visible y aparece aviso visual.
5. **DPAPI (RNF-002):** ejecutar las pruebas `WindowsSecretStoreTest` en Windows (no se omiten); comprobar que en `%APPDATA%\VivanetPay\secrets` los archivos son `.dpapi` (ilegibles) y que no aparecen tokens en `%APPDATA%\VivanetPay\logs`.
6. **Respaldo y restauración (RNF-013):** comprobar que se crea la copia de inicio en `backups`; restaurar una copia siguiendo el procedimiento del manual; probar también con un usuario de Windows cuyo nombre tenga apóstrofo o tildes.
7. **Reinicio abrupto (CP-012):** con la aplicación abierta y movimientos cargados, terminar el proceso desde el Administrador de tareas (o apagar sin cerrar) y volver a abrir: historial, cursor y movimientos intactos, sin anuncios repetidos de eventos antiguos.
8. **Sin Internet (CP-013):** desconectar la red; el historial se sigue viendo, los conectores pasan a desconectado con mensaje claro; al reconectar se recupera solos y sin duplicados.
9. **Actualización y firma (RNF-015):** instalar una versión nueva sobre la anterior y verificar que conserva datos y que el instalador está firmado (según `docs/EMPAQUETADO.md`).
10. **Zona horaria (CP-015):** cambiar la zona horaria de Windows a otra y comprobar que "hoy" y las horas siguen en hora de Colombia; probar un movimiento cerca de las 00:00.

### Con el teléfono Android y MacroDroid

11. **Webhook válido (CP-007):** configurar la macro según `docs/MACRODROID.md` (HMAC o formulario) y enviar una transferencia de prueba pequeña de Nequi; el evento debe aparecer como "por verificar" en menos de 5 s y la voz decir "Nueva notificación... Verifica el pago", nunca "Pago recibido".
12. **Rechazos (CP-008, CP-009):** cambiar a propósito el secreto en la macro (debe rechazar y quedar auditado), reenviar la misma notificación (no se duplica) y apagar el Wi-Fi del teléfono para comprobar el reintento con el mismo `eventId`.
13. **Texto real con comillas/emojis/saltos de línea:** usar el formato formulario y comprobar que el texto llega íntegro.
14. **Red local (R-01, R-02):** si se usa IP LAN, comprobar que el puerto no está expuesto en el router y preferir HTTPS (`tls.keystore`).
15. **Plantillas reales de Nequi (CP-005):** reunir al menos 30 notificaciones reales (incluidas con separadores de miles raros) y medir la extracción; las que no se reconozcan deben ir a Pendientes.

### Con la cuenta Google real

16. **Autorización (CP-003):** autorizar una cuenta de prueba; la pantalla de Google debe pedir solo lectura del correo; el conector queda "conectado".
17. **Revocación (CP-004):** revocar desde la aplicación y desde `myaccount.google.com/permissions`; el conector pasa a "requiere autorización" sin bloquear la aplicación; volver a autorizar.
18. **Correos reales (CP-005, CP-006):** enviar o reenviar un correo de plantilla aprobada (campos extraídos y evento persistido) y uno de formato desconocido (queda pendiente, sin anuncio de confirmado); probar un correo con remitente falsificado (debe quedar con confianza baja).
19. **Primera sincronización:** con un buzón con historial, comprobar que no se anuncian por voz los correos antiguos.

### Aceptación (IDD 11.3, último criterio)

20. Con las pruebas anteriores, validar manualmente varios pagos en Pendientes contra la app/extracto oficial; confirmar que el total "Confirmado" solo cambia tras su validación y que la voz de "Pago recibido" solo suena si se activa `policy.announceOnManualValidation`. Aprobar o ajustar los mensajes de voz (IDD 9.3) y las decisiones DP-01 y DP-12.

## 6. Cómo repetir la auditoría

```bash
cd vivanet-pay
mvn -q -DbuildDir=target-qa clean test        # 276 pruebas, 5 omitidas solo en Linux/macOS
mvn -q -DbuildDir=target-qa test -Dtest='Qa*Test,RedactorTest,GmailOAuthSupportTest,BackupRestoreTest'
```

Clases nuevas de esta auditoría: `qa/QaPolicyInvariantsTest`, `qa/QaRobustnessTest`, `qa/QaDurabilityTest` (+ `AbruptWriterMain`),
`qa/QaTimeAndOutageTest`, `qa/QaStaticSecurityScanTest`, `integration/android/QaWebhookHardeningTest`.
