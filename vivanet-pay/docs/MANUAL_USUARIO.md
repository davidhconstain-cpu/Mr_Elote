# Manual de usuario de Vivanet Pay

Este manual es para el dueño o la persona encargada de caja. Explica qué hace Vivanet Pay, cómo se usa cada pantalla y cómo revisar los pagos que la aplicación no puede confirmar por sí sola.

---

## 1. Qué hace Vivanet Pay

Vivanet Pay corre en su computador con Windows. Busca avisos de transferencias entrantes y los organiza:

- **Notificaciones de Nequi** que llegan al celular Android de la caja (con la ayuda de la app MacroDroid).
- **Correos** de Nequi y Bancolombia que llegan a una cuenta de Gmail autorizada.

Por cada aviso, Vivanet Pay guarda el monto, la entidad, el remitente y la referencia cuando el aviso los trae. Después lo muestra en el **Tablero** y en el **Historial**, y puede anunciarlo por voz.

### Lo que Vivanet Pay NO hace

- **No confirma por sí sola que el dinero llegó.** Una notificación o un correo no es un comprobante bancario.
- **No mueve dinero** ni hace transferencias.
- **No reemplaza el extracto** ni la conciliación bancaria.
- **No pide** su clave de Nequi, su PIN, ni su contraseña de Gmail o de su banco.

Por eso, antes de entregar un producto por un pago, confirme siempre el dinero en la app de Nequi o en su banco.

---

## 2. Instalación

1. Ejecute el instalador de Vivanet Pay que le entregó el proveedor (`VivanetPay.exe` o `.msi`).
2. El instalador se instala solo para su usuario de Windows; no pide permisos de administrador.
3. Si Windows muestra un aviso de **SmartScreen** ("Windows protegió su PC"), confirme que el instalador viene del proveedor antes de continuar.
4. Abra Vivanet Pay desde el menú Inicio o el acceso directo.

La primera vez que abre la aplicación, comienza una **prueba gratuita de 14 días**, sin tarjeta ni medio de pago. En **Diagnóstico** aparece cuántos días faltan y hasta qué fecha dura. Cuando la prueba vence, la aplicación muestra un aviso en pantalla. Para activar la licencia, comuníquese con el proveedor.

Sus datos (la base de datos, los registros y los respaldos) se guardan en `%APPDATA%\VivanetPay`, fuera de la carpeta del programa. Actualizar o desinstalar Vivanet Pay **no borra** esos datos.

---

## 3. Primer uso

1. **Abra Vivanet Pay.** Se abre en el **Tablero**.
2. **Inicio automático** (opcional): vaya a **Configuración** y marque *"Iniciar Vivanet Pay al iniciar sesión en Windows"*. Así la aplicación arranca sola cada vez que enciende el computador, en segundo plano.
3. **Conectores**: el proveedor debe dejar listos los conectores (Gmail y/o Android). Gmail y Android arrancan por defecto (Gmail queda en *Requiere autorización* hasta que lo autorice). Si la pantalla dice *"No hay conectores activos"*, alguien los desactivó en la configuración; comuníquese con el proveedor.
4. **Voz**: vaya a **Voz** y pulse **Probar voz**. Debe escuchar una frase de prueba. Si no suena, revise el volumen de Windows y la sección 9.

---

## 4. Las pantallas

La columna izquierda tiene el menú con las pantallas. Están en este orden: **Tablero, Historial, Pendientes, Conectores, Voz, Configuración y Diagnóstico**.

### 4.1 Tablero

Es la pantalla de inicio. Muestra el día de hoy según la hora de Colombia:

- **Confirmado hoy**: total y cantidad de pagos confirmados automáticamente por una fuente verificada o revisados por usted.
- **Por verificar hoy**: total y cantidad de posibles pagos que aún no están confirmados.
- **Pendientes**: cuántos avisos esperan su revisión. Con el botón **Ir a Pendientes** pasa directamente a esa pantalla.
- **Último evento**: el aviso más reciente.
- **Desglose por entidad**: una tabla con cada banco o entidad y sus dos columnas, *Confirmado* y *Por verificar*.
- **Conectores**: el estado de cada fuente (Gmail, Android).
- **Última sincronización**: cuándo se revisó por última vez el correo.

