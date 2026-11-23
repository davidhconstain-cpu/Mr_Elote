# Vivanet Pay — Modernización del backend (noviembre 2026)

Alcance: dependencias y plugins no-JavaFX de `pom.xml` y código no-UI (`domain`, `application`, `validation`, `parsing`, `persistence`, `integration`, `security`, `voice`, `observability`, `licensing`, `app`). La UI (`ui/`, `reporting/`, JavaFX) la revisa otro agente: ver `docs/MODERNIZACION_FRONTEND.md`.

Decisión del propietario: **el proyecto se queda en Java 21** (`release 21`). Solo se eligen versiones compatibles con JDK 21. No se instaló ni se probó JDK 25.

## 1. Dependencias y plugins

Fuente: `maven-metadata.xml` de Maven Central consultado el 23-nov-2026. Se toma la última versión **estable**: se descartan alfas, betas, milestones y RC (slf4j 2.1.0-alpha1, JUnit 6.2.0-M1, AssertJ 4.0.0-M1, plugins Maven 4.0.0-beta-*).

| Artefacto | Antes | Después | Motivo / nota |
|---|---|---|---|
| org.xerial:sqlite-jdbc | 3.47.1.0 | **3.54.1.0** | Trae el motor SQLite 3.54.1. La versión anterior traía SQLite 3.47.1, afectado por fallos de memoria corregidos en 3.49.1 y 3.50.2. API JDBC sin cambios. La API nativa de respaldo (H-06) sigue funcionando. |
| org.slf4j:slf4j-api | 2.0.16 | **2.0.20** | Última 2.0.x. La 2.1.0 sigue en alfa. Se fija también en `dependencyManagement` porque logback 1.6.8 pide 2.0.19. |
| ch.qos.logback:logback-classic | 1.5.12 | **1.6.8** | La 1.5.12 tenía avisos de seguridad (CVE-2024-12798, CVE-2024-12801 y CVE-2025-11226, corregidos en 1.5.13–1.5.19). La 1.6 pide Java ≥ 11. `logback.xml` y los conversores `%rmsg`/`%rex` cargan sin avisos de estado (comprobado con `exec:java --once`). Logback usa `ReentrantLock` en vez de `synchronized`, lo que va bien con hilos virtuales. |
| com.fasterxml.jackson.core:jackson-databind + datatype-jsr310 | 2.18.2 | **tools.jackson.core:jackson-databind 3.3.0** | **Migración mayor hecha** (ver §2). Jackson 3 lleva `java.time` integrado, así que se quita el módulo jsr310. La última 2.x es 2.23.0 y quedaba como alternativa. |
| com.google.api-client:google-api-client | 2.7.0 | **2.9.2** | Última. |
| com.google.oauth-client:google-oauth-client-jetty | 1.36.0 | **1.39.0** | Última. Ya no arrastra Jetty: `LocalServerReceiver` usa el `HttpServer` del JDK. |
| com.google.apis:google-api-services-gmail | v1-rev20240520-2.0.0 | **v1-rev20261106-2.9.2** | Última revisión, generada para api-client 2.9.2. |
| google-oauth-client-bom (nuevo, en dependencyManagement) | — | 1.39.0 | **Arregla una incoherencia real.** api-client 2.9.2 declara oauth-client 1.36.0, así que Maven resolvía `google-oauth-client` 1.36.0 junto a `-jetty`/`-java6` 1.39.0. |
| google-http-client-bom (nuevo) | — (1.43.3 / 2.0.0 mezclados) | 2.2.0 | Una sola versión de `google-http-client`, `-gson` y `-apache-v2`. |
| google-auth-library-bom (nuevo) | — (1.30.0 transitiva) | 1.58.0 | Última. Lo pedía `requireUpperBoundDeps`. |
| guava / gson / commons-codec / error_prone_annotations (gestionadas) | 33.1.0-jre / 2.11.0 / 1.17.1 / 2.35–2.36 | 33.8.0-jre / 2.15.0 / 1.23.0 / 2.52.0 | Transitivas de Google alineadas a la última. |
| org.junit.jupiter:junit-jupiter | 5.11.3 | **6.1.3** (vía `junit-bom`) | JUnit 6 pide Java 17. Las 287 pruebas existentes pasan sin cambios. |
| org.assertj:assertj-core | 3.26.3 | **3.27.7** | La 4.0.0 sigue en milestone. |
| maven-compiler-plugin | 3.13.0 | **3.16.0** | |
| maven-surefire-plugin | 3.5.2 | **3.6.1** | Compatible con JUnit Platform 6. |
| maven-resources-plugin | 3.3.1 | **3.5.0** | |
| maven-jar-plugin | 3.4.2 | **3.5.1** | |
| exec-maven-plugin | 3.5.0 | **3.7.0** | |
| maven-enforcer-plugin | — | **3.7.0** (nuevo) | Reglas: `requireJavaVersion [21,)`, `requireMavenVersion [3.9.6,)`, `requireUpperBoundDeps` y `banDuplicatePomDependencyVersions`. |
| maven-clean / install / deploy-plugin | (por defecto del super-POM) | 3.5.0 / 3.2.0 / 3.2.0 | Versiones fijadas para que el build sea reproducible. |
| javafx-maven-plugin, JavaFX | — | — | No se tocan (agente UI). |

