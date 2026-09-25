# Gesmin Backend — Cambios a implementar (para Codex)

**Código base:** commit `4c3cbb8`. Este documento junta lo decidido en la sesión de diseño (Claude + otra IA + Kevin) del 25 set 2026 con lo confirmado por Gesmin ese mismo día. Reemplaza cualquier propuesta anterior que aquí se marque como descartada.

**Regla de alcance, léela primero:** el sistema sigue un flujo lineal de 20 pasos con solo 3 puntos de decisión (cotización aceptada / instrumento apto / resultados conformes) y un único loop confirmado (instrumento no apto → vuelve a evaluación). Gesmin nunca pidió reaperturas de documentos cerrados, overrides administrativos de estado, ni funcionalidad de notificación. **No agregues nada que no esté explícitamente en la sección 1.** Si algo te parece "faltante" pero no está aquí, no lo implementes — pregunta antes.

> **Revisión 2 (25 set 2026, FreeBuff, pre-envío a Codex):** tres correcciones sobre la versión original: (1) Orden de Trabajo pasa a tener **DOS tablas** (flujo interno vs PATCH crudo) — la versión anterior afirmaba que `EvaluacionAptitudService` "no debería fallar" contra una sola tabla y era falso; (2) decisión explícita: `BORRADOR → APROBADA` directo **se permite** (la tabla anterior lo rompía); (3) el helper cortocircuita `actual == nuevo` para conservar la idempotencia validada en E1 caso 5. Además queda fijado que `COMPLETADA` de OT es terminal (decisión de Kevin, mismo día).

---

## 1. Cambios a implementar

### 1.1 Persistir los 3 PDF al momento de crearse (requerimiento nuevo, confirmado por Gesmin)

**Qué pide Gesmin:** que los PDF de Cotización, Orden de Trabajo e Informe Técnico se generen con todos los datos y **queden guardados en el sistema**, no solo servidos al vuelo. Kevin decidió que se generen automáticamente apenas se crea el registro (mismo criterio que ya usan `codigoSnapshot`/`nombreSnapshot` en `DetalleCotizacion`: una foto fija del momento).

**Estado actual:** `DocumentoPdfService.cotizacion(id)` / `.orden(id)` / `.informe(id)` ya generan el PDF completo con PDFBox, pero `DocumentoPdfController` los sirve regenerándolos en cada `GET`, sin guardar nada. El único PDF que sí se persiste hoy es el firmado (`InformeFirmadoService`, guarda a `./data/pdf-firmados/`).

**Qué construir:**
1. Nueva carpeta de almacenamiento `./data/pdf-generados/` (mismo patrón que `pdf-firmados`).
2. Propiedad de configuración (agregar a `application.properties`):
   ```properties
   gesmin.pdf-generados-dir=./data/pdf-generados
   ```
   Usar `@Value("${gesmin.pdf-generados-dir:./data/pdf-generados}")` y crear el directorio si no existe (`Files.createDirectories(...)`) la primera vez, igual que hace `InformeFirmadoService` con su ruta.
3. Al crear cada documento, generar los bytes con el método existente de `DocumentoPdfService` y escribirlos a disco con nombre determinístico:
   - `cotizacion-{id}.pdf`
   - `orden-trabajo-{id}.pdf`
   - `informe-tecnico-{id}-sin-firma.pdf`
4. `GET /api/.../pdf`: servir el archivo persistido si existe; si no existe (dato de antes de este cambio, o falló la escritura), regenerar al vuelo como hoy (fallback, no debe romper nada existente).
5. No hace falta columna nueva en la BD — el path es determinístico por tipo+id. Cambio quirúrgico, sin migración de esquema.

**Regla de snapshot (importante):** el PDF se genera **una sola vez** al crear el registro y **no se regenera** cuando el documento cambia de estado después. Es una foto fija del momento de creación (mismo criterio que `codigoSnapshot`/`nombreSnapshot`). Ejemplo: la cotización se crea en `BORRADOR` → el PDF queda con ese contenido aunque luego pase a `ENVIADA` o `APROBADA`.

