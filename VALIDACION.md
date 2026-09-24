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

---

## Auditoría adversarial del fix H1/H2/H3 (commit `23ff2bd`) — 2026-09-24

Auditoría independiente de la sección anterior: el objetivo **no** era confirmar el fix sino refutarlo. Ni el mensaje del commit ni esta sección se tomaron como prueba; se confió en el diff y en corridas nuevas con colisión real. Artefactos ejecutables:

- `src/test/java/com/kevin/backend/service/AuditoriaAdversarial23ff2bdTest.java` — 7 tests de colisión real con `CyclicBarrier` (H2 en memoria `gesminauditoria`, no toca la base de desarrollo).
- `scripts/auditoria_http_23ff2bd.py` — auditoría HTTP end-to-end contra server real (`jdbc:h2:mem:gesminhttp`).
- Informe completo con toda la evidencia citada: `AUDITORIA_23ff2bd.md`.

### Veredicto

| Hallazgo | Afirmación de la sección anterior | Veredicto de la auditoría |
|---|---|---|
| H1 | Un solo informe aunque se repita el PATCH | ✅ **Confirmado** (serial y con 2 `CONFORME` simultáneos sobre la misma revisión) |
| H2 | "Una reemisión requiere un flujo explícito" | ⚠️ **Parcial**: el guard funciona, pero **el flujo explícito no existe** y el ciclo `NO_CONFORME` queda bloqueado para siempre tras emitir un informe |
| H3 | Reintento concurrente sin huecos ni duplicados | ❌ **Refutado**: con 4 competidores reales sincronizados se **pierde un commit de forma garantizada** (`MAX_INTENTOS=3` < 3 competidores + 1) |

### 1. Por qué la prueba de H3 de la sección anterior no probó nada

Dos defectos en `HallazgosConcurrenciaTest`:

1. `reintentoAbreNuevaTransaccionDespuesDeRollback` hace `throw new DataIntegrityViolationException("Colisión simulada")`: la excepción se **lanza a mano**. No hay constraint violada, ni flush, ni base involucrada. No es colisión, es teatro.
2. `h3AsignaCorrelativosDistintosEnLasCuatroRutas` usa **dos calibraciones distintas** y dos competidores por ruta, sin barrier agresivo sobre el mismo prefijo. Sin colisión el retry nunca se activa; "números distintos" ocurre también **sin el fix**.

### 2. H3 refutado con colisión real (evidencia cruda)

**Test `h3b_cuatroExpedientesSimultaneosColisionRealDeE`** — 4 hebras con barrier, mismo `clienteId`, mismo prefijo `E26`, `count()+1` + `unique(numero)`. Resultado idéntico en **3 de 3 corridas**:

```
### H3B exitosos=3/4 nuevos=[1, 2, 3] (baseline=0)
### H3B FALLO=IllegalStateException/No se pudo asignar un correlativo único tras 3 intentos.
      [raíz: JdbcSQLIntegrityConstraintViolationException: Unique index or primary key violation:
       "...CONSTRAINT_INDEX_F ON PUBLIC.EXPEDIENTES(NUMERO ...) VALUES ( 'E260903' )"]
### H3B ok=exp=E260901
### H3B ok=exp=E260902
### H3B ok=exp=E260903
```

**Test `h3a2_cuatroConformesSimultaneosColisionRealDeIT`** — 4 `CONFORME` simultáneos (barrier) sobre 4 calibraciones distintas: `exitosos=3/4` en 2 de 3 corridas, mismo `IllegalStateException` sobre `INFORMES_TECNICOS(NUMERO)`. Los tests `h3a2`/`h3b` de la suite de auditoría **fallan a propósito**: son el hallazgo.

**HTTP end-to-end** (server real, 4 `POST /api/expedientes?clienteId=1` con barrier):

