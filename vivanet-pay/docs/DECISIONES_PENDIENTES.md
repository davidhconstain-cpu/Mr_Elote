# Decisiones pendientes (IDD §16)

Este documento resume las decisiones DP-01 a DP-12 del [IDD v1.0 §16](IDD_v1.0.md). El **24/11/2026** el propietario decidió DP-01, DP-03, DP-04 y DP-08 y el modo de autenticación del webhook (marcadas **DECIDIDA** más abajo). Para las demás, el valor **provisional** es solo un valor por defecto que permite probar la aplicación mientras el propietario decide.

Cómo leer la tabla:

- **Valor provisional**: lo que hace el código hoy, con la clave de configuración o la clase donde vive.
- **Al decidir**: qué habría que cambiar una vez tomada la decisión. Si dice "solo configuración", basta con cambiar `config.properties`; si dice "código", hace falta un cambio en Java o en el esquema.

Fuentes del valor provisional: `docs/ARQUITECTURA.md` (tabla de decisiones provisionales), `app/AppConfig.java` (javadoc con las claves) y los archivos de configuración de cada conector.

---

## Resumen

| ID | Tema | Prioridad IDD | Valor provisional (resumen) | Estado |
|---|---|---|---|---|
| DP-01 | Evidencia de "pago confirmado" | Crítica | Confirmación automática de fuentes verificadas (Nequi por webhook autenticado; Gmail con DKIM+SPF/DMARC); el resto, manual | **Decidida (24/11/2026)** |
| DP-02 | Alcance de bancos/correos en Gmail | Alta | Nequi y Bancolombia por correo; sin DaviPlata ni Davivienda | Abierta |
| DP-03 | Red del Android y del PC | Crítica | Misma red Wi‑Fi; el usuario elige su IP privada en Conectores | **Decidida (24/11/2026)** |
| DP-04 | Servicio intermedio en la nube | Crítica | No habrá servicio en la nube; receptor local | **Decidida (24/11/2026)** |
| DP-05 | Almacenamiento local o cloud | Alta | Solo local, con respaldo al iniciar en el mismo disco | Abierta |
| DP-06 | Multiempresa | Alta | Una instalación = un negocio; sin campo de empresa en la base | Abierta |
| DP-07 | Licenciamiento | Alta | Prueba local de 14 días; sin validación en línea | Abierta |
| DP-08 | Formato de exportación | Media | CSV con `;` y Excel `.xlsx` | **Decidida (24/11/2026)** |
| DP-09 | Contenido y voz de los anuncios | Media | Monto y entidad; sin remitente; volumen 80, velocidad 0 | Abierta |
| DP-10 | Retención de datos, logs y respaldos | Alta | Eventos sin borrado; logs 30 días / 300 MB; respaldos sin límite | Abierta |
| DP-11 | Canal de instalación | Media | Instalador `.exe`/`.msi` sin firmar, por usuario | Abierta |
| DP-12 | Correcciones y anulaciones | Alta | Cualquiera con nombre puede validar o descartar; sin corregir importes | Abierta |

---

## DP-01. Fuente de validación ("pago confirmado") — DECIDIDA (24/11/2026)

**Pregunta IDD:** ¿qué evidencia permitirá decir "pago confirmado": comprobación manual en la app, notificación oficial con criterios estrictos u otra integración autorizada?

**Decisión del propietario:** confirmación automática con fuentes de confianza (la opción más cómoda). **Riesgo aceptado:** una notificación de Android o un correo **no es un comprobante bancario**; un aviso falso o un secreto robado podría confirmarse como pago. Mitigación: reglas estrictas, registro completo, interruptor para volver al modo manual y la advertencia en Configuración ("revisa tu app ante dudas").

**Regla exacta (`validation/TrustedSourceValidationPolicy`, id `fuentes-verificadas-dp01-v1`)**. Un evento pasa a `VALIDADO_POR_FUENTE` (voz "Pago recibido por X pesos desde <entidad>.") solo si, además de pasar las reglas generales (formato, importe > 0, COP, sin advertencias, **sin posible duplicado/ambigüedad**, confianza ≥ `policy.minConfidence` = 0,9):

