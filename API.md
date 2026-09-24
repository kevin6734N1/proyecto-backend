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

> ✅ **Los datos SÍ persisten** (desde la validación del 2026-09-23 la BD está en archivo): podés reiniciar el backend sin perder nada. Si borrás la carpeta `data/` sí se pierde todo. Ver `VALIDACION.md` para el detalle de la sesión de pruebas.

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
| PATCH | `/{id}/estado?estado=ACEPTADO` | |

`estado`: `EN_PROCESO` · `RECHAZADO` · `EN_ESPERA` · `ACEPTADO` · `CERRADO`

### Cotizaciones — `/api/cotizaciones`
| Método | Ruta | Body |
|--------|------|------|
| GET | `/` y `/{id}` | |
| POST | `/` | Ver ejemplo abajo |
| PATCH | `/{id}/estado?estado=APROBADA` | |

`estado`: `BORRADOR` (nace) · `ENVIADA` · `APROBADA` · `RECHAZADA`

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

`estado`: `PENDIENTE` (nace) · `EN_PROCESO` · `EN_ESPERA_CLIENTE` · `COMPLETADA` · `CANCELADA`

> 🔒 **Regla:** solo se puede crear una orden si la cotización está **`APROBADA`**. Genera `numero` automático (ej. `OT260901`).

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
> 🔒 **Regla:** solo se puede crear una calibración si la evaluación tiene resultado **`APTO`**.

### Revisiones técnicas — `/api/revisiones-tecnicas`
| Método | Ruta | Body / Query |
|--------|------|--------------|
| GET | `/?calibracionId=1` | ⚠️ query param **obligatorio** |
| GET | `/{id}` | |
| POST | `/` | `{ "calibracionId": 1, "fechaRevision": "2026-09-23", "revisor": "Kevin", "observaciones": null }` — nace `PENDIENTE` |
| PATCH | `/{id}/resultado?resultado=CONFORME&observaciones=...` | `PENDIENTE` · `CONFORME` · `NO_CONFORME` |

> 🔒 **Reglas:**
> - Solo se puede crear una revisión si la calibración está **`COMPLETADA`**.
> - **`NO_CONFORME`** → la calibración vuelve a `EN_PROCESO` (loop de corrección: se registran mediciones de nuevo → `COMPLETADA` → nueva revisión). La misma revisión NO se reusa: se crea otra.
> - **`CONFORME`** → se genera **automáticamente** un `InformeTecnico` con correlativo.
> - Repetir `CONFORME` sobre la misma revisión no genera otro informe. Una calibración que ya tiene un informe no admite otra revisión ni otro resultado `CONFORME`; la reemisión aún no está definida.

### Informes técnicos (certificados) — `/api/informes-tecnicos`
| Método | Ruta | Body |
|--------|------|------|
| GET | `/` y `/{id}` | |
| POST | `/{id}/pdf-firmado` | `multipart/form-data`, campo `archivo` con PDF real (máx. 10 MB). Cambia a `PDF_CARGADO`. |
| GET | `/{id}/pdf` | Descarga vista previa generada, sin firma. |
| GET | `/{id}/pdf-firmado` | Descarga el archivo original solo desde `APROBADO`. |
| PATCH | `/{id}/estado?estado=APROBADO` | Solo `PDF_CARGADO → APROBADO → ENVIADO`. |

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
        InformeTecnico (GENERADO → PDF_CARGADO → APROBADO → ENVIADO)
              
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
3. **Errores 400 vs 404**: todo error de negocio o "no encontrado" llega como 400 con `{"mensaje": "..."}`. Centraliza el manejo en tu fetch/axios interceptor.
4. ~~**Datos volátiles**: H2 en memoria.~~ **Resuelto (2026-09-23):** la BD ahora está en archivo (`jdbc:h2:file:./data/gesmin`) y persiste entre reinicios. Verificado con reinicios reales del server en `VALIDACION.md`.
5. **CORS fijo a puerto 5175**: coordina con Kevin si tu frontend corre en otro puerto/origen.

---

## 10. ⚠️ AVISO PARA EL FRONTEND — hallazgos de la validación (2026-09-23)

> Se ejecutó una validación adversarial del flujo completo (125 requests, 13 casos). Detalle completo en `VALIDACION.md`. **Lo que sigue es lo que te afecta directamente al construir la UI.**

### 10.1 Reglas que el backend SÍ defiende (podés confiar)

| Regla | Qué devuelve |
|---|---|
| OT solo con cotización `APROBADA` | 400 con `mensaje` |
| Calibración solo con evaluación `APTO` | 400 con `mensaje` |
| Revisión solo con calibración `COMPLETADA` (PROGRAMADA y EN_PROCESO bloqueados) | 400 con `mensaje` |
| `NO_APTO` en evaluación → OT pasa a `EN_ESPERA_CLIENTE` automáticamente | 200, verificar `estado` de la OT |
| Calibración sobre evaluación `NO_APTO` | 400 con `mensaje` |
| Informe `APROBADO → ENVIADO` registra `fechaEnvio` | 200 |
| Correlativos `COI/OT/IT/E` | Reintento transaccional ante colisión; si se agotan 3 intentos, 400 con `mensaje`. |
| `PATCH` repetido `CONFORME` en la misma revisión | 200, sin generar otro informe ni cambiar observaciones. |
| Nueva revisión tras un informe de la calibración | 400 con `mensaje`, incluso si el informe sigue `GENERADO`. |
| Idempotencia de PATCH con el mismo estado | 200, misma representación |

