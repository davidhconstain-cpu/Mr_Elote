# Conectar el celular Android (Nequi + MacroDroid) con Vivanet Pay

Esta guía explica, paso a paso, cómo hacer que el celular donde llegan las notificaciones de **Nequi** avise
automáticamente a **Vivanet Pay** en el computador. No necesita conocimientos técnicos; siga los pasos en orden.

> **Importante:** una notificación de Nequi **no es prueba de que el dinero llegó**. Vivanet Pay la muestra como
> "Nueva notificación… verifica el pago". Confirme siempre en la app de Nequi antes de entregar el producto.

---

## Antes de empezar

Necesita:

1. El **celular Android** con la app Nequi donde llegan las notificaciones de pagos.
2. La app **MacroDroid** (gratis en Google Play).
3. El **computador** con Vivanet Pay abierto.
4. Que el celular y el computador estén conectados a **la misma red Wi-Fi** del negocio.

> **Nunca abra ni "redireccione" este puerto en el router (módem) hacia Internet.** El receptor está pensado
> solo para su red Wi-Fi privada. Si el celular y el computador van a estar en redes distintas (por ejemplo, el
> celular con datos móviles), **no** lo resuelva abriendo puertos: esa opción todavía está en evaluación
> (decisiones DP-03/DP-04) y requiere un servicio intermedio seguro.

---

## Paso 1. Preparar Vivanet Pay en el computador

1. Abra Vivanet Pay y vaya a **Conectores → Android**.
2. Active el conector Android. Si le pide permiso el **Firewall de Windows**, permita el acceso **solo en redes
   privadas** (no marque "redes públicas").
3. Anote la **dirección del receptor** que muestra la pantalla, por ejemplo:
   `http://192.168.1.20:47390/v1/events`
   - Si solo ve `127.0.0.1`, el receptor todavía no escucha en la red Wi-Fi. Pida a quien instaló el programa que
     configure la IP del computador (ver "Configuración técnica" al final).
4. Pulse **Mostrar secreto** y cópielo. Es una clave larga (unas 43 letras y números).
   - Trátelo como una contraseña: no lo envíe por WhatsApp ni lo publique.
5. Pulse **Probar recepción**. Debe decir "Prueba recibida correctamente". Esto **no** crea pagos.

---

## Paso 2. Dar permisos a MacroDroid en el celular

1. Instale **MacroDroid** desde Google Play y ábralo.
2. **Acceso a notificaciones:** MacroDroid lo pedirá al crear el disparador. Si no, vaya a
   *Ajustes del celular → Aplicaciones → Acceso especial → Acceso a notificaciones* y active **MacroDroid**.
3. **Quitar el ahorro de batería (muy importante, evita que MacroDroid se "duerma"):**
   - *Ajustes → Aplicaciones → MacroDroid → Batería* → elija **Sin restricciones** (o "No optimizar").
   - En Xiaomi/Redmi: además active **Inicio automático** y en *Ahorro de batería* elija **Sin restricciones**.
   - En Samsung: *Ajustes → Mantenimiento/Batería → Límites de uso en segundo plano* → quite MacroDroid de
     "Aplicaciones en suspensión" y agréguelo a **"Aplicaciones que nunca se suspenden"**.
   - En Huawei/Honor: *Batería → Inicio de aplicaciones* → MacroDroid en **Administrar manualmente** con todo activado.
   - Si existe la opción, **bloquee** MacroDroid en la lista de apps recientes (candado) para que no se cierre.
4. Verifique que las **notificaciones de Nequi estén activadas** (*Ajustes → Aplicaciones → Nequi → Notificaciones*).

---

## Paso 3. Crear la variable contador

Sirve para que cada aviso tenga un **identificador único**, así Vivanet Pay nunca cuenta dos veces el mismo pago.

1. En MacroDroid toque **Variables** (o *Variables globales*) → **+**.
2. Nombre: `vivanet_seq` · Tipo: **Entero** · Valor inicial: `0`.

---

## Paso 4. Crear la macro

1. Toque **Añadir macro** y póngale de nombre: `Vivanet Pay - Nequi`.

### Disparador

2. **+ Disparador → Dispositivo/Aplicaciones → Notificación recibida** (o "Notificación").
3. Seleccione **solo la app Nequi**. (No elija "todas las apps": Vivanet Pay solo acepta avisos de Nequi.)
4. Contenido del texto: **Cualquiera** (Vivanet Pay se encarga de reconocer los pagos).

### Acciones (en este orden)