- **ANDROID_NEQUI** (regla `AUTO_ANDROID_NEQUI`): llegó por el webhook **autenticado** (cabecera interna `webhook.auth`, que solo pone el receptor tras autenticar), parser `nequi-android-v1` con confianza ≥ 0,9, importe > 0 y, si el celular reporta el paquete, este contiene "nequi".
- **GMAIL** (regla `AUTO_GMAIL_DKIM`): el remitente es de un dominio esperado de la entidad detectada (Nequi: `nequi.com.co`; Bancolombia: `bancolombia.com.co`, incluidos subdominios) **y** `Authentication-Results` (o, si falta, `ARC-Authentication-Results`) emitido por Google indica `dkim=pass` alineado con ese dominio y (`spf=pass` o `dmarc=pass`) alineado; un `dmarc=fail` lo anula; las cabeceras de otro servidor se ignoran; parser de correo con confianza ≥ 0,9. **Sin cabecera de autenticación no se confirma.**
- En cualquier otro caso rige el flujo conservador (`PARSEADO` "por verificar", `PENDIENTE_REVISION` o `RECHAZADO`; voz "Nueva notificación… Verifica el pago.").
- **Históricos** (sincronización inicial de Gmail) pueden quedar confirmados pero **nunca** se anuncian por voz.
- **CP-014:** si un evento ya confirmado automáticamente coincide con otro de otra fuente (mismo importe y remitente en la ventana), el segundo va a revisión como posible duplicado: no se confirma ni se suma dos veces (`TrustedSourcePolicyTest.duplicadoEntreFuentesVaARevisionSinDobleConteo`).
- **Trazabilidad:** la tabla `validation` guarda `POLITICA:AUTO_ANDROID_NEQUI` o `POLITICA:AUTO_GMAIL_DKIM` con actor `SISTEMA`; `audit_log` registra el cambio de estado con la regla en el motivo. Cambiar el interruptor queda en `audit_log` (`SETTING_CHANGE`).

**Cómo volver al modo conservador:** `policy.automaticValidation=false` en `config.properties`, o el interruptor *Configuración → Confirmar automáticamente pagos de fuentes verificadas* (activado por defecto; se aplica a los eventos siguientes). Con `false`, solo la validación manual confirma, como antes.

**Valores por defecto actuales:** `policy.automaticValidation=true`, `policy.trustedSources=ANDROID_NEQUI,GMAIL`, `policy.minConfidence=0.9`, `policy.announceUnconfirmed=true`, `policy.announceOnManualValidation=false`.

**Texto del Tablero:** la tarjeta "Confirmado hoy" dice "Confirmados automáticamente por fuente verificada o revisados por ti."

---

## DP-02. Alcance de bancos y correos en Gmail

**Pregunta IDD:** ¿qué bancos y correos se procesarán en el MVP, además de estudiar el correo disponible de Nequi?

**Valor provisional**

- Remitentes permitidos por defecto: `nequi.com.co` y `bancolombia.com.co` (`integration/gmail/GmailSettings.java`, constante `DEFAULT_SENDERS`; clave `connector.gmail.senders`).
- Búsqueda inicial por defecto: `from:(<remitentes>) newer_than:2d` (clave `connector.gmail.query`).
- Parsers registrados (`parsing/DefaultParsers.java`): `NequiPushParser`, `NequiEmailParser`, `BancolombiaEmailParser`.
- DaviPlata y Davivienda no tienen parser. Si se añaden sus remitentes a `connector.gmail.senders`, sus correos no se reconocen y pasan a revisión manual (`parsing/UnrecognizedFallbackParser.java`); no se anuncian como confirmados.

**Al decidir**

- Solo configuración para cambiar remitentes o la ventana de búsqueda (`connector.gmail.senders`, `connector.gmail.query`).
- Código para añadir un banco: un nuevo `PaymentParser`, sus fixtures en `src/test/resources/fixtures/` y registrarlo en `DefaultParsers`.