**Dónde enganchar la generación — esto es importante, no lo cambies de lugar:**
- Cotización: dentro de `CotizacionService.crear()`, **después** de que `correlativos.ejecutar(() -> crearUnaVez(dto))` retorna. NO dentro de `crearUnaVez()`.
- Orden de Trabajo: igual, en `OrdenDeTrabajoService.crear()`, después del `ejecutar(...)`.
- Informe Técnico: en `RevisionTecnicaService.registrarResultado()`, después del `ejecutar(...)`, **solo si esa llamada efectivamente generó o reemitió un informe**. Criterio concreto:
  1. Si el resultado de la revisión quedó en `CONFORME`.
  2. Consultar el informe vigente de esa revisión (método existente tipo `findByRevisionTecnicaIdAndEstadoNot(..., ANULADO)` o el equivalente que ya use el servicio).
  3. Si existe un informe activo recién creado/reemitido, generar y persistir el PDF con su `id`.
  4. Si la llamada solo anuló un informe previo, o no generó informe nuevo, no escribir archivo.

**Por qué tiene que ser así:** `crearUnaVez()` y `registrarResultadoUnaVez()` corren dentro de la transacción que maneja `CorrelativoRetry`, la cual se reintenta hasta 3 veces en una transacción **nueva** si hay colisión de correlativo (`DataIntegrityViolationException`/`TransientDataAccessException`). Escribir un archivo a disco es una operación con efecto de lado que **no hace rollback** si la transacción de BD se revierte. Si generas el PDF dentro de esos métodos privados, un reintento puede dejar archivos huérfanos en disco de intentos fallidos. Generarlo después de que `ejecutar(...)` retorna garantiza que el PDF corresponde exactamente al registro que sí quedó guardado.

**Manejo de errores:** si falla la escritura del PDF después de que el registro ya se guardó en BD, no debe fallar la creación del documento (el registro ya es válido) — solo loggear el error; el fallback de regeneración al vuelo en el GET cubre ese caso.

---

### 1.2 Máquina de estados (H4) — versión recortada, solo transiciones hacia adelante

Reemplaza cualquier propuesta anterior con marcas ⚠️: las decisiones de negocio no confirmadas por Gesmin se resolvieron por "no lo pidieron, no lo construimos".

**Mecanismo:** un helper reutilizable, sin librerías externas:
```java
void validarTransicion(Enum<?> actual, Enum<?> nuevo, Map<Enum<?>, Set<Enum<?>>> permitidas)
```
generalizando el patrón que ya existe y funciona en `InformeTecnicoService.actualizarEstado` (tabla de transiciones + `IllegalArgumentException` con mensaje claro → ya cae en 400 vía `GlobalExceptionHandler`).

**Idempotencia (obligatoria dentro del helper):** si `actual == nuevo`, el helper retorna sin error ANTES de consultar la tabla. La validación adversarial (E1, caso 5) estableció que dos PATCH con el mismo estado responden 200 con la misma representación y sin efectos secundarios; un helper que rechace `actual == nuevo` rompería ese comportamiento validado. Ejemplo: `PATCH /api/calibraciones/1/estado?estado=COMPLETADA` sobre una calibración ya `COMPLETADA` sigue respondiendo 200 sin cambios.

**Una tabla por puerta de mutación, no una global:** el mapa `permitidas` es un parámetro a propósito. Donde hay dos puertas con reglas distintas (Orden de Trabajo: servicios internos vs PATCH crudo) se definen DOS mapas; donde la puerta es única (Cotización, Calibración, Expediente) se usa el mismo mapa en todos sus puntos de mutación. Ver la sección de Orden de Trabajo.

**Tablas de transición:**

