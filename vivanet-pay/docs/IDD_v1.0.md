**VIVANET PAY**

**Documento Integral de Ingeniería y Diseño (IDD)**

Especificación de requisitos, procesos, arquitectura, datos,\
seguridad, pruebas, despliegue y evolución

Versión 1.0 \| 22 de noviembre de 2026\
Estado: línea base inicial para revisión y aprobación

  -----------------------------------------------------------------------
  **Control documental**              **Valor**
  ----------------------------------- -----------------------------------
  Nombre del producto                 Vivanet Pay

  Tipo de solución                    Aplicación de escritorio Windows
                                      con conectores externos

  Plataforma principal                Windows 10/11; operación en segundo
                                      plano

  Fuentes iniciales                   Gmail y puente Android/MacroDroid
                                      para notificaciones de Nequi

  Moneda y contexto inicial           COP --- Colombia

  Modelo comercial acordado           Producto comercial; prueba gratuita
                                      de 14 días sin exigir tarjeta ni
                                      medio de pago al registro

  Propietario del documento           Proyecto Vivanet Pay

  Estado de aprobación                Borrador maestro; requiere revisión
                                      de los puntos pendientes
                                      identificados
  -----------------------------------------------------------------------

Confidencialidad: este documento contiene la definición funcional y
técnica del producto. Las credenciales, tokens, claves OAuth y secretos
de producción nunca deben incorporarse al documento ni al repositorio de
código.

# Historial y control del documento

  -----------------------------------------------------------------------
  **Versión**       **Fecha**         **Descripción**   **Estado**
  ----------------- ----------------- ----------------- -----------------
  0.1               22/11/2026        Consolidación de  Borrador
                                      decisiones        
                                      iniciales, guía   
                                      técnica           
                                      suministrada y    
                                      arquitectura      
                                      propuesta.        

  1.0               22/11/2026        Primera línea     Para revisión
                                      base integral:    
                                      requisitos,       
                                      flujos,           
                                      arquitectura,     
                                      datos, seguridad  
                                      y pruebas.        
  -----------------------------------------------------------------------

## Aprobaciones requeridas

  -----------------------------------------------------------------------
  **Rol**                 **Responsabilidad**     **Aprobación**
  ----------------------- ----------------------- -----------------------
  Propietario del         Confirma alcance,       Pendiente
  producto                prioridades, reglas de  
                          negocio y experiencia   
                          esperada.               

  Responsable técnico     Valida arquitectura,    Pendiente
                          tecnologías, seguridad  
                          y viabilidad.           

  Responsable de pruebas  Aprueba estrategia,     Pendiente
                          casos y criterios de    
                          salida.                 
  -----------------------------------------------------------------------

## Cómo interpretar este documento

-   APROBADO: decisión expresamente seleccionada por el usuario durante
    el diseño.

-   PROPUESTO: recomendación técnica que debe validarse antes de
    convertirse en compromiso.

-   PENDIENTE: dato o regla que todavía requiere decisión, evidencia o
    prueba.

-   FUERA DE ALCANCE: funcionalidad que no se desarrollará en la primera
    versión salvo cambio aprobado.

# Índice

-   1\. Propósito, visión y objetivos

-   2\. Alcance y límites

-   3\. Interesados, usuarios y entorno operativo

-   4\. Ingeniería de requisitos y reglas de negocio

-   5\. Flujos de procesos y casos de uso

-   6\. Arquitectura lógica y física

-   7\. Modelo de datos

-   8\. Seguridad, privacidad y confiabilidad

-   9\. Interfaz y experiencia de usuario

-   10\. Requisitos no funcionales

-   11\. Estrategia de pruebas y aceptación

-   12\. Despliegue, operación y soporte

-   13\. Plan de implementación

-   14\. Riesgos, supuestos y dependencias

-   15\. Trazabilidad y control de cambios

-   16\. Decisiones pendientes y anexos

# 1. Propósito, visión y objetivos

## 1.1 Propósito

Definir de forma verificable qué debe hacer Vivanet Pay, cómo debe
construirse, cuáles son sus límites, cómo se comprobará su calidad y qué
decisiones deben aprobarse antes de liberar el producto. Este documento
es la referencia común para producto, desarrollo, pruebas, despliegue y
mantenimiento.

## 1.2 Visión del producto

Vivanet Pay será una solución comercial que ayuda a un negocio a
detectar, organizar y anunciar transferencias entrantes. La primera
plataforma será una aplicación de escritorio para Windows que permanece
en segundo plano. El sistema tendrá un núcleo común de procesamiento y
conectores desacoplados para recibir eventos desde Gmail y, cuando esté
disponible, desde un dispositivo Android que transmita notificaciones de
Nequi.

## 1.3 Objetivo general

Reducir la revisión manual de pagos y facilitar la conciliación
operativa mediante la captura de avisos, extracción de datos,
validación, registro histórico y anuncios por voz, sin presentar una
notificación como prueba definitiva de que los fondos están abonados.

## 1.4 Objetivos específicos

-   Capturar eventos de pago desde fuentes autorizadas y configuradas.

-   Extraer, cuando el mensaje lo permita, banco, importe, remitente,
    referencia, fecha y hora.

-   Normalizar y validar la información antes de crear un movimiento.

-   Prevenir registros duplicados y conservar el rastro de origen.

-   Anunciar por voz los eventos que cumplan la política de confirmación
    definida.

-   Mostrar resumen del día, conteo, desglose por entidad e historial.

-   Permitir revisión manual de eventos dudosos o incompletos.

-   Mantener la arquitectura extensible para incorporar otras entidades
    y fuentes.

## 1.5 Indicadores de éxito propuestos

  ------------------------------------------------------------------------
  **Indicador**           **Meta inicial          **Método de medición**
                          propuesta**             
  ----------------------- ----------------------- ------------------------
  Integridad del registro 100% de eventos         Pruebas de base de datos
                          aceptados conservan     y auditoría.
                          fuente, fecha de        
                          captura e identificador 
                          técnico.                

  Duplicados              0 duplicados visibles   Pruebas de
                          en el historial para un reenvío/reintento.
                          mismo evento de origen. 

  Precisión de extracción ≥ 98% en plantillas de  Conjunto de
                          notificación conocidas  correos/notificaciones
                          y verificadas.          de prueba.

  Latencia Android        Objetivo \< 5 s desde   Prueba controlada; no es
                          recepción local de      SLA garantizado.
                          notificación hasta      
                          registro, en red        
                          disponible.             

  Disponibilidad local    La aplicación recupera  Prueba de reinicio y
                          operación después de    recuperación.
                          reinicio sin perder     
                          movimientos confirmados 
                          localmente.             
  ------------------------------------------------------------------------

# 2. Alcance y límites

## 2.1 Alcance de la primera versión (MVP)

-   Aplicación de escritorio para Windows 10/11.

-   Inicio con Windows, ejecución minimizada en la bandeja del sistema y
    opción de abrir/cerrar completamente.

-   Tablero con total recibido hoy, cantidad de movimientos, último
    evento y desglose por entidad.

-   Historial de transacciones con fecha, hora, banco/fuente, importe,
    remitente, referencia, estado y detalle.

-   Persistencia local mediante base de datos SQLite (propuesta
    técnica).

-   Conector Gmail con autorización OAuth y permisos mínimos de lectura,
    sujeto a verificación de notificaciones reales.

-   Conector Android desacoplado, preparado para MacroDroid y webhook
    HTTPS.

-   Motor de voz de Windows para avisos configurables.

-   Validación, deduplicación, registro de errores y revisión manual de
    eventos no concluyentes.

-   Configuración inicial, diagnóstico de conectores y exportación
    básica del historial (formato por definir).

## 2.2 Fuera de alcance inicial

-   Iniciar transferencias, mover dinero o ejecutar operaciones
    bancarias.

-   Acceder a credenciales bancarias, PIN, claves o sesiones privadas de
    Nequi.