---

## DP-03. Red del Android y del PC — DECIDIDA (24/11/2026)

**Pregunta IDD:** ¿el Android y el PC estarán normalmente en la misma red Wi-Fi, o deben funcionar desde cualquier lugar?

**Decisión del propietario:** **misma red Wi‑Fi**. El receptor sigue escuchando solo en `127.0.0.1` por defecto; el usuario lo habilita desde la pantalla Conectores.

- Conectores → Android → **«Recibir desde mi teléfono en esta Wi‑Fi»**: detecta las IPv4 privadas de las interfaces activas (`integration/android/LanAddresses`; excluye loopback, link-local, CGNAT/VPN, interfaces apagadas y virtuales como Docker, WSL, Hyper‑V, VirtualBox, VMware, VPN, Tailscale, ZeroTier), deja elegir una, guarda `connector.android.bindAddress=127.0.0.1,<ip>` de forma atómica en `config.properties` (`AppConfig.update`), reinicia el conector (`Bootstrap.restartConnector`) y muestra la URL exacta `http://<ip>:<puerto>/v1/events` con botón **Copiar**. Si el receptor no logra escuchar en esa IP, se restaura la configuración anterior. «Volver a solo este equipo» deshace el cambio.
- Se mantiene el rechazo de IP pública sin TLS y de `0.0.0.0`/`::` (salvo `allowWildcardBind=true`). La advertencia de "sin HTTPS en red local" se muestra como nota informativa, no como alarma.
- El Firewall de Windows no se toca desde la app: `docs/MACRODROID.md` documenta el comando `netsh advfirewall firewall add rule ... profile=private localport=<puerto>`.
- Limitación conocida: si el router cambia la IP del PC, hay que repetir «Usar esta red» (conviene reservar la IP en el router).

---

## DP-04. Receptor webhook y servicio intermedio en la nube — DECIDIDA (24/11/2026)

**Pregunta IDD:** ¿se acepta un pequeño servicio intermedio en la nube para que el Android alcance al PC fuera de la red local?

**Decisión del propietario:** **no** habrá servicio en la nube. El celular y el PC deben estar en la misma Wi‑Fi (ver DP-03); con datos móviles o en otra red el celular no entrega avisos. El contrato `POST /v1/events` queda estable (`tools/macrodroid/README.md`), por si en el futuro se reabre la decisión.

---

## DP-05. Almacenamiento local o sincronización cloud

**Pregunta IDD:** ¿la base será exclusivamente local o se requiere sincronización/backup cloud desde la primera versión?

**Valor provisional**

- Solo local: SQLite en `<datos>/vivanetpay.db`.
- Respaldo al iniciar (`backup.onStart=true`) en `<datos>/backups/`, en el mismo disco (`persistence/DatabaseMaintenance.java`).
- No hay código de sincronización. Los únicos accesos a Internet son Gmail (OAuth y API de Google).

**Al decidir**

- Si se pide copia fuera del equipo: código nuevo (no configuración) y revisión de privacidad (DP-10) porque sube datos personales.

---

## DP-06. Multiempresa

**Pregunta IDD:** ¿cada instalación representa un único negocio/cuenta, o se venderá a múltiples negocios con datos aislados?

**Valor provisional**

- Una instalación = un negocio. El esquema (`src/main/resources/db/migration/V1__init.sql`) no tiene ningún campo de empresa, cuenta ni inquilino.
- Los datos viven en el directorio del usuario de Windows (`%APPDATA%\VivanetPay`).

**Al decidir**

- Si se elige multiempresa: migración nueva (`V2__...sql`, agregada a `migrations.idx`) con identificador de negocio en las tablas, más aislamiento en todas las consultas de `persistence/`. Es un cambio de código y de esquema, no de configuración.
- Si se elige una instalación por negocio: no hace falta cambiar nada; conviene documentarlo en la licencia (DP-07).

---

## DP-07. Licenciamiento

