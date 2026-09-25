# Gesmin Backend — Guía de API para Frontend

Documentación para consumir la API REST del sistema de gestión técnico (cotizaciones → calibraciones → certificados).

---

## 1. Puesta en marcha

```bash
./mvnw spring-boot:run
```

| Dato | Valor |
|------|-------|
| Base URL | `http://localhost:8080/api` |
| Base de datos | H2 **en archivo** (`jdbc:h2:file:./data/gesmin`, carpeta `data/` — ver `.gitignore`) |
| Consola H2 | `http://localhost:8080/h2-console` (usuario `sa`, contraseña vacía) |
| Auth | **Ninguna todavía** (no hay tokens ni login; módulo Usuario pendiente) |

> ⚠️ **IMPORTANTE — CORS:** el backend solo acepta peticiones desde `http://localhost:5175` (ver `config/CorsConfig.java`). Si tu dev server corre en otro puerto (ej. el 5173 por defecto de Vite), o configuras el tuyo al 5175, o pide a Kevin que agregue tu origen.

> ✅ **Los datos SÍ persisten** (desde la validación del 2026-09-23 la BD está en archivo): podés reiniciar el backend sin perder nada. Si borrás la carpeta `data/` sí se pierde todo. Ver [historial E1](HISTORIAL_VALIDACION.md#e1) para la sesión de pruebas.

---

## 2. Convenciones generales

- **JSON** en todas las request/response (`Content-Type: application/json`).
- **Fechas**: formato ISO `"2026-09-23"`. **Horas**: `"14:30:00"`.
- **Enums**: strings en MAYÚSCULAS exactas (ej. `"APROBADA"`, `"EN_PROCESO"`). Un valor inválido devuelve 400.
- **IDs**: numéricos (`Long`).

### Formato de errores

| Caso | HTTP | Body |
|------|------|------|
| Error de validación (`@NotBlank`, `@NotNull`, etc.) | 400 | `{"marca": "La marca es obligatoria", ...}` — un campo por clave, valor = mensaje |
| Regla de negocio / entidad no encontrada | 400 | `{"mensaje": "No se puede generar una Orden de Trabajo: ..."}` |
| Falta un query param obligatorio | 400 | error por defecto de Spring (con `message`) |

> Ojo: los "no encontrado" devuelven **400, no 404**. Maneja ambos casos leyendo `mensaje` del body.

---

## 3. Catálogos maestros

### Marcas — `/api/marcas`
| Método | Ruta | Notas |
|--------|------|-------|
| GET | `/` | Lista todas |
| POST | `/` | `{ "nombre": "Fluke" }` |
| DELETE | `/{id}` | Físico |

### Modelos — `/api/modelos`
Igual que marcas. `{ "nombre": "87V" }`.

### Productos — `/api/productos`
| Método | Ruta | Body / Query |
|--------|------|--------------|
| GET | `/?buscar=texto` | Busca por nombre (opcional) |
| GET | `/{id}` | |
| POST | `/` | `{ "codigo": "PRD-001", "nombre": "...", "marcaId": 1, "modeloId": 1, "precio": 99.90, "stock": 10 }` |
| PUT | `/{id}` | Igual que POST |
| DELETE | `/{id}` | Soft-delete físico condicional (falla si ya se usó en cotizaciones) |

### Servicios — `/api/servicios`
| Método | Ruta | Body / Query |
|--------|------|--------------|
| GET | `/?buscar=texto` | |
| GET | `/{id}` | |
| POST | `/` | `{ "codigo": "SV-CAL-001", "nombre": "Calibracion de multimetro", "tipoServicio": "CALIBRACION", "tipoEquipoAplicable": "Multimetro", "descripcion": "...", "precioVenta": 150.00 }` |
| PUT | `/{id}` | |
| DELETE | `/{id}` | |

`tipoServicio`: `CALIBRACION` · `MANTENIMIENTO_PREVENTIVO` · `MANTENIMIENTO_CORRECTIVO` · `REPARACION`

---

## 4. Clientes, contactos e instrumentos

### Clientes — `/api/clientes`
| Método | Ruta | Body / Query |
|--------|------|--------------|
| GET | `/?buscar=texto` | Busca por razón social |
| GET | `/{id}` | Incluye `contactos[]` |
| POST | `/` | `{ "razonSocial": "...", "ruc": "20100047218", "direccion": "...", "rubro": "..." }` — RUC: exactamente 11 dígitos |
| PUT | `/{id}` | |
| DELETE | `/{id}` | |

### Contactos (anidados) — `/api/clientes/{clienteId}/contactos`
| Método | Ruta | Body |
|--------|------|------|
| POST | `/` | `{ "nombre": "...", "email": "a@b.com", "telefono": "..." }` |
| PUT | `/{contactoId}` | |
| DELETE | `/{contactoId}` | |

### Instrumentos — `/api/instrumentos`
| Método | Ruta | Body / Query |
|--------|------|--------------|
| GET | `/?clienteId=1&ubicacion=EN_TRANSITO` | Ambos filtros opcionales |
| GET | `/{id}` | |
| POST | `/` | `{ "tipo": "Multimetro", "marca": "Fluke", "modelo": "87V", "serie": "ABC123", "codigoCliente": "...", "clienteId": 1 }` |
| PUT | `/{id}` | |
| PATCH | `/{id}/despachar` | Ubicación → `EN_TRANSITO` |
| PATCH | `/{id}/recibir` | Ubicación → `UBICADO_EN_GESMIN` |
| DELETE | `/{id}` | |

`ubicacion`: `EN_TRANSITO` · `UBICADO_EN_GESMIN` · `UBICADO_EN_CLIENTE` (nace `UBICADO_EN_CLIENTE`)

---

## 5. Flujo comercial: expediente → cotización → orden

### Expedientes — `/api/expedientes`
| Método | Ruta | Body / Query |
|--------|------|--------------|
| GET | `/` y `/{id}` | |
| POST | `/?clienteId=1` | Sin body. Genera número automático |
| PATCH | `/{id}/estado?estado=CERRADO` | Solo desde `EN_PROCESO`. |

`estado`: `EN_PROCESO` · `RECHAZADO` · `EN_ESPERA` · `CERRADO`. `CERRADO` y `RECHAZADO` son terminales; se permite `EN_ESPERA → EN_PROCESO`. El cierre no exige OT completada y admite OT cancelada según la decisión de Gesmin.

### Cotizaciones — `/api/cotizaciones`
| Método | Ruta | Body |
|--------|------|------|
| GET | `/` y `/{id}` | |
| POST | `/` | Ver ejemplo abajo |
| PATCH | `/{id}/estado?estado=APROBADA` | |

`estado`: `BORRADOR` (nace) · `ENVIADA` · `APROBADA` · `RECHAZADA`. Transiciones: `BORRADOR → ENVIADA/APROBADA/RECHAZADA`, `ENVIADA → APROBADA/RECHAZADA`; `APROBADA` y `RECHAZADA` son terminales. `BORRADOR → APROBADA` directo sigue permitido. Una cotización aprobada con OT asociada no puede degradarse.

```json
POST /api/cotizaciones
{
  "clienteId": 1,
  "contactoId": null,
  "expedienteId": null,
  "fechaEmision": "2026-09-20",
  "fechaVencimiento": null,
  "observaciones": "...",
  "detalles": [
    {
      "servicioId": 1,            // XOR: exactamente uno de servicioId o productoId
      "cantidad": 1,              // obligatorio, mín. 1
      "precioUnitario": 150.00,
      "descuentoPorcentaje": 0,
      "costoAdicional": 0,
      "comentarios": "...",
      "marcaInstrumento": "Fluke",   // opcional, datos del instrumento del cliente
      "modeloInstrumento": "87V",
      "serieInstrumento": "ABC123"
    }
  ]
}
```

**Respuesta:** el backend genera `codigo` (ej. `COI260901`), calcula `subtotal`, `montoTotal` y fija snapshots del producto/servicio.

### Órdenes de trabajo — `/api/ordenes-trabajo`
| Método | Ruta | Body |
|--------|------|------|
| GET | `/` y `/{id}` | |
| POST | `/` | `{ "cotizacionId": 1, "fecha": "2026-09-22", "hora": "14:30:00", "ejecutor": "...", "area": "...", "lugar": "...", "detalles": [{ "actividad": "Calibracion de...", "evaluacionInicial": null, "conclusiones": null, "recomendaciones": null }] }` |
| PATCH | `/{id}/estado?estado=EN_PROCESO` | |

`estado`: `PENDIENTE` (nace) · `EN_PROCESO` · `EN_ESPERA_CLIENTE` · `COMPLETADA` · `CANCELADA`. Por PATCH: `PENDIENTE → EN_PROCESO/CANCELADA`, `EN_PROCESO → COMPLETADA/CANCELADA`, `EN_ESPERA_CLIENTE → CANCELADA`. `COMPLETADA` y `CANCELADA` son terminales. Solo la evaluación puede pasar la OT a `EN_ESPERA_CLIENTE` y devolverla a `PENDIENTE` tras autorización del cliente.

> 🔒 **Regla:** solo se puede crear una orden si la cotización está **`APROBADA`**. Genera `numero` automático (ej. `OT260901`). `COMPLETADA` se marca manualmente por PATCH desde `EN_PROCESO`; no hay automatismo nuevo.

---

## 6. Flujo técnico: evaluación → calibración → revisión → informe

### Evaluaciones de aptitud — `/api/evaluaciones-aptitud`
| Método | Ruta | Body / Query |
|--------|------|--------------|
| GET | `/?ordenDeTrabajoId=1` | ⚠️ query param **obligatorio** (GET sin él da 400) |
| GET | `/{id}` | |
| POST | `/` | `{ "ordenDeTrabajoId": 1, "instrumentoId": 1, "fechaEvaluacion": "2026-09-23", "observaciones": null }` — nace `PENDIENTE` |
| PATCH | `/{id}/resultado?resultado=APTO&observaciones=...` | `resultado`: `PENDIENTE` · `APTO` · `NO_APTO` |
| POST | `/{id}/respuesta-cliente` | `{ "autorizaContinuar": true, "comunicacionCliente": "..." }` — solo aplica cuando es `NO_APTO` |

### Herramientas (patrones de calibración) — `/api/herramientas`
| Método | Ruta | Body / Query |
|--------|------|--------------|
| GET | `/?buscar=texto&soloActivas=true` | Ambos opcionales |
| GET | `/{id}` | |
| POST | `/` | `{ "descripcion": "Patron de peso 1kg clase F1", "marca": "Ohaus", "modelo": "OIML-F1", "serie": "PAT-001", "codigoInterno": "HER-001" }` — `codigoInterno` único, nace `activo: true` |
| PUT | `/{id}` | |
| PATCH | `/{id}/desactivar` | **Soft-delete** (los patrones nunca se borran: trazabilidad ISO 17025) |

### Calibraciones — `/api/calibraciones`
| Método | Ruta | Body |
|--------|------|------|
| GET | `/` y `/{id}` | |
| POST | `/` | `{ "evaluacionAptitudId": 1, "tecnico": "Kevin", "procedimiento": "PR-CAL-003", "fecha": "2026-09-24", "patronesIds": [1] }` — nace `PROGRAMADA`, requiere ≥1 patrón |
| PATCH | `/{id}/estado?estado=COMPLETADA` | `PROGRAMADA` · `EN_PROCESO` · `COMPLETADA` · `CANCELADA` |
| PUT | `/{id}/mediciones` | Array de puntos (ver abajo). Reemplaza los anteriores y pone estado `EN_PROCESO` |

```json
PUT /api/calibraciones/1/mediciones
[
  {
    "puntoDescripcion": "10V DC",   // opcional
    "valorPatron": 10.00,           // obligatorio
    "valorMedido": 10.02,           // obligatorio
    "unidad": "V",                  // opcional
    "dentroDeTolerancia": true      // lo decide el técnico
  }
]
```

> 💡 El backend calcula `error = valorMedido − valorPatron` automáticamente. No lo envíes.
>
> 🔒 **Regla:** solo se puede crear una calibración si la evaluación tiene resultado **`APTO`**. Transiciones: `PROGRAMADA → EN_PROCESO`; `EN_PROCESO → COMPLETADA/CANCELADA`; `COMPLETADA → EN_PROCESO` conserva el ciclo de corrección. `CANCELADA` es terminal. Registrar mediciones también valida esta tabla antes de poner `EN_PROCESO`.

### Revisiones técnicas — `/api/revisiones-tecnicas`
| Método | Ruta | Body / Query |
|--------|------|--------------|
| GET | `/?calibracionId=1` | ⚠️ query param **obligatorio** |
| GET | `/{id}` | |
| POST | `/` | `{ "calibracionId": 1, "fechaRevision": "2026-09-23", "revisor": "Kevin", "observaciones": null }` — nace `PENDIENTE` |
| PATCH | `/{id}/resultado?resultado=CONFORME&observaciones=...` | `PENDIENTE` · `CONFORME` · `NO_CONFORME` |

> 🔒 **Reglas:**
> - Solo se puede crear una revisión si la calibración está **`COMPLETADA`**.
> - **`NO_CONFORME`** → la calibración vuelve a `EN_PROCESO` (ciclo de corrección: se registran mediciones de nuevo → `COMPLETADA` → recertificación de la misma revisión o creación de otra). El informe anterior queda ANULADO.
> - **`CONFORME`** → se genera **automáticamente** un `InformeTecnico` con correlativo.
> - Repetir el mismo `resultado` y las mismas `observaciones` es idempotente. Cambiar observaciones de un `CONFORME` anula su informe vigente y genera uno nuevo. Para corregir un resultado ya certificado: registrar `NO_CONFORME` con observación, completar de nuevo la calibración y registrar `CONFORME`; el informe anterior queda `ANULADO` y se crea otro con correlativo distinto.

### Informes técnicos (certificados) — `/api/informes-tecnicos`
| Método | Ruta | Body |
|--------|------|------|
| GET | `/` y `/{id}` | |
| POST | `/{id}/pdf-firmado` | `multipart/form-data`, campo `archivo` con PDF real (máx. 10 MB). Cambia a `PDF_CARGADO`. |
| GET | `/{id}/pdf` | Descarga vista previa generada, sin firma. |
| GET | `/{id}/pdf-firmado` | Descarga el archivo original solo desde `APROBADO`. |
| PATCH | `/{id}/estado?estado=APROBADO` | Solo `PDF_CARGADO → APROBADO → ENVIADO`; `ANULADO` no puede reactivarse. |

> 💡 Al marcar `ENVIADO` el backend llena `fechaEnvio` con la fecha actual.
> El antiguo `PATCH /{id}/pdf-cargado` fue retirado: el booleano se actualiza únicamente tras guardar un PDF válido. El backend no verifica criptográficamente la firma.

---

## 7. Diagrama del flujo completo

```
Cliente ──> Instrumento
   │
   └──> Cotizacion (BORRADOR → APROBADA)
              │ (regla: debe estar APROBADA)
              ▼
        Orden de Trabajo (PENDIENTE → ...)
              │
              ▼
        EvaluacionAptitud (PENDIENTE → APTO / NO_APTO + respuesta cliente)
              │ (regla: debe ser APTO)
              ▼
        Calibracion (PROGRAMADA) ──> PUT mediciones (→ EN_PROCESO) ──> COMPLETADA
              │ (regla: debe estar COMPLETADA)                             ▲
              ▼                                                            │
        RevisionTecnica ── NO_CONFORME ────────────────────────────────────┘
              │ CONFORME
              ▼
        InformeTecnico (GENERADO → PDF_CARGADO → APROBADO → ENVIADO; cualquiera de esos estados → ANULADO al reabrir su revisión)
              
        Expediente ──> PATCH /{id}/estado?estado=CERRADO  (cierre final)
```

---

## 8. Ejemplo: flujo completo con curl

```bash
B=http://localhost:8080/api

# 1. Cliente + instrumento
curl -s -X POST $B/clientes -H "Content-Type: application/json" \
  -d '{"razonSocial":"Industrias del Norte SAC","ruc":"20100047218"}'
curl -s -X POST $B/instrumentos -H "Content-Type: application/json" \
  -d '{"tipo":"Multimetro","marca":"Fluke","modelo":"87V","serie":"ABC123","clienteId":1}'

# 2. Servicio + cotización + aprobar
curl -s -X POST $B/servicios -H "Content-Type: application/json" \
  -d '{"codigo":"SV-CAL-001","nombre":"Calibracion de multimetro","tipoServicio":"CALIBRACION","tipoEquipoAplicable":"Multimetro","precioVenta":150.00}'
curl -s -X POST $B/cotizaciones -H "Content-Type: application/json" \
  -d '{"clienteId":1,"fechaEmision":"2026-09-20","detalles":[{"servicioId":1,"cantidad":1,"precioUnitario":150.00}]}'
curl -s -X PATCH "$B/cotizaciones/1/estado?estado=APROBADA"

# 3. Orden de trabajo
curl -s -X POST $B/ordenes-trabajo -H "Content-Type: application/json" \
  -d '{"cotizacionId":1,"fecha":"2026-09-22","ejecutor":"Kevin","detalles":[{"actividad":"Calibracion de multimetro"}]}'

# 4. Evaluación + marcar APTO
curl -s -X POST $B/evaluaciones-aptitud -H "Content-Type: application/json" \
  -d '{"ordenDeTrabajoId":1,"instrumentoId":1,"fechaEvaluacion":"2026-09-23"}'
curl -s -X PATCH "$B/evaluaciones-aptitud/1/resultado?resultado=APTO"

# 5. Patrón + calibración + mediciones + completar
curl -s -X POST $B/herramientas -H "Content-Type: application/json" \
  -d '{"descripcion":"Patron de peso 1kg clase F1","marca":"Ohaus","modelo":"OIML-F1","serie":"PAT-001","codigoInterno":"HER-001"}'
curl -s -X POST $B/calibraciones -H "Content-Type: application/json" \
  -d '{"evaluacionAptitudId":1,"tecnico":"Kevin","fecha":"2026-09-24","patronesIds":[1]}'
curl -s -X PUT $B/calibraciones/1/mediciones -H "Content-Type: application/json" \
  -d '[{"puntoDescripcion":"10V DC","valorPatron":10.00,"valorMedido":10.02,"unidad":"V","dentroDeTolerancia":true}]'
curl -s -X PATCH "$B/calibraciones/1/estado?estado=COMPLETADA"

# 6. Revisión CONFORME → informe automático
curl -s -X POST $B/revisiones-tecnicas -H "Content-Type: application/json" \
  -d '{"calibracionId":1,"fechaRevision":"2026-09-23","revisor":"Kevin"}'
curl -s -X PATCH "$B/revisiones-tecnicas/1/resultado?resultado=CONFORME"

# 7. Ciclo del informe
curl -s -F "archivo=@informe-firmado.pdf;type=application/pdf" $B/informes-tecnicos/1/pdf-firmado
curl -s -X PATCH "$B/informes-tecnicos/1/estado?estado=APROBADO"
curl -s -X PATCH "$B/informes-tecnicos/1/estado?estado=ENVIADO"
```

---

## 9. Pendientes / placeholders (para no tropezar)

1. **Formato de certificado de calibración**: por ahora el PDF generado usa el Informe Técnico compartido como referencia visual provisional. Falta el ejemplo del certificado específico.
2. **Sin usuarios ni login**: `tecnico`, `revisor`, `ejecutor` son texto libre. Cuando exista el módulo Usuario/Permisos, estos campos pasarán a referencias y probablemente habrá auth (JWT/session).
3. **Errores HTTP**: errores de negocio o "no encontrado" llegan como 400 con `{"mensaje": "..."}`; un agotamiento de reintentos de correlativo llega como 409. Un fallo inesperado del servidor llega como 500 con `{"mensaje":"Error interno del servidor."}` y el detalle queda solo en el log. Los 400 de parámetros obligatorios y los 404 de rutas inexistentes usan el body de Spring sin campo `trace`. Centraliza el manejo en tu fetch/axios interceptor.
4. ~~**Datos volátiles**: H2 en memoria.~~ **Resuelto (2026-09-23):** la BD ahora está en archivo (`jdbc:h2:file:./data/gesmin`) y persiste entre reinicios. Verificado con reinicios reales del server en [historial E1](HISTORIAL_VALIDACION.md#e1).
5. **CORS fijo a puerto 5175**: coordina con Kevin si tu frontend corre en otro puerto/origen.

---

## 10. ⚠️ AVISO PARA EL FRONTEND — hallazgos de la validación (2026-09-23)

> Se ejecutó una validación adversarial del flujo completo (125 requests, 13 casos). Detalle completo en [historial E1](HISTORIAL_VALIDACION.md#e1); estado vigente en [VALIDACION.md](VALIDACION.md). **Lo que sigue es lo que te afecta directamente al construir la UI.**

### 10.1 Reglas que el backend SÍ defiende (podés confiar)

| Regla | Qué devuelve |
|---|---|
| OT solo con cotización `APROBADA` | 400 con `mensaje` |
| Calibración solo con evaluación `APTO` | 400 con `mensaje` |
| Revisión solo con calibración `COMPLETADA` (PROGRAMADA y EN_PROCESO bloqueados) | 400 con `mensaje` |
| `NO_APTO` en evaluación → OT pasa a `EN_ESPERA_CLIENTE` automáticamente | 200, verificar `estado` de la OT |
| Calibración sobre evaluación `NO_APTO` | 400 con `mensaje` |
| Informe `APROBADO → ENVIADO` registra `fechaEnvio` | 200 |
| Correlativos `COI/OT/IT/E` | Contador anual bloqueado en BD por prefijo; la primera creación concurrente puede reintentarse. Si se agotan 3 intentos por contención, 409 con `mensaje`. |
| `PATCH` repetido con mismo resultado y observaciones | 200, sin generar otro informe. Si cambian las observaciones de `CONFORME`, se anula el IT anterior y se reemite. |
| Nueva revisión tras un informe vigente | 400 con `mensaje`; vuelve a permitirse tras anular el informe mediante reapertura de la revisión propietaria. |
| Idempotencia de PATCH con el mismo estado | 200, misma representación |

### 10.2 Reglas todavía pendientes

El backend compara resultado y observaciones, y anula el certificado vigente antes de reemitir. La UI debe mostrar el historial `ANULADO` y el nuevo informe según el flujo de la sección 12.

| # | Hueco | Riesgo en la UI | Workaround sugerido mientras tanto |
|---|---|---|---|
| C | **SUPERADO en E12:** los PATCH de cotización, OT, calibración y expediente validan las tablas anteriores; el informe conserva su flujo firmado → aprobación → envío. | Los saltos y regresiones no permitidos responden 400. | Mostrar solo destinos permitidos; repetir el mismo estado devuelve 200 sin mutación. |
| D | **PARCIAL:** `CERRADO` ya es terminal. El cierre no exige OT completada ni bloquea por OT cancelada, según Gesmin; tampoco valida automáticamente evaluaciones o informes. | La UI debe presentar el cierre conforme al proceso operativo acordado. | No mostrar opción de reapertura de `CERRADO`; el backend la rechaza. |
| E | **Revisiones simultáneas sobre la misma calibración se permiten** (y las `PENDIENTE` no reclamadas quedan huérfanas para siempre). | Dos usuarios podrían crear revisiones en paralelo y una queda sin usar. | Deshabilitá "Nueva revisión" si la calibración ya tiene una revisión con `resultado = PENDIENTE` (`GET /revisiones-tecnicas?calibracionId=X`). |

### 10.3 Errores: recordatorios verificados

- "No encontrado" sigue devolviendo **400 (no 404)** con `{"mensaje": "..."}` — verificado en clientes, calibraciones e informes.
- El contador de correlativos se bloquea por prefijo en la BD. Si se agotan 3 reintentos por contención, se devuelve **409** con `mensaje` legible.
- No hay `POST /informes-tecnicos`: los informes **solo** nacen automáticamente de una revisión CONFORME (405 si lo intentás).

> C está superado, D es parcial y E sigue pendiente; el estado vigente está en [VALIDACION.md](VALIDACION.md), y la auditoría y su respuesta originales en [historial E4–E5](HISTORIAL_VALIDACION.md#e4).

---

## 11. PDF generados y PDF firmado (2026-09-24)

Los PDF de cotización, OT e informe técnico sin firma se generan **una sola vez al crear el registro** y se guardan como snapshots inmutables en `./data/pdf-generados/`; los cambios posteriores no modifican esos archivos. Cada GET sirve el archivo persistido si existe. Si falta (dato antiguo, fallo de escritura o borrado manual), el GET regenera el PDF al vuelo **con los datos actuales de la BD** y lo devuelve, pero **no vuelve a escribirlo en disco**: el snapshot original no se restaura. Antes de servir un archivo guardado, el backend comprueba que el registro aún exista; un archivo residual de una cotización u OT eliminada no es descargable. El registro en BD permanece creado aunque falle el guardado del PDF. Si se reinicia solo la BD y se reutilizan IDs, hay que coordinar también `./data/pdf-generados/` para que un registro nuevo no reciba el snapshot viejo de otro. No hay que subir archivo para cotización u orden. Cada GET responde `application/pdf` con `Content-Disposition: attachment`; en React/axios usa `responseType: "blob"`.

| Documento | Endpoint | Cuándo existe |
|---|---|---|
| Cotización preliminar | `GET /api/cotizaciones/{id}/pdf` | Al crear la cotización. |
| Orden de trabajo | `GET /api/ordenes-trabajo/{id}/pdf` | Al crear la OT desde una cotización aprobada. |
| Informe técnico sin firma | `GET /api/informes-tecnicos/{id}/pdf` | Tras revisión `CONFORME`, que crea el correlativo IT. |
| Informe firmado | `GET /api/informes-tecnicos/{id}/pdf-firmado` | Tras cargar el archivo y pasar a `APROBADO`. |

Para cargar el firmado: `POST /api/informes-tecnicos/{id}/pdf-firmado` con `multipart/form-data`, nombre de campo **`archivo`**, archivo PDF de 1 byte a 10 MB. Se valida que sea un PDF legible y no se permite reemplazarlo. La respuesta es `InformeTecnicoDTO` con `pdfCargado=true`, `fechaCargaPdf` y estado `PDF_CARGADO`. El archivo se guarda en `./data/pdf-firmados/{id}.pdf`; hay que respaldar esa carpeta junto con H2 y `./data/pdf-generados/`. El booleano histórico por sí solo ya no sirve para aprobar: el archivo debe existir.

Después se usa `PATCH /api/informes-tecnicos/{id}/estado?estado=APROBADO` y, cuando se confirme la entrega al cliente, `PATCH ...?estado=ENVIADO` registra la fecha actual. La API **no envía correos** todavía; ese último PATCH es un registro manual de la fecha de envío. Los intentos de saltar estados devuelven 400 con `mensaje`.

**Límites de los modelos actuales:** la cotización se rotula `BORRADOR` porque aún no guarda moneda, IGV, forma de pago, asesor ni cuentas bancarias. Su PDF muestra el `montoTotal` actualmente guardado, sin inventar impuestos ni divisa. La OT no almacena cantidad o producto por cada actividad ni firmas reales. El Informe Técnico se genera con datos de calibración, evaluación y revisión, pero todavía no incluye todos los campos narrativos del formato legado; el certificado de calibración propio de Gesmin sigue pendiente de recibir. Estos PDF son funcionales para descarga y revisión, pero aún no son reproducciones finales de los formatos comerciales.

```bash
curl -o cotizacion.pdf "$B/cotizaciones/1/pdf"
curl -o orden.pdf "$B/ordenes-trabajo/1/pdf"
curl -o informe-sin-firma.pdf "$B/informes-tecnicos/1/pdf"
curl -F "archivo=@informe-firmado.pdf;type=application/pdf" "$B/informes-tecnicos/1/pdf-firmado"
curl -X PATCH "$B/informes-tecnicos/1/estado?estado=APROBADO"
curl -o informe-firmado.pdf "$B/informes-tecnicos/1/pdf-firmado"
```

---

## 12. Corrección posterior a la auditoría FreeBuff (2026-09-24)

La afirmación previa de H2/H3 en esta sección quedó refutada por la auditoría. El flujo actual para una revisión certificada es:

1. `PATCH /api/revisiones-tecnicas/{id}/resultado?resultado=NO_CONFORME&observaciones=Falla-confirmada`: anula el informe vigente de **esa revisión**, registra `fechaAnulacion` y `motivoAnulacion`, y pasa la calibración a `EN_PROCESO`. No se permite usar otra revisión pendiente para anular el certificado.
2. Tras corregir el trabajo, `PATCH /api/calibraciones/{id}/estado?estado=COMPLETADA`.
3. `PATCH /api/revisiones-tecnicas/{id}/resultado?resultado=CONFORME&observaciones=Corregida`: genera otro IT. También se puede crear una revisión nueva una vez anulado el informe previo.
4. El informe `ANULADO` permanece en `GET /api/informes-tecnicos` para trazabilidad, pero no se puede aprobar ni descargar su PDF, firmado o sin firma. El DTO incluye `fechaAnulacion` y `motivoAnulacion`.

### Registro y comunicación de una anulación (H2)

**Registro consultable actual:** la anulación queda en el propio `InformeTecnico`, sin evento separado. `GET /api/informes-tecnicos/{id}` devuelve `estado="ANULADO"`, `fechaAnulacion`, `motivoAnulacion` y el `fechaEnvio` histórico si existía. `GET /api/informes-tecnicos` incluye esos informes; el cliente debe filtrar `estado="ANULADO"` localmente, porque hoy no hay filtro de servidor. El informe anulado no puede aprobarse ni descargarse. La recertificación recibe otro número y el anterior permanece visible.

**Paso operativo vigente mientras Gesmin decide el procedimiento:** después de reabrir una revisión, el responsable operativo (por definir en Gesmin) consulta `GET /api/informes-tecnicos/{id}` para leer el motivo y la fecha de anulación. Si el certificado anterior ya fue comunicado al cliente, el aviso se gestiona manualmente fuera de esta API según el procedimiento que defina Gesmin. El backend **no envía avisos ni guarda confirmación, destinatario o fecha de comunicación de la anulación**. Por eso `ANULADO` acredita la invalidación interna, no que el cliente haya sido informado; la UI no debe presentarlo como “cliente notificado”.

**Alcance confirmado por Gesmin:** el aviso de anulación permanece manual fuera del sistema. No se crea endpoint de constancia, evento ni consumidor automático. El responsable, destinatario y momento se rigen por el procedimiento operativo externo; `ANULADO` no acredita aviso al cliente.

```http
GET /api/informes-tecnicos/1
HTTP/1.1 200
{"id":1,"numero":"IT260901","revisionTecnicaId":1,"instrumentoDescripcion":"Fluke 87V - Serie AUD-d1f6b","fechaEmision":"2026-09-24","estado":"ANULADO","pdfCargado":false,"fechaCargaPdf":null,"fechaEnvio":null,"fechaAnulacion":"2026-09-24","motivoAnulacion":"Reapertura por NO_CONFORME: Falla-confirmada"}
```

Un PATCH con el mismo resultado y observaciones conserva el estado sin emitir otro IT. Un `CONFORME` con observaciones distintas anula el informe anterior y emite uno nuevo. `ANULADO` no vuelve a estar vigente.

Los códigos `E`, `COI`, `OT` e `IT` se asignan mediante `correlativos_contadores`: una fila por tipo/año bloqueada durante la transacción que crea el documento. Al migrar una base existente, el primer uso del prefijo se inicializa desde los documentos históricos. El guard de unicidad del documento y el retry de 3 intentos siguen como respaldo ante carreras de inicialización. Si se agota ese retry, la respuesta es **409** `{"mensaje":"No se pudo asignar un correlativo único tras 3 intentos."}`.
