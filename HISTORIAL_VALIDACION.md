# Gesmin Backend — Historial de validación (append-only)

Cada cuerpo INICIO/FIN reproduce literalmente el VALIDACION.md del commit 4c3cbb8. Las líneas de resultado son metadatos nuevos. Las validaciones futuras se agregan al final.

<a id="e1"></a>

## E1 — 2026-09-23 — FreeBuff (atribución contextual; el texto no firma autor)

<!-- INICIO TEXTO ORIGINAL E1 -->
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

<!-- FIN TEXTO ORIGINAL E1 -->

**Resultado de esta entrada:** SUPERADO parcialmente por E2–E5 para H1–H4; H5–H8 se conservan.

<a id="e2"></a>

## E2 — 2026-09-24 — Codex

<!-- INICIO TEXTO ORIGINAL E2 -->
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

<!-- FIN TEXTO ORIGINAL E2 -->

**Resultado de esta entrada:** VIGENTE para PDF y el subcaso de H4 del informe.

<a id="e3"></a>

## E3 — 2026-09-24 — Codex

<!-- INICIO TEXTO ORIGINAL E3 -->
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

<!-- FIN TEXTO ORIGINAL E3 -->

**Resultado de esta entrada:** SUPERADO por la refutación E4 y el código posterior de E5 para H2/H3.

<a id="e4"></a>

## E4 — 2026-09-24 — FreeBuff

<!-- INICIO TEXTO ORIGINAL E4 -->
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

<!-- FIN TEXTO ORIGINAL E4 -->

**Resultado de esta entrada:** REFUTÓ E3 sobre 23ff2bd; SUPERADO por cambio de algoritmo en 4c3cbb8 dentro del alcance probado.

<a id="e5"></a>

## E5 — 2026-09-24 — Codex

<!-- INICIO TEXTO ORIGINAL E5 -->
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
<!-- FIN TEXTO ORIGINAL E5 -->

**Resultado de esta entrada:** Última respuesta anterior: evidencia del commit 4c3cbb8; límites y versiones explicitados en E6.

<a id="e6"></a>

## E6 — 2026-09-24 — Codex

**Texto original de esta entrada (inventario nuevo):** Las dos corridas de H3 corresponden a código distinto. En §23ff2bd§, §ExpedienteService.generarNumero§ usaba §countByNumeroStartingWith("E"+yy)+1§ y §CorrelativoRetry§ permitía tres intentos. En §4c3cbb8§, §CorrelativoService.siguiente§ usa la fila anual bloqueada por §CorrelativoContadorRepository.bloquear§ (§PESSIMISTIC_WRITE§) dentro de la transacción que guarda el documento.

(a) Las corridas usaron bases H2 en memoria distintas, pero esa diferencia de datos no explica por sí sola el cambio: ambas competían por el prefijo anual. (b) §enParalelo§ ejecuta §new CyclicBarrier(hebras)§ antes de cada operación; el barrier sí alinea el inicio. (c) Los expedientes compiten por §E26§ y los informes por §IT26§. Calibraciones distintas no significan prefijos IT distintos. El factor decisivo es el cambio de algoritmo entre commits.

Evidencia nueva: H3B (cuatro hebras) y el test de ocho hebras corrieron juntos contra HEAD §4c3cbb8§ y la misma base §gesminauditoria§:

~~~text
### H3B exitosos=4/4 nuevos=[1, 2, 3, 4] (baseline=0)
### H3B ok=exp=E260901
### H3B ok=exp=E260904
### H3B ok=exp=E260902
### H3B ok=exp=E260903
### H3 E barrier=8, intentos=3, resultados=[commit, commit, commit, commit, commit, commit, commit, commit], correlativos=[5, 6, 7, 8, 9, 10, 11, 12]
### H3 IT barrier=8, intentos=3, resultados=[commit, commit, commit, commit, commit, commit, commit, commit], correlativos=[1, 2, 3, 4, 5, 6, 7, 8]
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

**Resultado de esta entrada:** El 3/4 antiguo y el 8/8 nuevo no son dos verdades sobre el mismo código. Se mantiene explícita una **contradicción histórica abierta de evidencia**: no se ejecutaron ambos commits en un experimento controlado sobre el mismo entorno. La auditoría original registró HTTP `400` para el agotamiento en `23ff2bd`; la mención posterior de “500” no coincide con ese artefacto.

<a id="e7"></a>

## E7 — 2026-09-24 — FreeBuff

**Texto original de esta entrada (verificación nueva):** cierra la contradicción histórica de H3 registrada en E6.

**Alcance del código verificado:** HEAD era `328018b` y `git diff 4c3cbb8 HEAD --name-only` muestra solo `API.md`, `DB.md`, `HISTORIAL_VALIDACION.md` y `VALIDACION.md`; el código probado es exactamente el de `4c3cbb8`.