-   Garantizar confirmación bancaria en tiempo real sin una fuente
    oficial verificable.

-   Reemplazar extractos, comprobantes oficiales o conciliación bancaria
    formal.

-   Facturación electrónica, contabilidad completa, nómina o gestión de
    inventario.

-   Aplicación móvil Android nativa propia en la primera entrega;
    inicialmente Android podrá usar MacroDroid como puente.

-   Servicio multiusuario/multisucursal y sincronización cloud, hasta
    que se apruebe el modelo de despliegue.

-   Integraciones bancarias no verificadas ni autorizadas.

## 2.3 Consideración crítica sobre Nequi y Gmail

La guía técnica suministrada indica que las transferencias estándar
entre cuentas Nequi no generan necesariamente un correo por cada
operación; por eso plantea capturar la notificación push de Nequi en
Android mediante MacroDroid y enviarla a Apps Script. En consecuencia,
Gmail no se considerará fuente garantizada de todos los pagos Nequi. El
conector Android será la ruta prevista para esas notificaciones. Se debe
comprobar el comportamiento real de la cuenta y de la versión de Nequi
antes de cerrar el diseño.

La guía también describe lectura de correos para Bancolombia, Davivienda
y DaviPlata. Esas entidades quedan como conectores futuros o sujetos a
validación, no como integraciones garantizadas en el MVP.

## 2.4 Plataformas y modalidad comercial

Decisión aprobada: el producto tendrá un modelo comercial y un período
de prueba gratuito de 14 días, sin exigir tarjeta ni medio de pago al
registrarse. Está pendiente definir si la licencia se validará
completamente en línea, cómo se gestionarán activación, vencimiento,
renovación, tolerancia sin conexión y protección frente a manipulación
del reloj del equipo.

# 3. Interesados, usuarios y entorno

## 3.1 Interesados

  -----------------------------------------------------------------------
  **Interesado**                      **Necesidad / interés**
  ----------------------------------- -----------------------------------
  Propietario del negocio             Saber cuándo se reporta un pago,
                                      consultar totales e historial y
                                      reducir validaciones manuales.

  Operador de caja                    Recibir avisos claros y consultar
                                      el movimiento sin interrumpir la
                                      atención.

  Administrador de Vivanet Pay        Configurar licencia, fuentes, voz,
                                      seguridad, actualizaciones y
                                      soporte.

  Proveedor de correo (Google)        Autorizar el acceso OAuth de
                                      acuerdo con sus políticas y
                                      permisos.

  Proveedor de billetera/banco        Emitir notificaciones por sus
                                      canales oficiales; Vivanet Pay no
                                      controla su disponibilidad.

  Soporte técnico                     Diagnosticar errores sin solicitar
                                      contraseñas bancarias ni exponer
                                      información sensible.
  -----------------------------------------------------------------------

## 3.2 Perfil de usuario principal

Usuario de un negocio colombiano que recibe transferencias, utiliza un
computador Windows en el punto de venta y desea escuchar avisos de
nuevos pagos. Puede no tener conocimientos técnicos; por tanto, la
instalación y configuración deben ser guiadas, con mensajes
comprensibles y diagnósticos accionables.

## 3.3 Entorno operativo

-   Windows 10/11, computador con permisos suficientes para instalar la
    aplicación.

-   Conexión a Internet para sincronización de Gmail, recepción de
    webhooks y validación de licencia.

-   Altavoces o dispositivo de audio habilitado para anuncios por voz.

-   Cuenta Gmail controlada por el titular y autorización explícita
    OAuth.

-   Para el puente Nequi: teléfono Android con Nequi y MacroDroid,
    permiso de acceso a notificaciones y conectividad hacia el receptor.

-   La aplicación debe continuar mostrando el historial local aunque
    Internet esté temporalmente indisponible.

# 4. Ingeniería de requisitos y reglas de negocio

## 4.1 Convenciones de prioridad

  -----------------------------------------------------------------------
  **Prioridad**                       **Significado**
  ----------------------------------- -----------------------------------
  MUST                                Obligatorio para liberar el MVP.

  SHOULD                              Importante; puede diferirse con
                                      justificación aprobada.

  COULD                               Mejora opcional posterior.

  TBD                                 Pendiente de definición o
                                      validación.
  -----------------------------------------------------------------------

## 4.2 Requisitos funcionales

  -----------------------------------------------------------------------
  **ID**                  **Prioridad**           **Requisito**
  ----------------------- ----------------------- -----------------------
  RF-001                  MUST                    La aplicación debe
                                                  iniciar manualmente y
                                                  permitir configurar el
                                                  inicio automático con
                                                  Windows.

  RF-002                  MUST                    La aplicación debe
                                                  poder permanecer
                                                  minimizada en la
                                                  bandeja del sistema sin
                                                  cerrar sus servicios de
                                                  captura.

  RF-003                  MUST                    El usuario debe poder
                                                  abrir el tablero desde
                                                  el icono de bandeja.

  RF-004                  MUST                    El usuario debe poder
                                                  pausar/reanudar
                                                  conectores y cerrar
                                                  completamente la
                                                  aplicación.

  RF-005                  MUST                    El sistema debe recibir
                                                  eventos desde
                                                  conectores desacoplados
                                                  mediante un contrato
                                                  interno común.

  RF-006                  MUST                    Cada evento recibido
                                                  debe conservar la
                                                  fuente, identificador
                                                  externo cuando exista,
                                                  fecha de recepción y
                                                  contenido original
                                                  necesario para
                                                  auditoría.

  RF-007                  MUST                    El sistema debe extraer
                                                  importe y moneda cuando
                                                  el formato de origen
                                                  sea reconocido; si no,
                                                  debe marcar el evento
                                                  para revisión.

  RF-008                  MUST                    El sistema debe extraer
                                                  remitente, referencia,
                                                  banco y fecha/hora
                                                  cuando existan en la
                                                  fuente.

  RF-009                  MUST                    El sistema debe
                                                  normalizar importes a
                                                  COP como valor numérico
                                                  entero en pesos, sin
                                                  depender del formato
                                                  visual.

  RF-010                  MUST                    El sistema debe
                                                  detectar duplicados
                                                  mediante
                                                  identificadores
                                                  externos y una clave de
                                                  idempotencia.

  RF-011                  MUST                    El sistema debe impedir
                                                  que un evento no
                                                  validado se presente
                                                  como pago confirmado.

  RF-012                  MUST                    El sistema debe guardar
                                                  los movimientos
                                                  aceptados en
                                                  almacenamiento
                                                  persistente local.

  RF-013                  MUST                    El tablero debe mostrar
                                                  total recibido hoy,
                                                  cantidad de
                                                  movimientos, desglose
                                                  por entidad y último
                                                  movimiento.

  RF-014                  MUST                    El historial debe
                                                  permitir consultar
                                                  fecha, hora, entidad,
                                                  monto, remitente,
                                                  referencia, estado y
                                                  origen.

  RF-015                  MUST                    El motor de voz debe
                                                  anunciar los eventos
                                                  que cumplan la política
                                                  de anuncio configurada.

  RF-016                  MUST                    El usuario debe poder
                                                  activar/desactivar voz
                                                  y ajustar
                                                  volumen/velocidad
                                                  cuando la plataforma lo
                                                  permita.

  RF-017                  MUST                    La aplicación debe
                                                  informar el estado de
                                                  cada conector:
                                                  conectado,
                                                  desconectado, requiere
                                                  autorización o error.

  RF-018                  MUST                    El sistema debe
                                                  registrar errores
                                                  técnicos sin incluir
                                                  secretos ni tokens.

  RF-019                  SHOULD                  El usuario podrá
                                                  filtrar el historial
                                                  por fecha, fuente,
                                                  estado y rango de
                                                  montos.

  RF-020                  SHOULD                  El usuario podrá
                                                  exportar el historial a
                                                  CSV o Excel; formato
                                                  final por aprobar.

  RF-021                  MUST                    El conector Gmail debe
                                                  utilizar OAuth y
                                                  permisos mínimos de
                                                  lectura.

  RF-022                  MUST                    El conector Android
                                                  debe aceptar únicamente
                                                  solicitudes
                                                  autenticadas y
                                                  protegidas en tránsito.

  RF-023                  SHOULD                  El sistema debe ofrecer
                                                  una bandeja de eventos
                                                  pendientes de revisión
                                                  manual.

  RF-024                  MUST                    El sistema debe
                                                  conservar un registro
                                                  de cambios de estado de
                                                  cada evento.

  RF-025                  SHOULD                  El sistema debe mostrar
                                                  alertas de pérdida de
                                                  conexión, autorización
                                                  vencida o fallos de
                                                  sincronización.
  -----------------------------------------------------------------------