> Los totales "Por verificar" no son dinero recibido. Son avisos pendientes de comprobar.

### 4.2 Historial

Es la lista completa de movimientos. Puede filtrar por:

- **Fecha** (desde y hasta).
- **Fuente**: Gmail, Android (Nequi), Android (otra app), Manual, o todas.
- **Estado**: *Solo confirmados*, *Solo por verificar*, o un estado específico.
- **Monto mínimo y máximo**. Puede escribirlos como `125000` o `$125.000`.
- **Remitente o referencia**.

Pulse **Buscar** para aplicar los filtros y **Limpiar filtros** para quitarlos. Al seleccionar una fila, el panel de detalle muestra el estado, el motivo, el monto, la entidad, el remitente, la referencia, la fuente y las fechas del pago y de recepción.

Para llevarse la lista a una hoja de cálculo, pulse **Exportar a Excel…** y elija el formato en la ventana de guardado: **Excel (.xlsx)** (encabezado en negrita, montos como número con formato `#,##0`) o **CSV separado por punto y coma**. Ambos conservan las tildes y los montos son números enteros que Excel puede sumar. Los textos que empiezan por `=`, `+`, `-` o `@` se guardan como texto, nunca como fórmula.

### 4.3 Pendientes

Aquí aparecen los avisos que la aplicación no pudo confirmar, o que no pudo leer del todo. Cada uno muestra:

- **Por qué está pendiente** (el motivo).
- **Datos extraídos**: monto, entidad, remitente, referencia, fuente y fecha.
- **Texto original** del aviso o del correo.
- **Validaciones**: las reglas que se revisaron.

Los botones se explican en la sección 6.

### 4.4 Conectores

Muestra una tarjeta por cada fuente activa, con su estado y lo que debe hacer usted:

- **Gmail**: botones **Autorizar Gmail** (o **Volver a autorizar**), **Pausar** o **Reanudar**, **Revocar acceso** y **Cargar archivo de credenciales…** (**Reemplazar credenciales…** si ya hay uno). Si su instalador ya trae las credenciales de Google, solo pulse **Autorizar Gmail** e inicie sesión en el navegador. Si la aplicación dice que falta el archivo de credenciales, pulse **Cargar archivo de credenciales…**, elija el archivo `client_secret….json` que le entregó el proveedor (o que descargó de Google Cloud) y luego pulse **Autorizar Gmail**. Pasos completos en [`GMAIL_OAUTH.md`](GMAIL_OAUTH.md).
- **Android (Nequi)**: botones **Probar recepción**, **Mostrar secreto**, **Rotar secreto**, **Revocar secreto**, **Pausar** o **Reanudar**. Para configurar el celular, vea [`MACRODROID.md`](MACRODROID.md).

Los posibles estados de un conector son:

| Estado | Qué significa | Qué hacer |
|---|---|---|
| Conectado | Recibe avisos con normalidad. | Nada. |
| Desconectado | Sin conexión a Internet o el celular no alcanza el computador. | Revise Internet o el celular. Vivanet Pay vuelve a intentarlo solo. |
| Requiere autorización | La cuenta de Gmail dejó de autorizarse. | Pulse **Autorizar** y siga los pasos en el navegador. |
| Error | Falló algo técnico. | Revise **Diagnóstico**. Si sigue, envíe el paquete de diagnóstico al proveedor. |
| Pausado | La captura está en pausa. | Pulse **Reanudar**. |

### 4.5 Voz

- **Anunciar pagos por voz**: encendido o apagado. Si lo apaga, Vivanet Pay sigue registrando los pagos, pero no habla.
- **Silenciar ahora**: silencia la voz de inmediato, sin cambiar la configuración. Vuelva a pulsarlo para activarla.
- **Probar voz**: reproduce una frase de prueba.
- **Volumen**: de 0 a 100 %.
- **Velocidad**: de -10 a 10. El 0 es la velocidad normal.

La pantalla también indica el motor de voz que usa el equipo.

### 4.6 Configuración

- **Iniciar Vivanet Pay al iniciar sesión en Windows**: arranca la aplicación con Windows, minimizada en la bandeja.
- **Al cerrar la ventana, seguir funcionando en la bandeja del sistema**: si está marcada, la X de la ventana solo oculta la aplicación y sigue detectando pagos (ver sección 7).
- **Zona horaria** (America/Bogota) y **Moneda** (COP): solo lectura. Vivanet Pay siempre trabaja en pesos colombianos y hora de Colombia.