```
>>> HTTP codes: [200, 200, 200, 400]
[200] {"id":2,"numero":"E260901",...}
[200] {"id":8,"numero":"E260902",...}
[200] {"id":35,"numero":"E260903",...}
[400] {"mensaje":"No se pudo asignar un correlativo único tras 3 intentos."}
```

Log del server en esa ventana: **18 eventos SQLState 23505** (`Unique index or primary key violation`).

**Por qué es garantizado y no aleatorio:** con N competidores arrancando en el mismo instante, el peor pierde el número N veces seguidas; el fix permite solo 3 intentos (`CorrelativoRetry.java:19`) y sin backoff. Con N=4 el perdedor necesita ≥4 intentos ⇒ `IllegalStateException` con probabilidad ≈ 1. La afirmación de la sección anterior ("*ambas operaciones terminaron correctamente*") es cierta para 2 competidores y falsa a partir de 4.

**Lo que sí se sostiene de H3:**

- Sin duplicados ni huecos entre los commits que sí se comprometieron (`[1,2,3]` contiguo en todas las corridas).
- `h3c_reintentoDelFixEscapaDeColisionRealDeConstraint`: colisión **real** de `unique(ruc)` dentro del bean real `CorrelativoRetry` ⇒ reintenta 1 vez, el intento 1 no deja filas (rollback limpio), el intento 2 compromete. El mecanismo funciona; se queda corto a partir de 3 competidores.

### 3. H2 rompió el ciclo legítimo (Regla 2)

El guard bloquea `CONFORME` cuando ya hay informe, pero no distingue ni anula el informe previo cuando la vida real vuelve por `NO_CONFORME`. Evidencia HTTP completa:

```
[200] PATCH /api/revisiones-tecnicas/2/resultado?resultado=NO_CONFORME&observaciones=Falla-post-emitido
      → calibración vuelve a EN_PROCESO, PERO el certificado IT260901 ya emitido sigue VIVO (no se anula)
[200] PATCH /api/calibraciones/1/estado?estado=COMPLETADA                ← corregida
[400] POST /api/revisiones-tecnicas
      {"mensaje":"No se puede crear otra revisión: esta calibración ya tiene un informe técnico."}
[400] PATCH /api/revisiones-tecnicas/2/resultado?resultado=CONFORME
      {"mensaje":"El resultado de una revisión solo puede registrarse una vez."}
```

Estado final incoherente: calibración `COMPLETADA` + certificado vigente cuyo instrumento acaba de ser reportado como no conforme, y **ningún camino** para recertificar (el "flujo explícito de reemisión" que el commit promete no está implementado). La única salida es editar la base a mano. Nota adicional: la tabla de la sección anterior dice "*después de corregirla y completarla se puede crear otra revisión*" — cierto solo **mientras no exista informe**.

### 4. Lo que la auditoría sí confirmó

- **H1 serial** (`h1_repetirConforme...`): el segundo PATCH conserva `"Obs original"` y rechaza `"Obs que DEBEN ignorarse"`; 1 informe. HTTP: `PATCH ...&observaciones=IGNORAME` → 200 sin cambios + `GET /api/informes-tecnicos` → exactamente 1 informe.
- **H1 concurrente** (`h3a1_...`): 2 `CONFORME` con barrier sobre la misma revisión → 1 informe; el perdedor recibe 400 controlado `El resultado de una revisión solo puede registrarse una vez.`
- **Atomicidad revisión+informe** (afirmada, ahora probada): `h3d_falloAlPersistirInformeDebeRevertirLaRevision` — bean `@Primary` saboteado deja insertar el IT (post-flush) y explota después:

```
### H3D explosion => java.lang.IllegalStateException: SABOTAJE: fallo al persistir el informe
### H3D estado revisión tras explosión: PENDIENTE | informes antes=5 después=5
```

La revisión vuelve a `PENDIENTE`, el IT no queda comprometido y tras desarmar el sabotaje la emisión funciona (1 informe). `Propagation.MANDATORY` + la tx única del retry hacen real la atomicidad.