## 4.3 Requisitos no funcionales

  -----------------------------------------------------------------------
  **ID**                  **Atributo**            **Requisito**
  ----------------------- ----------------------- -----------------------
  RNF-001                 Seguridad               No almacenar
                                                  contraseñas bancarias
                                                  ni de Gmail.

  RNF-002                 Seguridad               Guardar tokens OAuth en
                                                  almacén seguro del
                                                  sistema operativo
                                                  cuando sea viable;
                                                  nunca en texto plano
                                                  dentro del código o
                                                  logs.

  RNF-003                 Seguridad               Cifrar el transporte
                                                  del webhook con HTTPS y
                                                  validar autenticidad,
                                                  integridad y vigencia
                                                  de cada solicitud.

  RNF-004                 Privacidad              Solicitar solo permisos
                                                  estrictamente
                                                  necesarios y explicar
                                                  el uso de datos.

  RNF-005                 Confiabilidad           Los reintentos no deben
                                                  crear movimientos
                                                  duplicados.

  RNF-006                 Confiabilidad           Una caída del conector
                                                  no debe corromper la
                                                  base local ni bloquear
                                                  la interfaz.

  RNF-007                 Rendimiento             El tablero debe abrir
                                                  en menos de 3 segundos
                                                  en un equipo que cumpla
                                                  los requisitos mínimos
                                                  (meta a validar).

  RNF-008                 Rendimiento             La interfaz no debe
                                                  congelarse durante
                                                  sincronización, lectura
                                                  o escritura.

  RNF-009                 Usabilidad              Los estados y errores
                                                  deben expresarse en
                                                  español claro y ofrecer
                                                  una acción recomendada.

  RNF-010                 Compatibilidad          Soporte objetivo para
                                                  Windows 10 y Windows 11
                                                  de 64 bits; matriz
                                                  exacta por validar.

  RNF-011                 Mantenibilidad          Separar dominio,
                                                  conectores,
                                                  persistencia, interfaz
                                                  y seguridad.

  RNF-012                 Observabilidad          Logs rotativos con
                                                  niveles y datos
                                                  sensibles redactados.

  RNF-013                 Recuperación            La base de datos debe
                                                  respaldarse y poder
                                                  restaurarse mediante
                                                  procedimiento
                                                  documentado.

  RNF-014                 Accesibilidad operativa El usuario debe poder
                                                  silenciar voz y seguir
                                                  consultando los pagos
                                                  visualmente.

  RNF-015                 Actualización           Las actualizaciones
                                                  deben estar firmadas o
                                                  verificadas y disponer
                                                  de mecanismo de
                                                  recuperación.
  -----------------------------------------------------------------------

## 4.4 Reglas de negocio

  -----------------------------------------------------------------------
  **ID**                              **Regla**
  ----------------------------------- -----------------------------------
  RN-001                              Una notificación no equivale por sí
                                      sola a confirmación bancaria del
                                      abono.

  RN-002                              Los eventos deben tener estados
                                      explícitos: RECIBIDO, PARSEADO,
                                      PENDIENTE_REVISION,
                                      VALIDADO_POR_FUENTE, ANUNCIADO,
                                      DUPLICADO, RECHAZADO o ERROR.

  RN-003                              Solo los estados habilitados por la
                                      política de validación pueden usar
                                      el mensaje de voz "pago
                                      confirmado". Para eventos no
                                      concluyentes se usará "notificación
                                      de posible pago recibida".

  RN-004                              El mismo identificador externo no
                                      puede producir más de un movimiento
                                      contabilizado.

  RN-005                              Si no existe identificador externo
                                      confiable, se calculará una huella
                                      con fuente, cuenta/conector, fecha,
                                      importe, remitente, referencia y
                                      contenido normalizado; la
                                      coincidencia se enviará a revisión
                                      si no es inequívoca.

  RN-006                              La moneda inicial será COP. No se
                                      admitirán otras monedas sin cambio
                                      aprobado.

  RN-007                              El valor se almacenará como entero
                                      en pesos; los separadores de miles
                                      son solo presentación.

  RN-008                              Los movimientos históricos no se
                                      borrarán físicamente desde la
                                      interfaz ordinaria; cualquier
                                      corrección dejará auditoría.

  RN-009                              El reloj local se utilizará para
                                      presentación, pero se conservará
                                      timestamp de origen y de recepción
                                      cuando estén disponibles.

  RN-010                              La pérdida de Internet no debe
                                      borrar datos ya guardados
                                      localmente.
  -----------------------------------------------------------------------

# 5. Flujos de procesos y casos de uso

## 5.1 Flujo A --- Inicio de Vivanet Pay

1.  Windows inicia sesión del usuario.

2.  El sistema operativo lanza Vivanet Pay si el inicio automático está
    habilitado.

3.  La aplicación carga configuración, licencia y base local.

4.  Valida integridad básica de la base y recupera tareas pendientes.

5.  Inicia conectores habilitados sin bloquear la interfaz.

6.  Se minimiza en la bandeja; el tablero puede abrirse desde el icono.

7.  Si un conector requiere autorización, muestra un estado de atención
    y no simula conexión.

## 5.2 Flujo B --- Captura de evento desde Gmail

8.  El usuario autoriza una cuenta Google mediante OAuth.

9.  El conector obtiene acceso de solo lectura y guarda el estado de
    sincronización.

10. Busca mensajes según criterios configurables y remitentes/plantillas
    validados.

11. Obtiene metadatos y cuerpo necesario; evita descargar contenido
    innecesario.

12. El analizador identifica entidad, monto, fecha, remitente y
    referencia.

13. El validador comprueba formato, importe positivo, moneda y señales
    de autenticidad.

14. El motor de idempotencia decide si es nuevo, duplicado o ambiguo.

15. El evento se persiste con estado y evidencia de origen.

16. El tablero se actualiza y el motor de voz actúa según política.

17. El conector avanza el cursor de sincronización solo después de
    persistir el evento.

## 5.3 Flujo C --- Captura desde Android/MacroDroid

18. Nequi muestra una notificación en el dispositivo Android.

19. MacroDroid captura únicamente notificaciones de la aplicación
    seleccionada y bajo permisos otorgados por el titular del
    dispositivo.

20. MacroDroid envía título, texto, fecha de captura e identificador del
    evento a un receptor HTTPS autenticado.

21. El receptor valida secreto rotativo/firma, marca temporal, nonce y
    tamaño del mensaje.

22. El adaptador Android convierte el mensaje al formato común de
    evento.

23. El motor central analiza y valida el contenido, deduplica y
    persiste.

24. El tablero se actualiza y se anuncia el estado apropiado.

25. Si no hay red, el puente debe reintentar con identificador
    idempotente; la estrategia de cola local debe validarse en la
    implementación.

## 5.4 Flujo D --- Revisión manual

26. El usuario abre la bandeja de eventos pendientes.

27. Consulta el texto de origen permitido, monto extraído y motivo de
    incertidumbre.

28. Contrasta el pago con la aplicación o canal oficial de la entidad.

