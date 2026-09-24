# Auditoría adversarial del commit `23ff2bd` — H1, H2, H3

**Fecha:** 2026-09-24 · **Método:** refutación con evidencia cruda (diff, tests de colisión real con barrier, HTTP end-to-end). Ni el mensaje del commit ni `VALIDACION.md` se tomaron como prueba.

**Artefactos de evidencia (nuevos, ejecutables):**
- `src/test/java/com/kevin/backend/service/AuditoriaAdversarial23ff2bdTest.java` — 7 tests de colisión real (H2 en memoria `gesminauditoria`).
- `scripts/auditoria_http_23ff2bd.py` — auditoría HTTP end-to-end con barrier real (corrida contra server con `jdbc:h2:mem:gesminhttp`, base de desarrollo `./data/gesmin` intacta).

**Veredicto global:**

| Hallazgo | Afirmación de Codex | Veredicto |
|---|---|---|
| H1 | "solo un informe, PATCH repetido conserva datos" | ✅ **PROBADO** (serial y concurrente sobre la misma revisión) |
| H2 | "otra revisión tras informe es rechazada" | ⚠️ **PARCIAL** — el guard existe y funciona, pero **dejó el ciclo NO_CONFORME permanentemente bloqueado** tras un informe emitido (Regla 2 confirmada) |
| H3 | "reintento con transacción nueva, sin huecos ni duplicados" | ❌ **REFUTADO** — colisión real de 4 competidores ⇒ **commit perdido garantizado** (`MAX_INTENTOS=3` < 3 competidores + 1). La prueba de Codex no activó nunca el retry real |

---

## Regla 0 — Diff leído antes de correr nada

`git show 23ff2bd --stat` → 13 archivos, +428/−34. Ubicación de cada fix en el diff:

- **H1**: `RevisionTecnicaService.registrarResultadoUnaVez`, líneas 90–93 del archivo actual:
  ```java
  if (revision.getResultado() == resultado) {
      return toDTO(revision); // PATCH repetido: no cambia observaciones ni crea otro informe.
  }
  if (revision.getResultado() != ResultadoRevision.PENDIENTE || resultado == ResultadoRevision.PENDIENTE) {
      throw new IllegalArgumentException("El resultado de una revisión solo puede registrarse una vez.");
  }
  ```
- **H2**: guard en `crear()` (líneas 59–62) + guard en `registrarResultadoUnaVez()` (líneas 98–102), ambos apoyados en `InformeTecnicoRepository.existsByCalibracionId` (query JPQL nueva). El commit dice: *"Una reemisión requiere un flujo explícito, no otra revisión sobre el mismo trabajo."*
- **H3**: `CorrelativoRetry.java` nuevo (líneas 19, 28–35: `MAX_INTENTOS = 3`, loop que reintenta `DataIntegrityViolationException|TransientDataAccessException` en tx `REQUIRES_NEW`); envoltura de las 4 rutas de correlativo (`ExpedienteService.crear` L39, `CotizacionService.crear` L52, `OrdenDeTrabajoService.crear` L38, `RevisionTecnicaService.registrarResultado` L78–80) + `saveAndFlush` para que la violación de constraint explote **dentro** del intento.

La lógica que el commit dice contener **sí existe** en el diff. El problema no es de ausencia de código: es de diseño insuficiente y de pruebas que no ejercitan lo que dicen probar.

---

## Hallazgo 1 — H3 REFUTADO: commit perdido con 4 competidores reales

### 1a. La prueba de Codex no probó el retry

En `HallazgosConcurrenciaTest`:

- `reintentoAbreNuevaTransaccionDespuesDeRollback` hace `throw new DataIntegrityViolationException("Colisión simulada")` — la excepción se **lanza a mano**; no hay ninguna constraint violada, ningún flush, ninguna base involucrada en la "colisión".
- `h3AsignaCorrelativosDistintosEnLasCuatroRutas` usa **dos calibraciones distintas** (lo confiesa `VALIDACION.md`: *"Para probar H3 se requieren dos calibraciones distintas"*) y sin barrier agresivo sobre el mismo prefijo. Sin colisión, el retry nunca se ejecuta: prueba de que "números distintos" ocurren incluso **sin el fix** (dos tx seriales siempre generan números distintos).