5. **+ Acción → Variables → Fijar valor de variable** → `vivanet_seq` → **Incrementar en 1**.
6. **+ Acción → Conectividad → Solicitud HTTP** (HTTP Request) y configure:
   - **Método:** `POST`
   - **URL:** la dirección del Paso 1, por ejemplo `http://192.168.1.20:47390/v1/events`
   - **Tipo de contenido / Content-Type:** `application/json`
   - **Cabeceras (Headers):** agregue una:
     - Nombre: `Authorization`
     - Valor: `Bearer ` seguido del secreto copiado (con un espacio después de *Bearer*). Ejemplo:
       `Bearer AbC123...` (el suyo será distinto).
   - **Cuerpo (Body):** copie el contenido del archivo `tools/macrodroid/payload-template.json`:

     ```json
     {
       "schemaVersion": 1,
       "eventId": "md-[system_time]-[v=vivanet_seq]",
       "source": "ANDROID_NEQUI",
       "app": "Nequi",
       "title": "[notification_title]",
       "text": "[notification_text]",
       "capturedAt": "[system_time]",
       "device": "Celular caja"
     }
     ```

     - Lo que está entre corchetes `[...]` son **"textos mágicos"** de MacroDroid: se reemplazan solos por el
       título, el texto y la hora de la notificación. Si su versión de MacroDroid usa otro nombre, bórrelo y
       elíjalo con el botón **"..." / Texto mágico** (opciones *Título de la notificación*, *Texto de la
       notificación*, *Hora del sistema* y la variable *vivanet_seq*).
     - Puede cambiar `"Celular caja"` por el nombre que quiera (sin comillas dentro).
   - **Tiempo de espera:** 10–30 segundos. Si su versión permite **reintentar** en caso de fallo, actívelo: es
     seguro, porque el reintento envía el mismo `eventId` y Vivanet Pay no lo duplica.
7. Guarde la macro y verifique que esté **activada**.

### Si las notificaciones traen comillas (`"`) o varias líneas: use el formato de formulario

MacroDroid pega el texto de la notificación tal cual dentro del JSON. Si el texto contiene comillas dobles, una
barra invertida o un salto de línea (por ejemplo, un nombre como `JUAN "EL PAISA" PÉREZ`), el JSON queda roto y
Vivanet Pay responde **400** (se pierde el aviso). Para evitarlo, Vivanet Pay acepta **el mismo mensaje** como
formulario `application/x-www-form-urlencoded`, donde cada valor viaja codificado y las comillas no rompen nada:

- **Content-Type:** `application/x-www-form-urlencoded`
- **Autenticación:** idéntica (`Authorization: Bearer <secreto>`).
- **Cuerpo:** si su versión de MacroDroid ofrece enviar el cuerpo como **pares clave/valor de formulario**
  (*Form URL Encoded*), agregue estos campos con los mismos textos mágicos y deje que MacroDroid los codifique:

  | Clave | Valor |
  |---|---|
  | `schemaVersion` | `1` |
  | `eventId` | `md-[system_time]-[v=vivanet_seq]` |
  | `source` | `ANDROID_NEQUI` |
  | `app` | `Nequi` |
  | `title` | `[notification_title]` |
  | `text` | `[notification_text]` |
  | `capturedAt` | `[system_time]` |
  | `device` | `Celular caja` |

  Si solo puede escribir el cuerpo como texto, use una sola línea (plantilla en
  `tools/macrodroid/payload-template.form.txt`). Ojo: en texto plano el formulario se rompe con `&`, `%` y `+`
  dentro de la notificación, y MacroDroid no los codifica por usted; por eso se prefiere el modo clave/valor.
- Mismas reglas que con JSON: mismos campos (sin campos extra ni repetidos), mismo tope de tamaño (413), misma
  validación (400 ante cualquier error) y misma idempotencia por `eventId`. No se relaja ninguna comprobación.
- Verifíquelo con la macro de prueba del Paso 5 (agregue `test` = `true`) y con una notificación real que lleve
  comillas. Si el JSON le funciona bien, no necesita cambiar nada.

---

## Paso 5. Probar sin crear pagos

1. Cree una segunda macro temporal con la misma acción **Solicitud HTTP**, pero con el cuerpo del archivo
   `tools/macrodroid/payload-prueba.json` (tiene `"test": true`) y dispárela manualmente (*Probar acciones*).
2. Resultado esperado: código **200** y `test_ok`. En Vivanet Pay, *Conectores → Android* aumenta el contador de
   pruebas. **No se registra ningún pago.**
3. Cuando reciba una transferencia real pequeña por Nequi, verifique que aparezca en el tablero como
   "por verificar".

### Si algo falla (código que muestra MacroDroid)