29. Selecciona validar, descartar o mantener pendiente.

30. El sistema registra usuario/acción/fecha y no sobrescribe el
    contenido original.

31. Solo una validación permitida cambia la elegibilidad para anunciar
    como confirmado.

## 5.5 Flujo E --- Aviso por voz

32. Un evento nuevo se persiste y cambia a un estado elegible para
    anuncio.

33. Se genera una frase con banco, importe y, si está permitido,
    remitente.

34. Se encola el anuncio para evitar solapamientos.

35. Windows reproduce el mensaje en español.

36. El sistema marca el evento como anunciado y registra fecha/hora.

37. Si el audio está silenciado o no disponible, el evento permanece
    visible y se registra el fallo.

## 5.6 Flujo F --- Recuperación tras desconexión

38. El conector detecta timeout, error de red o autorización vencida.

39. Marca el conector como desconectado/requiere autorización.

40. Aplica reintentos con espera incremental y límite.

41. Al recuperar conexión, sincroniza desde el último cursor confirmado.

42. La idempotencia evita duplicados al procesar eventos repetidos.

43. El tablero muestra la hora de última sincronización exitosa.

## 5.7 Casos de uso principales

  -----------------------------------------------------------------------------
  **ID**         **Caso de      **Actor**       **Resultado    **Requisitos**
                 uso**                          esperado**     
  -------------- -------------- --------------- -------------- ----------------
  CU-01          Iniciar        Usuario /       La aplicación  RF-001 a RF-004
                 aplicación     Windows         carga y queda  
                                                disponible en  
                                                bandeja.       

  CU-02          Autorizar      Propietario     OAuth aprobado RF-021
                 Gmail                          y conector en  
                                                estado         
                                                conectado.     

  CU-03          Recibir        Conector        Evento         RF-005 a RF-012
                 notificación   Gmail/Android   guardado o     
                 bancaria                       enviado a      
                                                revisión.      

  CU-04          Consultar      Usuario         Métricas e     RF-013, RF-014
                 tablero                        historial      
                                                visibles.      

  CU-05          Escuchar aviso Usuario / motor Se reproduce   RF-015, RF-016
                                de voz          aviso conforme 
                                                al estado.     

  CU-06          Resolver       Usuario         Decisión       RF-023, RF-024
                 evento dudoso  autorizado      registrada con 
                                                auditoría.     

  CU-07          Revisar        Usuario         Estado y       RF-017, RF-025
                 conexión                       diagnóstico de 
                                                cada conector. 

  CU-08          Exportar       Usuario         Archivo        RF-020
                 historial                      generado según 
                                                filtros.       
  -----------------------------------------------------------------------------

## 5.8 Diagrama textual de contexto

Gmail ──OAuth/API──\> Conector Gmail ──┐\
Android/Nequi ──MacroDroid/HTTPS──\> Conector Android ──┤\
Motor de transacciones\
├── Validador e idempotencia\
├── SQLite / auditoría\
├── Tablero JavaFX\
└── Voz Windows

El flujo debe ser asíncrono: conectores y operaciones de red no se
ejecutarán en el hilo de interfaz.

# 6. Arquitectura lógica y física

## 6.1 Principios arquitectónicos

-   Separación por capas y responsabilidades.

-   Núcleo de dominio independiente de Gmail, MacroDroid y JavaFX.

-   Conectores intercambiables mediante interfaces.

-   Procesamiento idempotente y orientado a eventos.

-   Persistencia local como fuente del historial de la instalación.

-   Seguridad por defecto y mínimo privilegio.

-   Trazabilidad desde el evento original hasta el anuncio.

## 6.2 Arquitectura propuesta

  -----------------------------------------------------------------------
  **Capa**                **Responsabilidad**     **Componentes
                                                  propuestos**
  ----------------------- ----------------------- -----------------------
  Presentación            Tablero, bandeja,       JavaFX; System Tray
                          configuración,          API.
                          historial y alertas.    

  Aplicación              Orquestar casos de uso  Servicios de
                          y procesos asíncronos.  aplicación; colas
                                                  internas.

  Dominio                 Reglas de pago,         Transaction,
                          estados, normalización  PaymentEvent,
                          e idempotencia.         ValidationPolicy.

  Integración             Recibir y transformar   Gmail API; receptor
                          eventos externos.       HTTPS; adaptador
                                                  MacroDroid.

  Infraestructura         Persistencia, logs,     SQLite; logging;
                          secretos,               Windows Credential
                          configuración.          Manager/DPAPI.

  Voz                     Generar y reproducir    Servicio TTS compatible
                          avisos.                 con Windows.
  -----------------------------------------------------------------------

## 6.3 Tecnologías propuestas --- sujetas a prueba de concepto

  -----------------------------------------------------------------------
  **Elemento**            **Propuesta**           **Estado**
  ----------------------- ----------------------- -----------------------
  Lenguaje                Java 21                 Propuesto; verificar
                                                  instalación local.

  Interfaz                JavaFX                  Propuesto.

  Construcción            Maven                   Propuesto.

  Base de datos           SQLite + JDBC           Propuesto para MVP
                                                  local.

  Gmail                   Gmail API + OAuth 2.0   Propuesto; verificar
                                                  cuotas y revisión OAuth
                                                  según distribución.

  Android                 MacroDroid → HTTPS      Basado en la guía;
                          webhook                 requiere teléfono
                                                  Android y prueba.

  Voz                     Motor de voz disponible Propuesto; validar
                          en Windows              voces es-CO y API
                                                  elegida.

  Instalador              JPackage/MSIX u otro    Pendiente de selección.
                          instalador Windows      

  Actualización           Canal de actualización  Pendiente de diseño.
                          firmado                 
  -----------------------------------------------------------------------

## 6.4 Organización sugerida del código

com.vivanetpay\
app/ arranque, ciclo de vida y configuración\
domain/ entidades, estados y reglas\
application/ casos de uso y orquestación\
integration/\
gmail/ OAuth, búsqueda y lectura\
android/ webhook y normalización\
persistence/ repositorios y SQLite\
validation/ reglas y detección de duplicados\
voice/ cola y síntesis de voz\
ui/ pantallas JavaFX y bandeja\
security/ secretos, firma y permisos\
reporting/ consultas y exportación\
observability/ logs, métricas y diagnóstico

## 6.5 Diagrama de despliegue lógico

Equipo Windows:\
- Vivanet Pay Desktop (proceso principal)\
- SQLite local y archivos de log\
- Almacén seguro de credenciales del sistema operativo\
- Servicio/cliente de voz local\
\
Servicios externos:\
- Google Gmail API (con OAuth autorizado)\
\
Dispositivo Android opcional:\
- Nequi + MacroDroid\
- Envío HTTPS hacia el receptor de eventos\
\
La ubicación del receptor HTTPS está PENDIENTE: puede ser un servicio
local accesible por red privada o un servicio intermedio en la nube. No
debe exponerse un puerto doméstico a Internet sin diseño de seguridad.

## 6.6 Decisión sobre el receptor Android

La opción de enviar desde Android directamente al computador solo
funciona de forma fiable si ambos dispositivos pueden alcanzarse por una
red compatible y el equipo está encendido. Para recepción fuera de la
red local, se requerirá un servicio intermedio seguro. Esta decisión
depende de si el negocio necesita recibir pagos cuando el teléfono y el
computador están en redes distintas.

# 7. Modelo de datos