### 1b. Colisión real forzada (evidencia nueva)

**Test:** `h3b_cuatroExpedientesSimultaneosColisionRealDeE` — 4 hebras con `CyclicBarrier`, mismo `clienteId`, mismo prefijo `E26` + `count()+1` + `unique(numero)`.

**Resultado (3 de 3 corridas idénticas):**
```
### H3B exitosos=3/4 nuevos=[1, 2, 3] (baseline=0)
### H3B FALLO=IllegalStateException/No se pudo asignar un correlativo único tras 3 intentos.
      [raíz: JdbcSQLIntegrityConstraintViolationException: Unique index or primary key violation:
       "PUBLIC.CONSTRAINT_F ... ON PUBLIC.EXPEDIENTES(NUMERO ...) VALUES ( 'E260903' )"]
### H3B ok=exp=E260901
### H3B ok=exp=E260902
### H3B ok=exp=E260903
```
Assertion fallida: `H3B: COMMIT PERDIDO — alguna creación falló ==> expected: <4> but was: <3>`

**Test:** `h3a2_cuatroConformesSimultaneosColisionRealDeIT` — 4 CONFORME simultáneos sobre 4 calibraciones distintas **con barrier** (a diferencia de Codex, que los lanzó sin sincronizar):
```
### H3A2 exitosos=3/4 correlativos=[2, 3, 4] (baseline=1)   ← corrida 1 y 2
### H3A2 exitosos=3/4 correlativos=[1, 2, 3] (baseline=0)   ← corrida 3
### H3A2 FALLO=IllegalStateException/No se pudo asignar un correlativo único tras 3 intentos.
      [raíz: JdbcSQLIntegrityConstraintViolationException ... 'IT260904' / 'IT260903']
```

**Evidencia HTTP end-to-end** (server real, 4 `POST /api/expedientes` con barrier):
```
>>> HTTP codes: [200, 200, 200, 400]
[200] {"id":2,"numero":"E260901",...}
[200] {"id":8,"numero":"E260902",...}
[200] {"id":35,"numero":"E260903",...}
[400] {"mensaje":"No se pudo asignar un correlativo único tras 3 intentos."}
```
Log del server: **18 eventos SQLState 23505** (`Unique index or primary key violation`).

### 1c. Por qué es *garantizado* y no aleatorio

Con N competidores sincronizados por barrier, el peor se queda sin número N veces seguidas. El fix permite `MAX_INTENTOS = 3`. Con **N = 4** el perdedor necesita ≥ 4 intentos ⇒ `IllegalStateException` con probabilidad ≈ 1. Con barrier, no es una carrera probabilística: es aritmética. `VALIDACION.md` afirma *"obtuvieron números diferentes y ambas operaciones terminaron correctamente"* — cierto para **2** competidores, falso para 4.

### 1d. Lo que sí se sostiene de H3

- Sin duplicados ni huecos entre los commits que **sí** se comprometieron (`[1,2,3]` contiguo; verificado en las 4 corridas).
- `h3c_reintentoDelFixEscapaDeColisionRealDeConstraint`: colisión **real** de `unique(ruc)` (no simulada) dentro del bean real `CorrelativoRetry` ⇒ reintenta 1 vez, el intento 1 no deja filas (rollback limpio), el intento 2 compromete. **El mecanismo de retry en sí funciona; se queda corto a partir de 3 competidores.**

---

## Hallazgo 2 — H2 rompió el ciclo legítimo (Regla 2 confirmada)

El guard bloquea `CONFORME` cuando ya hay informe, pero **no anula ni distingue el informe previo** cuando la vida real vuelve por NO_CONFORME. Evidencia (test `h2_cicloNoConformeTrasInformeQuedaBloqueado` + HTTP):