- **Cotización** (`EstadoCotizacion`): `BORRADOR→{ENVIADA, APROBADA, RECHAZADA}`, `ENVIADA→{APROBADA,RECHAZADA}`, `APROBADA→{}` (terminal), `RECHAZADA→{}` (terminal). Sin reapertura de ENVIADA/RECHAZADA — no está en el diagrama, no se construye.
  - **Decisión sobre `BORRADOR → APROBADA` directo (revisión 2):** se PERMITE y no es un descuido. El diagrama original no tiene un paso "enviar cotización" entre generar y aceptar (la aceptación llega por correo, fuera del sistema), y todo el historial validado (E1 con 125 requests, los scripts HTTP y la corrida E11) aprueba `BORRADOR → APROBADA` en un solo PATCH — obligar a pasar por `ENVIADA` rompería el camino feliz ya validado y el frontend de Nicolás, que hoy parchea directo. `ENVIADA` queda como paso opcional para cuando se quiera registrar el envío. Si Gesmin exigiera el paso obligatorio más adelante, el cambio es una línea de esta tabla, nada más.
  - Guard adicional en `CotizacionService`: bloquear cualquier `actualizarEstado()` sobre una Cotización `APROBADA` si ya existe una `OrdenDeTrabajo` asociada (`ordenDeTrabajoRepository.findByCotizacionId`). Esto cierra el hallazgo H15: hoy se puede re-parchear a `RECHAZADA` una cotización con una OT ya viva colgando de ella. (Con `APROBADA` terminal en la tabla este guard es redundante como protección, pero produce un mensaje de error mucho más claro que el genérico de transición; déjalo.)

- **Calibración** (`EstadoCalibracion`): `PROGRAMADA→{EN_PROCESO}`, `EN_PROCESO→{COMPLETADA,CANCELADA}`, `COMPLETADA→{EN_PROCESO}` (**no tocar esta transición regresiva** — es el loop real de `NO_CONFORME` que ya usa `RevisionTecnicaService`; bloquearla rompe funcionalidad existente), `CANCELADA→{}` (terminal).

- **Orden de Trabajo** (`EstadoOrdenTrabajo`): hay DOS tablas porque hay DOS puertas de mutación. El helper recibe la tabla como parámetro: define ambos mapas y pásale a cada punto de mutación el suyo. Confundirlas es la trampa principal de esta implementación.

  **Tabla COMPLETA (flujo de negocio) — para `EvaluacionAptitudService` y cualquier mutación interna de OT:**
  - `PENDIENTE → {EN_PROCESO, EN_ESPERA_CLIENTE, CANCELADA}`
  - `EN_PROCESO → {COMPLETADA, CANCELADA}`
  - `EN_ESPERA_CLIENTE → {PENDIENTE, CANCELADA}`
  - `COMPLETADA → {}` (terminal — decisión de Kevin, 25 set 2026)
  - `CANCELADA → {}` (terminal)

  Estas cuatro transiciones son las que hoy dispara `EvaluacionAptitudService` y DEBEN pasar el validador contra esta tabla: `PENDIENTE→EN_ESPERA_CLIENTE` (línea 70, NO_APTO), `PENDIENTE→EN_PROCESO` (línea 72, APTO), `EN_ESPERA_CLIENTE→PENDIENTE` (línea 109, cliente autoriza), `EN_ESPERA_CLIENTE→CANCELADA` (línea 114, cliente no autoriza). Las dos últimas NO existen en la tabla del PATCH — por eso la versión anterior de este documento, que hablaba de una sola tabla, habría roto el loop NO_APTO del diagrama.

  **Tabla RESTRINGIDA — SOLO para el PATCH crudo (`OrdenDeTrabajoService.actualizarEstado`):**
  - `PENDIENTE → {EN_PROCESO, CANCELADA}` (arranque manual = paso 8 del diagrama: "ingreso físico al laboratorio")
  - `EN_PROCESO → {COMPLETADA, CANCELADA}`
  - `EN_ESPERA_CLIENTE → {CANCELADA}` (una OT atascada esperando al cliente puede cancelarse a mano; no se la puede forzar a `PENDIENTE` ni a `EN_PROCESO` por PATCH)
  - `COMPLETADA → {}` y `CANCELADA → {}` (terminales también por PATCH)

  El PATCH **no** permite `EN_ESPERA_CLIENTE` ni la vuelta a `PENDIENTE`: esos estados salen exclusivamente de `EvaluacionAptitudService`, como ya funciona hoy — no se toca esa parte.

  Nota de conducta (cambio esperado, no bug): hoy un PATCH crudo puede poner la OT en `EN_PROCESO` y después registrarse una evaluación `NO_APTO` (que dispararía `EN_PROCESO → EN_ESPERA_CLIENTE`). Con la tabla completa esa secuencia pasa a responder 400 — es el blindaje trabajando: según el diagrama, la evaluación de aptitud precede al trabajo. Si algún test vigente quedara en rojo por esto, detente y pregunta; NO amplíes la tabla por tu cuenta.


  `EstadoOrdenTrabajo.COMPLETADA`: **no se construye lógica automática nueva para alcanzarlo.** Queda como transición manual disponible por PATCH (`EN_PROCESO → COMPLETADA`), porque el diagrama nunca la pide como requisito de nada. Es terminal (decisión de Kevin, 25 set 2026): una OT `COMPLETADA` no vuelve atrás ni se cancela, ni por flujo ni por PATCH.