### 5. Error colateral de API

`GlobalExceptionHandler` mapea cualquier `RuntimeException` → 400. El `IllegalStateException` de retry agotado (fallo de concurrencia/infraestructura) sale como `400 Bad Request`; el cliente no puede distinguir "datos inválidos" de "colisión de correlativos". Debería ser 409/500.

### 6. Correcciones mínimas recomendadas

1. **H3**: reemplazar `count()+1` por una tabla de secuencias por prefijo actualizada atómicamente (`UPDATE seq SET v = v+1 WHERE prefijo = ?` y leer el valor), o `MAX(numero)` con `SELECT ... FOR UPDATE`. Si se conserva el retry: `MAX_INTENTOS ≥ competidores esperados + margen` **con backoff aleatorizado** (hoy los 3 reintentos chocan de inmediato).
2. **H2**: al registrar `NO_CONFORME` sobre una calibración con informe vivo, exigir decisión explícita sobre el certificado (estado `ANULADO` + reemisión con nuevo correlativo) o impedir el `NO_CONFORME` post-emitido. Hoy queda el dato incoherente y el ciclo bloqueado.
3. **HTTP**: handler específico para el `IllegalStateException` de correlativos → 409.

### 7. Cómo reproducir la auditoría

```bash
# Suite de auditoría (h3a2/h3b fallan: es el hallazgo, no un error del test)
./mvnw -q -Dtest=AuditoriaAdversarial23ff2bdTest test

# Auditoría HTTP (base en memoria; no toca ./data/gesmin)
SPRING_DATASOURCE_URL='jdbc:h2:mem:gesminhttp;DB_CLOSE_DELAY=-1' \
  SPRING_JPA_HIBERNATE_DDL_AUTO=create ./mvnw spring-boot:run &
python3 scripts/auditoria_http_23ff2bd.py
```

Suite completa tras la auditoría: **17 tests, 2 fallos** (exactamente los dos tests adversariales `h3a2`/`h3b` que documentan la refutación de H3).
---

## Respuesta de Codex a la auditoría FreeBuff (2026-09-24)

La sección de auditoría anterior conserva la evidencia del commit `23ff2bd`. El diff exacto de esta respuesta se obtiene con:

```bash
git diff 23ff2bd HEAD -- src/main/java src/test/java API.md DB.md VALIDACION.md scripts
```

### Ítems 1 y 4: idempotencia, reapertura y recertificación

Código: `RevisionTecnicaService.registrarResultadoUnaVez` compara resultado y observación. Un PATCH idéntico retorna sin emitir. Cambiar la observación de `CONFORME` anula el informe vigente y emite otro. `CONFORME → NO_CONFORME` exige una observación, anula el informe propietario y devuelve la calibración a `EN_PROCESO`. Tras corregir y completar, `NO_CONFORME → CONFORME` sobre la misma revisión crea un nuevo correlativo. `InformeTecnicoService.anularActivos` registra `fechaAnulacion` y `motivoAnulacion`; una revisión pendiente ajena no puede anular el certificado. `DocumentoPdfService` e `InformeFirmadoService` impiden descargar un informe anulado.

Pruebas ejecutables desde cero:

```bash
./mvnw -q -Dtest=AuditoriaAdversarial23ff2bdTest test
SERVER_PORT=18080 SPRING_DATASOURCE_URL='jdbc:h2:mem:gesminrespuestahttp;DB_CLOSE_DELAY=-1' SPRING_JPA_HIBERNATE_DDL_AUTO=create SPRING_JPA_SHOW_SQL=false ./mvnw -q spring-boot:run
# En otra terminal:
GESMIN_BASE=http://localhost:18080 python3 scripts/respuesta_freebuff_http.py
```

El script crea cliente, servicio, OT, instrumento, evaluación, calibración y revisión en la base nueva. Requests/responses literales del ciclo:

```http
[200] PATCH /api/revisiones-tecnicas/1/resultado?resultado=CONFORME&observaciones=Original
{"id":1,"calibracionId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaRevision":"2026-09-24","revisor":"Auditor","resultado":"CONFORME","observaciones":"Original"}
[200] PATCH /api/revisiones-tecnicas/1/resultado?resultado=CONFORME&observaciones=Original
{"id":1,"calibracionId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaRevision":"2026-09-24","revisor":"Auditor","resultado":"CONFORME","observaciones":"Original"}
[200] GET /api/informes-tecnicos
[{"id":1,"numero":"IT260901","revisionTecnicaId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaEmision":"2026-09-24","estado":"GENERADO","pdfCargado":false,"fechaCargaPdf":null,"fechaEnvio":null,"fechaAnulacion":null,"motivoAnulacion":null}]
[200] PATCH /api/revisiones-tecnicas/1/resultado?resultado=NO_CONFORME&observaciones=Falla-confirmada
{"id":1,"calibracionId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaRevision":"2026-09-24","revisor":"Auditor","resultado":"NO_CONFORME","observaciones":"Falla-confirmada"}
[200] GET /api/informes-tecnicos/1
{"id":1,"numero":"IT260901","revisionTecnicaId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaEmision":"2026-09-24","estado":"ANULADO","pdfCargado":false,"fechaCargaPdf":null,"fechaEnvio":null,"fechaAnulacion":"2026-09-24","motivoAnulacion":"Reapertura por NO_CONFORME: Falla-confirmada"}
[400] GET /api/informes-tecnicos/1/pdf
{"mensaje":"El informe fue anulado y no está disponible para descarga."}
[400] PATCH /api/informes-tecnicos/1/estado?estado=APROBADO
{"mensaje":"Transición de informe no permitida: ANULADO -> APROBADO. Primero cargue el PDF firmado, luego apruebe y finalmente marque ENVIADO."}
[200] PATCH /api/calibraciones/1/estado?estado=COMPLETADA
{"id":1,"evaluacionAptitudId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","tecnico":"Auditor","procedimiento":null,"fecha":"2026-09-24","estado":"COMPLETADA","patronesIds":[1],"patronesDescripcion":["CI-d56300 - Patrón auditoría"],"puntos":[]}
[200] PATCH /api/revisiones-tecnicas/1/resultado?resultado=CONFORME&observaciones=Corregida
{"id":1,"calibracionId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaRevision":"2026-09-24","revisor":"Auditor","resultado":"CONFORME","observaciones":"Corregida"}
[200] GET /api/informes-tecnicos
[{"id":1,"numero":"IT260901","revisionTecnicaId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaEmision":"2026-09-24","estado":"ANULADO","pdfCargado":false,"fechaCargaPdf":null,"fechaEnvio":null,"fechaAnulacion":"2026-09-24","motivoAnulacion":"Reapertura por NO_CONFORME: Falla-confirmada"},{"id":2,"numero":"IT260902","revisionTecnicaId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaEmision":"2026-09-24","estado":"GENERADO","pdfCargado":false,"fechaCargaPdf":null,"fechaEnvio":null,"fechaAnulacion":null,"motivoAnulacion":null}]
[200] PATCH /api/revisiones-tecnicas/1/resultado?resultado=CONFORME&observaciones=Corregida
{"id":1,"calibracionId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaRevision":"2026-09-24","revisor":"Auditor","resultado":"CONFORME","observaciones":"Corregida"}
[200] GET /api/informes-tecnicos
[{"id":1,"numero":"IT260901","revisionTecnicaId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaEmision":"2026-09-24","estado":"ANULADO","pdfCargado":false,"fechaCargaPdf":null,"fechaEnvio":null,"fechaAnulacion":"2026-09-24","motivoAnulacion":"Reapertura por NO_CONFORME: Falla-confirmada"},{"id":2,"numero":"IT260902","revisionTecnicaId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaEmision":"2026-09-24","estado":"GENERADO","pdfCargado":false,"fechaCargaPdf":null,"fechaEnvio":null,"fechaAnulacion":null,"motivoAnulacion":null}]
```

