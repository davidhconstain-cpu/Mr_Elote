# Decisiones pendientes (IDD §16)

Este documento resume las decisiones DP-01 a DP-12 del [IDD v1.0 §16](IDD_v1.0.md) y dice qué valor **provisional** usa hoy el código. Ningún valor provisional cierra la decisión: cada uno es un valor por defecto que permite probar la aplicación mientras el propietario del producto decide.

Cómo leer la tabla:

- **Valor provisional**: lo que hace el código hoy, con la clave de configuración o la clase donde vive.
- **Al decidir**: qué habría que cambiar una vez tomada la decisión. Si dice "solo configuración", basta con cambiar `config.properties`; si dice "código", hace falta un cambio en Java o en el esquema.

Fuentes del valor provisional: `docs/ARQUITECTURA.md` (tabla de decisiones provisionales), `app/AppConfig.java` (javadoc con las claves) y los archivos de configuración de cada conector.

---

## Resumen

| ID | Tema | Prioridad IDD | Valor provisional (resumen) | Estado |
|---|---|---|---|---|
| DP-01 | Evidencia de "pago confirmado" | Crítica | Ningún pago automático se confirma; solo la validación manual | Abierta |
| DP-02 | Alcance de bancos/correos en Gmail | Alta | Nequi y Bancolombia por correo; sin DaviPlata ni Davivienda | Abierta |
| DP-03 | Red del Android y del PC | Crítica | Solo red local (LAN); IP fija configurada a mano | Abierta |
| DP-04 | Servicio intermedio en la nube | Crítica | No existe; el receptor es local | Abierta |
| DP-05 | Almacenamiento local o cloud | Alta | Solo local, con respaldo al iniciar en el mismo disco | Abierta |
| DP-06 | Multiempresa | Alta | Una instalación = un negocio; sin campo de empresa en la base | Abierta |
| DP-07 | Licenciamiento | Alta | Prueba local de 14 días; sin validación en línea | Abierta |
| DP-08 | Formato de exportación | Media | Solo CSV | Abierta |
| DP-09 | Contenido y voz de los anuncios | Media | Monto y entidad; sin remitente; volumen 80, velocidad 0 | Abierta |
| DP-10 | Retención de datos, logs y respaldos | Alta | Eventos sin borrado; logs 30 días / 300 MB; respaldos sin límite | Abierta |
| DP-11 | Canal de instalación | Media | Instalador `.exe`/`.msi` sin firmar, por usuario | Abierta |
| DP-12 | Correcciones y anulaciones | Alta | Cualquiera con nombre puede validar o descartar; sin corregir importes | Abierta |

---

## DP-01. Fuente de validación ("pago confirmado")

**Pregunta IDD:** ¿qué evidencia permitirá decir "pago confirmado": comprobación manual en la app, notificación oficial con criterios estrictos u otra integración autorizada?

**Valor provisional**

- `policy.automaticValidation=false` (`app/AppConfig.java`; por defecto en `validation/ConservativeValidationPolicy.java`).
- `policy.trustedSources=` (vacío). Si se llena, los eventos de esas fuentes pueden pasar a `VALIDADO_POR_FUENTE` sin revisión manual.
- `policy.minConfidence=0.9`: por debajo, el evento va a revisión.
- `policy.announceUnconfirmed=true`: los posibles pagos se anuncian como "Nueva notificación… Verifica el pago."
- `policy.announceOnManualValidation=false`: al validar a mano no se anuncia por voz.
- En la práctica, un pago pasa a **Confirmado** solo cuando alguien lo valida en la pantalla Pendientes (`application/ReviewService.validate`).

**Al decidir**

- Solo configuración si la evidencia elegida es una fuente que ya existe (por ejemplo, incluir `ANDROID_NEQUI` en `policy.trustedSources`, o activar `policy.automaticValidation`). Antes conviene probar con eventos reales.
- Código si aparece una evidencia nueva (por ejemplo, un comprobante oficial): hay que añadir la fuente en `domain/EventSource.java` y la regla en `ConservativeValidationPolicy`.
- Revisar el texto del tablero "Confirmado hoy" (ver nota de inconsistencias en `README.md`), porque describe la confirmación de forma que depende de esta decisión.

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

## DP-03. Red del Android y del PC

**Pregunta IDD:** ¿el Android y el PC estarán normalmente en la misma red Wi-Fi, o deben funcionar desde cualquier lugar?

**Valor provisional**