- **Expediente** (`EstadoExpediente`): `EN_PROCESO→{RECHAZADO,EN_ESPERA,CERRADO}`, `EN_ESPERA→{EN_PROCESO,RECHAZADO}`, `RECHAZADO→{}` (terminal), `CERRADO→{}` (terminal — esto bloquea la reapertura de un expediente cerrado que encontró la validación adversarial). **Sin guard cruzado de "ninguna OT cancelada"** — Kevin confirmó con Gesmin que un expediente con una OT `CANCELADA` se cierra igual, así que no se agrega esa validación.

**Aplicar también donde hoy se muta estado sin pasar por `actualizarEstado()`:** `EvaluacionAptitudService` (`registrarResultado`, `registrarRespuestaCliente`) y `RevisionTecnicaService` (línea donde hace `calibracion.setEstado(EstadoCalibracion.EN_PROCESO)`) escriben `OrdenDeTrabajo.estado`/`Calibracion.estado` directo con `setEstado(...)`, sin pasar por el método validado. Si el validador solo se agrega al PATCH crudo, esta segunda puerta queda igual de abierta. Usa el mismo helper `validarTransicion` ahí también, PERO con la tabla que corresponda a cada puerta: para `RevisionTecnicaService` (muta `Calibracion.estado`, línea 135) la tabla única de Calibración; para `EvaluacionAptitudService` (muta `OrdenDeTrabajo.estado`) la **tabla COMPLETA** de OT, NUNCA la restringida del PATCH — sus transiciones (`PENDIENTE→EN_ESPERA_CLIENTE`, `PENDIENTE→EN_PROCESO`, `EN_ESPERA_CLIENTE→PENDIENTE`, `EN_ESPERA_CLIENTE→CANCELADA`) no existen en la tabla del PATCH y fallarían si le pasas esa. Con la tabla correcta nada de lo que ya funciona debe romperse — es solo blindaje.

**Verificación de la implementación:** `./mvnw test` completo en verde antes de dar por cerrado el punto — al día de hoy son 32 tests, incluidas las suites de auditoría H1–H3 (`AuditoriaAdversarial23ff2bdTest` 10, `AuditoriaH3Cruzada4c3cbb8Test` 5, `CorrelativoErrorHttpTest` 1, `HallazgosConcurrenciaTest` 5, `DocumentoPdfServiceTest` 4, etc.). Si una transición de la máquina nueva rompe un test vigente, NO lo borres ni lo debilites: detente y consulta.