Sobre la convergencia de dependencias: `dependencyConvergence` estricto no es viable con el ecosistema Google, porque sus propias librerías piden versiones distintas de `error_prone_annotations`, `j2objc-annotations` y `grpc-context`. Se usa `requireUpperBoundDeps`: ninguna librería recibe una versión menor que la que pide.

**¿Algo obliga a usar Java 25?** No. Se midió el bytecode de todos los JAR del classpath (compile y test). El máximo es Java 17 (Jackson 3, JUnit 6). Las demás librerías del backend están en 8–11. JavaFX 23 está en 21. Ninguna versión elegida exige más de 21.

## 2. Migraciones mayores

### Jackson 2 → 3 (hecha)
- **Por qué es seguro:** Jackson se usa solo en 5 archivos. Las librerías Google usan Gson (`GsonFactory`), no Jackson, así que no hay conflicto. `jackson-annotations` sigue en `com.fasterxml.jackson.annotation` (2.23, la que trae databind 3) y no se usan anotaciones.
- **Cambios:**
  - `AppContext`: `JsonMapper.builder().disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build()`, sin `JavaTimeModule`. `json()` devuelve ahora `tools.jackson.databind.ObjectMapper`; la UI no lo usa.
  - `WebhookPayloadParser`:
    - El `JsonFactory` (`tools.jackson.core.json`) se arma en el builder con los mismos límites (`StreamReadConstraints`, `STRICT_DUPLICATE_DETECTION`, comentarios deshabilitados con `JsonReadFeature.ALLOW_JAVA_COMMENTS`), y `FAIL_ON_TRAILING_TOKENS` va en `JsonMapper.builder(factory)`. En Jackson 3 el mapper es inmutable, así que ya no existe `getFactory().disable(...)`.
    - Cambios de nombres: `fieldNames()` → `propertyNames()`, `isTextual()/textValue()` → `isString()/stringValue()`, y se captura `JacksonException`.
  - `WebhookRequestProcessor` e `IngestionService`: `JsonProcessingException` → `JacksonException`. En Jackson 3 las excepciones son no comprobadas, pero se conserva el mismo manejo. `WebhookRequestProcessor` usa `JsonMapper.shared()`.
  - `SecretStoreDataStoreFactory`: `JsonMapper.shared()` y `tools.jackson.core.type.TypeReference`. La `JacksonException` se envuelve en `IOException` para respetar el contrato de `DataStore` de Google, que antes cumplía `JsonProcessingException`.
- **Formato persistido:** no cambia. Solo se serializan `Map` (`LinkedHashMap`/`HashMap`), y a ellos no les afecta el nuevo orden alfabético por defecto de Jackson 3 para POJOs. Las credenciales OAuth ya guardadas (JSON `{clave: base64}`) se siguen leyendo.