**Pregunta IDD:** definir precio, duración posterior a la prueba, renovación, modo sin conexión, activación y política de reinstalación.

**Valor provisional**

- Prueba local de 14 días (`licensing/LicenseService.java`, `TRIAL_DURATION`), iniciada en la primera ejecución. Sin validación en línea.
- Tolerancia de reloj de 10 minutos (`CLOCK_TOLERANCE`). Si el reloj retrocede más, la licencia queda en `SOSPECHOSO` de forma persistente.
- `license.enforce=false` (`app/AppConfig.java`): la licencia solo informa. Con `true`, la captura no arranca si la prueba venció o está sospechosa.
- Al vencer, la aplicación muestra un aviso y el diagnóstico indica "Contacta al proveedor para activar la licencia" (no hay pantalla de activación).

**Al decidir**

- Precio, duración y renovación: decisión comercial; no cambia el código hasta que haya un mecanismo de activación.
- Activación, reinstalación y validación en línea: código nuevo. Cambiar `license.enforce` a `true` solo tiene sentido cuando exista activación.

---

## DP-08. Formato de exportación — DECIDIDA (24/11/2026)

**Pregunta IDD:** ¿CSV, Excel, PDF o una combinación?

**Decisión del propietario:** CSV con punto y coma y Excel `.xlsx`.

- CSV (`reporting/CsvExporter`): separador `;` por defecto (`export.csv.separator`: `;`, `,`, `|` o `tab`), BOM UTF-8, CRLF, neutralización de fórmulas, montos como entero sin separador de miles.
- Excel (`reporting/XlsxExporter`): `.xlsx` generado a mano (OOXML mínimo con `java.util.zip`; **sin dependencias nuevas**, así no se toca `maven-enforcer` ni el empaquetado): encabezado en negrita y fila fija, autofiltro, anchos de columna, ID e importe numéricos (`#,##0`), fechas como texto (America/Bogota), todo texto como cadena en línea (nunca fórmula; los que empiezan por `= + - @` llevan además `quotePrefix`), caracteres no válidos de XML 1.0 eliminados. Se descartó fastexcel/POI: para una sola hoja plana no compensan el tamaño ni el riesgo de versión.
- Historial → **Exportar a Excel…** abre el selector con filtros `.xlsx` y `.csv`.
- No hay PDF (no se pidió).

---

## DP-09. Contenido y voz de los anuncios

**Pregunta IDD:** ¿anunciar monto y banco solamente, o también remitente? ¿Qué voz y volumen por defecto?

**Valor provisional**

- Monto y entidad; **sin remitente** (`voice.includeSender=false`).
- Volumen 80 (`voice.volume`), velocidad 0 en escala de -10 a 10 (`voice.rate`), voz predeterminada del sistema (`voice.name` vacío).
- Frases en `voice/AnnouncementMessages.java`, según IDD §9.3.
- Anuncio de posible pago: "Nueva notificación de [entidad] por [monto] pesos. Verifica el pago."

**Al decidir**

- Solo configuración para remitente, volumen, velocidad y voz (`voice.includeSender`, `voice.volume`, `voice.rate`, `voice.name`).
- Cambiar el texto de las frases: cambio de código en `AnnouncementMessages`.

---

## DP-10. Retención de eventos, datos personales, logs y respaldos

**Pregunta IDD:** ¿cuánto tiempo conservar eventos, datos personales, logs y respaldos?

**Valor provisional**

- **Eventos y movimientos**: no se borran nunca. Triggers de SQLite impiden el `DELETE` (RN-008, `V1__init.sql`; prueba `noSePermiteBorradoFisicoNiImportesNoPositivosRN008`).
- **Logs**: `src/main/resources/logback.xml`: 10 MB por archivo, `maxHistory` de 30 días, tope total de 300 MB. El propio archivo marca la retención como provisional.
- **Respaldos**: se crean al iniciar y desde Diagnóstico; **no hay borrado automático**.
- **Paquete de diagnóstico**: redacta tokens, claves y correos (`ui/DiagnosticsPackage.java`).