## 7.1 Entidades principales

  ---------------------------------------------------------------------------
  **Entidad**             **Campos principales**      **Propósito**
  ----------------------- --------------------------- -----------------------
  payment_event           id, source, external_id,    Representa cada aviso
                          received_at,                recibido, incluso si
                          source_timestamp,           queda pendiente.
                          raw_payload_ref, status,    
                          fingerprint                 

  transaction             id, event_id, bank,         Movimiento normalizado
                          amount_minor, currency,     que se presenta en el
                          sender, reference,          historial.
                          occurred_at, created_at     

  connector               id, type, enabled,          Estado y configuración
                          connection_status,          de cada integración.
                          last_success_at, cursor,    
                          settings_ref                

  validation              id, event_id, rule, result, Evidencia de
                          reason, validated_at, actor validaciones
                                                      automáticas o manuales.

  voice_announcement      id, transaction_id,         Control de anuncios y
                          message, status, queued_at, reintentos.
                          spoken_at                   

  audit_log               id, entity_type, entity_id, Trazabilidad de
                          action, before_value,       cambios.
                          after_value, actor,         
                          created_at                  

  app_setting             key,                        Preferencias no
                          value_protected_or_plain,   secretas y referencias
                          updated_at                  a secretos.

  license_state           license_id, status,         Estado local de
                          trial_started_at,           licencia; detalles
                          trial_ends_at,              comerciales pendientes.
                          last_check_at               
  ---------------------------------------------------------------------------

## 7.2 Reglas de almacenamiento

-   Usar transacciones SQL para guardar evento, movimiento, validación y
    auditoría de forma coherente.

-   Crear índices para fecha, banco, estado, external_id y fingerprint.

-   Aplicar UNIQUE a identificadores externos cuando sean confiables y a
    claves de idempotencia definidas.

-   Guardar importes como INTEGER en pesos COP; no usar float/double
    para dinero.

-   Guardar timestamps en UTC y convertir a zona America/Bogota para
    presentación.

-   Separar contenido original, datos extraídos y datos normalizados.

-   Definir política de retención, respaldo y borrado conforme a
    requisitos legales y comerciales pendientes.

## 7.3 Estados de un evento

  -----------------------------------------------------------------------
  **Estado**              **Descripción**         **¿Anuncio "pago
                                                  confirmado"?**
  ----------------------- ----------------------- -----------------------
  RECIBIDO                La fuente entregó el    No
                          evento.                 

  PARSEADO                Se extrajeron campos    No
                          con estructura          
                          reconocida.             

  PENDIENTE_REVISION      Falta evidencia o hay   No
                          ambigüedad.             

  VALIDADO_POR_FUENTE     La política de          Sí, si la política
                          validación aceptó la    aprobada lo permite
                          evidencia definida.     

  ANUNCIADO               Se reprodujo el mensaje No aplica
                          configurado.            

  DUPLICADO               Coincide con un evento  No
                          ya procesado.           

  RECHAZADO               No cumple reglas de     No
                          aceptación.             

  ERROR                   Fallo técnico que       No
                          requiere reintento o    
                          diagnóstico.            
  -----------------------------------------------------------------------

# 8. Seguridad, privacidad y confiabilidad

## 8.1 Principios

-   No solicitar ni almacenar claves de Nequi, PIN, claves bancarias ni
    contraseñas Gmail.

-   OAuth de Google con alcance mínimo; solo lectura para el conector
    Gmail.

-   No utilizar una URL pública anónima para recibir webhooks sensibles.

-   Autenticar cada webhook con secreto robusto rotativo o firma HMAC,
    marca temporal y nonce para evitar repetición.

-   HTTPS obligatorio para comunicación fuera de localhost/red privada
    protegida.

-   Validar tamaño, tipo y estructura de cada payload; limitar tasa de
    solicitudes.

-   No incluir cuerpo completo de correos, tokens ni datos personales
    innecesarios en logs.

-   Restringir acceso al equipo y a los archivos de datos según el
    perfil del sistema operativo.

-   Registrar consentimiento y finalidad del tratamiento de datos
    personales.

## 8.2 Riesgos de suplantación

Un correo puede ser falsificado, reenviado o alterado; una notificación
push puede ser incompleta o imitada. El nombre de remitente, el texto y
el monto no son por sí solos evidencia suficiente de abono. Se
recomienda validar remitentes/dominios, encabezados disponibles,
estructura esperada, integridad del evento y, cuando sea necesario,
contrastar con la app o canal oficial. No se debe afirmar que el sistema
verifica el saldo bancario si no existe integración oficial que lo
permita.

## 8.3 Protección de credenciales

-   Usar almacén de credenciales de Windows o mecanismo equivalente con
    protección del usuario.

-   Separar secretos de desarrollo, pruebas y producción.

-   No subir client_secret, refresh_token, webhook secret ni archivos de
    credenciales al repositorio.

-   Permitir revocar la conexión Gmail desde la aplicación y explicar
    cómo revocarla desde Google.

-   Rotar secretos de webhook y revocar inmediatamente ante sospecha de
    exposición.

## 8.4 Privacidad y cumplimiento

El sistema puede tratar datos personales contenidos en notificaciones
(por ejemplo, nombre, referencia y monto). Antes de comercializarlo se
debe definir aviso de privacidad, finalidad, base legal, responsable del
tratamiento, retención, atención de solicitudes y condiciones
contractuales aplicables en Colombia. Esta documentación técnica no
constituye asesoría jurídica.

## 8.5 Respaldo y recuperación

Para MVP local se propone copia de seguridad automática de SQLite, con
cierre consistente o backup API de SQLite, verificación periódica de
integridad y restauración probada. La frecuencia, retención, cifrado y
ubicación de copias deben aprobarse. Una copia en el mismo disco no
protege frente a pérdida o daño del equipo.

# 9. Interfaz y experiencia de usuario

## 9.1 Pantallas del MVP

  -----------------------------------------------------------------------
  **Pantalla**                        **Contenido mínimo**
  ----------------------------------- -----------------------------------
  Tablero                             Total del día, cantidad de
                                      movimientos, último evento,
                                      desglose por entidad, estado de
                                      conectores.

  Historial                           Tabla de movimientos, búsqueda,
                                      filtros, detalle y estado de
                                      validación.

  Pendientes                          Eventos ambiguos, motivo, datos
                                      extraídos, revisión y decisión.

  Conectores                          Gmail: autorización/estado/última
                                      sincronización. Android:
                                      endpoint/estado/prueba de
                                      recepción.

  Voz                                 Activar/desactivar, prueba de voz,
                                      volumen/velocidad si están
                                      disponibles.

  Configuración                       Inicio con Windows, comportamiento
                                      de bandeja, moneda, zona horaria y
                                      preferencias.

  Diagnóstico                         Último error, logs redactados,
                                      versión de aplicación y guía de
                                      solución.
  -----------------------------------------------------------------------

## 9.2 Comportamiento en segundo plano

-   Inicio automático configurable al iniciar sesión en Windows.

-   Al cerrar la ventana, minimizar a bandeja por defecto; mostrar
    confirmación si el usuario intenta salir completamente.

-   Menú de bandeja: Abrir, Pausar/reanudar captura, Silenciar/activar
    voz, Estado de conectores y Salir.

-   No reproducir anuncios históricos al iniciar; solo eventos nuevos
    posteriores al cursor persistido.

-   Mostrar notificación visual de eventos cuando la voz esté silenciada
    o falle.

## 9.3 Mensajes de voz

  -----------------------------------------------------------------------
  **Situación**                       **Mensaje sugerido**
  ----------------------------------- -----------------------------------
  Evento validado                     "Pago recibido por \[monto\] pesos
                                      desde \[entidad\]."

  Notificación no concluyente         "Nueva notificación de \[entidad\]
                                      por \[monto\] pesos. Verifica el
                                      pago."

  Evento duplicado                    No anunciar; registrar duplicado.

  Error de audio                      Mostrar aviso visual y conservar el
                                      evento.

  Conector desconectado               "Atención: la conexión de
                                      \[fuente\] está desconectada."
  -----------------------------------------------------------------------

## 9.4 Accesibilidad y control

La voz debe poder silenciarse de inmediato. Los datos importantes
también deben estar disponibles visualmente. Los importes se presentarán
en formato colombiano, por ejemplo \$125.000, sin decimales. La
aplicación no debe depender exclusivamente del color para comunicar
estados.