**Inspección del código vigente:** `CorrelativoService.siguiente` declara `@Transactional(propagation = Propagation.MANDATORY)` y bloquea la fila anual mediante `CorrelativoContadorRepository.bloquear` (`PESSIMISTIC_WRITE`), de modo que el lock vive hasta el commit del documento. Los cuatro generadores pasan por `contador.siguiente(...)`: `ExpedienteService` (E), `CotizacionService` (COI), `OrdenDeTrabajoService` (OT) e `InformeTecnicoService` (IT). `CorrelativoRetry` reintenta con `PROPAGATION_REQUIRES_NEW` y `MAX_INTENTOS=3`; `GlobalExceptionHandler` mapea `CorrelativoAgotadoException` a **409**.

**Evidencia cruda de la reproducción (misma base `gesminauditoria`, código `4c3cbb8`):**

```text
4c3cbb8 / FreeBuff / H3B+barrier=8 juntos / gesminauditoria: H3B exitosos=4/4 nuevos=[1, 2, 3, 4] (baseline=0)
### H3B ok=exp=E260904
### H3B ok=exp=E260901
### H3B ok=exp=E260902
### H3B ok=exp=E260903
### H3 E barrier=8, intentos=3, resultados=[commit, commit, commit, commit, commit, commit, commit, commit], correlativos=[5, 6, 7, 8, 9, 10, 11, 12]
### H3 IT barrier=8, intentos=3, resultados=[commit, commit, commit, commit, commit, commit, commit, commit], correlativos=[1, 2, 3, 4, 5, 6, 7, 8]
### HTTP 409 {"mensaje":"No se pudo asignar un correlativo único tras 3 intentos."}
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 -- in com.kevin.backend.service.AuditoriaAdversarial23ff2bdTest
```

Los únicos `SQLState 23505` registrados en las corridas fueron sobre `CORRELATIVOS_CONTADORES(PREFIJO)` (carrera de inicialización del contador, cubierta por el retry), no sobre el índice único del documento: la colisión real que E4 refutó en `23ff2bd` ya no se produce. Los dos tests que en E4 fallaban a propósito (`h3a2`, `h3b`) hoy pasan dentro de la suite completa (10/10).

**Cierre de la contradicción:** `git show 23ff2bd:src/main/java/com/kevin/backend/service/ExpedienteService.java` confirma `countByNumeroStartingWith(prefijoAnual) + 1` y `CorrelativoRetry` con `MAX_INTENTOS=3`; el algoritmo refutado por E4 y el vigente son distintos, por lo que “3/4” y “8/8” nunca fueron dos verdades sobre el mismo código.

**Reproducción:**

```bash
./mvnw -q -Dtest='AuditoriaAdversarial23ff2bdTest#h3b_cuatroExpedientesSimultaneosColisionRealDeE,respuesta_h3_ochoCompetidoresSuperanTresIntentosEnMismoPrefijo' test
./mvnw -q -Dtest=CorrelativoErrorHttpTest test
./mvnw -q -Dtest=AuditoriaAdversarial23ff2bdTest test
```

Nota: la ficha H3 de `VALIDACION.md` de `4c3cbb8` documentaba el separador de métodos con `+`, que no es sintaxis válida de surefire; la forma correcta es con coma, como arriba.

**Límites que se mantienen:** no se reejecutó la refutación de `23ff2bd` (E4) ni se corrió el experimento cruzado “ambos commits, mismo entorno”: el algoritmo antiguo se confirmó por inspección de `git show`, no por ejecución. La garantía demostrada cubre 8 competidores sincronizados por prefijo en E e IT (y los 4 expedientes HTTP concurrentes de E5); no hay prueba de contención ilimitada ni de timeouts extremos. El HTTP **400** histórico queda como registro del código `23ff2bd`, no del vigente.

**Resultado de esta entrada:** Cierra la contradicción histórica de H3; la ficha vigente pasa a RESUELTO en el alcance probado (≤ 8 competidores). E4 conserva su valor histórico sobre `23ff2bd`.


<a id="e8"></a>

## E8 — 2026-09-24 — Codex

**Texto original de esta entrada (delimitación H2):** La anulación de un certificado vigente al registrar `NO_CONFORME` ya queda persistida en el propio `InformeTecnico`: estado `ANULADO`, `fechaAnulacion` y `motivoAnulacion`. El DTO y `GET /api/informes-tecnicos/{id}` exponen esos campos; `GET /api/informes-tecnicos` conserva el informe anulado junto al nuevo. No hizo falta cambiar código funcional ni crear un evento adicional para obtener trazabilidad consultable desde la API.