- El receptor escucha solo en `127.0.0.1` (`connector.android.bindAddress=127.0.0.1`). Para recibir desde el celular hay que añadir la IP LAN del PC a mano, por ejemplo `127.0.0.1,192.168.1.20`.
- Un `bindAddress` con IP pública sin TLS no arranca; con IP privada sin TLS arranca con advertencia (`integration/android/AndroidWebhookConnector.java`, prueba `direccionPublicaSinTlsNoArranca`).
- `0.0.0.0` y `::` están bloqueados salvo `connector.android.allowWildcardBind=true`.
- Documentado en `docs/MACRODROID.md` (la guía dice expresamente que no se debe abrir puertos en el router).

**Al decidir**

- Si basta con la misma Wi-Fi: solo documentación y, si se quiere, un asistente de configuración de la IP.
- Si hace falta fuera de la red local: depende de DP-04 (servicio intermedio). No se resuelve solo con configuración.

---

## DP-04. Receptor webhook y servicio intermedio en la nube

**Pregunta IDD:** ¿se acepta un pequeño servicio intermedio en la nube para que el Android alcance al PC fuera de la red local?

**Valor provisional**

- No existe ningún servicio intermedio en el código. El receptor HTTP(S) es local y el PC no es accesible desde Internet.
- El contrato `POST /v1/events` es el mismo en cualquier caso (`tools/macrodroid/README.md`), así que un intermediario podría reenviar al PC sin cambiar la app de MacroDroid.

**Al decidir**

- Código nuevo fuera de este repositorio (o en otro módulo) para el intermediario, con su propia autenticación, TLS y registro de auditoría.
- Revisar `docs/ARQUITECTURA.md`: su tabla dice "Sin receptor Android en el núcleo", lo cual ya no es correcto (ver inconsistencias).

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

## DP-08. Formato de exportación

**Pregunta IDD:** ¿CSV, Excel, PDF o una combinación?

**Valor provisional**

- Solo **CSV**, con BOM UTF-8 y protección contra inyección de fórmulas (`reporting/CsvExporter.java`). Botón "Exportar CSV…" en el Historial.
- No hay Excel ni PDF. Ojo: `docs/ARQUITECTURA.md` dice "Sin exportación en el núcleo", pero la exportación ya existe (ver inconsistencias).

**Al decidir**

- Excel o PDF: código nuevo (no hay librería de hojas de cálculo en `pom.xml`; añadir una dependencia requiere coordinarlo, ver la nota de versiones en `ARQUITECTURA.md`).

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

## Decisión adicional: modo de autenticación del webhook Android

No está en la lista DP-01..DP-12, pero afecta directamente a DP-03 y DP-04 y a la seguridad del canal Android.

**Valor por defecto del código: `hmac`.**

- `integration/android/AuthMode.java`: si `connector.android.auth` está vacío, el modo es `HMAC`. El mismo valor se aplica en `AndroidWebhookConfig`.
- En modo `hmac`, el emisor firma cada solicitud con HMAC-SHA256 (timestamp, nonce y cuerpo). El secreto no viaja.

**Lo que necesita MacroDroid: `token`.**

- MacroDroid no calcula HMAC, así que la guía (`docs/MACRODROID.md`) exige `connector.android.auth=token` en `config.properties`. Sin esa línea, MacroDroid recibe 401 en cada aviso.
- En modo `token`, el secreto viaja en cada solicitud (`Authorization: Bearer <secreto>`). `AuthMode.java` lo documenta como trade-off: un atacante con acceso a la red podría enviar eventos falsos. Mitigaciones: secreto de 256 bits, rotación, límite de tasa, bind local y TLS opcional.

**Consecuencias y decisiones abiertas**

1. Un instalador nuevo con MacroDroid falla hasta que alguien edita `config.properties`. Hoy no hay asistente que lo haga; la pantalla Conectores solo muestra el modo en uso.
2. Si se quiere que el modo `token` sea el predeterminado para instalaciones de MacroDroid, hay que cambiar el valor por defecto en `AuthMode.parse` y en `AndroidWebhookConfig.from`, y revisar la prueba `comodinBloqueadoPorDefectoYNombresNoPermitidos` y las de autenticación del conector.
3. Si se prefiere mantener `hmac` por defecto, la alternativa es un emisor que sí firme (Tasker, HTTP Shortcuts o un puente propio). Esto también encaja con DP-04, porque un intermediario en la nube sería el lugar natural para firmar.
4. Mientras no se decida, la guía de MacroDroid debe seguir indicando la línea `connector.android.auth=token` de forma explícita.