### Google API client / OAuth / Gmail (hecha)
- `GoogleNetHttpTransport` está **@Deprecated** en api-client 2.9.x. Google indica usar `NetHttpTransport`. `GoogleGmailAuth.transport()` usa ahora `new NetHttpTransport()`, que valida con el almacén de certificados del JDK, el mismo que cargaba antes `GoogleUtils.getCertificateTrustStore()` (también deprecado). Desaparece el `GeneralSecurityException`.
- `GoogleCredential` y `JacksonFactory` no se usan; ya se usaba `GsonFactory`. `AuthorizationCodeInstalledApp` + `LocalServerReceiver` (oauth-client-java6/jetty 1.39.0) **no** están deprecados y siguen siendo el flujo recomendado para apps de escritorio (loopback + PKCE del flujo de Google).
- Resultado: **0 avisos de deprecación** al compilar (`-Xlint:all,-processing,-serial,-try` y `showDeprecation`).

## 3. Patrones modernos (Java 21), por archivo

El código ya usaba records, `sealed interface AppEvent`, switch expressions, `instanceof` con patrón, `java.time` (no hay `Date`/`Calendar`/`SimpleDateFormat`) y try-with-resources. No hay `HttpURLConnection`. Solo se cambió lo que mejora claridad o robustez:

| Archivo | Cambio | Beneficio |
|---|---|---|
| `integration/android/AndroidWebhookConnector` | El receptor HTTP pasa de `ThreadPoolExecutor(2-4, cola 64)` a **un hilo virtual por solicitud** (`Executors.newThreadPerTaskExecutor(Thread.ofVirtual()...)`), con una compuerta `bounded(...)` de 64 solicitudes simultáneas (`Semaphore`) que lanza `RejectedExecutionException` como hacía `AbortPolicy`. El pool de ingesta sigue en hilos de plataforma acotados (respuesta 503 `busy`). La fábrica `daemon()` usa `Thread.ofPlatform()`. | Reduce el riesgo **R-01** del QA: un cliente lento (slowloris) ya no ocupa uno de solo 4 hilos de plataforma. El límite total sigue acotado. |
| `integration/gmail/GoogleGmailClient.translate` | Cadena `if/else instanceof` → `switch` con patrones de tipo, y nuevo `firstReason()` con `getFirst()`. | El compilador verifica el orden de dominancia: las subclases `TokenResponseException`/`GoogleJsonResponseException` van antes que `HttpResponseException`. |
| `integration/gmail/GoogleGmailAuth` | `NetHttpTransport` (ver §2). | Sin API deprecada. |
| `persistence/IngestionWriter`, `application/IngestionService`, `AndroidWebhookConnector` | `list.get(list.size() - 1)` / `get(0)` → `getLast()` / `getFirst()` (SequencedCollection). | Legibilidad. |
| 5 archivos con Jackson | Migración a Jackson 3 (§2). | |
| `test/.../GmailOAuthSupportTest` | `new URL(String)` (deprecado) → `URI.create(...).toURL()`. | Sin avisos de deprecación en pruebas. |
| `test/.../VirtualThreadReceiverTest` (nuevo, 2 pruebas) | Comprueba que la compuerta rechaza por encima del cupo, libera el cupo al terminar y lo devuelve si el delegado rechaza, y que las tareas corren en hilos virtuales. | |

**Pinning (Java 21).** En 21 un hilo virtual que bloquea dentro de `synchronized` inmoviliza su portador (JEP 491 lo resuelve recién en 24). Por eso solo se usan hilos virtuales en el receptor HTTP: en ese camino `synchronized` protege secciones cortas y sin E/S (`RateLimiter`, `NonceCache`, secretos ya en memoria en `WebhookSecretManager`). La espera de la ingesta (`Future.get`) ocurre fuera de `synchronized`, y la escritura en SQLite (JNI) corre en el ejecutor de ingesta de plataforma.

**Se dejaron sin cambiar, a propósito:**
- `IngestionService` (un único hilo de plataforma). Serializa las escrituras SQLite; un hilo virtual no aporta nada ahí.
- `AnnouncementQueue` (un hilo; la voz no debe solaparse).
- `GmailConnector` (`ScheduledExecutorService` de un hilo).
- `WindowsSpeechEngine` y `AbstractFileSecretStore`. Usan `synchronized` con E/S (PowerShell, archivos) desde hilos de plataforma; pasarlos a `ReentrantLock` solo serviría si algún día se llaman desde hilos virtuales.
- `IngestionResult` (record público consumido por conectores y UI). Convertirlo en una jerarquía sealed rompería el contrato documentado en `ARQUITECTURA.md` sin ganar claridad, porque `EventStatus` ya es un enum exhaustivo.

