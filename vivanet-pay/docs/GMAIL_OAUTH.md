# Conector Gmail: credenciales de Google y autorización

Vivanet Pay lee correos bancarios con la API de Gmail usando OAuth 2.0 de "aplicación instalada" (PKCE + redirección
a `127.0.0.1` en un puerto aleatorio). Pide un único permiso: `https://www.googleapis.com/auth/gmail.readonly`
(solo lectura; la app no puede enviar, borrar ni modificar correos).

Para hablar con Google la app necesita un **archivo de credenciales** (`client_secret.json`, cliente OAuth tipo
"App de escritorio"). Es del **producto**, no de cada usuario: el proveedor lo crea una sola vez y lo distribuye con el
instalador. Para apps instaladas Google no considera confidencial ese `client_secret`; la seguridad la dan PKCE y la
redirección loopback. Aun así **no se sube al repositorio** (`.gitignore`).

La app busca las credenciales en este orden:

1. Archivo del usuario: `connector.gmail.clientSecretsPath` (por defecto `<carpeta de datos>/gmail/client_secret.json`;
   en Windows `C:\Users\<usuario>\AppData\Roaming\VivanetPay\gmail\client_secret.json`). Si existe, gana.
2. Recurso empaquetado en el instalador: `/vivanetpay/gmail/client_secret.json` (lo coloca el CI, ver A.4).
3. Ninguno: la app muestra un aviso con la ruta exacta y el botón **Cargar archivo de credenciales…**.

En ambos casos se valida que el JSON sea de tipo `installed` (App de escritorio); uno de tipo "web" se rechaza.

---

## A. Propietario / proveedor (una sola vez)

### A.1 Proyecto y API

1. Entra a <https://console.cloud.google.com/> y crea un proyecto (p. ej. "Vivanet Pay").
2. Menú **APIs y servicios > Biblioteca**: busca "Gmail API" y pulsa **Habilitar**.

### A.2 Pantalla de consentimiento

1. **APIs y servicios > Pantalla de consentimiento de OAuth** (en consolas nuevas: **Google Auth Platform**, con las
   secciones **Branding**, **Público** y **Acceso a los datos**).
2. Tipo de usuario / Público: **Externo**. Estado de publicación: **Prueba**.
3. Nombre de la app, correo de asistencia y correo de contacto del desarrollador.
4. **Acceso a los datos (Permisos)**: agrega únicamente `.../auth/gmail.readonly`.
5. **Usuarios de prueba**: agrega **tu propio correo** (y el de cada cuenta que vaya a autorizar; máx. 100).
   Mientras la app esté en **Prueba**, solo los usuarios de prueba pueden autorizar y **el token caduca a los 7 días**
   (la app mostrará "Requiere autorización" y habrá que pulsar **Autorizar Gmail** de nuevo).

### A.3 Credencial

1. **APIs y servicios > Credenciales > Crear credenciales > ID de cliente de OAuth**.
2. Tipo de aplicación: **App de escritorio**. Nombre: "Vivanet Pay".
3. Pulsa **Descargar JSON** (`client_secret_XXXX.json`).

### A.4 Incluirlo en el instalador (GitHub)

1. En el repositorio: **Settings > Secrets and variables > Actions > New repository secret**.
2. Nombre: `GMAIL_CLIENT_SECRET_JSON`. Valor: el contenido completo del JSON descargado.
3. El job "Instalador de Windows" lo escribe en `vivanet-pay/src/main/resources/vivanetpay/gmail/client_secret.json`
   antes de compilar, de modo que queda dentro del `.msi` y de la versión portable. Si el secreto no existe, el CI avisa
   con `::notice::` y el instalador se genera **sin** credenciales (el usuario deberá cargar su archivo). El job de
   pruebas no necesita el secreto. Para compilar localmente con credenciales, copia el JSON a esa ruta (git la ignora).

### A.5 Vender o instalar a terceros

`gmail.readonly` es un **scope restringido**:

- En modo **Prueba** solo funcionan los usuarios de prueba y los tokens caducan a los 7 días: sirve para ti, no para clientes.
- Para clientes reales hay que pasar a **En producción** y completar la **verificación de OAuth** de Google: política de
  privacidad pública, dominio verificado, justificación del scope, video de demostración y descripción del uso de los datos.
- Los scopes restringidos exigen además una **evaluación de seguridad anual (CASA)** por un evaluador autorizado, con costo
  y tiempo de revisión; planéala antes del lanzamiento comercial. Sin verificación los usuarios verán "Google no ha verificado esta app".
- Alternativa: que cada cliente cree su propio proyecto (Prueba con su cuenta, o "Interno" en Workspace) y cargue su archivo
  con **Cargar archivo de credenciales…**; evita la verificación central pero complica la instalación.
- Se debe cumplir la política de "Limited Use" de Google: los datos de Gmail solo se usan para detectar pagos entrantes,
  no se transfieren ni se usan para publicidad.

---

## B. Usuario final

- **Si el instalador ya trae las credenciales** (lo normal): en **Conectores**, tarjeta de Gmail, pulsa **Autorizar Gmail**,
  inicia sesión en Google y concede el permiso de solo lectura.
- **Si la app dice que falta el archivo de credenciales**: pulsa **Cargar archivo de credenciales…**, elige el archivo
  `client_secret….json` que te dio el proveedor (o que descargaste de Google Cloud, tipo "App de escritorio") y, al ver
  "Credenciales cargadas", pulsa **Autorizar Gmail**. La app valida el archivo y lo copia a su carpeta de datos
  (también puedes copiarlo a mano a la ruta indicada en el aviso). **Reemplazar credenciales…** sirve para cambiarlo después.
- "Ese archivo es de tipo Aplicación web": el cliente debe crearse como **App de escritorio**.
- Si Google dice "app no verificada" o "acceso bloqueado": tu correo debe estar como usuario de prueba (A.2) o la app verificada (A.5).

---

## Ajustes opcionales

En `config.properties` (prefijo `connector.gmail.`; también se acepta `gmail.`):

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


## Cómo funciona la autorización

1. En la pantalla de Conectores, pulsar **Autorizar Gmail**: se abre el navegador con la pantalla de consentimiento de Google.
2. Google redirige a `http://127.0.0.1:<puerto aleatorio>/` (servidor local temporal) con un código; la app lo canjea usando PKCE.
3. Los tokens se guardan **solo** en el `SecretStore` (almacén seguro del sistema; Credential Manager/DPAPI en Windows),
   nunca en texto plano, en logs ni en la base de datos.
4. Primera sincronización: los correos que cumplen la búsqueda se registran como **históricos** (no se anuncian por voz).
   Después se usa `users.history.list` con el `historyId` como cursor; si Google lo expira (404) se resincroniza con una
   búsqueda acotada. El cursor solo avanza después de que el evento quedó persistido.

Estados: `Requiere autorización` (sin tokens, 401, `invalid_grant`, revocado), `Desconectado` (sin red; se reintenta con
backoff), `Conectado` (con hora de la última sincronización). Un fallo de Gmail nunca bloquea el resto de la app.

## Revocar el acceso

- Desde la app: **Revocar Gmail** llama al endpoint de revocación de Google, borra los tokens locales y el cursor.
- Manual (por si la app no puede contactar a Google): <https://myaccount.google.com/permissions> > Vivanet Pay > **Quitar acceso**.