# 10. Requisitos de instalación, rendimiento y operación

## 10.1 Requisitos mínimos propuestos

  -----------------------------------------------------------------------
  **Recurso**                         **Mínimo propuesto (por validar)**
  ----------------------------------- -----------------------------------
  Sistema operativo                   Windows 10/11 de 64 bits con
                                      soporte vigente.

  Procesador                          CPU x64 de doble núcleo.

  Memoria                             4 GB RAM; recomendado 8 GB.

  Almacenamiento                      500 MB para aplicación y base
                                      inicial; crecimiento según
                                      retención.

  Red                                 Internet para Gmail, licencia y
                                      webhook remoto.

  Audio                               Dispositivo de salida habilitado
                                      para anuncios.
  -----------------------------------------------------------------------

## 10.2 Operación sin Internet

La interfaz, historial local y consulta de movimientos almacenados deben
funcionar sin conexión. Gmail no podrá sincronizar y un webhook remoto
puede no llegar al equipo. Los conectores deben reflejar claramente su
estado y sincronizar posteriormente sin duplicar. La garantía de entrega
desde Android depende de que MacroDroid pueda reintentar o almacenar
eventos; debe validarse con el teléfono real.

## 10.3 Logs y soporte

-   Niveles DEBUG (solo desarrollo), INFO, WARN y ERROR.

-   Rotación por tamaño/fecha y retención configurable.

-   Identificador de correlación por evento para seguir recepción,
    análisis, validación, persistencia y anuncio.

-   Redacción automática de tokens, correos completos y datos sensibles.

-   Exportación de paquete de diagnóstico con consentimiento del
    usuario.

# 11. Estrategia de pruebas y aceptación

## 11.1 Niveles de prueba

  -----------------------------------------------------------------------
  **Nivel**               **Qué valida**          **Ejemplos**
  ----------------------- ----------------------- -----------------------
  Unitarias               Reglas y parsers        COP \$50.000;
                          aislados.               \$1.250.000; monto sin
                                                  símbolo; remitente con
                                                  tildes.

  Integración             Conectores, base y      OAuth, lectura Gmail,
                          servicios.              webhook, SQLite, TTS.

  Sistema                 Flujos completos.       Evento → validación →
                                                  registro → tablero →
                                                  voz.

  Seguridad               Controles y abuso.      Token inválido, replay,
                                                  payload gigante, logs
                                                  con secretos.

  Compatibilidad          Windows y equipos       Inicio automático,
                          objetivo.               bandeja, audio,
                                                  reinicio.

  Aceptación              Uso real supervisado.   Transferencias de
                                                  prueba y comparación
                                                  con canal oficial.
  -----------------------------------------------------------------------

## 11.2 Casos de prueba mínimos

  -----------------------------------------------------------------------------
  **ID**            **Escenario**     **Procedimiento**      **Resultado
                                                             esperado**
  ----------------- ----------------- ---------------------- ------------------
  CP-001            Inicio normal     Abrir aplicación y     Tablero visible;
                                      cargar tablero.        conectores
                                                             muestran estado
                                                             real.

  CP-002            Inicio minimizado Iniciar con Windows    Proceso aparece en
                                      habilitado.            bandeja; no abre
                                                             ventanas
                                                             inesperadas según
                                                             preferencia.

  CP-003            Gmail autorizado  Autorizar cuenta de    Conector conectado
                                      prueba.                y permiso
                                                             limitado.

  CP-004            OAuth revocado    Revocar autorización   Conector informa
                                      Google.                requiere
                                                             autorización; no
                                                             se bloquea la app.

  CP-005            Correo válido     Procesar plantilla     Campos extraídos
                                      real aprobada.         correctamente y
                                                             evento persistido.

  CP-006            Correo            Procesar formato no    Evento pendiente;
                    desconocido       reconocido.            no se anuncia como
                                                             confirmado.

  CP-007            Webhook válido    Enviar evento Android  Evento recibido y
                                      firmado/autenticado.   normalizado.

  CP-008            Webhook inválido  Enviar token/firma     Solicitud
                                      incorrecta.            rechazada y
                                                             auditada sin
                                                             almacenar pago.

  CP-009            Replay            Reenviar el mismo      No se crea segundo
                                      evento.                movimiento.

  CP-010            Monto inválido    Monto cero, negativo o Rechazo o revisión
                                      malformado.            según regla; nunca
                                                             contabilizar
                                                             silenciosamente.

  CP-011            Audio silenciado  Recibir evento         Movimiento
                                      elegible con voz       visible; no se
                                      apagada.               reproduce audio.

  CP-012            Reinicio          Cerrar Windows         Base íntegra;
                                      abruptamente y         cursor y
                                      reiniciar.             movimientos
                                                             recuperados.

  CP-013            Sin Internet      Desconectar red.       Historial local
                                                             disponible y
                                                             conectores
                                                             marcados
                                                             desconectados.

  CP-014            Evento duplicado  Mismo pago llega por   Detección de
                    Gmail/Android     dos fuentes.           posible duplicado
                                                             y resolución
                                                             segura.

  CP-015            Zona horaria      Evento cerca de        Fecha local de
                                      medianoche.            Colombia correcta.
  -----------------------------------------------------------------------------

## 11.3 Criterios de aceptación del MVP

-   La aplicación instala, inicia y opera en Windows en segundo plano.

-   El tablero y el historial muestran únicamente datos persistidos.

-   Un evento repetido no genera dos movimientos contabilizados.

-   Los eventos dudosos no se anuncian como pagos confirmados.

-   El conector Gmail usa OAuth y puede revocarse.

-   El puente Android rechaza solicitudes no autenticadas.

-   La voz se puede activar y silenciar.

-   La base local sobrevive a reinicio y cuenta con procedimiento de
    respaldo/restauración.

-   No quedan defectos críticos o altos de seguridad abiertos.

-   El propietario acepta los flujos y mensajes de voz con pruebas
    controladas.

# 12. Despliegue, operación y soporte

## 12.1 Entornos

  -----------------------------------------------------------------------
  **Entorno**             **Uso**                 **Datos**
  ----------------------- ----------------------- -----------------------
  Desarrollo              Programación local y    Datos ficticios;
                          pruebas unitarias.      credenciales de prueba.

  Pruebas                 Validar conectores y    Cuenta/canales de
                          flujos de aceptación.   prueba autorizados.

  Producción              Uso comercial en el     Cuenta real del
                          negocio.                titular, permisos
                                                  mínimos y respaldo.
  -----------------------------------------------------------------------

## 12.2 Instalación

-   Empaquetar runtime y dependencias necesarias para evitar que el
    usuario instale Java manualmente, si se confirma JPackage como
    solución.

-   Instalador firmado o con hash publicado y canal de distribución
    confiable.

-   Crear acceso directo y opción de inicio con Windows.

-   Crear directorio de datos separado del directorio de instalación.

-   Asistente de primera ejecución: licencia, privacidad, Gmail, Android
    opcional, voz y prueba.

-   Desinstalación que pregunte expresamente si el usuario desea
    conservar o eliminar datos locales.

## 12.3 Actualizaciones

Las actualizaciones deben comprobar firma/integridad, mostrar versión y
notas, realizar respaldo antes de migraciones de base y permitir
recuperación si la actualización falla. No se actualizará
silenciosamente una versión que pueda alterar el comportamiento de
anuncios o conectores.

## 12.4 Soporte

El soporte debe solicitar versión, sistema operativo, estado del
conector y paquete de diagnóstico redactado. Nunca solicitar
contraseñas, PIN, códigos de autenticación ni acceso remoto no
autorizado.