### 4.7 Diagnóstico

Pantalla para revisar el estado técnico y para copias de seguridad:

- Versión de la aplicación, licencia y prueba.
- Estado de la base de datos y **Verificar integridad**.
- **Último error registrado** (ya sin datos sensibles).
- **Abrir carpeta de logs**.
- **Hacer respaldo ahora** y **Restaurar respaldo…** (sección 8).
- **Exportar paquete de diagnóstico…**: crea un archivo ZIP con la versión, el estado de los conectores y los registros, con tokens, claves y correos enmascarados. No incluye la base de datos. Envíelo solo al soporte del proveedor.

---

## 5. Pago confirmado y posible pago: ¿cuál es la diferencia?

Vivanet Pay separa dos cosas que pueden parecerse:

| Lo que ve en pantalla | Qué significa | Cómo se ve |
|---|---|---|
| **Confirmado** | Un pago que **Vivanet Pay confirmó automáticamente** porque viene de una fuente verificada (aviso de Nequi por el canal autenticado del celular, o un correo de Nequi/Bancolombia con DKIM y SPF/DMARC válidos), o que usted revisó y validó en Pendientes. | Aparece en el Tablero como *Confirmado hoy*. La voz dice *"Pago recibido por … pesos desde …"*. |
| **Por verificar** | Un posible pago. Vivanet Pay recibió el aviso, pero no tiene evidencia suficiente para afirmar que el dinero llegó. | Aparece en el Tablero como *Por verificar hoy*, con el estado *Por verificar* o *Pendiente de revisión*. |

Otros estados que puede ver en el Historial:

- **Duplicado**: el mismo aviso llegó dos veces (por ejemplo, un reenvío de MacroDroid). No se cuenta ni se anuncia.
- **Rechazado**: el aviso no cumple las reglas, por ejemplo, un monto de cero o en otra moneda. O usted lo descartó en Pendientes.
- **Error**: falló algo técnico. Revise **Diagnóstico**.

### ¿Por qué una notificación no basta?

- Las transferencias entre cuentas Nequi **no siempre generan un correo**. Por eso el aviso llega del celular, y un celular puede mostrar una notificación que no corresponde a un abono real.
- Una notificación puede ser una prueba, un reenvío, una notificación de otra cuenta o una transacción que después se revierte.
- El dinero se da por recibido cuando lo confirma su banco o Nequi, no cuando llega el aviso.

Por eso, cuando el aviso **no** cumple las reglas de una fuente verificada (formato dudoso, confianza baja, posible duplicado, correo sin autenticación válida), la voz dice *"Nueva notificación de [entidad] por [monto] pesos. Verifica el pago."* y **no** dice *"Pago recibido"*. La frase *"Pago recibido"* solo se usa para pagos confirmados.

**Riesgo que usted aceptó:** la confirmación automática se basa en una notificación o un correo, que **no es un comprobante bancario**; revise su app de Nequi o banco ante cualquier duda. Si prefiere confirmar cada pago a mano, desactive *Configuración → Confirmar automáticamente pagos de fuentes verificadas*. Los avisos históricos (sincronización inicial de Gmail) pueden quedar confirmados pero nunca se anuncian por voz.

---

## 6. Cómo revisar los pendientes

Revise la pantalla **Pendientes** al menos una vez al día, o cuando el Tablero indique pendientes.

1. Seleccione el aviso en la lista.
2. Lea el **motivo** y los **datos extraídos**.
3. **Revise el dinero en su banco o en Nequi** (en el historial de la app, no en la notificación).
4. Elija una acción:

| Botón | Cuándo usarlo | Qué pasa |
|---|---|---|
| **Validar (ya lo vi en mi banco)** | Confirmó el abono en su banco o en Nequi. | El pago pasa a **Confirmado**. Queda registrado quién lo validó. |
| **Descartar…** | El aviso no corresponde a un pago real, o es un duplicado. | Le pedirá un **motivo** obligatorio. El aviso queda como **Rechazado** y no se borra. |
| **Mantener pendiente** | Aún no puede comprobarlo. | El aviso sigue en Pendientes. Queda constancia de la revisión. |

