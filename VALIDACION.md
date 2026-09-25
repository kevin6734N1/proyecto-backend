# Gesmin Backend — Validación vigente

**Código evaluado:** base `1183d19`, decisiones del 2026-09-25 aplicadas en E12 y corrección de PDF residual en E13; H1/H3 conservan la evidencia previa. E14 (FreeBuff, 2026-09-25) re-verificó E12/E13 sobre `4385bca` (cb5a844 + 4385bca) con corrida HTTP end-to-end; E15 (Codex, 2026-09-25) corrige H9/H17 sobre esa base y ejecuta la suite completa. Los resultados vigentes están en las fichas de abajo y en [E14](HISTORIAL_VALIDACION.md#e14) / [E15](HISTORIAL_VALIDACION.md#e15). Cada hallazgo tiene una ficha única. `RESUELTO` indica que la conducta señalada se verificó en el alcance descrito; `ABIERTO` indica una regla aún sin defender, una decisión pendiente o evidencia contradictoria sin comparación controlada. Los textos previos y sus veredictos se conservan literalmente en [HISTORIAL_VALIDACION.md](HISTORIAL_VALIDACION.md).

**PDF generado:** E12 verifica snapshots automáticos de cotización, OT e informe tras confirmar cada registro, con fallback para archivos ausentes. E13 verifica que un archivo residual no se sirve si la cotización u OT ya no existe. La reutilización de IDs tras reiniciar solo la BD exige coordinar también `data/pdf-generados/` (véase DB.md). **E14 re-verificó ambas features por HTTP real**: los tres archivos existen en disco tras crear los registros, el GET sirve el snapshot sin regenerarlo, el fallback regenera al vuelo y los GET de registros inexistentes no sirven PDF residual (véase [Feature E12/E13](#feature-pdf)).

## Severidad — hallazgos nuevos (E14; H9/H17 corregidos y verificados en E15, 2026-09-25)

| Orden | Hallazgo | Severidad | Estado |
|---|---|---|---|
| 1 | [H9](#h9) | 🔴 **CRÍTICA** — un fallo real del servidor (incluso del propio certificado) se reportaba como "error del request" | RESUELTO en el alcance E15: fallo real de disco → 500, negocio → 400, correlativo → 409 |
| 2 | [H17](#h17) | 🟡 MEDIA — errores de binding y routing exponían stack trace en 400/404 | RESUELTO en el alcance E15: POST sin parámetro → 400 y GET inexistente → 404, ambos sin `trace` |
| 3 | [H13](#h13) | 🟠 ALTA — segunda puerta de mutación de estado sin guard (extensión de H4) | RESUELTO en E12; re-verificado por lectura+HTTP en E14 |
| 4 | [H15](#h15) | 🟠 MEDIA-ALTA — "OT implica cotización aprobada" no es invariante | RESUELTO en E12; reconfirmado por HTTP en E14 |
| 5 | [H14](#h14) | 🟠 ALTA (diseño) — regla de negocio nunca construida | SUPERADO como requisito: cierre manual de OT decidido; reconfirmado en E14 |
| 6 | [H10](#h10) | 🟡 MEDIA — migración cruda sin versionado | ABIERTO; confirmado por lectura (re-verificado E14) |
| 7 | [H12](#h12) | 🟡 MEDIA-BAJA — contador sin resincronización | ABIERTO; corrida E11: bloquea, no duplica (código idéntico en E14) |
| 8 | [H11](#h11) | 🟡 BAJA (dev) / ALTA (prod) — consola H2 + ddl-auto | ABIERTO; confirmado por lectura (re-verificado E14) |
| 9 | [H16](#h16) | ⚪ BAJA-MEDIA — enum zombie | RESUELTO en E12; reconfirmado por HTTP en E14 |

<a id="feature-pdf"></a>

## Feature E12/E13 — Persistencia automática de PDF — VERIFICADA en E14 (FreeBuff, HTTP real sobre `4385bca`)

**Qué se verificó y cómo (corrida completa en `validation/e14_http.log`, server real `./mvnw spring-boot:run`, base `./data/gesmin`):**

1. **Los tres PDF se persisten al crear el registro.** Tras crear cotización 2, OT 2 e informe 8 vía API:

```text
EXISTS data/pdf-generados/cotizacion-2.pdf (1706 bytes, %PDF=%PDF-)
EXISTS data/pdf-generados/orden-trabajo-2.pdf (1711 bytes, %PDF=%PDF-)
EXISTS data/pdf-generados/informe-tecnico-8-sin-firma.pdf (1982 bytes, %PDF=%PDF-)
```

2. **El GET sirve el snapshot y NO regenera.** `GET /api/cotizaciones/2/pdf → 200` con mtime del archivo idéntico antes/después (`1790365408 → 1790365408, SIN regeneración`). Mismo resultado para el PDF de la OT tras cambiar su estado (`PATCH OT EN_PROCESO → 200`, mtime sin cambio).

3. **Fallback: borrar el archivo a mano → el GET regenera al vuelo.** Se eliminó `cotizacion-2.pdf` del disco y `GET /api/cotizaciones/2/pdf` respondió `200` con un PDF válido (`%PDF-1.6`). Matiz de diseño verificado: la regeneración del fallback es **en memoria** y el archivo NO se reescribe en disco (el snapshot no se restaura). Consecuencia: mientras el archivo esté ausente, el PDF servido refleja los datos ACTUALES del registro, no el snapshot original de creación.

4. **E13: registro inexistente no sirve PDF residual** (por más que existiera un archivo huérfano en disco):

```text
[400] GET /api/cotizaciones/999999/pdf {"mensaje":"Cotización no encontrada con id 999999"}
[400] GET /api/ordenes-trabajo/999999/pdf {"mensaje":"Orden de trabajo no encontrada con id 999999"}
[400] GET /api/informes-tecnicos/999999/pdf {"mensaje":"Informe técnico no encontrado con id 999999"}
```

5. **Generación DESPUÉS de `correlativos.ejecutar(...)` y fuera de la transacción de retry** (lectura de líneas):
   - `CotizacionService.java:59-62`: `crear()` llama `correlativos.ejecutar(() -> crearUnaVez(dto))` y DESPUÉS `pdfGenerados.guardarCotizacion(creada.id())` (línea 61). `crearUnaVez` no genera PDF.
   - `OrdenDeTrabajoService.java:43-46`: mismo patrón; `guardarOrden` en línea 45.
   - `RevisionTecnicaService.java:80-88`: `registrarResultado` ejecuta el retry y solo si el intento confirmado produjo informe nuevo llama `pdfGenerados.guardarInforme(...)` (línea 87).
   - `DocumentoGeneradoService.java:27/31/35` (métodos `guardar*`), `:58` (`Files.createDirectories`) y `:67` (`Files.createLink` atómico que nunca reemplaza un snapshot previo).

6. **Snapshot inmutable ante cambios de estado del documento:** verificado por test (`PdfAutoPersistenciaTest`, verde en E14: "estados posteriores e idempotencia conservaron bytes") y por HTTP (mtime del PDF de la OT sin cambio tras `EN_PROCESO`).

**Veredicto:** la feature funciona según lo declarado por E12/E13; sin regresiones detectadas. Límite no probado: un fallo de permisos de disco posterior al commit solo está cubierto por log (el documento no revierte); no se forzó ese escenario por HTTP.

<a id="h1"></a>

## H1 — RESUELTO en el alcance probado

**Regla vigente:** un PATCH con el mismo resultado y observación no crea otro informe. Cambiar la observación de `CONFORME` anula el informe vigente y emite uno nuevo; no deja dos vigentes.

**Evidencia cruda (HTTP, base H2 nueva):**

```text
[200] PATCH /api/revisiones-tecnicas/1/resultado?resultado=CONFORME&observaciones=Original
{"id":1,"calibracionId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaRevision":"2026-09-24","revisor":"Auditor","resultado":"CONFORME","observaciones":"Original"}
[200] PATCH /api/revisiones-tecnicas/1/resultado?resultado=CONFORME&observaciones=Original
{"id":1,"calibracionId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaRevision":"2026-09-24","revisor":"Auditor","resultado":"CONFORME","observaciones":"Original"}
[200] GET /api/informes-tecnicos
[{"id":1,"numero":"IT260901","revisionTecnicaId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaEmision":"2026-09-24","estado":"GENERADO","pdfCargado":false,"fechaCargaPdf":null,"fechaEnvio":null,"fechaAnulacion":null,"motivoAnulacion":null}]
### H1 observación cambiada: anterior=ANULADO, histórico=2, vigente=1; PATCH idéntico no creó tercero
```

**Última verificación:** Codex, 2026-09-24; `scripts/respuesta_freebuff_http.py` y `AuditoriaAdversarial23ff2bdTest` sobre `4c3cbb8`. **Historial:** [E1](HISTORIAL_VALIDACION.md#e1) → [E3](HISTORIAL_VALIDACION.md#e3) (SUPERADO por E4/E5) → [E4](HISTORIAL_VALIDACION.md#e4) → [E5](HISTORIAL_VALIDACION.md#e5).

<a id="h2"></a>

## H2 — RESUELTO técnicamente; comunicación manual confirmada por Gesmin

**Regla vigente:** un informe vigente bloquea una revisión ajena. La revisión propietaria puede pasar de `CONFORME` a `NO_CONFORME` con observación: el informe pasa a `ANULADO` y la calibración a `EN_PROCESO`. Tras completar la calibración se puede certificar la misma revisión o crear otra; el informe anterior permanece en el historial y no se descarga. El propio informe es el registro de anulación consultable por `GET /api/informes-tecnicos/{id}` (estado, fecha y motivo); no existe un evento separado.

**Evidencia cruda (HTTP):**

```text
[200] PATCH /api/revisiones-tecnicas/1/resultado?resultado=NO_CONFORME&observaciones=Falla-confirmada
{"id":1,"calibracionId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaRevision":"2026-09-24","revisor":"Auditor","resultado":"NO_CONFORME","observaciones":"Falla-confirmada"}
[200] GET /api/informes-tecnicos/1
{"id":1,"numero":"IT260901","revisionTecnicaId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaEmision":"2026-09-24","estado":"ANULADO","pdfCargado":false,"fechaCargaPdf":null,"fechaEnvio":null,"fechaAnulacion":"2026-09-24","motivoAnulacion":"Reapertura por NO_CONFORME: Falla-confirmada"}
[400] GET /api/informes-tecnicos/1/pdf
{"mensaje":"El informe fue anulado y no está disponible para descarga."}
[200] PATCH /api/calibraciones/1/estado?estado=COMPLETADA
{"id":1,"evaluacionAptitudId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","tecnico":"Auditor","procedimiento":null,"fecha":"2026-09-24","estado":"COMPLETADA","patronesIds":[1],"patronesDescripcion":["CI-d56300 - Patrón auditoría"],"puntos":[]}
[200] PATCH /api/revisiones-tecnicas/1/resultado?resultado=CONFORME&observaciones=Corregida
{"id":1,"calibracionId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaRevision":"2026-09-24","revisor":"Auditor","resultado":"CONFORME","observaciones":"Corregida"}
[200] GET /api/informes-tecnicos
[{"id":1,"numero":"IT260901","revisionTecnicaId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaEmision":"2026-09-24","estado":"ANULADO","pdfCargado":false,"fechaCargaPdf":null,"fechaEnvio":null,"fechaAnulacion":"2026-09-24","motivoAnulacion":"Reapertura por NO_CONFORME: Falla-confirmada"},{"id":2,"numero":"IT260902","revisionTecnicaId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaEmision":"2026-09-24","estado":"GENERADO","pdfCargado":false,"fechaCargaPdf":null,"fechaEnvio":null,"fechaAnulacion":null,"motivoAnulacion":null}]
### H2 revisión nueva tras ANULADO: anterior=IT260916, nueva=IT260917
```

**Comunicación al cliente — alcance confirmado por Gesmin:** el responsable operativo consulta `GET /api/informes-tecnicos/{id}` y, si el certificado ya se comunicó, gestiona el aviso manual fuera de la API. El sistema no envía avisos ni registra constancia; `ANULADO` acredita la invalidación interna, no la recepción del aviso. No se implementa notificación automática. Véase [API.md](API.md#registro-y-comunicación-de-una-anulación-h2) y E12.

**Última verificación:** Codex, 2026-09-24; script HTTP y test `respuesta_h2_nuevaRevisionTrasAnularInformePrevio` sobre `4c3cbb8`; inspección del contrato API vigente el 2026-09-24. **Historial:** [E1](HISTORIAL_VALIDACION.md#e1) → [E3](HISTORIAL_VALIDACION.md#e3) (SUPERADO por E4/E5) → [E4](HISTORIAL_VALIDACION.md#e4) → [E5](HISTORIAL_VALIDACION.md#e5) → [E8](HISTORIAL_VALIDACION.md#e8) → [E12](HISTORIAL_VALIDACION.md#e12).

<a id="h3"></a>

## H3 — RESUELTO en el alcance probado (≤ 24 competidores por prefijo, 3/3 corridas)

**Regla vigente:** `CorrelativoService.siguiente` bloquea la fila anual `correlativos_contadores.prefijo` con `PESSIMISTIC_WRITE` dentro de la transacción del documento (`Propagation.MANDATORY`); el lock se libera solo al confirmar el documento. Los cuatro tipos E/COI/OT/IT lo usan. La carrera de inicialización del contador y errores transitorios se cubren con retry de tres intentos en transacción nueva (`REQUIRES_NEW`); su agotamiento responde **409** vía `CorrelativoAgotadoException`.

**Evidencia cruda, etiquetada por versión:**

```text
23ff2bd / FreeBuff / H3B / gesminauditoria: exitosos=3/4 nuevos=[1, 2, 3]
23ff2bd / HTTP / gesminhttp: [200, 200, 200, 400], SQLState 23505
4c3cbb8 / Codex / H3B / gesminauditoria: exitosos=4/4 nuevos=[1, 2, 3, 4]
4c3cbb8 / Codex / E barrier=8: resultados=[commit, commit, commit, commit, commit, commit, commit, commit], correlativos=[5, 6, 7, 8, 9, 10, 11, 12]
4c3cbb8 / Codex / IT barrier=8: resultados=[commit, commit, commit, commit, commit, commit, commit, commit], correlativos=[1, 2, 3, 4, 5, 6, 7, 8]
4c3cbb8 / FreeBuff / H3B+barrier=8 juntos / gesminauditoria: H3B exitosos=4/4 nuevos=[1, 2, 3, 4]; E barrier=8 8/8 commit; IT barrier=8 8/8 commit; suite completa 10/10 OK; HTTP agotamiento=409
--- E9: experimento cruzado, mismo test (AuditoriaH3Cruzada4c3cbb8Test), misma base gesminh3cruzada, misma sesión ---
23ff2bd / CRUZADA sonda 4 expedientes barrier: corridas 1-3 = exitosos=3/4 nuevos=[1, 2, 3]; FALLO=IllegalStateException "tras 3 intentos" [raíz: 23505 Unique index ... EXPEDIENTES(NUMERO) VALUES ('E260903')]
4c3cbb8 / CRUZADA sonda 4 expedientes barrier: corridas 1-3 = exitosos=4/4 nuevos=[1, 2, 3, 4]
4c3cbb8 / CRUZADA E barrier=8: 6/6 corridas = 8/8 commit, correlativos=[1..8]
4c3cbb8 / CRUZADA IT barrier=8: 3/3 corridas = 8/8 commit, correlativos=[1..8]
4c3cbb8 / CRUZADA E barrier=24: 3/3 corridas = 24/24 commit, correlativos=[1..24]
4c3cbb8 / CRUZADA IT barrier=24: 3/3 corridas = 24/24 commit, correlativos=[1..24]
```

En las corridas del código vigente los únicos `SQLState 23505` ocurren en `CORRELATIVOS_CONTADORES(PREFIJO)` (carrera de inicialización del contador, cubierta por el retry), no en el índice único del documento, que era la colisión real de `23ff2bd`.

**Contradicción histórica cerrada por ejecución (E9):** el mismo test (`AuditoriaH3Cruzada4c3cbb8Test`, sonda sin asserts), la misma base H2 en memoria (`gesminh3cruzada`), el mismo barrier y el mismo prefijo anual reproducen los dos números según el commit: **3/4 en 3 de 3 corridas sobre `23ff2bd`** (con `23505` real sobre `EXPEDIENTES(NUMERO)` dentro del barrier) y **4/4 en 3 de 3 sobre `4c3cbb8`**. Descartes por ejecución, ya no por teoría: (a) mismas bases y mismo entorno en ambas mitades del experimento; (b) el barrier alinea de verdad — el commit viejo colisiona dentro del barrier, es decir hay contención real, no desincronización; (c) los 4 expedientes compiten por el mismo prefijo `E26` en ambos commits. La causa es el algoritmo: `count()+1` con `MAX_INTENTOS=3` sin backoff (con N competidores el peor necesita N intentos y agota) vs. la fila anual `PESSIMISTIC_WRITE` que serializa hasta el commit del documento. Las ocho solicitudes HTTP de E en E5 también respondieron 200 y dieron `E260901`–`E260908`.

**Reproducción:**

```bash
# Experimento cruzado: la sonda (sin asserts) corre igual en ambos commits; 3 corridas por commit.
git worktree add /tmp/gesmin-23ff2bd 23ff2bd
cp src/test/java/com/kevin/backend/service/AuditoriaH3Cruzada4c3cbb8Test.java /tmp/gesmin-23ff2bd/src/test/java/com/kevin/backend/service/
cd /tmp/gesmin-23ff2bd && ./mvnw -q -Dtest='AuditoriaH3Cruzada4c3cbb8Test#cruzado_h3b_cuatroExpedientesMismoPrefijo' -Dsurefire.failIfNoSpecifiedTests=false test   # → 3/4
cd -  # volver al checkout vigente antes de los tests siguientes
# Contención vigente, E e IT por separado (N=8 y N=24 > MAX_INTENTOS=3):
./mvnw -q -Dtest='AuditoriaH3Cruzada4c3cbb8Test#cruzado_h3_E_N8_superaPresupuestoDeIntentos+cruzado_h3_IT_N8_superaPresupuestoDeIntentos' -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -q -Dtest='AuditoriaH3Cruzada4c3cbb8Test#cruzado_h3_E_N24_ochoVecesElPresupuesto+cruzado_h3_IT_N24_ochoVecesElPresupuesto' -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -q -Dtest='AuditoriaAdversarial23ff2bdTest#h3b_cuatroExpedientesSimultaneosColisionRealDeE+respuesta_h3_ochoCompetidoresSuperanTresIntentosEnMismoPrefijo' test
./mvnw -q -Dtest=CorrelativoErrorHttpTest test
```

Nota: surefire no acepta `,` para separar dos métodos de la misma clase (`#m1,m2` corre un solo método); la sintaxis válida es `#m1+m2`.

**Alcance probado y límites:** contención real con barrier de N=8 y N=24 competidores por prefijo (8× el presupuesto de `MAX_INTENTOS=3`) en las rutas E e IT por separado, 3 o más corridas por punto, sin commits perdidos y con correlativos consecutivos sin huecos ni repetidos (verificado por asserts). El experimento cruzado "ambos commits, mismo entorno" ya existe (E9): la refutación de `23ff2bd` quedó reproducida por ejecución, no solo por `git show`. El **409** de agotamiento está probado por `CorrelativoErrorHttpTest`. Límites que se mantienen: single-JVM contra H2 en memoria (sin prueba multi-nodo ni de timeouts de lock); la contención HTTP de E5 (8 requests) y el script `scripts/h3_auditoria_http_4c3cbb8.py` (N=12) no se reejecutaron en E9; sin prueba de contención ilimitada. No se encontró un N de fallo en el rango probado: el lock serializa y el presupuesto de intentos ya no es el límite; **veredicto RESUELTO sin matiz de N** (no hay "funciona salvo cuando N supera X" conocido). Los textos históricos no se borran: [E6](HISTORIAL_VALIDACION.md#e6) mantenía la contradicción abierta, [E7](HISTORIAL_VALIDACION.md#e7) la cerró por inspección y [E9](HISTORIAL_VALIDACION.md#e9) por ejecución. **Última verificación:** FreeBuff, 2026-09-24, experimento cruzado + contención N=8/N=24 sobre `4c3cbb8` (HEAD `328018b`, sin cambios de código). **Historial:** [E1](HISTORIAL_VALIDACION.md#e1) → [E3](HISTORIAL_VALIDACION.md#e3) (SUPERADO por E4/E5) → [E4](HISTORIAL_VALIDACION.md#e4) → [E5](HISTORIAL_VALIDACION.md#e5) → [E6](HISTORIAL_VALIDACION.md#e6) → [E7](HISTORIAL_VALIDACION.md#e7) → [E9](HISTORIAL_VALIDACION.md#e9).

<a id="h4"></a>

## H4 — RESUELTO en las transiciones definidas por Gesmin

**Regla vigente:** `TransicionesEstado.validarTransicion` se aplica a los PATCH de cotización, OT, calibración y expediente. La OT usa dos tablas: `ORDEN_PATCH` para el PATCH manual y `ORDEN_INTERNA` para evaluación; `actual == nuevo` devuelve sin mutación. El informe conserva la secuencia `GENERADO → PDF_CARGADO → APROBADO → ENVIADO`. Las tablas exactas para Nicolás están en [API.md](API.md#5-flujo-comercial-expediente--cotización--orden) y E12. Se mantiene `BORRADOR → APROBADA` directo.

**Evidencia actual:** `TransicionesEstadoTest` exige aprobación directa, rechaza terminales y distingue las dos puertas de OT; `FlujoEstadosServiceTest` recorre el loop NO_APTO con los servicios reales. La suite completa y el caso H15 de integración se registran en E12. El texto histórico de E1 que mostró saltos arbitrarios queda **SUPERADO por E12**; no se borra.

**E14 (HTTP real sobre `4385bca`):** las dos tablas de OT existen y se usan por la puerta correcta — `ORDEN_INTERNA` en `TransicionesEstado.java:24-29`, usada por `EvaluacionAptitudService` (líneas 71, 75, 114, 121); `ORDEN_PATCH` en `TransicionesEstado.java:31-36`, aplicada en `OrdenDeTrabajoService.java:92`. Corrida:

```text
[400] PATCH OT PENDIENTE->EN_ESPERA_CLIENTE {"mensaje":"Transición de estado no permitida: PENDIENTE -> EN_ESPERA_CLIENTE."}
[200] PATCH OT PENDIENTE->EN_PROCESO
[200] PATCH OT EN_PROCESO->COMPLETADA
[400] PATCH OT COMPLETADA->CANCELADA {"mensaje":"Transición de estado no permitida: COMPLETADA -> CANCELADA."}
```

**Última verificación:** FreeBuff, 2026-09-25, E14 (código + HTTP + suite). **Historial:** [E1](HISTORIAL_VALIDACION.md#e1) → [E2](HISTORIAL_VALIDACION.md#e2) → [E11](HISTORIAL_VALIDACION.md#e11) → [E12](HISTORIAL_VALIDACION.md#e12) → [E14](HISTORIAL_VALIDACION.md#e14).

<a id="h5"></a>

## H5 — PARCIAL: reapertura cerrada; cierre sin guards cruzados por decisión de Gesmin

**Regla vigente:** `CERRADO` y `RECHAZADO` son terminales; `CERRADO → EN_PROCESO` ya responde 400. Gesmin confirmó que un expediente puede cerrarse con una OT `CANCELADA`, por lo que no se añadió ese guard. El cierre tampoco valida automáticamente evaluaciones o informes. El posible control adicional de esos componentes permanece **ABIERTO** sin regla aprobada; no se declara un cierre global del hallazgo.

**Evidencia actual:** la tabla `TransicionesEstado.EXPEDIENTE` (líneas 38-42) rechaza salir de `CERRADO` y `TransicionesEstadoTest` lo comprueba; `ExpedienteService.cambiarEstado` (línea 61) es la única validación: no existen guards cruzados con OT, evaluaciones ni informes. La evidencia HTTP previa de E1, que reabría el expediente, está **SUPERADA por E12**.

**E14 (HTTP real sobre `4385bca`):**

```text
[200] PATCH /api/expedientes/2/estado?estado=CERRADO   (cerró con OT 2/3/4 PENDIENTE: sin guard cruzado)
[400] PATCH CERRADO->EN_PROCESO {"mensaje":"Transición de estado no permitida: CERRADO -> EN_PROCESO."}
```

El cierre con OTs vivas y `PENDIENTE` se ejecutó sin error: confirma que no hay validación cruzada, consistente con la decisión de Gesmin (solo se descartó el guard de OT CANCELADA; el resto de controles sigue sin regla aprobada). No se probó una validación cruzada de cierre porque no se implementó.

**Última verificación:** FreeBuff, 2026-09-25, E14. **Historial:** [E1](HISTORIAL_VALIDACION.md#e1) → [E11](HISTORIAL_VALIDACION.md#e11) → [E12](HISTORIAL_VALIDACION.md#e12) → [E14](HISTORIAL_VALIDACION.md#e14).

<a id="h6"></a>

## H6 — ABIERTO

**Regla vigente:** se permiten varias revisiones `PENDIENTE` sobre una calibración `COMPLETADA`; algunas pueden quedar sin uso. El guard del informe vigente de H2 no impide crear esas pendientes antes de certificar.

**Evidencia cruda:** `validation/requests.log` caso 2:

```text
POST /revisiones-tecnicas -> 201 | {"id":2,"calibracionId":1,"instrumentoDescripcion":"Fluke 87V - Serie SN-170403","fechaRevision":"2026-09-23","revisor":"Concurrente-A","resultado":"PENDIENTE","observaciones":null}
POST /revisiones-tecnicas -> 201 | {"id":3,"calibracionId":1,"instrumentoDescripcion":"Fluke 87V - Serie SN-170403","fechaRevision":"2026-09-23","revisor":"Concurrente-B","resultado":"PENDIENTE","observaciones":null}
```

En el test vigente `h1Yh2ImpidenRepetirRevisionYEmitirSegundoInforme` se crean dos revisiones pendientes antes de certificar la primera.

**E14 (HTTP real sobre `4385bca`):** dos revisiones `PENDIENTE` (#75 y #76) creadas sobre la misma calibración 35 `COMPLETADA` sin informe activo:

```text
[201] POST /api/revisiones-tecnicas {"id":75,"calibracionId":35,...,"resultado":"PENDIENTE"}
[201] POST /api/revisiones-tecnicas {"id":76,"calibracionId":35,...,"resultado":"PENDIENTE"}
```

Matiz nuevo verificado: cuando la calibración YA tiene un informe activo, el guard introducido en E12 (`RevisionTecnicaService.java:66-70`, "esta calibración ya tiene un informe técnico") rechaza revisiones nuevas con 400. El hallazgo sigue abierto en su forma original (pendientes sin usar antes de certificar).

**Última verificación:** FreeBuff, 2026-09-25, E14 (HTTP). **Historial:** [E1](HISTORIAL_VALIDACION.md#e1) → [E3](HISTORIAL_VALIDACION.md#e3) → [E14](HISTORIAL_VALIDACION.md#e14).

<a id="h7"></a>

## H7 — ABIERTO como observación de ambiente; no atribuido al backend

**Hecho documentado:** E1 registró que, tras morir el proceso sin cierre limpio, `IT260906` visto por GET desapareció y la revisión #11 volvió a `PENDIENTE`. Los reinicios limpios conservaron datos; no hubo reinicios de devtools. No hay una reproducción actual ni una prueba que atribuya la pérdida al código.

**Evidencia cruda:** [validation/requests.log](validation/requests.log) registra `PATCH /revisiones-tecnicas/11/resultado?resultado=CONFORME -> 200` (línea 212), `GET /informes-tecnicos -> 200` con `IT260906` (línea 213), y después de la interrupción `GET /revisiones-tecnicas?calibracionId=1 -> 200` con revisión #11 `PENDIENTE` (línea 243) y `GET /informes-tecnicos -> 200` sin `IT260906` (línea 244). Las responses completas permanecen allí y en E1. **Última verificación:** FreeBuff, 2026-09-23. **Historial:** [E1](HISTORIAL_VALIDACION.md#e1).

<a id="h8"></a>

## H8 — ABIERTO: decisión de contrato HTTP

**Regla vigente:** un `GET /{id}` inexistente devuelve 400 mediante `GlobalExceptionHandler.handleRuntime`; no hay decisión registrada para cambiarlo a 404. El 409 de agotamiento de correlativo tiene handler separado y no modifica esta regla.

**Evidencia cruda:** `validation/requests.log` caso 12:

```text
GET /clientes/99999 -> 400 | {"mensaje":"Cliente no encontrado con id 99999"}
GET /calibraciones/99999 -> 400 | {"mensaje":"Calibración no encontrada con id 99999"}
GET /informes-tecnicos/99999 -> 400 | {"mensaje":"Informe Técnico no encontrado con id 99999"}
```

El handler vigente retorna `HttpStatus.BAD_REQUEST` para `RuntimeException`.

**E14 (HTTP real sobre `4385bca`):**

```text
[400] GET /api/clientes/999999 {"mensaje":"Cliente no encontrado con id 999999"}
[400] GET /api/cotizaciones/999999 {"mensaje":"Cotización no encontrada con id 999999"}
[400] GET /api/ordenes-trabajo/999999 {"mensaje":"Orden de Trabajo no encontrada con id 999999"}
```

Sin cambio de contrato (ver también [H17](#h17): estos 400 además llegan por la rama genérica de `RuntimeException`).

**Última verificación:** FreeBuff, 2026-09-25, E14 (HTTP). **Historial:** [E1](HISTORIAL_VALIDACION.md#e1) → [E5](HISTORIAL_VALIDACION.md#e5) → [E14](HISTORIAL_VALIDACION.md#e14).

<a id="h9"></a>

## H9 — RESUELTO en E15, alcance probado: errores de servidor responden 500

**Regla vigente:** `GlobalExceptionHandler` devuelve 400 para `IllegalArgumentException` de negocio y para `MethodArgumentNotValidException`; `CorrelativoAgotadoException` mantiene 409. Las demás `RuntimeException` se registran con `logger.error` y stack en el log, y responden 500 con solo `{"mensaje":"Error interno del servidor."}`. El 400 observado por FreeBuff en E14 para un fallo de disco queda **SUPERADO por E15**; el texto y la salida originales siguen íntegros en [E14](HISTORIAL_VALIDACION.md#e14).

**Evidencia cruda (2026-09-25):**

```text
$ ./mvnw -q -Dtest='HallazgoH9RamasHandlerTest,CorrelativoErrorHttpTest' test
### H9b Bean Validation POST /api/marcas → HTTP 400 {"nombre":"El nombre de la marca es obligatorio"}
### H9b excepción de NEGOCIO (IllegalArgumentException) → HTTP 400 {"mensaje":"Cliente no encontrado con id 99999"}
### H9b fallo de SERVIDOR (IllegalStateException + IOException de disco) → HTTP 500 {"mensaje":"Error interno del servidor."}
### HTTP POST /api/expedientes?clienteId=7
### HTTP 409 {"mensaje":"No se pudo asignar un correlativo único tras 3 intentos."}
exit_code=0

$ ./mvnw test
### H9 sonda directa: java.lang.IllegalStateException: No se pudo almacenar el PDF firmado. [causa: java.nio.file.FileAlreadyExistsException: /home/kevin/proyectos/proyecto-backend/data/pdf-firmados]
### H9 HTTP POST /api/informes-tecnicos/1/pdf-firmado (fallo real de disco)
### H9 HTTP 500 {"mensaje":"Error interno del servidor."}
Tests run: 41, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
exit_code=0
```

**Reproducción:** `./mvnw -q -Dtest='HallazgoH9RamasHandlerTest,CorrelativoErrorHttpTest' test` para ramas 400/500/409; `./mvnw -q -Dtest='AuditoriaHallazgosNuevos4c3cbb8Test#h9_falloRealDeAlmacenamientoDelCertificadoSeRespondeComo500' test` para fallo real de disco con PDF válido y ruta HTTP controlada por MockMvc; `./mvnw test` para regresión. El test de disco restaura `data/pdf-firmados` en su `finally`.

**Límite:** la sonda de fallo real usa servicio y controlador reales con MockMvc, no un proceso HTTP externo. E14 sí usó servidor real para demostrar el 400 anterior. No se hizo prueba de observabilidad de logs desplegados ni de todas las posibles `RuntimeException`.

**Última verificación:** Codex, 2026-09-25, E15 (ramas HTTP y fallo real de disco). **Historial:** [E11](HISTORIAL_VALIDACION.md#e11) → [E14](HISTORIAL_VALIDACION.md#e14) → [E15](HISTORIAL_VALIDACION.md#e15).

<a id="h10"></a>

## H10 — ABIERTO, confirmado por lectura: migración de esquema cruda en cada arranque, sin control de versión

**Evidencia citada:** `src/main/java/com/kevin/backend/config/InformeEstadoSchemaMigration.java:22-23`:

```java
jdbc.execute("ALTER TABLE informes_tecnicos ALTER COLUMN estado "
        + "ENUM('APROBADO', 'ENVIADO', 'GENERADO', 'PDF_CARGADO', 'ANULADO') NOT NULL");
```

Re-verificado en E14 (25-09-2026, checkout `4385bca`): el código sigue igual (mismo `ApplicationRunner` sin condición, líneas 14-23). El `pom.xml` vigente no contiene Flyway ni Liquibase (grep sin resultados; dependencias: data-jpa, validation, webmvc, pdfbox 3.0.8, devtools, lombok, h2, h2console, springdoc 2.8.5 y starters de test). El resto del esquema sigue dependiendo de `ddl-auto=update`. No hay registro de qué migraciones se aplicaron. Es sintaxis específica de H2: el día que se cambie de motor, el arranque se rompe sin aviso.

**Nota honesta (no se puede probar por corrida):** el riesgo es de despliegue futuro (PostgreSQL/MySQL), no de la configuración actual; un test sobre H2 solo demostraría lo que ya demostró `InformeEstadoSchemaMigrationTest` (que la sentencia es idempotente en H2). Demostrar el fallo requeriría una base PostgreSQL real. La lectura del código es la evidencia completa disponible.

**Responsable (Codex):** introducir versionado de migraciones (Flyway/Liquibase) o, como mínimo, un mecanismo de "migración ya aplicada". Sesión de higiene de infraestructura, junto con H11.

**Última verificación:** FreeBuff, 2026-09-25 (E11) y re-verificado por lectura en E14 sobre `4385bca`. **Historial:** [E11](HISTORIAL_VALIDACION.md#e11) → [E14](HISTORIAL_VALIDACION.md#e14).

## H11 — ABIERTO, confirmado por lectura: consola H2 habilitada en la configuración única (sin perfiles)

**Evidencia citada:** `src/main/resources/application.properties` (único properties de `src/main/resources/`; no existen `application-dev.properties` ni `application-prod.properties`):

```text
Línea 8:  spring.jpa.hibernate.ddl-auto=update
Línea 11: spring.h2.console.enabled=true
Línea 12: spring.h2.console.path=/h2-console
Línea 13: spring.h2.console.settings.web-allow-others=true
```

Además `pom.xml:71` incluye `spring-boot-h2console`. No hay `spring-boot-starter-actuator` (verificado por grep). Como no hay separación por perfiles, esta configuración ES el perfil productivo si la app se despliega así: la consola H2 quedaría accesible desde fuera con usuario `sa` y contraseña vacía.

**Responsable (Codex + decisión Gesmin sobre el motor de destino):** separar `application-dev.properties` / `application-prod.properties` antes de cualquier despliegue.

**E14 (re-verificación por lectura, checkout `4385bca`):** `src/main/resources/` sigue conteniendo un único `application.properties` (glob `application*.properties` = 1 archivo; no hay perfiles dev/prod). Líneas vigentes: 8 `spring.jpa.hibernate.ddl-auto=update`, 11 `spring.h2.console.enabled=true`, 12 `spring.h2.console.path=/h2-console`, 13 `spring.h2.console.settings.web-allow-others=true`. El `pom.xml` sigue incluyendo `spring-boot-h2console` y sin actuator ni spring-security: la consola H2 queda expuesta sin protección y sin opción de desactivarla por perfil.

**Última verificación:** FreeBuff, 2026-09-25 (E11) y re-verificado por lectura en E14 sobre `4385bca`. **Historial:** [E11](HISTORIAL_VALIDACION.md#e11) → [E14](HISTORIAL_VALIDACION.md#e14).

<a id="h12"></a>

## H12 — ABIERTO, confirmado por corrida (con matiz): el contador no se resincroniza tras borrados del año en curso

**Evidencia citada:** `src/main/java/com/kevin/backend/service/CorrelativoService.java:26-31`: el `orElseGet` de `contadores.bloquear(prefijo)` inicializa el contador nuevo con `historicos.getAsLong()` (un `COUNT()`), y solo la primera vez que no existe la fila. Después de ese momento, nada vuelve a consultar el conteo real.

**Evidencia cruda (corrida E11, `h12_contadorRecreadoSeAlimentaDeCountYColisionaConVivos`):** se crearon `E260902`, `E260903`, `E260904` y luego se simuló la limpieza manual descrita en el informe: se borró una fila INTERNA (`E260903`, no la última) y la fila `E26` de `correlativos_contadores`:

```text
### H12 vivos=E260902,E260904 (COUNT previo con las tres=4)
### H12 fila interna E260903 y contador E26 borrados; contador recreado desde COUNT()
### H12 nueva creación FALLO: CorrelativoAgotadoException: No se pudo asignar un correlativo único tras 3 intentos.
      [raíz: ...Unique index or primary key violation: ...EXPEDIENTES(NUMERO ...) VALUES ( /* 4 */ 'E260904' )]
### H12 veredicto: el contador recreado NO se resincroniza con el máximo vivo; la creación falla por colisión
```

**Matiz respecto del informe:** el efecto medido es incluso peor de lo descrito: el `COUNT()` post-borrado (2) hizo recrear el contador y la siguiente creación intentó reasignar `E260904`, que ESTÁ viva → el intento colisiona 3 veces (el `CorrelativoRetry` no ayuda porque el desalineamiento no es transitorio) y **toda creación de expedientes queda bloqueada** (409) hasta una corrección manual. No duplica números (el unique constraint protege), pero deja la ruta inutilizable.

**Decisión de negocio implicada (no implementar sin Gesmin):** si Gesmin prevé reseeds o limpiezas de datos de prueba, la inicialización debería calcular `MAX(número)` vivo y no `COUNT()`; si nunca se borra nada, la conducta actual es irrelevante en producción. La corrección es de código (Codex), pero la necesidad depende de la práctica operativa (Gesmin).

**E14 (re-verificación por lectura, checkout `4385bca`):** el código no cambió. `CorrelativoService.java:27-31`: `contadores.bloquear(prefijo).orElseGet(...)` inicializa con `nuevo.setUltimo(historicos.getAsLong())` (línea 29), y ese `historicos` es un `COUNT()` aportado por cada servicio (p. ej. `ExpedienteService.java:66-68`). Después de la primera creación del año, nada vuelve a consultar el conteo real: sin resync posterior.

**E14 no re-ejecutó el experimento E11** (borrado de fila + contador y colisión posterior); la evidencia de la colisión sigue siendo la corrida E11 citada arriba, sobre código idéntico en la zona relevante (4c3cbb8 → 4385bca no tocó CorrelativoService: verificado por `git diff --shortstat`, solo modos de archivo).

**Última verificación:** FreeBuff, 2026-09-25, corrida E11 (ejecución) + lectura E14. **Historial:** [E11](HISTORIAL_VALIDACION.md#e11) → [E14](HISTORIAL_VALIDACION.md#e14).

<a id="h13"></a>

## H13 — RESUELTO en las puertas de mutación conocidas

**Regla vigente:** `EvaluacionAptitudService.registrarResultado` y `registrarRespuestaCliente` validan con `ORDEN_INTERNA` antes de mutar OT. `RevisionTecnicaService.registrarResultado` valida `COMPLETADA → EN_PROCESO` con la tabla de Calibración. La puerta adicional `CalibracionService.registrarMediciones` usa la misma tabla. Los PATCH aplican las tablas de H4.

**Evidencia:** búsqueda de `setEstado` en los servicios y pruebas `TransicionesEstadoTest`; `FlujoEstadosServiceTest` recorre el loop interno, y la suite completa de E12 cubre las rutas adversariales previas. El hecho de E11 de mutaciones directas sin guard queda **SUPERADO por E12**.

**E14 (re-verificación por lectura + HTTP, checkout `4385bca`):** inventario completo de mutaciones de OT fuera del PATCH: `EvaluacionAptitudService.java:71-76` (NO_APTO/APTO), `:113-122` (respuesta cliente → PENDIENTE/CANCELADA) — todas precedidas de `validarTransicion` con `ORDEN_INTERNA` (líneas 71, 75, 114, 121). Calibración: `CalibracionService.java:71` (PATCH), `:97-99` (`registrarMediciones`), `RevisionTecnicaService.java:147-149` (reapertura) usan la tabla `CALIBRACION`; `ExpedienteService.java:61` usa `EXPEDIENTE`; `CotizacionService.java:105-107` usa `COTIZACION`. No queda ninguna `setEstado` de flujo sin validación previa. El loop NO_APTO completo fue además recorrido por HTTP real en E14 (ver [H14](#h14) y la tabla de abajo de esta ficha en E14b).

**Última verificación:** FreeBuff, 2026-09-25, E14 (lectura + HTTP + suite). **Historial:** [E11](HISTORIAL_VALIDACION.md#e11) → [E12](HISTORIAL_VALIDACION.md#e12) → [E14](HISTORIAL_VALIDACION.md#e14).

<a id="h14"></a>

## H14 — SUPERADO como requisito automático; OT COMPLETADA sigue manual

**Decisión vigente de Kevin/Gesmin:** no crear lógica automática que marque la OT `COMPLETADA`. El operador puede aplicar `PATCH /api/ordenes-trabajo/{id}/estado?estado=COMPLETADA` desde `EN_PROCESO`; `COMPLETADA` es terminal. El hallazgo de E11, «ningún flujo la marca automáticamente», sigue siendo verdadero como hecho, pero ya no representa una función pendiente dentro del alcance confirmado.

**Evidencia:** `TransicionesEstado.ORDEN_PATCH` (líneas 31-36) permite `EN_PROCESO → COMPLETADA` y rechaza salir de `COMPLETADA`; `TransicionesEstadoTest` verifica la terminalidad. No se construyó automatismo.

**E14 (HTTP real sobre `4385bca`):** el PATCH manual funciona y ningún flujo automático alcanza `COMPLETADA`:

```text
[200] PATCH /api/ordenes-trabajo/3/estado?estado=EN_PROCESO
[200] PATCH /api/ordenes-trabajo/3/estado?estado=COMPLETADA
[400] PATCH /api/ordenes-trabajo/3/estado?estado=CANCELADA {"mensaje":"Transición de estado no permitida: COMPLETADA -> CANCELADA."}
```

Por lectura: la única escritura de `EstadoOrdenTrabajo.COMPLETADA` en los servicios está en el PATCH (`OrdenDeTrabajoService.java:92-96` vía `orden.setEstado(nuevoEstado)`); evaluación y respuesta-cliente solo escriben `EN_ESPERA_CLIENTE`, `EN_PROCESO`, `PENDIENTE` y `CANCELADA` (líneas 71-76 y 113-122 de `EvaluacionAptitudService.java`). El cierre de la OT sigue siendo una decisión manual del operador, como se decidió.

**Última verificación:** FreeBuff, 2026-09-25, E14 (HTTP + lectura). **Historial:** [E11](HISTORIAL_VALIDACION.md#e11) → [E12](HISTORIAL_VALIDACION.md#e12) → [E14](HISTORIAL_VALIDACION.md#e14).

<a id="h15"></a>

## H15 — RESUELTO: cotización APROBADA con OT asociada no se degrada

**Regla vigente:** `APROBADA` es terminal. Si ya existe una OT, `CotizacionService.actualizarEstado` devuelve un mensaje específico al intentar cambiar el estado. Repetir `APROBADA` es idempotente. `BORRADOR → APROBADA` directo se mantiene.

**Evidencia cruda:** E11 probó la degradación antigua; **SUPERADA por E12**. La misma prueba de integración fue conservada y actualizada para exigir el guard:

```text
### H15 OT OT260901 estado=PENDIENTE creada sobre COI COI260901 (guard de APROBADA funcionó al crear)
### H15 PATCH COI COI260901 estado=RECHAZADA con OT viva → bloqueado: No se puede cambiar la cotización aprobada: ya tiene una Orden de Trabajo asociada.
### H15 invariante: OT 1 sigue PENDIENTE sobre cotización APROBADA
```

**E14 (HTTP real sobre `4385bca`):**

```text
[400] PATCH /api/cotizaciones/2/estado?estado=RECHAZADA {"mensaje":"No se puede cambiar la cotización aprobada: ya tiene una Orden de Trabajo asociada."}
[200] PATCH /api/cotizaciones/2/estado?estado=APROBADA   (idempotente)
```

con las OTs 2, 3 y 4 vivas (`PENDIENTE`/`EN_PROCESO`/`COMPLETADA`) sobre esa cotización. Guard en `CotizacionService.java:102-107` (antes de la tabla `COTIZACION`, línea 108).

**Última verificación:** FreeBuff, 2026-09-25, E14 (HTTP). **Historial:** [E11](HISTORIAL_VALIDACION.md#e11) → [E12](HISTORIAL_VALIDACION.md#e12) → [E14](HISTORIAL_VALIDACION.md#e14).

<a id="h16"></a>

## H16 — RESUELTO: `ACEPTADO` retirado del expediente

**Regla vigente:** `EstadoExpediente` solo admite `EN_PROCESO`, `EN_ESPERA`, `RECHAZADO` y `CERRADO`. `API.md` y `DB.md` ya no anuncian `ACEPTADO`. Antes de retirarlo, `SELECT ESTADO, COUNT(*) FROM EXPEDIENTES GROUP BY ESTADO` sobre `data/gesmin.mv.db` devolvió únicamente `CERRADO | 1`; no hubo filas que migrar.

**Evidencia:** enum, contrato y esquema actualizados; consulta de BD y regresión de E12. La pregunta histórica de E11 queda **SUPERADA por la decisión de Gesmin** de eliminarlo.

**E14 (re-verificación, checkout `4385bca`):** grep de `ACEPTADO` en todo el repo: solo aparecen menciones históricas/documentales (`VALIDACION.md`, `HISTORIAL_VALIDACION.md`, `HALLAZGOS_NUEVOS.md`, `CAMBIOS_PARA_CODEX.md`, `Contexto - Gesmin.txt`); cero en `src/`. Por HTTP:

```text
[400] PATCH /api/expedientes/3/estado?estado=ACEPTADO
{"mensaje":"Method parameter 'estado': Failed to convert value of type 'java.lang.String' to required type 'com.kevin.backend.model.EstadoExpediente'; ... for value [ACEPTADO]"}
```

**Última verificación:** FreeBuff, 2026-09-25, E14 (grep + HTTP). **Historial:** [E11](HISTORIAL_VALIDACION.md#e11) → [E12](HISTORIAL_VALIDACION.md#e12) → [E14](HISTORIAL_VALIDACION.md#e14).

<a id="h17"></a>

## H17 — RESUELTO en E15, alcance probado: 400/404 de Spring sin stack en el body

**Regla vigente:** `application.properties` fija `server.error.include-stacktrace=never` y `server.error.include-message=always`. El stack que FreeBuff observó en E14 queda **SUPERADO por E15**; su salida original está intacta en [E14](HISTORIAL_VALIDACION.md#e14). La propiedad de mensaje permite incluirlo cuando Spring lo proporciona; los dos cuerpos observados no contienen campo `message`.

**Evidencia cruda (servidor embebido con puerto aleatorio y H2 aislada):**

```text
$ ./mvnw -q -Dtest=ErrorResponseHttpTest test
### E15 GET /api/no-existe-e15 → HTTP 404 {"timestamp":"2026-09-25T22:02:41.494Z","status":404,"error":"Not Found","path":"/api/no-existe-e15"}
### E15 POST /api/expedientes → HTTP 400 {"timestamp":"2026-09-25T22:02:41.529Z","status":400,"error":"Bad Request","path":"/api/expedientes"}
exit_code=0
```

El POST omite `clienteId`, parámetro obligatorio. El test comprueba código HTTP, cuerpo JSON y ausencia de claves `trace` y `exception` en ambos casos.

**Reproducción:** `./mvnw -q -Dtest=ErrorResponseHttpTest test` o `./mvnw test`. **Límite:** se verificaron estas dos rutas de Spring en el perfil por defecto; no se agregaron perfiles dev/prod.

**Última verificación:** Codex, 2026-09-25, E15 (HTTP real embebido). **Historial:** [E14](HISTORIAL_VALIDACION.md#e14) → [E15](HISTORIAL_VALIDACION.md#e15).