```
[200] PATCH /api/revisiones-tecnicas/2/resultado?resultado=NO_CONFORME&observaciones=Falla-post-emitido
      → la calibración vuelve a EN_PROCESO PERO el certificado IT260901 ya emitido sigue VIVO (no se anula)
[200] PATCH /api/calibraciones/1/estado?estado=COMPLETADA          ← corregida
[400] POST /api/revisiones-tecnicas
      {"mensaje":"No se puede crear otra revisión: esta calibración ya tiene un informe técnico."}
[400] PATCH /api/revisiones-tecnicas/2/resultado?resultado=CONFORME
      {"mensaje":"El resultado de una revisión solo puede registrarse una vez."}
```

Estado final: calibración `COMPLETADA` + certificado vigente cuyo instrumento **acaba de ser reportado como no conforme**. Sin flujo explícito de anulación/reemisión (que el commit promete pero no implementa), **no existe ningún camino** para recertificar: ciclo roto + incoherencia de datos. La única salida es editar la base a mano.

---

## Hallazgo 3 — Lo que Codex afirmó y SÍ quedó probado

- **H1 serial** (`h1_repetirConforme...`): segundo PATCH conserva `"Obs original"`, informes=1. HTTP: `[200] PATCH ...&observaciones=IGNORAME` seguido de `GET /api/informes-tecnicos` → exactamente 1 informe `IT260901`.
- **H1 concurrente** (`h3a1_dosConformesSimultaneosMismaRevision`): 2 CONFORME con barrier sobre la misma revisión → 1 informe, el perdedor recibe `El resultado de una revisión solo puede registrarse una vez.` (400 controlado).
- **Atomicidad revisión+informe** (afirmada en VALIDACION.md, ahora probada): `h3d_falloAlPersistirInformeDebeRevertirLaRevision` — bean `@Primary` saboteado deja insertar el IT (post-flush) y **explota después**:
  ```
  ### H3D explosion => java.lang.IllegalStateException: SABOTAJE: fallo al persistir el informe
  ### H3D estado revisión tras explosión: PENDIENTE | informes antes=5 después=5
  ```
  La revisión vuelve a `PENDIENTE`, el IT no queda comprometido, y tras desarmar el sabotaje la emisión funciona (1 informe). La combinación `Propagation.MANDATORY` + tx única del retry hace real la atomicidad.

---

## Error colateral de API (menor, real)

`GlobalExceptionHandler` mapea **cualquier** `RuntimeException` → 400. El `IllegalStateException` de retry agotado (un fallo de infraestructura/concurrencia) sale como `400 Bad Request` con `{"mensaje":"No se pudo asignar un correlativo único tras 3 intentos."}`. Debería ser 409/500; hoy el cliente no puede distinguir "datos inválidos" de "colisión de concurrencia".

---

## Correcciones mínimas recomendadas

1. **H3**: en lugar de `count()+1` con reintento a ciegas, usar `MAX(numero)` con `PESSIMISTIC_WRITE` sobre un locking/query nativo con `READ_COMMITTED` + `SELECT ... FOR UPDATE` en la fila "última", o bien una tabla de secuencias por prefijo actualizada atómicamente (`UPDATE seq SET v = v+1 WHERE prefijo = ?` y leer el valor). Si se conserva el retry: `MAX_INTENTOS ≥ competidores esperados + margen` (o dinámico) **con backoff aleatorizado** — hoy los 3 reintentos vuelven a chocar de inmediato.
2. **H2**: al registrar `NO_CONFORME` sobre una calibración con informe vivo, exigir una decisión explícita sobre el certificado (anular `EstadoInforme.ANULADO` + reemisión con nuevo correlativo), o impedir `NO_CONFORME` post-emitido y canalizar la reclamos por otro flujo. Hoy queda el dato incoherente.
3. **HTTP**: handler específico para `IllegalStateException` de correlativos → 409 con cuerpo de reintento.

## Cómo reproducir

```bash
# Suite de auditoría (H3A2/H3B fallan: es el hallazgo, no un error del test)
./mvnw -q -Dtest=AuditoriaAdversarial23ff2bdTest test

# Auditoría HTTP (base en memoria; no toca ./data/gesmin)
SPRING_DATASOURCE_URL='jdbc:h2:mem:gesminhttp;DB_CLOSE_DELAY=-1' \
  SPRING_JPA_HIBERNATE_DDL_AUTO=create ./mvnw spring-boot:run &
python3 scripts/auditoria_http_23ff2bd.py
```
