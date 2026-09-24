# Gesmin Backend — Validación vigente

**Código evaluado:** `4c3cbb8` (2026-09-24). Cada hallazgo tiene una ficha única. `RESUELTO` indica que la conducta señalada se verificó en el alcance descrito; `ABIERTO` indica una regla aún sin defender, una decisión pendiente o evidencia contradictoria sin comparación controlada. Los textos previos y sus veredictos se conservan literalmente en [HISTORIAL_VALIDACION.md](HISTORIAL_VALIDACION.md).

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

## H2 — ABIERTO para la comunicación de anulación; trazabilidad y recertificación verificadas

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

**Comunicación al cliente — decisión de Gesmin pendiente:** la anulación interna no acredita que el cliente haya sido avisado. El paso operativo actual es que el responsable operativo consulte `GET /api/informes-tecnicos/{id}` y, si el certificado ya se comunicó, gestione el aviso manual fuera de la API. El sistema no envía avisos ni registra constancia de aviso. Gesmin debe elegir entre mantener evidencia externa, añadir constancia manual por API o automatizar con evento persistente y consumidor; también debe fijar destinatarios, responsable y momento. Véase [API.md](API.md#registro-y-comunicación-de-una-anulación-h2).

**Última verificación:** Codex, 2026-09-24; script HTTP y test `respuesta_h2_nuevaRevisionTrasAnularInformePrevio` sobre `4c3cbb8`; inspección del contrato API vigente el 2026-09-24. **Historial:** [E1](HISTORIAL_VALIDACION.md#e1) → [E3](HISTORIAL_VALIDACION.md#e3) (SUPERADO por E4/E5) → [E4](HISTORIAL_VALIDACION.md#e4) → [E5](HISTORIAL_VALIDACION.md#e5) → [E8](HISTORIAL_VALIDACION.md#e8).

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

## H4 — ABIERTO; subcaso del informe SUPERADO

**Regla vigente:** el informe solo admite carga de PDF, aprobación y envío en secuencia; `GENERADO → ENVIADO` directo se rechaza. Cotización, OT, calibración y expediente aún aceptan cambios de estado sin una máquina de transiciones.

**Evidencia cruda antigua (SUPERADA por E2):** `validation/requests.log` registró:

```text
PATCH /informes-tecnicos/4/estado?estado=ENVIADO -> 200 | {"id":4,"numero":"IT260904","revisionTecnicaId":5,"instrumentoDescripcion":"Fluke 87V - Serie SN-170403","fechaEmision":"2026-09-23","estado":"ENVIADO","pdfCargado":false,"fechaCargaPdf":null,"fechaEnvio":"2026-09-23"}
```

**Evidencia directa actual:** En el código vigente, `InformeTecnicoService.actualizarEstado` lanza excepción si no es `PDF_CARGADO → APROBADO` o `APROBADO → ENVIADO`. `CotizacionService.actualizarEstado`, `OrdenDeTrabajoService.actualizarEstado`, `CalibracionService.actualizarEstado` y `ExpedienteService.cambiarEstado` hacen `setEstado(nuevoEstado)` y guardan sin comprobar transición.

**Última verificación:** Codex, 2026-09-24, inspección de código vigente; E2 probó el informe. No se repitió HTTP para los otros estados. **Historial:** [E1](HISTORIAL_VALIDACION.md#e1) → [E2](HISTORIAL_VALIDACION.md#e2).

<a id="h5"></a>

## H5 — ABIERTO

**Regla vigente:** el cierre del expediente no valida OT, evaluación ni informe y permite reabrirlo.

**Evidencia cruda:** `validation/requests.log` caso 9 registró OT `CANCELADA` y evaluación `NO_APTO` sin respuesta, y luego:

```text
PATCH /expedientes/1/estado?estado=EN_PROCESO -> 200 | {"id":1,"numero":"E260901","fecha":"2026-09-23","clienteId":1,"clienteRazonSocial":"Acme Val 170403","estado":"EN_PROCESO"}
PATCH /expedientes/1/estado?estado=CERRADO -> 200 | {"id":1,"numero":"E260901","fecha":"2026-09-23","clienteId":1,"clienteRazonSocial":"Acme Val 170403","estado":"CERRADO"}
```

El código actual de `ExpedienteService.cambiarEstado` sigue ejecutando `exp.setEstado(nuevoEstado); return ...save(exp)` sin otras comprobaciones.

**Última verificación:** FreeBuff, 2026-09-23 (HTTP); Codex, 2026-09-24 (inspección de código). **Historial:** [E1](HISTORIAL_VALIDACION.md#e1).

<a id="h6"></a>

## H6 — ABIERTO

**Regla vigente:** se permiten varias revisiones `PENDIENTE` sobre una calibración `COMPLETADA`; algunas pueden quedar sin uso. El guard del informe vigente de H2 no impide crear esas pendientes antes de certificar.

**Evidencia cruda:** `validation/requests.log` caso 2:

```text
POST /revisiones-tecnicas -> 201 | {"id":2,"calibracionId":1,"instrumentoDescripcion":"Fluke 87V - Serie SN-170403","fechaRevision":"2026-09-23","revisor":"Concurrente-A","resultado":"PENDIENTE","observaciones":null}
POST /revisiones-tecnicas -> 201 | {"id":3,"calibracionId":1,"instrumentoDescripcion":"Fluke 87V - Serie SN-170403","fechaRevision":"2026-09-23","revisor":"Concurrente-B","resultado":"PENDIENTE","observaciones":null}
```

En el test vigente `h1Yh2ImpidenRepetirRevisionYEmitirSegundoInforme` se crean dos revisiones pendientes antes de certificar la primera.

**Última verificación:** FreeBuff, 2026-09-23 (HTTP concurrente); Codex, 2026-09-24 (test de servicio). **Historial:** [E1](HISTORIAL_VALIDACION.md#e1) → [E3](HISTORIAL_VALIDACION.md#e3).

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

**Última verificación:** FreeBuff, 2026-09-23 (HTTP); Codex, 2026-09-24 (inspección de handler). **Historial:** [E1](HISTORIAL_VALIDACION.md#e1) → [E5](HISTORIAL_VALIDACION.md#e5).