Evidencia HTTP de la corrida H2 ya registrada en la ficha vigente y E5:

```text
[200] PATCH /api/revisiones-tecnicas/1/resultado?resultado=NO_CONFORME&observaciones=Falla-confirmada
{"id":1,"calibracionId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaRevision":"2026-09-24","revisor":"Auditor","resultado":"NO_CONFORME","observaciones":"Falla-confirmada"}
[200] GET /api/informes-tecnicos/1
{"id":1,"numero":"IT260901","revisionTecnicaId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaEmision":"2026-09-24","estado":"ANULADO","pdfCargado":false,"fechaCargaPdf":null,"fechaEnvio":null,"fechaAnulacion":"2026-09-24","motivoAnulacion":"Reapertura por NO_CONFORME: Falla-confirmada"}
```

Verificación reproducible del flujo con `./mvnw -q -Dtest=AuditoriaAdversarial23ff2bdTest#respuesta_h2_nuevaRevisionTrasAnularInformePrevio test`. Para la request y response HTTP completas, ejecutar `python3 scripts/respuesta_freebuff_http.py` contra una instancia local con la configuración que describe el script. La inspección del código confirma que `InformeTecnicoService.anularActivos` escribe la fecha y motivo, `InformeTecnicoDTO` los incluye y `InformeTecnicoController` expone ambos GET.

Reejecución local del test focalizado el 2026-09-24, sobre `jdbc:h2:mem:gesminauditoria`:

```text
$ ./mvnw -q -Dtest=AuditoriaAdversarial23ff2bdTest#respuesta_h2_nuevaRevisionTrasAnularInformePrevio test
### H2 revisión nueva tras ANULADO: anterior=IT260901, nueva=IT260902
exit_code=0
```

La comunicación de una anulación ya enviada se documentó como paso manual: El responsable operativo consulta ese GET y gestiona el aviso fuera de la API conforme al procedimiento de Gesmin. No existe registro de notificación, consumidor automático ni prueba de que el cliente fue avisado. Las tres opciones pendientes son evidencia externa, constancia manual en API o evento persistente con consumidor. Gesmin debe decidir destinatarios, responsable y momento. `API.md` detalla el contrato y `VALIDACION.md` deja H2 abierto por esa decisión.

**Resultado de esta entrada:** H2 tiene anulación consultable por API y recertificación probada; la regla y constancia de comunicación al cliente permanecen abiertas. Esta entrada no cambia H1 ni H3.

<a id="e9"></a>

## E9 — 2026-09-24 — FreeBuff

**Texto original de esta entrada (experimento cruzado H3):** ejecuta el experimento que E6 dejó abierto y E7 cerró solo por inspección: ambos commits corren el mismo test en el mismo entorno. Sonda: `AuditoriaH3Cruzada4c3cbb8Test#cruzado_h3b_cuatroExpedientesMismoPrefijo` (4 expedientes, barrier, prefijo `E26`, sin asserts para poder medir el comportamiento crudo en ambos commits). Base H2 en memoria `gesminh3cruzada` en las dos mitades. Código probado en la mitad vigente: HEAD `328018b`; `git diff 4c3cbb8 HEAD --name-only` solo toca docs y `git diff HEAD -- src/` da 85 archivos con 0 inserciones y 0 borrados (solo fin de línea), más el test cruzado sin commitear.

**Causa de la discrepancia 8/8 vs 3/4 — evidencia de ejecución (3 corridas por commit):**

```text
23ff2bd / corrida 1: ### CRUZADA H3B exitosos=3/4 nuevos=[1, 2, 3] (baseline=0)
### CRUZADA H3B FALLO=IllegalStateException/No se pudo asignar un correlativo único tras 3 intentos.
      [raíz: JdbcSQLIntegrityConstraintViolationException: Unique index or primary key violation:
       "PUBLIC.CONSTRAINT_F INDEX PUBLIC.CONSTRAINT_INDEX_F ON PUBLIC.EXPEDIENTES(NUMERO NULLS FIRST) VALUES ( /* 27 */ 'E260903' )"]
23ff2bd / corridas 2 y 3: exitosos=3/4 nuevos=[1, 2, 3] (idénticas)
4c3cbb8 / corridas 1-3: ### CRUZADA H3B exitosos=4/4 nuevos=[1, 2, 3, 4] (idénticas)
```