### 10.2 Reglas todavía pendientes

H1 y H2 quedaron resueltos en el backend. La UI puede seguir deshabilitando botones ya usados para dar claridad, pero no necesita cruzar informes y revisiones para impedir la duplicación.

| # | Hueco | Riesgo en la UI | Workaround sugerido mientras tanto |
|---|---|---|---|
| C | Los PATCH de estado de cotización, OT, calibración y expediente aún permiten saltos o regresiones. **El informe ya exige PDF real → aprobación → envío.** | Un combo libre puede generar estados inconsistentes en los otros módulos. | Ofrecé solo las transiciones que correspondan en cada pantalla. Para el informe usa carga multipart, luego `APROBADO` y después `ENVIADO`. |
| D | **El cierre de expediente no valida nada**: se puede cerrar con OTs sin completar, evaluaciones `NO_APTO` sin respuesta del cliente, o informes nunca enviados. También se reabre un CERRADO con un PATCH. | El expediente puede quedar "CERRADO" con trabajo pendiente adentro. | Antes de ofrecer el botón "Cerrar expediente", validá del lado del frontend que sus OTs estén `COMPLETADA`/`CANCELADA` y que no queden evaluaciones `PENDIENTE`/`NO_APTO` sin respuesta. Confirmar con Gesmin si hace falta alguna regla real aquí. |
| E | **Revisiones simultáneas sobre la misma calibración se permiten** (y las `PENDIENTE` no reclamadas quedan huérfanas para siempre). | Dos usuarios podrían crear revisiones en paralelo y una queda sin usar. | Deshabilitá "Nueva revisión" si la calibración ya tiene una revisión con `resultado = PENDIENTE` (`GET /revisiones-tecnicas?calibracionId=X`). |

### 10.3 Errores: recordatorios verificados

- "No encontrado" sigue devolviendo **400 (no 404)** con `{"mensaje": "..."}` — verificado en clientes, calibraciones e informes.
- Los correlativos se reintentan hasta 3 veces en transacciones separadas. Si se agotan los intentos, se devuelve 400 con `mensaje` legible.
- No hay `POST /informes-tecnicos`: los informes **solo** nacen automáticamente de una revisión CONFORME (405 si lo intentás).

> C, D y E siguen pendientes. H1, H2 y H3 ya tienen guardas y pruebas; C sigue pendiente para cotización, OT, calibración y expediente.

---

## 11. PDF generados y PDF firmado (2026-09-24)

Los tres reportes se generan al pedirlos. No hay que subir un archivo para cotización u orden. Cada GET responde `application/pdf` con `Content-Disposition: attachment`; en React/axios usa `responseType: "blob"`.

| Documento | Endpoint | Cuándo existe |
|---|---|---|
| Cotización preliminar | `GET /api/cotizaciones/{id}/pdf` | Al crear la cotización. |
| Orden de trabajo | `GET /api/ordenes-trabajo/{id}/pdf` | Al crear la OT desde una cotización aprobada. |
| Informe técnico sin firma | `GET /api/informes-tecnicos/{id}/pdf` | Tras revisión `CONFORME`, que crea el correlativo IT. |
| Informe firmado | `GET /api/informes-tecnicos/{id}/pdf-firmado` | Tras cargar el archivo y pasar a `APROBADO`. |

Para cargar el firmado: `POST /api/informes-tecnicos/{id}/pdf-firmado` con `multipart/form-data`, nombre de campo **`archivo`**, archivo PDF de 1 byte a 10 MB. Se valida que sea un PDF legible y no se permite reemplazarlo. La respuesta es `InformeTecnicoDTO` con `pdfCargado=true`, `fechaCargaPdf` y estado `PDF_CARGADO`. El archivo se guarda en `./data/pdf-firmados/{id}.pdf`; hay que respaldar esa carpeta junto con H2. El booleano histórico por sí solo ya no sirve para aprobar: el archivo debe existir.

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

## 12. H1, H2 y H3 corregidos (2026-09-24)

- Repetir `PATCH /api/revisiones-tecnicas/{id}/resultado?resultado=CONFORME` devuelve la revisión existente sin generar otro IT ni cambiar sus observaciones. Cambiar un resultado final por otro devuelve 400.
- Crear una revisión o registrar `CONFORME` para otra revisión de una calibración que ya tiene cualquier informe devuelve 400. Esto aplica desde `GENERADO`; no hay flujo de reemisión definido. El ciclo `NO_CONFORME → EN_PROCESO → nueva revisión` continúa disponible.
- Si coinciden dos operaciones que asignan códigos `E`, `COI`, `OT` o `IT`, se reintenta toda la creación en una transacción nueva hasta 3 veces. La revisión y su informe se confirman o revierten juntos; una colisión no debe dejar la revisión `CONFORME` sin informe.