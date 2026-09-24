# Gesmin Backend — Reporte de validación técnica

**Fecha:** 2026-09-23 · **Sesión:** 17:04 (una sola corrida) · **Ambiente:** Linux, Java 21 (Temurin), Spring Boot 4.1.1, H2 en archivo

> Validación adversarial del flujo completo `Cliente → Cotización → aprobación → Orden de Trabajo → Evaluación de aptitud → Calibración → Revisión Técnica → Informe → ENVIADO → cierre de expediente`, con casos negativos y reporte honesto de fallos.
>
> **Regla cumplida:** no se resembrió nada. No hay seeds en el proyecto y no se recargaron datos: toda la cadena se construyó exclusivamente vía API desde base vacía.

---

## 0. Preparación del ambiente

| Item | Resultado |
|---|---|
| Config H2 | Cambiada de `jdbc:h2:mem:testdb` a `jdbc:h2:file:./data/gesmin;DB_CLOSE_ON_EXIT=FALSE` en `src/main/resources/application.properties`. **El cambio fue necesario** (era requisito de la validación); no se tocó nada más del código. |
| Compilación | ✅ `./mvnw compile` → 0 errores |
| Tests | ✅ `./mvnw test` → BUILD SUCCESS (`BackendApplicationTests.contextLoads`). Nota: falla si corre con el server levantado en paralelo (colisión de lock H2 en `./data/`); con el server detenido pasa limpio. |
| Reinicios por devtools | **0** en toda la corrida (`grep "Restarting due to"` = 0 en los logs del server). |
| Base persistida | `./data/gesmin.mv.db` — sobrevivió 3 arranques del server sin reseed. |

---

## 1. Flujo completo (CASO 1 — camino feliz, una sola corrida)

Todos los pasos con request, response y HTTP en `validation/requests.log` (125 requests en total). Resumen:

| Paso | Request | HTTP | Resultado |
|---|---|---|---|
| Crear cliente | `POST /api/clientes` | 200 | id=1, RUC validado 11 dígitos |
| Crear instrumento | `POST /api/instrumentos` | 201 | id=1, nace `UBICADO_EN_CLIENTE` |
| Crear servicio | `POST /api/servicios` | 201 | id=1 |
| Crear expediente | `POST /api/expedientes?clienteId=1` | 200 | `E260901`, estado `EN_PROCESO` |
| Crear cotización | `POST /api/cotizaciones` | 201 | `COI260901`, `montoTotal` calculado, snapshot de servicio |
| Aprobar cotización | `PATCH /api/cotizaciones/1/estado?estado=APROBADA` | 200 | |
| Crear OT | `POST /api/ordenes-trabajo` | 201 | `OT260901`, nace `PENDIENTE` |
| Crear evaluación | `POST /api/evaluaciones-aptitud` | 201 | nace `PENDIENTE` |
| Registrar APTO | `PATCH /api/evaluaciones-aptitud/1/resultado?resultado=APTO` | 200 | |
| Crear patrón | `POST /api/herramientas` | 201 | id=1 |
| Crear calibración | `POST /api/calibraciones` | 201 | nace `PROGRAMADA`, ≥1 patrón |
| Registrar mediciones | `PUT /api/calibraciones/1/mediciones` | 200 | `error` calculado (10.02−10.00=0.02), estado → `EN_PROCESO` |
| Completar calibración | `PATCH /api/calibraciones/1/estado?estado=COMPLETADA` | 200 | |
| Crear revisión | `POST /api/revisiones-tecnicas` | 201 | nace `PENDIENTE` |
| Registrar CONFORME | `PATCH /api/revisiones-tecnicas/1/resultado?resultado=CONFORME` | 200 | **genera automáticamente informe `IT260901`** estado `GENERADO` |
| Marcar PDF | `PATCH /api/informes-tecnicos/1/pdf-cargado` | 200 | `pdfCargado=true`, estado `PDF_CARGADO` |
| Aprobar informe | `PATCH /api/informes-tecnicos/1/estado?estado=APROBADO` | 200 | |
| Enviar informe | `PATCH /api/informes-tecnicos/1/estado?estado=ENVIADO` | 200 | `fechaEnvio` llenada automáticamente |
| Cerrar expediente | `PATCH /api/expedientes/1/estado?estado=CERRADO` | 200 | |