Los dos números contradictorios del historial se reproducen a voluntad cambiando únicamente el commit: nunca fueron dos verdades sobre el mismo código. Descartes por ejecución: (a) mismas bases y mismo entorno en ambas mitades del experimento; (b) el barrier alinea de verdad — en el commit viejo hay colisión real `23505` sobre `EXPEDIENTES(NUMERO)` dentro del barrier, es decir contención real, no desincronización; (c) los 4 expedientes compiten por el mismo prefijo `E26` en ambos commits (nuevos `[1..3]` contiguos). Causa real: en `23ff2bd` `generarNumero()` calcula `count()+1` y `CorrelativoRetry` permite 3 intentos sin backoff (con 4 competidores el peor necesita un 4.º intento y agota); en `4c3cbb8` `CorrelativoService.siguiente` serializa con `PESSIMISTIC_WRITE` sobre la fila anual `correlativos_contadores.prefijo` hasta el commit del documento.

**Contención real sobre el código vigente (asserts: ningún commit perdido, sin duplicados, secuencia contigua sin huecos):**

```text
E  barrier=8,  intentos=3: 6/6 corridas → 8/8 commit, correlativos=[1..8]
IT barrier=8,  intentos=3: 3/3 corridas → 8/8 commit, correlativos=[1..8]
E  barrier=24, intentos=3: 3/3 corridas → 24/24 commit, correlativos=[1..24]
IT barrier=24, intentos=3: 3/3 corridas → 24/24 commit, correlativos=[1..24]
```

E barrier=8 corrió 6 veces por un artefacto de sintaxis de surefire (ver nota): las 3 invocaciones con `,` corrieron solo E y las 3 con `+` corrieron E e IT. N=24 es 8× el presupuesto de intentos y no se pierde ninguno: el lock serializa y el retry dejó de ser el mecanismo principal. No se encontró el N de fallo en el rango probado (Regla 4: no existe el matiz "funciona salvo cuando N supera X" dentro de lo medido).

**Regresión del vigente en la misma sesión:** `AuditoriaAdversarial23ff2bdTest` 10/10, `AuditoriaH3Cruzada4c3cbb8Test` 5/5, `CorrelativoErrorHttpTest` 1/1 con `### HTTP 409 {"mensaje":"No se pudo asignar un correlativo único tras 3 intentos."}` (reports en `target/surefire-reports/`).

**Nota de reproducibilidad:** surefire no acepta `,` para separar dos métodos de la misma clase: `-Dtest='Clase#m1,m2'` corre un solo método por invocación. La sintaxis válida es `-Dtest='Clase#m1+m2'`.

**Reproducción:**

```bash
# Sonda en el commit viejo (el test compila igual en ambos commits):
git worktree add /tmp/gesmin-23ff2bd 23ff2bd
cp src/test/java/com/kevin/backend/service/AuditoriaH3Cruzada4c3cbb8Test.java /tmp/gesmin-23ff2bd/src/test/java/com/kevin/backend/service/
cd /tmp/gesmin-23ff2bd && ./mvnw -q -Dtest='AuditoriaH3Cruzada4c3cbb8Test#cruzado_h3b_cuatroExpedientesMismoPrefijo' -Dsurefire.failIfNoSpecifiedTests=false test   # → 3/4
# Contención vigente, E e IT por separado:
./mvnw -q -Dtest='AuditoriaH3Cruzada4c3cbb8Test#cruzado_h3_E_N8_superaPresupuestoDeIntentos+cruzado_h3_IT_N8_superaPresupuestoDeIntentos' -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -q -Dtest='AuditoriaH3Cruzada4c3cbb8Test#cruzado_h3_E_N24_ochoVecesElPresupuesto+cruzado_h3_IT_N24_ochoVecesElPresupuesto' -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -q -Dtest=CorrelativoErrorHttpTest test
```

Límites: single-JVM contra H2 en memoria (sin multi-nodo ni timeouts de lock); la contención HTTP de E5 y el script `scripts/h3_auditoria_http_4c3cbb8.py` (N=12) no se reejecutaron en E9.

**Resultado de esta entrada:** H3 RESUELTO en el alcance probado (≤ 24 competidores por prefijo, 3 o más corridas por punto, E e IT por separado); contradicción 8/8 vs 3/4 cerrada por ejecución (dos commits, un entorno). La ficha vigente se actualiza en consecuencia.


<a id="e10"></a>

## E10 — 2026-09-24 — Codex

**Texto original de esta entrada (corrección de reproducibilidad E9):** Al revisar E9 antes de confirmar los archivos, detecté que sus comandos de contención vigente quedaban dentro del worktree `/tmp/gesmin-23ff2bd` tras ejecutar la sonda antigua. En la ficha H3 vigente agregué `cd -` para volver al checkout actual antes de las pruebas E/IT. El texto de E9 se conserva íntegro por la regla append-only. En `API.md` corregí una mayúscula del paso operativo H2. En el script HTTP auxiliar de FreeBuff reemplacé la comparación fija con `E2609` por el prefijo del año y mes de ejecución, y ahora el veredicto exige secuencias consecutivas E e IT.

