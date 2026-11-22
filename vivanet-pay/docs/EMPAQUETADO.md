# Empaquetado de Vivanet Pay para Windows (jpackage)

Requisitos en una máquina **Windows 10/11 x64**: JDK 21 (incluye `jpackage` y `jlink`), Maven 3.9+ y,
para generar `.exe`/`.msi`, [WiX Toolset 3.x](https://wixtoolset.org/) en el `PATH`.
`jpackage` no puede generar instaladores de Windows desde Linux.

## 1. Compilar y reunir dependencias (con JavaFX de Windows)

```bat
cd vivanet-pay
mvn -DskipTests -Djavafx.platform=win clean package dependency:copy-dependencies -DoutputDirectory=target\libs
copy target\vivanet-pay-0.1.0-SNAPSHOT.jar target\libs\
```

(Con `-DbuildDir=target-xxx` si compila en paralelo con otros agentes; ajuste las rutas.)
Las dependencias de `org.openjfx` deben ser las de clasificador `win`; por eso `-Djavafx.platform=win`.

## 2. Instalador `.exe` (o `.msi`) con runtime incluido

```bat
jpackage ^
  --type exe ^
  --name VivanetPay ^
  --app-version 0.1.0 ^
  --vendor "Vivanet" ^
  --description "Vivanet Pay: avisos de transferencias entrantes" ^
  --input target\libs ^
  --main-jar vivanet-pay-0.1.0-SNAPSHOT.jar ^
  --main-class com.vivanetpay.ui.Launcher ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --java-options "-Dvivanet.app=packaged" ^
  --win-menu --win-menu-group "Vivanet Pay" ^
  --win-shortcut ^
  --win-per-user-install ^
  --win-dir-chooser ^
  --dest target\dist
```

- Cambie `--type exe` por `--type msi` para un MSI. `--win-per-user-install` evita pedir administrador
  (instala en `%LOCALAPPDATA%`), recomendado porque la aplicación guarda sus datos por usuario.
- No se necesita `--module-path`: `Launcher` es una clase sin herencia de `Application`, por lo que JavaFX corre desde
  el classpath. El runtime incluido lo crea `jpackage` (jlink) automáticamente; si desea uno mínimo añada
  `--add-modules java.base,java.desktop,java.logging,java.naming,java.sql,java.xml,jdk.unsupported,jdk.crypto.ec`
  (verifique con la aplicación empaquetada: Gmail necesita TLS/EC y SQLite necesita `java.sql`).
- Icono opcional: `--icon packaging\vivanetpay.ico`.

El instalador crea `VivanetPay.exe` (el launcher). Los **datos** (base, logs, respaldos, `config.properties`)
viven en `%APPDATA%\VivanetPay`, no en la carpeta de instalación, así que actualizar o desinstalar no los borra.

## 3. Inicio con Windows

Desde *Configuración → Iniciar al iniciar sesión en Windows* la aplicación escribe (con `reg.exe`)
`HKCU\Software\Microsoft\Windows\CurrentVersion\Run\VivanetPay = "<ruta>\VivanetPay.exe" --minimized`.
La ruta se detecta con la propiedad `jpackage.app-path` o el proceso actual; en desarrollo (`mvn javafx:run`) la
opción se rechaza con un aviso. Con `--minimized` la ventana no se muestra y la aplicación queda en la bandeja.

Verificación manual: `reg query HKCU\Software\Microsoft\Windows\CurrentVersion\Run /v VivanetPay`.

## 4. Ejecutar en desarrollo

```bash
mvn javafx:run                     # ventana JavaFX (requiere entorno gráfico)
mvn javafx:run -Djavafx.args=--minimized
mvn -q compile exec:java           # solo el núcleo, sin UI (HeadlessMain)
```

## 5. Notas de firma y SmartScreen

El instalador sin firmar mostrará SmartScreen. Firme el `.exe`/`.msi` con `signtool` y un certificado de firma de
código antes de distribuir (decisión de distribución pendiente, ver IDD §12).