**Avisos del compilador.** Con `-Xlint:all` completo sobre el código no-UI solo quedan `[serial]` (excepciones sin `serialVersionUID`) y `[try]` (scopes `Correlation` que no se referencian dentro del `try`, que es intencional). Ambos ya estaban excluidos en el pom; no hay avisos de deprecación ni de `rawtypes`/`unchecked`. No se activó `-Werror` porque afecta también al código UI que se modifica en paralelo. Se recomienda activarlo cuando termine la modernización de la UI.

## 4. CVE revisados

- **No se pudo consultar OSV en línea.** El proxy de red del entorno bloquea `api.osv.dev` (HTTP 403), y la API global de avisos de GitHub tampoco está disponible desde esta sesión. La revisión se basa en avisos conocidos hasta mediados de 2026 y en la regla de usar siempre la última estable.
- **Avisos resueltos por las versiones finales:**
  - logback ≤ 1.5.12: CVE-2024-12798, CVE-2024-12801, CVE-2025-11226.
  - SQLite 3.47.1 (dentro de sqlite-jdbc 3.47.1.0): CVE-2025-29087, CVE-2025-29088, CVE-2025-6965.
  - Las transitivas antiguas de Google (guava 31.1-android, http-client 1.43.3) se sustituyen por guava 33.8.0-jre y http-client 2.2.0.
- **Sin avisos conocidos** en las versiones resueltas:
  - jackson-core/databind 3.3.0, gson 2.15.0, guava 33.8.0-jre, commons-codec 1.23.0.
  - httpclient 4.5.14 / httpcore 4.4.16: CVE-2020-13956 corregido en 4.5.13.
  - commons-logging 1.2, sqlite-jdbc 3.54.1.0, logback 1.6.8, slf4j 2.0.20.
  - grpc-context/api 1.70.0 y opencensus 0.31.1: solo API de contexto, sin red.
- **Pendiente:** repetir con `mvn org.owasp:dependency-check-maven:check` o con OSV desde una máquina con acceso, antes de publicar. Lista exacta de coordenadas: `mvn dependency:list -DexcludeGroupIds=org.openjfx`.

## 5. Resultado

| Comprobación | JDK 21.0.13 (Maven 3.9.11) |
|---|---|
| `mvn -q -DbuildDir=target-back clean test` | **289 pruebas, 0 fallos, 0 errores, 5 omitidas** (solo Windows). Antes del cambio: 282/0/0/5; incluye 2 pruebas nuevas de este trabajo y las nuevas de UI. |
| `package -DskipTests` | OK (`vivanet-pay-0.1.0-SNAPSHOT.jar`). |
| `exec:java -Dexec.args=--once` (headless) | OK: arranca, migra la BD, crea el respaldo y escribe el log rotativo, sin avisos de logback. |
| enforcer (`validate`) | OK. |
| JDK 25 | No probado, por decisión del propietario. |

## 6. Java 25: nota

Nada obliga a pasar a Java 25: todas las dependencias elegidas funcionan en 21, cuyo bytecode máximo es 17. El código sigue sin APIs deprecadas, así que compilar con 25 debería ser directo si algún día se decide (jpackage incluye el runtime y el usuario final no lo notaría). El único beneficio concreto para este proyecto sería JEP 491, que elimina el pinning de hilos virtuales en `synchronized`. No hace falta hoy, por la forma en que se acotaron los hilos virtuales (§3).

## 7. Pendiente

1. Revisión de CVE en línea (OSV / OWASP dependency-check) desde una red con acceso (§4).
2. Activar `-Werror` cuando la UI quede estable.
3. Actualizar `docs/QA_INFORME.md`, R-01: mitigado con hilos virtuales y un límite de 64 solicitudes. Sigue vigente el límite de 30 s por solicitud.
4. Probar en Windows real el flujo OAuth de Gmail con `NetHttpTransport` y oauth-client 1.39.0 (navegador → loopback → token). Las pruebas automatizadas cubren el almacenamiento y la clasificación de errores, pero no la llamada real a Google.