Reejecución local sobre el checkout vigente: `./mvnw -q -Dtest=AuditoriaH3Cruzada4c3cbb8Test test`. Salida focal de la corrida:

```text
### CRUZADA H3 IT barrier=24, intentos=3, resultados=[commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit], correlativos=[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24]
### CRUZADA H3B exitosos=4/4 nuevos=[1, 2, 3, 4] (baseline=0)
### CRUZADA H3 IT barrier=8, intentos=3, resultados=[commit, commit, commit, commit, commit, commit, commit, commit, commit], correlativos=[25, 26, 27, 28, 29, 30, 31, 32]
### CRUZADA H3 E barrier=24, intentos=3, resultados=[commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit, commit], correlativos=[5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28]
### CRUZADA H3 E barrier=8, intentos=3, resultados=[commit, commit, commit, commit, commit, commit, commit, commit], correlativos=[29, 30, 31, 32, 33, 34, 35, 36]
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
exit_code=0
```

La corrida también registró `SQLState 23505` solo en `CORRELATIVOS_CONTADORES(PREFIJO)` durante la inicialización paralela, seguido de los commits anteriores.

**Resultado de esta entrada:** Corrige instrucciones de reproducción y comparación del script; no cambia el algoritmo de correlativos ni los resultados históricos de E9.

<a id="e11"></a>

## E11 — 2026-09-25 — FreeBuff

<!-- INICIO TEXTO ORIGINAL E11 -->
# Auditoría de hallazgos nuevos H9–H16 (HALLAZGOS_NUEVOS.md) sobre el código vigente `4c3cbb8`

Reglas de la auditoría: ningún hallazgo se aceptó por lectura del informe; cada uno se verificó por ejecución (HTTP/test con salida cruda) o por lectura directa del código con la línea citada. Se separan hechos (Grupo A) de decisiones de negocio (Grupo B). H1–H3 y lo cerrado en [VALIDACION.md](VALIDACION.md) no se reabrió. El PDF citado por la solicitud no existe; el informe real es `HALLAZGOS_NUEVOS.md` (verificado en disco).

## Grupo A — hechos verificables

| ID | Veredicto | Método | Evidencia |
|---|---|---|---|
| H9 | **CONFIRMADO (CRÍTICO)** | Corrida HTTP + test de ramas | Fallo real de disco al guardar el PDF firmado del certificado → `### H9 HTTP 400 {"mensaje":"No se pudo almacenar el PDF firmado."}`; sonda directa: `IllegalStateException: No se pudo almacenar el PDF firmado. [causa: FileAlreadyExistsException]`. Ramas: `IllegalArgumentException` (negocio) → 400 y `IllegalStateException` (bug de servidor) → 400, misma rama `handleRuntime` (`GlobalExceptionHandler.java:30-35`); solo `MethodArgumentNotValidException` y `CorrelativoAgotadoException` (409) tienen ramas propias. |
| H10 | CONFIRMADO | Lectura con línea | `InformeEstadoSchemaMigration.java:22-23` ejecuta `ALTER TABLE ... ENUM(...)` sin condición en cada arranque; sin Flyway/Liquibase en `pom.xml` (grep: sin coincidencias). No demostrable por corrida: el riesgo es de motor futuro (PostgreSQL/MySQL); probarlo requeriría una base PostgreSQL real. |
| H11 | CONFIRMADO | Lectura con línea | `application.properties`: L11 `spring.h2.console.enabled=true`, L13 `web-allow-others=true`, L8 `ddl-auto=update`; único properties (sin perfiles dev/prod); `pom.xml:71` `spring-boot-h2console`; sin actuator. Como no hay perfiles, esta configuración ES el perfil de producción si se despliega. |
| H12 | CONFIRMADO con matiz | Corrida | Tras borrar fila interna `E260903` + contador `E26`: `### H12 nueva creación FALLO: CorrelativoAgotadoException ... [raíz: 23505 ... VALUES ( /* 4 */ 'E260904' )]` — el `COUNT()` recreó el contador desalineado y la creación intentó reasignar `E260904`, VIVA. Efecto real: la ruta queda bloqueada (409) hasta corrección manual; no duplica (protege el unique). Matiz: peor de lo descrito en el informe. |
| H13 | Hecho confirmado | Lectura (grep) | Grep propio coincide línea por línea con el informe: `EvaluacionAptitudService.java:70,72,109,114` y `RevisionTecnicaService.java:135` mutan estado fuera de `actualizarEstado` sin guard. Sin corrida HTTP propia: el hecho es la ausencia de validador, y su corrección depende de las tablas de transición pendientes de Gesmin. |
| H15 | Conducta actual CONFIRMADA por corrida; arreglo = decisión de negocio | Corrida | `### H15 PATCH COI COI260901 estado=RECHAZADA con OT viva → estado final=RECHAZADA` + `### H15 invariante resultante: OT 2 sigue PENDIENTE sobre una cotización RECHAZADA` (OT creada tras el guard de APROBADA de `OrdenDeTrabajoService.java:48-52`; sin guard posterior en `CotizacionService.java:90-94`). |

