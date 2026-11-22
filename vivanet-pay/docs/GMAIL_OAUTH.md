# Conector Gmail: configuración de OAuth

Vivanet Pay lee correos bancarios con la API de Gmail usando OAuth 2.0 de "aplicación instalada" (PKCE + redirección
a `127.0.0.1` en un puerto aleatorio). Pide un único permiso: `https://www.googleapis.com/auth/gmail.readonly`
(solo lectura; la app no puede enviar, borrar ni modificar correos).

El `client_id`/`client_secret` **no están en el repositorio**: cada instalación (o el proveedor del producto)
los obtiene en Google Cloud y los coloca en un archivo externo.

## 1. Crear el proyecto en Google Cloud

1. Entra a <https://console.cloud.google.com/> y crea un proyecto (p. ej. "Vivanet Pay").
2. **APIs y servicios > Biblioteca**: busca "Gmail API" y pulsa **Habilitar**.
3. **APIs y servicios > Pantalla de consentimiento de OAuth**:
   - Tipo de usuario: **Externo** (o Interno si usas Google Workspace y solo tu organización).
   - Nombre de la app, correo de soporte y de contacto del desarrollador.
   - **Permisos (scopes)**: agrega únicamente `.../auth/gmail.readonly`.
   - **Usuarios de prueba**: mientras la app esté en modo "Prueba", agrega las cuentas Gmail que podrán autorizar
     (máximo 100). En modo prueba los tokens de actualización caducan a los 7 días (la app mostrará
     "Requiere autorización" y habrá que volver a autorizar).
4. **APIs y servicios > Credenciales > Crear credenciales > ID de cliente de OAuth**:
   - Tipo de aplicación: **App de escritorio**.
   - Descarga el JSON (`client_secret_XXXX.json`).

## 2. Dónde poner el archivo de credenciales

Por defecto: `<directorio de datos>/gmail/client_secret.json`
(el directorio de datos es el de la aplicación, fuera de la carpeta de instalación).

Para usar otra ruta, en `config.properties`:

```properties
connectors.enabled=gmail,android
connector.gmail.clientSecretsPath=C:/ProgramData/VivanetPay/secrets/client_secret.json
```

Otros ajustes (todos opcionales, prefijo `connector.gmail.`; también se acepta `gmail.`):

| Clave | Por defecto | Descripción |
|---|---|---|
| `clientSecretsPath` | `<datos>/gmail/client_secret.json` | Archivo descargado de Google Cloud. |
| `senders` | `nequi.com.co,bancolombia.com.co` | Dominios/direcciones permitidas. Filtro local de la sincronización incremental (el cuerpo de otros remitentes no se descarga). |
| `query` | `from:(<senders con OR>) newer_than:2d` | Búsqueda de Gmail para la sincronización inicial y la resincronización. |
| `pollSeconds` | `60` | Intervalo de sondeo (mínimo 5). |
| `initialMax` | `50` | Máximo de correos en la sincronización inicial. |
| `backoffBaseSeconds` / `backoffMaxSeconds` | `5` / `900` | Backoff exponencial con jitter ante fallos. |

No subas `client_secret*.json` al repositorio (ya está en `.gitignore`).

## 3. Cómo funciona la autorización

1. En la pantalla de Conectores, pulsar **Autorizar Gmail**: se abre el navegador con la pantalla de consentimiento de Google.
2. Google redirige a `http://127.0.0.1:<puerto aleatorio>/` (servidor local temporal) con un código; la app lo canjea usando PKCE.
3. Los tokens se guardan **solo** en el `SecretStore` (almacén seguro del sistema; Credential Manager/DPAPI en Windows),
   nunca en texto plano, en logs ni en la base de datos.
4. Primera sincronización: los correos que cumplen la búsqueda se registran como **históricos** (no se anuncian por voz).
   Después se usa `users.history.list` con el `historyId` como cursor; si Google lo expira (404) se resincroniza con una
   búsqueda acotada. El cursor solo avanza después de que el evento quedó persistido.

Estados: `Requiere autorización` (sin tokens, 401, `invalid_grant`, revocado), `Desconectado` (sin red; se reintenta con
backoff), `Conectado` (con hora de la última sincronización). Un fallo de Gmail nunca bloquea el resto de la app.

## 4. Revocar el acceso

- Desde la app: **Revocar Gmail** llama al endpoint de revocación de Google, borra los tokens locales y el cursor.
- Manual (por si la app no puede contactar a Google): <https://myaccount.google.com/permissions> > Vivanet Pay > **Quitar acceso**.

## 5. Verificación de OAuth y distribución comercial (R-06)

`gmail.readonly` es un **scope restringido**. Implicaciones:

- Con la app en modo **Prueba** solo funcionan los usuarios de prueba listados y los tokens caducan a los 7 días.
- Para distribuir a clientes reales hay que publicar la app ("En producción") y pasar la **verificación de OAuth** de Google:
  política de privacidad pública, dominio verificado, justificación del scope, video de demostración y descripción del uso de los datos.
- Los scopes restringidos pueden exigir además una **evaluación de seguridad anual (CASA)** por un evaluador autorizado, con costo y tiempo
  de revisión; planearlo antes del lanzamiento comercial. Mientras no se verifique, los usuarios verán la advertencia "Google no ha verificado esta app".
- Alternativa de modelo: que cada cliente cree su propio proyecto de Google Cloud (modo "Interno" en Workspace, o Prueba con su cuenta);
  evita la verificación central pero complica la instalación.
- Se debe cumplir la política de "Limited Use" de Google: los datos de Gmail solo se usan para detectar pagos entrantes, no se transfieren ni se usan para publicidad.

## 6. Limitación importante: Nequi puede no enviar correo (IDD 2.3, R-01)

Las transferencias entre cuentas Nequi **no necesariamente generan un correo por cada operación**. Por eso Gmail no es una fuente
garantizada de todos los pagos Nequi: la ruta prevista para esas notificaciones es el conector Android (MacroDroid). Gmail sirve
para entidades que sí envían correo (p. ej. Bancolombia, Davivienda, DaviPlata, sujetos a validación con correos reales).
Verifica con tu cuenta qué correos llegan antes de depender de este canal.

## 7. Datos que se leen y envían al núcleo

Solo mensajes de los remitentes configurados: texto (text/plain, o HTML convertido a texto), y las cabeceras `From`, `Subject`,
`Date`, `Message-ID`, `Reply-To`, `Return-Path`, `Authentication-Results`, `ARC-Authentication-Results` y `Received-SPF`
(para evaluar autenticidad, IDD 8.2). Los adjuntos se ignoran.