# 13. Plan de implementación

  -----------------------------------------------------------------------
  **Fase**                **Entregables**         **Condición de salida**
  ----------------------- ----------------------- -----------------------
  0\. Cierre de           Aprobación de alcance,  Requisitos MUST
  requisitos              estados, mensajes,      aprobados; TBD críticos
                          despliegue Android y    resueltos.
                          licencias.              

  1\. Esqueleto Windows   Proyecto Java, JavaFX,  Arranque, minimizar,
                          Maven, bandeja, ciclo   abrir y salir
                          de vida y               funcionan.
                          configuración.          

  2\. Dominio y SQLite    Modelo de eventos,      Pruebas unitarias e
                          transacciones, estados, integridad de base
                          repositorios, auditoría aprobadas.
                          e idempotencia.         

  3\. Tablero e historial Resumen, listado,       Datos correctos desde
                          filtros básicos y       SQLite.
                          pendientes.             

  4\. Voz                 Cola de anuncios,       No duplica ni anuncia
                          políticas,              eventos históricos.
                          silenciamiento y        
                          pruebas.                

  5\. Gmail               OAuth, sincronización   Plantillas reales
                          incremental, parser y   validadas; no se
                          revocación.             atribuye cobertura
                                                  inexistente.

  6\. Android             Contrato webhook,       Pruebas con dispositivo
                          autenticación,          real y red real.
                          MacroDroid, reintentos  
                          y diagnóstico.          

  7\. Seguridad y         Pruebas de abuso,       Sin vulnerabilidades
  resiliencia             respaldo, recuperación, críticas/altas
                          logs y actualización.   conocidas.

  8\. Empaquetado y       Instalador, guía de     Criterios del MVP
  piloto                  usuario, piloto y       cumplidos.
                          aceptación.             
  -----------------------------------------------------------------------

## 13.1 Método de trabajo

-   Trabajar por incrementos pequeños y demostrables.

-   No desarrollar un conector hasta que su contrato de entrada y casos
    de prueba estén definidos.

-   Mantener código en control de versiones y cambios revisables.

-   Cada requisito debe tener al menos un caso de prueba asociado.

-   Mantener una lista de decisiones y riesgos actualizada.

-   Demostrar al propietario cada incremento antes de avanzar al
    siguiente.

-   No copiar literalmente el código de la guía suministrada sin
    revisión de seguridad y calidad.

## 13.2 Primera entrega técnica

La primera entrega de programación debe incluir: proyecto Maven Java 21,
ventana JavaFX, ciclo de vida de bandeja, configuración de inicio,
esquema SQLite, entidad PaymentEvent, repositorio, registro de logs y
pruebas unitarias. Gmail, Android y voz real se incorporarán después de
verificar el núcleo.

# 14. Riesgos, supuestos y dependencias

  -------------------------------------------------------------------------------------
  **ID**            **Riesgo**                   **Nivel**         **Mitigación**
  ----------------- ---------------------------- ----------------- --------------------
  R-01              Nequi puede no enviar correo Alta              Usar
                    por cada transferencia                         Android/MacroDroid
                    estándar.                                      como fuente
                                                                   prevista; validar
                                                                   con pruebas reales.

  R-02              Notificación falsa o         Alta              No tratar texto como
                    manipulada.                                    prueba bancaria;
                                                                   autenticación de
                                                                   webhook y política
                                                                   de validación.

  R-03              Cambios en formato de        Alta              Parsers versionados,
                    correos/notificaciones.                        pruebas de regresión
                                                                   y bandeja de
                                                                   revisión.

  R-04              MacroDroid detenido por      Media/Alta        Guía de permisos,
                    ahorro de batería/permisos.                    monitoreo y pruebas
                                                                   en dispositivo real.

  R-05              Computador y teléfono en     Alta              Decidir receptor
                    redes distintas.                               local o servicio
                                                                   intermedio; no
                                                                   exponer puertos sin
                                                                   seguridad.

  R-06              Restricciones/verificación   Media/Alta        Revisar scopes, tipo
                    OAuth de Google.                               de aplicación y
                                                                   requisitos para
                                                                   distribución
                                                                   comercial.

  R-07              Pérdida/daño del equipo.     Alta              Copias externas,
                                                                   restauración probada
                                                                   y política de
                                                                   retención.

  R-08              Doble evento por dos         Media             Idempotencia global
                    canales.                                       y revisión de
                                                                   coincidencias.

  R-09              Licencia de prueba alterada  Media             Diseño de licencias
                    por reloj o reinstalación.                     firmado y validación
                                                                   periódica; reglas
                                                                   comerciales
                                                                   pendientes.

  R-10              La voz no está disponible o  Media             Fallback visual,
                    se bloquea.                                    prueba de audio y
                                                                   cola de anuncios.
  -------------------------------------------------------------------------------------

## 14.1 Supuestos

-   El titular autoriza expresamente la lectura de su Gmail y el acceso
    a notificaciones del teléfono.

-   El uso inicial será en Colombia y COP.

-   El computador Windows estará encendido para ejecutar la aplicación
    de escritorio.

-   El usuario podrá verificar manualmente operaciones en los canales
    oficiales.

-   El servicio de notificaciones de cada entidad puede cambiar sin
    aviso a Vivanet Pay.

## 14.2 Dependencias externas

-   Disponibilidad y políticas de Google Gmail API/OAuth.

-   Disponibilidad de notificaciones de Nequi y permisos Android.

-   Funcionamiento y licencia de MacroDroid.

-   Conectividad de red entre Android y receptor.

-   Voces TTS instaladas en Windows.

-   Proveedor de licenciamiento y distribución comercial, aún por
    definir.

# 15. Trazabilidad y control de cambios

## 15.1 Matriz resumida de trazabilidad

  ------------------------------------------------------------------------------
  **Necesidad/objetivo**   **Requisitos**    **Caso de uso**   **Pruebas**
  ------------------------ ----------------- ----------------- -----------------
  Objetivo: capturar pagos RF-005 a RF-012   CU-03             CP-005 a CP-010

  Objetivo: revisar pagos  RF-013, RF-014,   CU-04, CU-06      CP-006, CP-014
                           RF-023                              

  Objetivo: avisos por voz RF-015, RF-016    CU-05             CP-011

  Objetivo: Gmail seguro   RF-021; RNF-001 a CU-02             CP-003, CP-004
                           RNF-004                             

  Objetivo: Android seguro RF-022; RNF-003,  CU-03             CP-007, CP-008,
                           RNF-005                             CP-009

  Objetivo: operación      RF-001 a RF-004,  CU-01, CU-07      CP-001, CP-002,
  confiable                RF-017, RF-018                      CP-012, CP-013
  ------------------------------------------------------------------------------

## 15.2 Gestión de cambios

-   Toda solicitud de cambio debe incluir motivo, beneficio, impacto,
    urgencia y requisitos afectados.

-   Analizar impacto en alcance, arquitectura, datos, seguridad,
    pruebas, licencias y calendario.

-   Aprobar el cambio antes de implementarlo.

-   Actualizar requisito, caso de uso, matriz de trazabilidad y casos de
    prueba.

-   Mantener historial de versiones y decisiones; no sobrescribir
    acuerdos anteriores sin registrar el cambio.

## 15.3 Definición de terminado (Definition of Done)

-   Código revisado y compilación reproducible.

-   Pruebas unitarias e integración relevantes aprobadas.

-   Requisitos asociados trazados y demostrados.

-   Errores y logs revisados; no se exponen secretos.

-   Documentación de configuración y soporte actualizada.

-   Aceptación del propietario cuando corresponda.

# 16. Decisiones pendientes para cerrar la línea base