## Grupo B — decisiones de negocio (NO implementadas, Regla de la auditoría)

| ID | Conducta actual (evidencia) | Regla presunta | Decisión que falta de Gesmin |
|---|---|---|---|
| H14 | `EstadoOrdenTrabajo.COMPLETADA` solo existe en el enum (`EstadoOrdenTrabajo.java:7`); ningún flujo la asigna; alcanzable solo por PATCH crudo de H4 | OT `COMPLETADA` cuando el Informe llega a `ENVIADO` (candidato de la revisión) | ¿Qué evento marca `COMPLETADA`? Sin respuesta no es implementable (es una regla no construida, no un guard faltante). Impacta el cierre de expediente de H5. |
| H15 | (ver Grupo A: degradación APROBADA→RECHAZADA con OT viva, corrida E11) | "OT implica cotización aprobada" como invariante | (1) ¿`APROBADA` es terminal? (2) si degrada, ¿las OT vivas se cancelan en cascada o se bloquea la transición? |
| H16 | `ACEPTADO` solo en `EstadoExpediente.java:7`; sin uso en services/controllers/tests; documentado en `API.md:119,121` y `DB.md:60`; los expedientes nacen `EN_PROCESO` (`ExpedienteService.java:52`) | Semánticamente solapa con `Cotizacion.APROBADA` | ¿Es un paso futuro del flujo (¿qué lo dispara?) o se elimina del enum (actualizando API.md/DB.md)? |
| H4/H5 (arrastre) | Guards de transición ausentes en `CotizacionService.java:93`, `OrdenDeTrabajoService.java:86`, `CalibracionService.java:70`, `ExpedienteService.java:60` (lectura, líneas citadas) | Máquina de transiciones + guards cruzados de cierre | Tablas de transición y excepciones manuales (rol/motivo), según el diseño en evaluación; sin respuestas no se codifica. |

## Cruce de preguntas a Gesmin (el informe no trae listado explícito; se formulan aquí)

El informe marca ⚠️ en H14, H16 y el diseño H4/H5, pero no trae un listado numerado de preguntas. Las preguntas bien formuladas que trae implícitamente y las que faltan:

- ✅ H14: "¿qué evento de negocio debe marcar la OT como COMPLETADA?" — bien formulada; el candidato (Informe ENVIADO) es coherente con trazabilidad ISO.
- ✅ H16: "¿ACEPTADO se usa o se elimina?" — bien formulada.
- ❌ Falta para H15: la pregunta sobre terminalidad de APROBADA y destino de OTs vivas (el informe describe el riesgo pero no la pregunta).
- ❌ Falta para H12: "¿Gesmin prevé reseeds/limpiezas de datos de prueba en producción?" (define si la corrección del contador es necesaria).
- ❌ Falta para H4/H5: el listado de tablas de transición con sus ⚠️ (p. ej. ¿el PATCH manual de OT puede forzar EN_ESPERA_CLIENTE/EN_PROCESO?; ¿un expediente con OT CANCELADA puede cerrarse?).
- ❌ Falta para H9: no requiere pregunta (es bug con fix técnico); solo confirmar el contrato de códigos HTTP (400 vs 404 de H8) en la misma decisión.

## Corridas (reproducción)

```bash
./mvnw -q -Dtest='AuditoriaHallazgosNuevos4c3cbb8Test,HallazgoH9RamasHandlerTest' -Dsurefire.failIfNoSpecifiedTests=true test
```

Resultados: `Tests run: 5, Failures: 0, Errors: 0` (3 de `AuditoriaHallazgosNuevos4c3cbb8Test` [H9, H12, H15] + 2 de `HallazgoH9RamasHandlerTest`). Suite completa como regresión: 32 tests, 0 fallos, 0 errores. Nota de sintaxis: entre CLASES el separador válido es `,`; `+` solo sirve entre métodos de la misma clase (en la corrida inicial con `+` surefire no matcheó nada y saltó en silencio con `failIfNoSpecifiedTests=false`).

Artefactos nuevos (tests de auditoría, no cambian código de producción):

- `src/test/java/com/kevin/backend/service/AuditoriaHallazgosNuevos4c3cbb8Test.java` — H9 (fallo real de disco + HTTP), H12 (borrado + recreación del contador), H15 (degradación con OT viva). Base H2 en memoria `gesmine11`; el test H9 respalda y restaura `./data/pdf-firmados` (el directorio de desarrollo) y no deja residuos.
- `src/test/java/com/kevin/backend/service/HallazgoH9RamasHandlerTest.java` — ramas del handler (negocio vs bug de servidor, ambos → 400).