| Código | Qué significa | Qué hacer |
|---|---|---|
| (sin respuesta) / tiempo agotado | El celular no alcanza el computador. | Misma Wi-Fi; IP y puerto correctos; permitir Vivanet Pay en el Firewall (redes privadas); computador encendido. |
| 401 | Secreto incorrecto o vencido. | Copie de nuevo el secreto (puede haber sido rotado) y revise el espacio después de `Bearer`. |
| 400 | El cuerpo no es válido. | Revise que copió la plantilla completa. Si la notificación trae comillas `"`, MacroDroid rompe el JSON: use el formato de formulario (ver Paso 4). |
| 413 | Mensaje demasiado grande. | No agregue campos; use solo la plantilla. |
| 415 | Falta el tipo `application/json`. | Configure el Content-Type. |
| 429 | Demasiados envíos seguidos. | Espere un minuto. Revise que la macro no se dispare en bucle. |
| 503 | Vivanet Pay está en **pausa** u ocupado. | Reanude la captura en Vivanet Pay. |
| 200 `duplicate` | Ese aviso ya se había recibido. | Normal en reintentos; no se cuenta dos veces. |

---

## Cambiar el secreto (rotación)

Hágalo si cambió de celular, si alguien más vio el secreto o periódicamente:

1. En Vivanet Pay: **Conectores → Android → Rotar secreto**. Copie el nuevo.
2. El secreto anterior sigue funcionando unos minutos (periodo de gracia, 10 min por defecto) para que alcance a
   actualizar MacroDroid.
3. En MacroDroid, edite la cabecera `Authorization` con `Bearer <nuevo secreto>`.
4. Si sospecha que alguien lo robó, use **Revocar secreto**: el anterior deja de funcionar **de inmediato**.

---

## Recomendaciones de seguridad

- Use una red Wi-Fi **con contraseña** y que no sea pública ni de invitados.
- **No** abra puertos en el router ni use servicios para "publicar" el puerto en Internet.
- No comparta el secreto. Vivanet Pay nunca lo escribe en los registros (logs).
- Vivanet Pay **nunca** pide la clave de Nequi, el PIN ni contraseñas bancarias.
- Si el celular se pierde: **Revocar secreto** en Vivanet Pay.

---

## Configuración técnica (para quien instala)

Archivo `config.properties` en la carpeta de datos (`%APPDATA%\VivanetPay`):

```properties
connectors.enabled=android
connector.android.auth=token                       # MacroDroid no calcula HMAC; por defecto es hmac
connector.android.bindAddress=127.0.0.1,192.168.1.20 # IP LAN FIJA del PC (reserve la IP en el router)
connector.android.port=47390
# HTTPS opcional (recomendado fuera de una Wi-Fi de confianza):
# connector.android.tls.keystore=C:\\Users\\<usuario>\\AppData\\Roaming\\VivanetPay\\receptor.p12
```

- Por defecto el receptor escucha **solo en 127.0.0.1** (no accesible desde el celular). La IP LAN debe
  añadirse explícitamente. `0.0.0.0` está bloqueado salvo `connector.android.allowWildcardBind=true`.
- Con una IP de red privada **sin** HTTPS el receptor arranca con una **advertencia** visible en *Conectores*.
  Con una IP pública sin HTTPS **no arranca**.
- HTTPS: genere un certificado con
  `keytool -genkeypair -alias vivanet -keyalg EC -groupname secp256r1 -validity 825 -dname "CN=Vivanet Pay" -ext SAN=ip:192.168.1.20 -storetype PKCS12 -keystore receptor.p12`
  y guarde la contraseña del archivo en el almacén seguro con la clave `android.android-principal.tls_keystore_password`.
  MacroDroid debe confiar en ese certificado (instalarlo en el celular como certificado de usuario).
- Modo `hmac`: para emisores que puedan firmar (Tasker, HTTP Shortcuts, un puente propio). Ver
  `tools/macrodroid/README.md` y `tools/macrodroid/probar-receptor.ps1`.

### Por qué existe el modo `token` (y su costo)

MacroDroid no puede calcular firmas HMAC, así que envía el secreto en la cabecera `Authorization` en cada aviso.
Consecuencias: si alguien en la misma red captura un aviso (más fácil sin HTTPS), podría reenviarlo —Vivanet Pay
no lo cuenta dos veces gracias al `eventId`— o usar el secreto para enviar avisos falsos. Por eso: el secreto es
aleatorio de 256 bits, se puede rotar/revocar, hay límite de envíos por IP, el receptor solo escucha en la red
local y los avisos de Android **nunca** se dan por pagos confirmados sin verificación. El modo `hmac` evita que
el secreto viaje y bloquea reenvíos con nonce y ventana de 5 minutos.