**Al decidir**

- Logs: solo configuración (`logback.xml`, requiere reempaquetar).
- Respaldos: hace falta código para la política de borrado (hoy no existe).
- Eventos: un cambio de retención implica revisar los triggers de RN-008 y una migración; es una decisión legal y de auditoría, no técnica.

---

## DP-11. Canal de instalación

**Pregunta IDD:** ¿distribución directa con instalador firmado, Microsoft Store u otro canal?

**Valor provisional**

- Instalador generado con `jpackage` (`docs/EMPAQUETADO.md`): `.exe` o `.msi`, `--win-per-user-install` (sin administrador; datos en `%APPDATA%\VivanetPay`).
- Sin firmar; `EMPAQUETADO.md` advierte que SmartScreen lo bloqueará hasta firmarlo.

**Al decidir**

- Firma de código con `signtool`: proceso y certificado (no es cambio de código).
- Microsoft Store: empaquetado MSIX distinto; código de empaquetado nuevo.

---

## DP-12. Correcciones y anulaciones

**Pregunta IDD:** ¿quién puede corregir o anular un movimiento, y qué evidencia y auditoría se exige?

**Valor provisional** (`application/ReviewService.java`)

- Validar exige un **actor no vacío** (texto libre, sin roles) y un importe ya extraído. Un evento sin importe no se puede confirmar (`"corrección pendiente DP-12"`).
- Descartar exige **motivo** y deja el evento en `RECHAZADO` (no se borra).
- Mantener pendiente solo deja constancia en la auditoría.
- No hay forma de **corregir importes**. Un evento ya confirmado (`ANUNCIADO`) solo puede pasar a `RECHAZADO` según la máquina de estados (`domain/EventStatus.java`), sin acción de corrección en la app.
- Todo queda en `audit_log`, que no admite borrado ni modificación (RN-008).

**Al decidir**

- Roles (quién puede validar o anular): código nuevo, porque hoy el actor es texto libre.
- Corrección de importes: código nuevo, con una nueva transición de estado y su evidencia en auditoría.

---

## Decisión adicional: modo de autenticación del webhook Android — DECIDIDA (24/11/2026)

**Decisión del propietario: el modo por defecto es `token`.** `AuthMode.parse` y `AndroidWebhookConfig.from` devuelven `TOKEN` cuando `connector.android.auth` está vacío; `hmac` sigue disponible con `connector.android.auth=hmac` (las pruebas del modo HMAC lo fijan explícitamente; `AuthModeDefaultTest` cubre el valor por defecto).

- Con `token`, MacroDroid funciona sin editar `config.properties` (`Authorization: Bearer <secreto>`).
- Trade-off aceptado: el secreto viaja en cada solicitud; en red local sin HTTPS un atacante en la misma Wi‑Fi podría capturarlo y, con la confirmación automática (DP-01), inyectar avisos que se confirmarían. Mitigaciones: secreto de 256 bits, rotación/revocación desde Conectores, límite de tasa, escucha solo en la IP elegida, TLS opcional y la posibilidad de desactivar la confirmación automática.

---

## Notas de las correcciones finales

- **Conectores por defecto:** `connectors.enabled` pasó de vacío a `gmail,android` (`AppConfig.DEFAULT_CONNECTORS`). Gmail sin credenciales queda en `REQUIERE_AUTORIZACION` sin tocar la red; Android escucha solo en `127.0.0.1`. `connectors.enabled=none` no arranca ninguno (un valor en blanco vuelve al valor por defecto).
- **Doble conteo (R-04, relacionado con DP-12):** `ReviewService.validate` rechaza confirmar un evento enlazado como posible duplicado (en cualquier sentido) de otro ya confirmado, salvo que se indique un motivo de "pago distinto", que queda en `validation` (`MANUAL_PAGO_DISTINTO`) y en `audit_log` (`DUPLICATE_OVERRIDE`). Pendientes ofrece "Descartar como duplicado". Quién puede autorizar ese override sigue dentro de DP-12.