Notas importantes:

- Un aviso **sin monto** no se puede validar. Si el texto no trae el monto, use **Descartar** o **Mantener pendiente**.
- Validar y descartar **no se pueden deshacer desde la aplicación**. Quedan registrados en la auditoría.
- Si detecta un error en el monto, no hay forma de corregirlo en la aplicación. Descarte el aviso con el motivo *"monto incorrecto"* y anote el pago correcto en su registro habitual.

---

## 7. Silenciar la voz y usar la bandeja

### Silenciar la voz

- Desde **Voz**: pulse **Silenciar ahora**.
- Desde la bandeja del sistema: clic derecho sobre el icono de Vivanet Pay → **Silenciar voz**. Para volver a activarla, **Activar voz**.
- Para apagarla de forma permanente: en **Voz**, desmarque **Anunciar pagos por voz**.

Mientras la voz está silenciada, Vivanet Pay sigue registrando los pagos y muestra un aviso visual.

### La bandeja del sistema

El icono de Vivanet Pay está junto al reloj de Windows. Desde su menú puede:

- **Abrir Vivanet Pay**: muestra la ventana.
- **Pausar captura** / **Reanudar captura**: detiene o reanuda la detección de pagos en todos los conectores.
- **Silenciar voz** / **Activar voz**.
- **Estado de conectores**: el estado de cada fuente.
- **Salir**: cierra la aplicación por completo. Le pedirá confirmación, porque mientras esté cerrada **no se detectarán pagos**.

Si en **Configuración** dejó marcado *"seguir funcionando en la bandeja"*, al pulsar la X de la ventana la aplicación se oculta, pero **sigue funcionando**. Esto es lo normal.

---

## 8. Respaldo y restauración

### Respaldos automáticos

Cada vez que abre Vivanet Pay, la aplicación guarda una copia de la base de datos en la carpeta `backups` dentro de `%APPDATA%\VivanetPay`. Estas copias **no se borran solas**: revise el espacio en disco de vez en cuando.

### Respaldo manual

1. Vaya a **Diagnóstico**.
2. Pulse **Hacer respaldo ahora**. Le indicará dónde quedó el archivo.

### Copiar los respaldos fuera del computador

Los respaldos están en el **mismo disco** que la aplicación. Si el disco falla, se pierden. Recomendamos copiar la carpeta `backups` a una memoria USB o a un disco externo cada semana.

### Restaurar un respaldo

1. Vaya a **Diagnóstico** → **Restaurar respaldo…** y elija el archivo `.db`.
2. Lea la advertencia: los datos actuales se **reemplazan** por los del respaldo. Los movimientos posteriores a esa copia **se pierden**.
3. Confirme. Vivanet Pay se detiene y se cierra.
4. Vuelva a abrir la aplicación.

Antes de restaurar, haga un respaldo de la situación actual para no perder nada.

---

## 9. Problemas frecuentes

**No suena la voz.**
Revise que *Anunciar pagos por voz* esté marcado y que la voz no esté silenciada (en **Voz** y en la bandeja). Revise el volumen de Windows y pulse **Probar voz**. Si la prueba falla, abra **Diagnóstico** y revise el último error.

**Un pago que sí llegó aparece como "Por verificar".**
Es lo esperado. Vivanet Pay no confirma pagos por sí sola. Revise el dinero en su banco o en Nequi y pulse **Validar** en **Pendientes**.

**Un mismo pago aparece dos veces.**
Vivanet Pay lo detecta como **Duplicado** cuando llega con el mismo identificador. Si aparece dos veces como *Por verificar* (por ejemplo, desde Gmail y desde el celular), uno queda en **Pendientes** como posible duplicado. Descarte el que sobra con el motivo *"duplicado"*.

**El celular no envía avisos.**
Revise que el celular y el computador estén en la **misma red Wi-Fi**, que el computador esté encendido y que MacroDroid no esté cerrado por el ahorro de batería. El proveedor puede ayudarle con la configuración. Detalles en [`MACRODROID.md`](MACRODROID.md).

**Gmail dice "Requiere autorización".**
Pulse **Autorizar Gmail** en **Conectores** y vuelva a iniciar sesión en Google. Si primero pide el archivo de credenciales, use **Cargar archivo de credenciales…**. Si la app de Google está en modo *Prueba*, la autorización caduca cada 7 días. Vea [`GMAIL_OAUTH.md`](GMAIL_OAUTH.md).