Las siguientes decisiones son necesarias para convertir este documento
en una especificación completamente aprobada. No se deben resolver por
suposición.

  -----------------------------------------------------------------------------
  **ID**            **Tema**          **Pregunta/decisión     **Prioridad**
                                      requerida**             
  ----------------- ----------------- ----------------------- -----------------
  DP-01             Fuente de         ¿Qué evidencia          Crítica
                    validación        permitirá decir "pago   
                                      confirmado":            
                                      comprobación manual en  
                                      la app, notificación    
                                      oficial con criterios   
                                      estrictos u otra        
                                      integración autorizada? 

  DP-02             Alcance Gmail     ¿Qué bancos/correos se  Alta
                                      procesarán en el MVP    
                                      además de estudiar el   
                                      correo disponible de    
                                      Nequi?                  

  DP-03             Android remoto    ¿El Android y el PC     Crítica
                                      estarán normalmente en  
                                      la misma red Wi-Fi o    
                                      deben funcionar desde   
                                      cualquier lugar?        

  DP-04             Receptor webhook  ¿Se acepta un pequeño   Crítica
                                      servicio intermedio en  
                                      la nube para que        
                                      Android alcance al PC   
                                      fuera de la red local?  

  DP-05             Almacenamiento    ¿La base será           Alta
                                      exclusivamente local o  
                                      se requiere             
                                      sincronización/backup   
                                      cloud desde la primera  
                                      versión?                

  DP-06             Multiempresa      ¿Cada instalación       Alta
                                      representa un único     
                                      negocio/cuenta o se     
                                      venderá a múltiples     
                                      negocios con datos      
                                      aislados?               

  DP-07             Licenciamiento    Definir precio,         Alta
                                      duración posterior a    
                                      prueba, renovación,     
                                      modo sin conexión,      
                                      activación y política   
                                      de reinstalación.       

  DP-08             Exportación       ¿CSV, Excel, PDF o      Media
                                      combinación?            

  DP-09             Voz               ¿Anunciar monto y banco Media
                                      solamente o también     
                                      remitente? ¿Qué voz y   
                                      volumen por defecto?    

  DP-10             Retención         ¿Cuánto tiempo          Alta
                                      conservar eventos,      
                                      datos personales, logs  
                                      y respaldos?            

  DP-11             Instalador        ¿Distribución directa   Media
                                      con instalador firmado, 
                                      Microsoft Store u otro  
                                      canal?                  

  DP-12             Correcciones      Definir quién puede     Alta
                                      corregir/anular un      
                                      movimiento y qué        
                                      evidencia y auditoría   
                                      se exige.               
  -----------------------------------------------------------------------------

## 16.1 Decisiones ya registradas

  -----------------------------------------------------------------------
  **Decisión**            **Estado**              **Detalle**
  ----------------------- ----------------------- -----------------------
  Nombre del producto     APROBADO                Vivanet Pay.

  Plataforma principal    APROBADO                Aplicación de
                                                  escritorio Windows.

  Arquitectura Android    APROBADO                Preparada para Windows
                                                  y Android; Windows es
                                                  principal.

  Funcionamiento          APROBADO                Inicio y operación en
                                                  segundo plano,
                                                  minimizada en bandeja.

  Primera fuente a        APROBADO                Gmail; el usuario
  estudiar                                        indicó que el correo
                                                  disponible es Gmail.

  Primer caso bancario    APROBADO                Nequi, modalidad
                                                  personal.

  Modelo comercial        APROBADO                Producto comercial.

  Prueba gratuita         APROBADO                14 días sin exigir
                                                  tarjeta ni medio de
                                                  pago al registrarse.

  Java 21 / JavaFX /      PROPUESTO               Base técnica
  Maven / SQLite                                  recomendada; falta
                                                  validar entorno y
                                                  aprobar stack final.

  MacroDroid              PROPUESTO               Puente Android descrito
                                                  en la guía técnica;
                                                  requiere prueba con
                                                  dispositivo real.
  -----------------------------------------------------------------------

# Anexo A. Referencia técnica suministrada

Documento aportado por el propietario: "Guía Paso a Paso -
Automatización de Transferencias (Nequi, Apps Script y Tablero con
Voz)", 15 páginas.

  -----------------------------------------------------------------------
  **Páginas**             **Contenido usado como  **Tratamiento en
                          referencia**            Vivanet Pay**
  ----------------------- ----------------------- -----------------------
  1                       Arquitectura: correo    Se conserva la
                          para entidades que      distinción entre fuente
                          notifican;              Gmail y puente Android;
                          Android/MacroDroid para se reemplazan
                          notificación push       Sheets/Apps Script en
                          Nequi; Sheets y panel   la arquitectura
                          con voz.                objetivo.

  2--7                    Esquema de movimientos, Se reutilizan conceptos
                          Apps Script, búsqueda   de campos y
                          de correos,             procesamiento; el
                          deduplicación y cálculo código requiere
                          de tablero.             rediseño y revisión.

  8--13                   Panel HTML, consulta    Se adopta la
                          periódica y síntesis de experiencia funcional;
                          voz del navegador.      la implementación será
                                                  JavaFX y voz Windows.

  14--15                  Configuración           Se toma como guía de
                          MacroDroid, POST HTTP y prototipo; se exige
                          solución de problemas.  HTTPS, autenticación
                                                  robusta, idempotencia y
                                                  validación.
  -----------------------------------------------------------------------

Observación: el código de la guía utiliza un token de ejemplo en el
código, un webhook desplegado con acceso amplio y expresiones regulares
simples. Esos elementos no deben copiarse a producción sin rediseño de
seguridad. La guía se considera referencia funcional, no código aprobado
para producción.

# Anexo B. Contrato conceptual del evento

Ejemplo conceptual de evento normalizado (no es un endpoint productivo
ni contiene credenciales):

{\
\"eventId\": \"identificador-unico-del-origen\",\
\"source\": \"ANDROID_NEQUI\",\
\"sourceTimestamp\": \"2026-11-22T18:30:05Z\",\
\"receivedAt\": \"2026-11-22T18:30:06Z\",\
\"bank\": \"Nequi\",\
\"amount\": 50000,\
\"currency\": \"COP\",\
\"sender\": \"Nombre si está disponible\",\
\"reference\": \"Referencia si está disponible\",\
\"rawText\": \"Contenido de notificación permitido\",\
\"schemaVersion\": 1\
}

El contrato definitivo debe definir validación de esquema, límites de
longitud, campos obligatorios, firma/autenticación, timestamp, nonce y
política de retención del texto original.

# Anexo C. Glosario

  -----------------------------------------------------------------------
  **Término**                         **Definición**
  ----------------------------------- -----------------------------------
  Evento                              Aviso recibido desde una fuente
                                      externa; no implica necesariamente
                                      que el pago esté confirmado.

  Transacción                         Registro normalizado que Vivanet
                                      Pay presenta en el historial.

  Conector                            Componente que integra una fuente
                                      externa, por ejemplo Gmail o
                                      Android.

  Webhook                             Solicitud HTTP que un sistema envía
                                      a un receptor para notificar un
                                      evento.

  OAuth 2.0                           Mecanismo de autorización para que
                                      Vivanet Pay acceda a Gmail sin
                                      conocer la contraseña.

  Idempotencia                        Propiedad por la que procesar
                                      repetidamente el mismo evento no
                                      crea movimientos duplicados.

  TTS                                 Text-to-Speech; tecnología que
                                      convierte texto en voz.

  MVP                                 Producto mínimo viable: primera
                                      versión con alcance acotado y
                                      verificable.

  Trazabilidad                        Relación verificable entre
                                      objetivos, requisitos, casos de
                                      uso, implementación y pruebas.
  -----------------------------------------------------------------------

# Cierre y condición de uso

Este IDD constituye la línea base inicial de Vivanet Pay. Puede
utilizarse para planificar el desarrollo y preparar prototipos, pero no
debe interpretarse como autorización automática para desplegar en
producción. Antes de producción deben aprobarse los puntos pendientes
críticos, completar análisis de seguridad, validar fuentes reales de
notificaciones y ejecutar los criterios de aceptación.

Siguiente hito recomendado: aprobar los requisitos MUST y resolver
DP-01, DP-03 y DP-04 (criterio de confirmación del pago, conectividad
Android--Windows y ubicación del receptor webhook). Después se congela
la línea base 1.0 y se inicia la Fase 1 de implementación.