Límites honestos: H10 y H11 son verificaciones de lectura (el riesgo es de despliegue futuro; demostrar el fallo de H10 requeriría PostgreSQL real); H13 es lectura porque su corrección está bloqueada por decisiones pendientes; H9 se probó con MockMvc (stack real, advice real, excepción real de disco) y no contra un server vivo; el efecto del fix de H9 en códigos HTTP no se implementó. Grupo B NO implementado (no hay norma validada; falta decisión de Gesmin).

<!-- FIN TEXTO ORIGINAL E11 -->

**Resultado de esta entrada:** H9 CONFIRMADO CRÍTICO (corrida); H10/H11 CONFIRMADOS (lectura); H12 CONFIRMADO con matiz (corrida: bloquea, no duplica); H13 hecho confirmado (lectura; corrección bloqueada por decisión); H14/H15/H16 → REQUIERE DECISIÓN DE NEGOCIO (H15 con conducta actual demostrada por corrida). Fichas nuevas agregadas a VALIDACION.md con H9 arriba de la tabla de severidad. Grupo B no implementado.

<a id="e12"></a>

## E12 — 2026-09-25 — Codex

**Texto original de esta entrada (aplicación de decisiones de Gesmin y revisión de E11):** El alcance aprobado está en `CAMBIOS_PARA_CODEX.md`, revisión 2 del 25 set 2026. E11 se conserva íntegra como auditoría del código previo. Esta entrada registra el nuevo comportamiento, las pruebas y los hallazgos que siguen abiertos.

**PDF al crear el registro.** `CotizacionService.crear` y `OrdenDeTrabajoService.crear` llaman a `DocumentoGeneradoService` después de que `CorrelativoRetry.ejecutar` confirma la operación. `RevisionTecnicaService.registrarResultado` devuelve el ID del informe creado por el intento confirmado; solo entonces guarda el PDF sin firma. Una llamada idempotente o una anulación sin nueva emisión no escribe PDF. Los nombres son `cotizacion-{id}.pdf`, `orden-trabajo-{id}.pdf` e `informe-tecnico-{id}-sin-firma.pdf` en `./data/pdf-generados/`. El GET lee el snapshot, o genera al vuelo si falta el archivo. La lectura del informe sigue rechazando `ANULADO` incluso si su archivo persistido existe. La escritura usa temporal y enlace atómico que no reemplaza un snapshot anterior; un fallo se registra y no revierte el documento ya confirmado.

**Estados y alcance de negocio.** `TransicionesEstado` define una tabla por puerta. `BORRADOR → APROBADA` directo sigue permitido y `APROBADA`/`RECHAZADA` son terminales. La OT usa `ORDEN_INTERNA` para las transiciones disparadas por evaluación y `ORDEN_PATCH` para cambios manuales; `COMPLETADA` solo se marca manualmente desde `EN_PROCESO` y es terminal. Calibración conserva `COMPLETADA → EN_PROCESO` para corrección; también se valida `registrarMediciones`, puerta adicional identificada al inventariar `setEstado`. Expediente `CERRADO`/`RECHAZADO` son terminales. No se añadió guard de cierre por OT cancelada porque Gesmin confirmó que el cierre es válido, ni automatismo de notificación o de OT completada. `ACEPTADO` se eliminó del enum del expediente y de API/DB. Antes de retirarlo, la consulta a la base local devolvió:

```text
$ SELECT ESTADO, COUNT(*) FROM EXPEDIENTES GROUP BY ESTADO;
ESTADO  | COUNT(*)
CERRADO | 1
(1 row)
```

**Evidencia cruda de las rutas nuevas:**

```text
$ ./mvnw -q -Dtest=PdfAutoPersistenciaTest test
### PDF snapshots: COI=cotizacion-1.pdf OT=orden-trabajo-1.pdf IT=informe-tecnico-1-sin-firma.pdf; estados posteriores e idempotencia conservaron bytes; fallback OT=PDF
exit_code=0

$ ./mvnw -q -Dtest=FlujoEstadosServiceTest test
### Estados: NO_APTO→EN_ESPERA_CLIENTE; PATCH→PENDIENTE bloqueado; respuesta cliente→PENDIENTE; APTO→EN_PROCESO; COMPLETADA y CERRADO terminales
exit_code=0

$ ./mvnw -q -Dtest=AuditoriaHallazgosNuevos4c3cbb8Test#h15_cotizacionAprobadaConOtVivaNoPuedeDegradarseARechazada test
### H15 OT OT260901 estado=PENDIENTE creada sobre COI COI260901 (guard de APROBADA funcionó al crear)
### H15 PATCH COI COI260901 estado=RECHAZADA con OT viva → bloqueado: No se puede cambiar la cotización aprobada: ya tiene una Orden de Trabajo asociada.
### H15 invariante: OT 1 sigue PENDIENTE sobre cotización APROBADA
exit_code=0

$ ./mvnw -q test
classes=12 tests=38 failures=0 errors=0 skipped=0
exit_code=0
```