---

### 1.3 Eliminar `EstadoExpediente.ACEPTADO` del enum

No aparece en ningún flujo de negocio ni lo pide el diagrama original (el "sí" del paso 7 simplemente continúa el flujo, no crea un estado "aceptado" propio para el expediente). Antes de borrarlo del enum, revisa si hay filas de datos de prueba en la BD local con ese valor; si las hay, pásalas a `EN_PROCESO` antes de quitar el valor. Actualiza también la documentación que lo lista como valor posible: `API.md` (sección de expedientes, enum de estados) y `DB.md` (comentario de la columna estado) — si quedan desincronizadas, el valor "fantasma" seguirá confundiendo al frontend.

---

## 2. Qué NO construir (para no agregar alcance por iniciativa propia)

- Reapertura de Cotización `ENVIADA`/`RECHAZADA` a `BORRADOR`.
- Cualquier override administrativo que fuerce manualmente estados de OT fuera de las 2–3 transiciones permitidas en 1.2.
- Feature de notificación al cliente al anular un certificado — el caso técnico (evitar 2 certificados activos para la misma calibración) ya lo resuelve `InformeTecnicoService.anularActivos`; lo demás es proceso manual fuera del sistema.
- Guard de cierre de expediente basado en OT `CANCELADA` — confirmado que no debe bloquear.
- Lógica automática nueva para alcanzar `EstadoOrdenTrabajo.COMPLETADA`.
- Regenerar PDFs cuando un documento cambia de estado (son snapshots del momento de creación).

---

## 3. Ya resuelto, no tocar

- **H1** (informe duplicado al re-parchear CONFORME) y **H3** (correlativo no thread-safe): resueltos (`existsByRevisionTecnicaIdAndEstadoNot` + lock pesimista con reintento vía `CorrelativoRetry`).
- **H2** (sin guard post-ENVIADO): mitigado por `InformeTecnicoService.anularActivos`, que ya impide que coexistan dos informes activos para la misma calibración.

---

## 4. Pendiente real, fuera de este sprint (no depende de Codex ni de Gesmin todavía)

- **H9** — `GlobalExceptionHandler` captura `RuntimeException` genérica → 400 siempre, ocultando bugs reales de servidor (ej. `NullPointerException`) como si fueran errores de cliente. Cuando se resuelva: diferenciar `IllegalArgumentException` / excepciones de negocio propias → 400; cualquier otra `RuntimeException` → 500 + log. Es decisión técnica de Kevin, no bloquea el flujo, se puede hacer en cualquier momento.
- **H10** — migración de esquema con `ALTER TABLE` crudo en cada arranque (`InformeEstadoSchemaMigration`), sin Flyway/Liquibase. Tema de infraestructura, no bloquea el flujo funcional.
- **H11** — consola H2 expuesta (`web-allow-others=true`) + `ddl-auto=update`. Aceptable en desarrollo, revisar antes de cualquier despliegue real.
- **H12** — el contador de correlativos solo se resincroniza vía `COUNT()` la primera vez que no existe fila para el prefijo/año; si algún día se borran filas del año en curso, puede desalinearse. Riesgo bajo, no urgente.
- Módulos priorizados que siguen sin tocarse por este cambio: **Usuario + Permisos**, **módulo Calidad**, **exportar tablas a Excel**.

---

## 5. Preguntas que siguen abiertas con Gesmin

Solo queda una, y no depende de código:

- **Paso 15, "Cargar PDF firmado":** el mecanismo ya está resuelto — Gesmin confirmó que quiere archivo real subido **y** metadata ("ambos"), y eso ya lo cumple `InformeFirmadoService`. No hay pregunta pendiente aquí; se deja documentado para cerrar el tema formalmente en `VALIDACION.md`/`HISTORIAL_VALIDACION.md`.