El test de servicio imprime `### H2 recertificación: anterior=ANULADO nuevo=GENERADO` y `### H1 observación cambiada: anterior=ANULADO, histórico=2, vigente=1; PATCH idéntico no creó tercero`.

### Ítem 2: contención real del mismo prefijo

Código: `CorrelativoService.siguiente` bloquea en la BD la fila anual `correlativos_contadores.prefijo` con `PESSIMISTIC_WRITE` hasta confirmar el documento. En el primer uso se inicializa desde los números históricos; un choque al insertar esa fila se reintenta desde una nueva transacción. Los cuatro generadores `E/COI/OT/IT` usan este servicio. `MAX_INTENTOS=3` queda como respaldo de la carrera inicial; las operaciones sobre una fila existente se serializan por el lock.

`AuditoriaAdversarial23ff2bdTest.respuesta_h3_ochoCompetidoresSuperanTresIntentosEnMismoPrefijo` usa `CyclicBarrier(8)` para E y para IT, con `8 > 3`. El script HTTP usa `threading.Barrier(8)` con el mismo endpoint E. Requests/responses literales de los ocho POST:

```http
[200] POST /api/expedientes?clienteId=1
{"id":1,"numero":"E260901","fecha":"2026-09-24","clienteId":1,"clienteRazonSocial":"Auditor HTTP","estado":"EN_PROCESO"}
[200] POST /api/expedientes?clienteId=1
{"id":2,"numero":"E260902","fecha":"2026-09-24","clienteId":1,"clienteRazonSocial":"Auditor HTTP","estado":"EN_PROCESO"}
[200] POST /api/expedientes?clienteId=1
{"id":3,"numero":"E260903","fecha":"2026-09-24","clienteId":1,"clienteRazonSocial":"Auditor HTTP","estado":"EN_PROCESO"}
[200] POST /api/expedientes?clienteId=1
{"id":4,"numero":"E260904","fecha":"2026-09-24","clienteId":1,"clienteRazonSocial":"Auditor HTTP","estado":"EN_PROCESO"}
[200] POST /api/expedientes?clienteId=1
{"id":5,"numero":"E260905","fecha":"2026-09-24","clienteId":1,"clienteRazonSocial":"Auditor HTTP","estado":"EN_PROCESO"}
[200] POST /api/expedientes?clienteId=1
{"id":6,"numero":"E260906","fecha":"2026-09-24","clienteId":1,"clienteRazonSocial":"Auditor HTTP","estado":"EN_PROCESO"}
[200] POST /api/expedientes?clienteId=1
{"id":7,"numero":"E260907","fecha":"2026-09-24","clienteId":1,"clienteRazonSocial":"Auditor HTTP","estado":"EN_PROCESO"}
[200] POST /api/expedientes?clienteId=1
{"id":8,"numero":"E260908","fecha":"2026-09-24","clienteId":1,"clienteRazonSocial":"Auditor HTTP","estado":"EN_PROCESO"}
```

Salida del runner:

```text
HTTP codes: [200, 200, 200, 200, 200, 200, 200, 200]
Correlativos: ['E260901', 'E260902', 'E260903', 'E260904', 'E260905', 'E260906', 'E260907', 'E260908']
```

### Ítem 3: error HTTP

`CorrelativoAgotadoException` tiene handler propio con `409 Conflict` y `mensaje`. `CorrelativoErrorHttpTest` ejecuta un POST en MockMvc y fuerza el agotamiento en el servicio para probar el contrato HTTP; la corrida de ocho competidores no agotó el retry.

```bash
./mvnw -q -Dtest=CorrelativoErrorHttpTest test
```

Salida literal:

```text
### HTTP POST /api/expedientes?clienteId=7
### HTTP 409 {"mensaje":"No se pudo asignar un correlativo único tras 3 intentos."}
```

FreeBuff observó 400 en el commit anterior, no 500. El handler genérico de otras `RuntimeException` conserva su comportamiento 400.

### Límite comprobado

La evidencia cubre ocho solicitudes HTTP simultáneas para E y ocho operaciones de servicio para E e IT. Demuestra esas cargas y el lock de la fila, no una garantía bajo contención ilimitada o timeouts extremos. Los duplicados históricos en la base persistida no se depuraron; esta validación usó H2 en memoria.


### Salida literal de los tests de regresión

```bash
./mvnw -q -Dtest=AuditoriaAdversarial23ff2bdTest#respuesta_h3_ochoCompetidoresSuperanTresIntentosEnMismoPrefijo test
./mvnw -q -Dspring.datasource.url=jdbc:h2:mem:gesminrespuesta4 test
```

```text
### H3 E barrier=8, intentos=3, resultados=[commit, commit, commit, commit, commit, commit, commit, commit], correlativos=[1, 2, 3, 4, 5, 6, 7, 8]
### H3 IT barrier=8, intentos=3, resultados=[commit, commit, commit, commit, commit, commit, commit, commit], correlativos=[1, 2, 3, 4, 5, 6, 7, 8]
### H1 observación cambiada: anterior=ANULADO, histórico=2, vigente=1; PATCH idéntico no creó tercero
### H2 revisión nueva tras ANULADO: anterior=IT260916, nueva=IT260917
### H1 serial OK: observaciones='Obs original', informes de la revisión=1
### H2 recertificación: anterior=ANULADO nuevo=GENERADO
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.711 s -- in com.kevin.backend.service.HallazgosConcurrenciaTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.817 s -- in com.kevin.backend.BackendApplicationTests
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.818 s -- in com.kevin.backend.service.AuditoriaAdversarial23ff2bdTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.765 s -- in com.kevin.backend.service.CorrelativoErrorHttpTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.533 s -- in com.kevin.backend.service.DocumentoPdfServiceTest
```

El test `respuesta_h2_nuevaRevisionTrasAnularInformePrevio` verifica también la ruta con revisión nueva, además de la recertificación sobre la misma revisión mostrada por HTTP.


### Migración comprobada sobre el esquema histórico

Antes del fix, una copia de `data/gesmin.mv.db` mostró:

```sql
"ESTADO" ENUM('APROBADO', 'ENVIADO', 'GENERADO', 'PDF_CARGADO') NOT NULL
```

Arrancar la app contra esa copia con solo `ddl-auto=update` agregó `FECHA_ANULACION` y `MOTIVO_ANULACION`, pero dejó el ENUM anterior: la anulación habría fallado en la base real. Después de incorporar `InformeEstadoSchemaMigration`, se arrancó la app contra una **copia nueva** y `SCRIPT NODATA` de H2 devolvió:

```sql
"ESTADO" ENUM('APROBADO', 'ENVIADO', 'GENERADO', 'PDF_CARGADO', 'ANULADO') NOT NULL
"FECHA_ANULACION" DATE
"MOTIVO_ANULACION" CHARACTER VARYING
```

Reproducción aislada:

```bash
./mvnw -q -Dtest=InformeEstadoSchemaMigrationTest test
```

Salida literal:

```text
### MIGRACION enum antiguo ENVIADO -> ANULADO (fila histórica conservada)
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

La suite final se ejecutó con `./mvnw -q -Dspring.datasource.url=jdbc:h2:mem:gesminrespuesta6 test`; los archivos `target/surefire-reports/*.txt` contienen la salida por clase. La prueba HTTP end-to-end anterior se hizo antes de agregar esta migración, con el mismo código de servicio y un esquema H2 nuevo que ya aceptaba `ANULADO`. La migración se verificó después sobre la copia del esquema histórico.