El test `AuditoriaHallazgosNuevos4c3cbb8Test` de H15, aportado con E11 para demostrar la degradación antigua, se conservó y ahora exige el rechazo y comprueba que la OT siga viva sobre una cotización `APROBADA`. No se borró cobertura; la salida antigua permanece literalmente en E11. `DocumentoPdfServiceTest` agregó snapshot, fallback y rechazo de informe anulado; `TransicionesEstadoTest` verifica las tablas. Los tests de H1/H3 siguen en la suite completa.

**Inventario comparado con VALIDACION.md y E11:** H1 y H3 siguen resueltos según su alcance previo, sin cambio de algoritmo; H2 tiene recertificación trazable y Gesmin confirmó comunicación manual fuera de la API. H4 y H13 quedan resueltos para las puertas conocidas. H5 queda parcial: se impide reabrir `CERRADO`, pero no existe guard cruzado de evaluaciones o informes; el cierre con OT cancelada se permite por decisión explícita. H6, H7 y H8 continúan abiertos como antes. H9 sigue **ABIERTO y CRÍTICO**: `GlobalExceptionHandler` aún convierte `RuntimeException` de servidor en HTTP 400; E11 conserva su sonda. H10/H11 siguen abiertos como deuda de migración y configuración. H12 sigue abierto con su colisión demostrada tras borrar contador y fila interna; no se tocó el algoritmo. H14 se supera como requisito automático: `COMPLETADA` de OT será manual. H15 queda resuelto por estado terminal y guard específico. H16 queda resuelto al quitar `ACEPTADO` tras comprobar la base local. La tabla y fichas vigentes están en `VALIDACION.md`.

**Límites:** la prueba de PDFs comprueba creación real tras commit, lectura de bytes, inmutabilidad y fallback por archivo ausente; no forzó un fallo de permisos de disco después del commit. La suite usa H2 local/en memoria; no demuestra comportamiento multi-nodo ni despliegue con otro motor. No se ejecutó una corrida HTTP externa de las nuevas tablas de estado; `FlujoEstadosServiceTest` usa los servicios reales, y los códigos HTTP previstos provienen del handler vigente de `IllegalArgumentException`. H9, H10, H11 y H12 permanecen explícitamente fuera de este cambio.

**Resultado de esta entrada:** aplica las decisiones de `CAMBIOS_PARA_CODEX.md` y registra los límites vigentes, sin reescribir E11.


<a id="e13"></a>

## E13 — 2026-09-25 — Codex

**Texto original de esta entrada (revisión posterior al commit de E12):** Se detectó una regresión de lectura en los PDFs persistidos: `DocumentoGeneradoService.leerCotizacion` y `leerOrden` servían el archivo si existía en disco sin verificar que la fila correspondiente aún existiera. Tras borrar una cotización u OT, un PDF residual podía seguir descargándose. `DocumentoPdfService` ahora expone validadores de existencia usados antes de leer el snapshot o activar el fallback; la validación previa de `ANULADO` en informes sigue vigente.

**Prueba:** `DocumentoPdfServiceTest.pdfGeneradosQuedanComoSnapshotYElGetRetrocompatibleRespetaAnulacion` ahora crea los tres archivos, simula repositorios sin cotización y sin OT, y exige `IllegalArgumentException` al leer aunque el archivo persista. Después restituye las filas simuladas y verifica snapshot, fallback e informe anulado. La prueba focalizada y la suite completa terminaron así:

```text
$ ./mvnw -q -Dtest=DocumentoPdfServiceTest test
exit_code=0

$ ./mvnw -q test
classes=12 tests=38 failures=0 errors=0 skipped=0
exit_code=0
```

**Límite que permanece:** el nombre del PDF está fijado por tipo e ID, como pidió Gesmin. Si se reinicia la base y se reutiliza el mismo ID mientras subsiste una carpeta de PDFs vieja, la comprobación de existencia no detecta que el archivo pertenece a otro registro. Base y carpeta `pdf-generados` deben restaurarse o limpiarse juntas; se documentó en `DB.md`. E12 queda íntegra como registro del primer commit, y esta entrada añade la corrección.

**Resultado de esta entrada:** los GET de cotización y OT ya no sirven PDFs residuales de registros ausentes; resta la coordinación operativa de respaldos ante reutilización de IDs.