**Gmail o Android aparece como "Desconectado".**
Es temporal. Revise Internet o el celular; la aplicación reintenta sola. Si lleva mucho tiempo desconectado, use **Probar recepción** (Android) o revise **Diagnóstico**.

**Un aviso aparece en Pendientes y no trae monto o entidad.**
El banco cambió el formato del aviso, o no es uno de los que Vivanet Pay reconoce. Lea el motivo en el panel de detalle. Revise el texto original y decida: validar (si confirmó el dinero), descartar o mantener pendiente.

**Aparece la alerta "la conexión de [fuente] está desconectada".**
Vivanet Pay avisa por voz y en pantalla. Siga la tabla de la sección 4.4.

**Pasó la prueba de 14 días.**
Vivanet Pay muestra un aviso. Comuníquese con el proveedor para activar la licencia.

---

## 10. Privacidad y seguridad

- Vivanet Pay **nunca pide** la clave de Nequi, el PIN, ni la contraseña de Gmail o del banco. Si alguien le pide esos datos en nombre de Vivanet Pay, no los entregue y avise al proveedor.
- Para Gmail, la autorización se hace en la **página de Google**. El permiso que se pide es **solo de lectura**: Vivanet Pay no puede enviar, borrar ni modificar correos. Además, solo procesa los correos de los remitentes configurados.
- El **secreto del celular** (el que se muestra en **Conectores → Android**) es como una contraseña. No lo envíe por WhatsApp. Si cree que alguien lo vio, pulse **Rotar secreto** (el anterior sigue valiendo unos minutos) o **Revocar secreto** (deja de funcionar al instante).
- Los registros (logs) ocultan tokens, claves y correos. El paquete de diagnóstico no incluye la base de datos.
- Si cambia de equipo o pierde el celular, revise **Conectores** y revoque el acceso correspondiente.

Para revocar el acceso de Gmail también puede entrar a <https://myaccount.google.com/permissions>, buscar Vivanet Pay y pulsar **Quitar acceso**.

---

## 11. Preguntas frecuentes

**¿Puedo exportar los movimientos a Excel?**
Sí. En el Historial, **Exportar a Excel…** guarda un archivo `.xlsx` o un CSV separado por punto y coma. No hay exportación a PDF.

**¿Hasta cuándo se guardan los movimientos?**
Los movimientos no se borran desde la aplicación. Los registros (logs) se conservan 30 días. Los respaldos se conservan hasta que los borre manualmente. La política final de conservación está pendiente de decisión.

**¿La aplicación necesita Internet?**
Sí, para recibir correos de Gmail. El celular y el computador deben estar en la misma red Wi-Fi para el canal Android.

**¿Qué pasa si el computador se apaga?**
Mientras esté apagado, Vivanet Pay no recibe avisos. Los correos de Gmail que llegaron en ese tiempo se leen al volver a iniciar la aplicación. Los avisos del celular que no alcanzaron el computador dependen de que MacroDroid los reintente (ver [`MACRODROID.md`](MACRODROID.md)). **No anuncia por voz los avisos antiguos** (de más de 15 minutos).

**¿Por qué los importes no tienen decimales?**
Vivanet Pay trabaja en pesos enteros, como `$125.000`. Un aviso con centavos no se redondea: pasa a revisión.

**¿Puedo cambiar la voz o el remitente en el anuncio?**
El volumen y la velocidad se cambian en **Voz**. El remitente no se anuncia por defecto. Cambiar la voz o el contenido del anuncio es una configuración técnica; consulte al proveedor.

---

## 12. Cuándo llamar al proveedor

- Un conector sigue en **Error** después de revisar **Diagnóstico**.
- Hay un problema de integridad de la base de datos.
- Necesita activar la licencia.
- Necesita cambiar un conector, un remitente o la configuración técnica.

Antes de llamar, tenga a la mano la **versión** de Vivanet Pay (en **Diagnóstico**) y, si el proveedor lo pide, el **paquete de diagnóstico**. El soporte nunca le pedirá contraseñas, PIN ni acceso remoto a su equipo sin su autorización.