**Veredicto: ✅ 19/19 pasos OK sin atajos.**

---

## 2. Casos negativos

| # | Caso | Esperado según diseño | Pasó de verdad | HTTP | Veredicto |
|---|---|---|---|---|---|
| 2 | Dos revisiones **simultáneas** sobre la misma calibración COMPLETADA | ⚠️ no definido | **Ambas se crearon** (revisiones #2 y #3 sobre calibración #1, sin conflicto) | 201, 201 | ❌ si la regla es "una revisión activa por calibración" / ⚠️ si no |
| 3 | Informe sobre calibración ya ENVIADA (único camino posible: nueva revisión CONFORME) | ⚠️ no hay guard post-ENVIADO en el diseño | Se creó revisión #4 CONFORME y **generó un segundo certificado (`IT260902`)** para el instrumento ya ENVIADO | 200 | ❌ |
| 4 | Segundo informe para la misma revisión | No debería duplicarse | `POST /informes-tecnicos` no existe (405 ✅), pero **re-parchear CONFORME sobre la revisión #1 generó `IT260903`**: la revisión #1 terminó con **2 informes** | 405 / 200 | ❌ |
| 5 | Idempotencia: dos PATCH con el mismo estado (informe ENVIADO, cotización APROBADA, calibración COMPLETADA, expediente CERRADO) | ⚠️ no definido | Todos 200, misma representación, `fechaEnvio` sin cambios (mismo día) | 200 ×4 | ✅ idempotente de facto |
| 6 | Saltar estado: informe `GENERADO → ENVIADO` directo (sin PDF_CARGADO ni APROBADO) | ⚠️ no definido si se bloquea | **No lo bloquea**: quedó ENVIADO con `pdfCargado=false` y sin aprobación previa | 200 | ⚠️ |
| 7a | Revisión sobre calibración PROGRAMADA | 400 con guard | `{"mensaje":"No se puede revisar: la calibración debe estar COMPLETADA (estado actual: PROGRAMADA)."}` | 400 | ✅ |
| 7b | Revisión sobre calibración EN_PROCESO | 400 con guard | `{"mensaje":"...(estado actual: EN_PROCESO)."}` | 400 | ✅ |
| 8 | Evaluación NO_APTO | OT → `EN_ESPERA_CLIENTE`; calibración bloqueada | OT quedó `EN_ESPERA_CLIENTE` ✅; `POST /calibraciones` sobre evaluación NO_APTO → `{"mensaje":"No se puede programar una calibración: la evaluación de aptitud debe tener resultado APTO."}` ✅ | 200 / 400 | ✅ |
| 9 | Cierre de expediente con OT CANCELADA y evaluación NO_APTO **sin respuesta del cliente** | ⚠️ diseño no define validación de cierre | **Lo permite sin ningún check.** Además se pudo "reabrir" un expediente CERRADO (PATCH a `EN_PROCESO`) y cerrarlo de nuevo | 200 ×3 | ⚠️ |
| 10 | Loop de corrección: 3× `NO_CONFORME` seguidos | Sin límite según diseño | 3 iteraciones OK (`EN_PROCESO → mediciones → COMPLETADA → nueva revisión`). **No hay límite ni contador.** Las revisiones NO_CONFORME se acumulan con su calibración padre (no huérfanas); las PENDIENTE no reclamadas del caso 2 sí quedan huérfanas para siempre | 200/201 | ✅ comportamiento / ⚠️ sin tope |
| 11 | Dos correlativos concurrentes (2 CONFORME simultáneos → 2 informes) | Números únicos | **COLISIÓN REAL**: uno ganó `IT260905` (200), el otro → 400 con `Unique index or primary key violation ... VALUES 'IT260905'` (stacktrace crudo de H2 al cliente). **Rollback limpio**: la revisión perdedora quedó `PENDIENTE`, sin informe y re-intentable | 200 / 400 | ⚠️ (sin corrupción, pero falla feo y pierde la operación) |
| 12 | `GET /{id}` con id=99999 en clientes / calibraciones / informes | 400 según API.md ("no encontrado = 400, no 404") | 400 con `{"mensaje":"... no encontrado con id 99999"}` en las 3 entidades | 400 ×3 | ✅ consistente con diseño (debatible como REST) |
| 13 | Persistencia tras reinicio + correlativo continuo | Estado intacto, correlativo no reinicia | Expediente CERRADO, informe ENVIADO y calibración COMPLETADA intactos tras reinicio; nueva CONFORME generó **`IT260906`** (no `IT260901` de nuevo) | 200/201 | ✅ |

---

## 3. Hallazgos y riesgos

| # | Severidad | Hallazgo | Dónde |
|---|---|---|---|
| H1 | 🔴 ALTA | **Re-parchear CONFORME sobre una revisión genera un informe duplicado cada vez.** `registrarResultado` llama a `generarDesdeRevision` sin verificar si ya existe informe para esa revisión. Evidencia: revisión #1 terminó con `IT260901` (ENVIADO) + `IT260903` (GENERADO). Riesgo directo de certificados duplicados (trazabilidad ISO 17025). Fix sugerido: guard `existsByRevisionTecnicaId` o endpoint idempotente. | `RevisionTecnicaService.registrarResultado` |
| H2 | 🔴 ALTA | **Sin guard post-ENVIADO.** Una calibración con certificado ya ENVIADO acepta nuevas revisiones CONFORME y vuelve a certificar el mismo instrumento (`IT260902`). Si Gesmin no quiere re-certificación sobre expedientes cerrados, falta el bloqueo. | `RevisionTecnicaService.crear` / `registrarResultado` |
| H3 | 🟠 MEDIA | **Correlativo `count()+1` no es thread-safe** (afecta IT, OT, COI y expedientes). Bajo concurrencia hay colisión → 400 con stacktrace crudo de H2. El unique constraint evita corrupción, pero habría que capturar la excepción y reintentar, o usar secuencia. Evidencia: caso 11. | `generarNumero()` en Informe/OT/Cotización/Expediente |
| H4 | 🟠 MEDIA | **No hay máquina de estados en los PATCH de estado**: se acepta cualquier transición (GENERADO→ENVIADO, COMPLETADA→PROGRAMADA, estados regresivos). Los únicos guards reales son los de creación (OT requiere APROBADA, calibración requiere APTO, revisión requiere COMPLETADA). | Todos los `actualizarEstado` de los services |
| H5 | 🟡 BAJA | **Cierre de expediente sin validación**: se cierra con OT CANCELADA, evaluación NO_APTO sin respuesta, o informes GENERADO nunca enviados. También se reabre un CERRADO con un PATCH. Confirmar con Gesmin si el cierre debe validar algo. | `ExpedienteService.cambiarEstado` |
| H6 | 🟡 BAJA | Revisiones simultáneas sobre la misma calibración se permiten (caso 2); las PENDIENTE no reclamadas quedan huérfanas funcionales (sin resultado, sin informe, para siempre). No corrompen nada. | `RevisionTecnicaService.crear` |
| H7 | ⚪ INFO (ambiente) | La sandbox de ejecución mata procesos hijos entre comandos: en el 2do arranque el server murió sin shutdown limpio y **se perdió 1 commit en flight** (el informe `IT260906` creado y verificado vía GET desapareció; su revisión #11 quedó `PENDIENTE`). **No fue devtools** (0 reinicios) ni falla de persistencia (los reinicios limpios preservaron todo). No se atribuye al código. | ambiente de ejecución |
| H8 | ⚪ INFO | `GET inexistente` devuelve 400 (no 404), consistente con lo documentado en API.md, pero cualquier cliente REST estándar esperará 404. Decisión de producto pendiente. | `GlobalExceptionHandler` |

---

## 4. Supuestos tomados (validar contra Gesmin)

1. **"Informe sobre calibración ENVIADA"** (caso 3): no existe endpoint de creación de informes, así que se probó por el único camino posible — nueva revisión CONFORME sobre esa calibración. Si Gesmin define otro camino (ej. re-emisión de certificado), el test sería otro.
2. **Re-parchear CONFORME sobre una revisión ya CONFORME** (caso 4): se asumió que re-ejecutar el PATCH original no debía generar un segundo informe. Si la regla de Gesmin es "el PATCH de resultado vale una sola vez", el hallazgo H1 aplica tal cual.
3. **Idempotencia** (caso 5): se asumió "misma representación observable" como criterio; `fechaEnvio` no pudo verificarse en días distintos (corrida de un solo día).
4. **Cierre de expediente** (caso 9): se asumió que CERRADO es terminal salvo acción administrativa; la re-apertura vía PATCH se trató como capacidad, no como bug, porque el diseño no lo prohíbe.
5. **Loop sin tope** (caso 10): no hay máximo documentado en API.md/DB.md; la acumulación de revisiones se reportó como comportamiento, no como fallo.
6. **Concurrencia** (caso 11): se asumió "sin duplicados y sin corrupción" como requisito mínimo; el 400 crudo del perdedor se calificó ⚠️ porque el diseño de correlativos no especifica comportamiento concurrente.

---

## 5. Estado final (accesible vía GET — Regla 4)

Server levantado sobre `./data/gesmin.mv.db` (estado persistido, sin reseed):

```
GET http://localhost:8080/api/expedientes/1        → E260901  CERRADO
GET http://localhost:8080/api/ordenes-trabajo/1    → OT260901 CANCELADA
GET http://localhost:8080/api/calibraciones        → 2 calibraciones (1 COMPLETADA, 1 EN_PROCESO)
GET http://localhost:8080/api/revisiones-tecnicas?calibracionId=1 → 11 revisiones
GET http://localhost:8080/api/informes-tecnicos    → IT260901 ENVIADO · IT260902 GENERADO ·
                                                      IT260903 GENERADO · IT260904 ENVIADO · IT260905 GENERADO
```

También inspeccionable en la consola H2: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:file:./data/gesmin`, usuario `sa`, contraseña vacía).

---

## 6. Artefactos de la validación

| Archivo | Contenido |
|---|---|
| `validation/requests.log` | Los 125 requests con método, ruta, HTTP code y body de respuesta |
| `validation/harness.sh` | Helpers de logging y request |
| `validation/flow.sh` | CASO 1: flujo completo en una corrida |
| `validation/negatives.sh` | CASOS 2–12 |
| `validation/correlativos.sh` | CASO 11b: re-test correcto de correlativos concurrentes |
| `validation/rollback-check.sh` | CASO 11c: consistencia post-colisión |
| `validation/persistence.sh` | CASO 13: reinicio + persistencia |
| `validation/summary.sh` | Snapshot final de estado (solo GETs) |
| `validation/ids.env` | IDs generados en la sesión |

**Cómo re-correr** (server detenido, base limpia opcional borrando `./data/`):

```bash
./mvnw spring-boot:run &      # terminal 1
bash validation/flow.sh       # terminal 2, en orden:
bash validation/negatives.sh
bash validation/correlativos.sh
bash validation/persistence.sh
bash validation/summary.sh
```

---

## 7. Resumen ejecutivo

- **Camino feliz: ✅ 19/19 pasos** en una sola corrida, sin reseed, sin reinicios de devtools.
- **Compila y testea limpio** (`compile` + `test` BUILD SUCCESS).
- **2 hallazgos de severidad ALTA** (duplicación de informes al re-parchear CONFORME; ausencia de guard post-ENVIADO), **2 MEDIOS** (correlativo no thread-safe; sin máquina de estados en PATCH), **2 BAJOS** y **2 informativos**.
- **La persistencia en archivo funciona**: 3 arranques del server preservaron el estado y el correlativo continuó donde iba.

---

## Actualización 2026-09-24 — tres PDF y archivo firmado

La validación de 2026-09-23 anterior es histórica. El endpoint marcador `PATCH /api/informes-tecnicos/{id}/pdf-cargado` fue retirado; ahora un archivo real se carga por `POST /api/informes-tecnicos/{id}/pdf-firmado`.

| Comprobación | Resultado |
|---|---|
| Cotización, orden e informe | Se generan como tres PDF distintos con cabeceras, correlativos y datos de sus registros. Los tres se abrieron con PDFBox y sus campos clave se extrajeron correctamente. |
| Paginación | Cotización extensa se divide en varias páginas y conserva el texto final. |
| Revisión visual | Se renderizaron las tres páginas de prueba y se verificaron encabezados, secciones, firmas de la OT, pie y ausencia de texto cortado. |
| Archivo firmado inválido | Rechazado; no crea archivo ni cambia el estado. |
| Archivo firmado válido | Se guarda sin sobrescribir, actualiza `pdfCargado` y pasa a `PDF_CARGADO`. |
| Descarga para Ventas | Bloqueada antes de `APROBADO`; tras aprobación devuelve exactamente los bytes cargados. |
| Estados del informe | `GENERADO → ENVIADO` rechazado; `PDF_CARGADO → APROBADO → ENVIADO` aceptado. La aprobación comprueba la existencia física del archivo, incluso si un registro viejo tenía `pdfCargado=true`. |
| Pruebas automatizadas | `./mvnw -q -Dspring.datasource.url=jdbc:h2:mem:gesminpdf test`: 5 tests, 0 fallos. H2 en memoria evita el lock de la BD de desarrollo. |

**Alcance:** la cotización es un borrador descargable porque el modelo no tiene moneda, IGV, forma de pago, asesor ni cuentas bancarias. La orden carece de cantidad/producto por actividad y de firmas reales. El Informe Técnico usa el ejemplo entregado como referencia provisional para el documento final; aún falta el formato específico de Certificado de Calibración. El backend almacena el PDF que el usuario afirma firmado, pero no verifica firmas criptográficas y no envía correos. No se hicieron pruebas E2E HTTP contra la base persistida en esta actualización.

**Persistencia:** el PDF firmado se guarda en `./data/pdf-firmados/{id}.pdf`, fuera de Git. Un respaldo debe incluir tanto `./data/gesmin.mv.db` como `./data/pdf-firmados/`. Los informes de la validación previa marcados con el antiguo booleano no tendrán archivo real y no podrán aprobarse hasta resolverlos de forma explícita.

---

## Actualización 2026-09-24 — H1, H2 y H3

Los casos y datos de la corrida del 2026-09-23 arriba permanecen como evidencia histórica. Los siguientes resultados son de pruebas nuevas sobre H2 **en memoria**; no se reejecutaron los 125 requests ni se modificó la base persistida de desarrollo.

| Caso | Resultado |
|---|---|
| H1: repetir `CONFORME` sobre la misma revisión | Solo existe un informe para la revisión; el segundo PATCH conserva el resultado y las observaciones originales. |
| H1 concurrente: dos `CONFORME` simultáneos sobre la misma revisión | Ambos terminan `CONFORME`, pero se genera un solo informe. La calibración se bloquea durante la operación. |
| H2: otra revisión tras generar un informe | Rechazada incluso antes de aprobar/enviar el informe. Una revisión `PENDIENTE` antigua tampoco puede generar un segundo IT para esa calibración. |
| Ciclo `NO_CONFORME` | La calibración vuelve a `EN_PROCESO`; después de corregirla y completarla se puede crear otra revisión. |
| H3: reintento tras rollback | El primer intento falla de forma simulada, su inserción revierte y el segundo empieza una transacción limpia. |
| H3: dos creaciones concurrentes por tipo | Expedientes, cotizaciones, OT e informes sobre **calibraciones distintas** obtuvieron números diferentes y ambas operaciones terminaron correctamente. |

`./mvnw -q -Dspring.datasource.url=jdbc:h2:mem:gesminfix test` → **10 tests, 0 fallos**. El antiguo `validation/correlativos.sh` usaba dos revisiones de la **misma** calibración: ahora ese caso debe terminar con una sola emisión por H2. Para probar H3 se requieren dos calibraciones distintas, como en la prueba de integración nueva.

**Implementación:** las creaciones de E, COI y OT y el registro de resultado que genera IT se reintentan por completo dentro de `TransactionTemplate`, con una transacción nueva por intento (máximo 3). La revisión y el informe permanecen atómicos. El informe también verifica si su revisión ya tiene uno antes de guardarse.