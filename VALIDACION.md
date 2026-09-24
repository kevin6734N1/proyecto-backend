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

## H2 — RESUELTO en el flujo probado

**Regla vigente:** un informe vigente bloquea una revisión ajena. La revisión propietaria puede pasar de `CONFORME` a `NO_CONFORME` con observación: el informe pasa a `ANULADO` y la calibración a `EN_PROCESO`. Tras completar la calibración se puede certificar la misma revisión o crear otra; el informe anterior permanece en el historial y no se descarga.

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

**Última verificación:** Codex, 2026-09-24; script HTTP y test `respuesta_h2_nuevaRevisionTrasAnularInformePrevio` sobre `4c3cbb8`. La comunicación al cliente de una anulación ya enviada sigue manual. **Historial:** [E1](HISTORIAL_VALIDACION.md#e1) → [E3](HISTORIAL_VALIDACION.md#e3) (SUPERADO por E4/E5) → [E4](HISTORIAL_VALIDACION.md#e4) → [E5](HISTORIAL_VALIDACION.md#e5).

<a id="h3"></a>

## H3 — ABIERTO: contradicción histórica de evidencia

**Código vigente:** `CorrelativoService.siguiente` bloquea `correlativos_contadores.prefijo` con `PESSIMISTIC_WRITE` en la transacción del documento. Los cuatro tipos E/COI/OT/IT lo usan. Se conserva retry de tres intentos para la carrera de inicialización y errores transitorios.

**Evidencia cruda, etiquetada por versión:**

```text
23ff2bd / FreeBuff / H3B / gesminauditoria: exitosos=3/4 nuevos=[1, 2, 3]
23ff2bd / HTTP / gesminhttp: [200, 200, 200, 400], SQLState 23505
4c3cbb8 / Codex / H3B / gesminauditoria: exitosos=4/4 nuevos=[1, 2, 3, 4]
4c3cbb8 / Codex / E barrier=8: resultados=[commit, commit, commit, commit, commit, commit, commit, commit], correlativos=[5, 6, 7, 8, 9, 10, 11, 12]
4c3cbb8 / Codex / IT barrier=8: resultados=[commit, commit, commit, commit, commit, commit, commit, commit], correlativos=[1, 2, 3, 4, 5, 6, 7, 8]
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

**Causa identificada:** no son resultados del mismo código. `23ff2bd` calculaba `count()+1` y reintentaba hasta tres veces; `4c3cbb8` usa una fila anual bloqueada. (a) Sí, las corridas usan bases H2 distintas, pero eso no basta para explicar la diferencia. (b) No: `enParalelo` usa `CyclicBarrier(hebras)`. (c) No: todos los E compiten por `E26` y todos los IT por `IT26`, aunque sus calibraciones sean distintas. Las ocho solicitudes HTTP de E en E5 también respondieron 200 y dieron `E260901`–`E260908`.

**Reproducción del código vigente:** `./mvnw -q -Dtest=AuditoriaAdversarial23ff2bdTest#h3b_cuatroExpedientesSimultaneosColisionRealDeE+respuesta_h3_ochoCompetidoresSuperanTresIntentosEnMismoPrefijo test`.

**Contradicción abierta:** las afirmaciones opuestas del historial no se borran. Falta comparar ambos commits en un experimento controlado idéntico y probar más de ocho competidores y timeouts extremos; por ello no se declara garantía universal. El HTTP histórico registrado fue **400**, no 500. **Última verificación:** Codex, 2026-09-24, test H3B + barrier=8 juntos sobre `4c3cbb8`; FreeBuff verificó `23ff2bd`. **Historial:** [E1](HISTORIAL_VALIDACION.md#e1) → [E3](HISTORIAL_VALIDACION.md#e3) (SUPERADO por E4/E5) → [E4](HISTORIAL_VALIDACION.md#e4) → [E5](HISTORIAL_VALIDACION.md#e5) → [E6](HISTORIAL_VALIDACION.md#e6).

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
