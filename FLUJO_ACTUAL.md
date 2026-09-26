# Flujo de trabajo actual de Gesmin

Estado del backend al 25-set-2026. Este diagrama muestra el recorrido operativo y las decisiones principales; no sustituye la especificación de negocio pendiente de Gesmin.

```mermaid
flowchart TD
    solicitud(["Solicitud del cliente<br/>fuera del backend"])
    registro["Registrar o consultar cliente, contacto e instrumento"]
    expediente["Abrir expediente<br/>EN_PROCESO"]
    cotizacion["Crear cotización<br/>BORRADOR + PDF"]
    cotEnviada["Registrar cotización como ENVIADA"]
    apruebaCot{"¿Cotización aprobada?"}
    sinOt["Cotización ENVIADA o RECHAZADA<br/>no se crea OT"]
    aprobada["Registrar APROBADA"]
    orden["Crear orden de trabajo<br/>PENDIENTE + PDF"]
    traslado["Despachar y recibir instrumento<br/>si aplica"]
    evaluacion["Crear evaluación de aptitud<br/>PENDIENTE"]
    apto{"¿Instrumento APTO?"}
    noApto["Registrar NO_APTO<br/>OT EN_ESPERA_CLIENTE"]
    autoriza{"¿Cliente autoriza continuar?"}
    nuevaEval["El backend crea nueva evaluación PENDIENTE<br/>OT vuelve a PENDIENTE"]
    otCancelada["OT CANCELADA"]
    otProceso["Registrar APTO<br/>OT EN_PROCESO"]
    calibracion["Crear calibración PROGRAMADA<br/>con patrón"]
    mediciones["Registrar o corregir mediciones<br/>calibración EN_PROCESO"]
    calCompleta["Marcar calibración COMPLETADA"]
    otCompleta["Marcar OT COMPLETADA<br/>acción manual"]
    revision["Crear revisión técnica PENDIENTE"]
    conforme{"¿Resultados CONFORMES?"}
    correccion["Registrar NO_CONFORME<br/>calibración EN_PROCESO<br/>IT vigente ANULADO si existía"]
    informe["Registrar CONFORME<br/>IT GENERADO con correlativo<br/>PDF sin firma"]
    firmado["Cargar PDF firmado<br/>PDF_CARGADO"]
    aprobacion["Revisión y aprobación técnica<br/>APROBADO"]
    disponible["Informe disponible para Ventas<br/>descargar PDF firmado"]
    gmail["Ventas envía el PDF por Gmail<br/>fuera del sistema"]
    marcarEnvio["Marcar ENVIADO<br/>fechaEnvio = fecha actual del servidor"]
    cierre["Cerrar expediente manualmente<br/>CERRADO es terminal"]
    reporte["Reporte de cumplimiento y cierre<br/>pendiente: sin endpoint automático"]

    solicitud --> registro --> expediente --> cotizacion --> cotEnviada --> apruebaCot
    apruebaCot -->|No| sinOt
    apruebaCot -->|Sí| aprobada --> orden --> traslado --> evaluacion --> apto
    apto -->|No| noApto --> autoriza
    autoriza -->|Sí| nuevaEval --> apto
    autoriza -->|No| otCancelada
    apto -->|Sí| otProceso --> calibracion --> mediciones --> calCompleta --> otCompleta --> revision --> conforme
    conforme -->|No| correccion --> mediciones
    conforme -->|Sí| informe --> firmado --> aprobacion --> disponible --> gmail --> marcarEnvio --> cierre
    otCancelada -->|Si corresponde| cierre
    cierre -.-> reporte
```

## Cómo leer los pasos

- La solicitud y el envío por Gmail ocurren fuera del backend. Registrar la cotización como `ENVIADA` tampoco envía un correo automáticamente. Se puede aprobar una cotización directamente desde `BORRADOR` sin pasar por `ENVIADA`.
- El expediente se abre al inicio en este recorrido, aunque `expedienteId` es opcional al crear una cotización. Los PDF de cotización, OT e informe sin firma se generan al crear sus registros y quedan como snapshots.
- `NO_APTO` pone la OT en `EN_ESPERA_CLIENTE`. Si el cliente autoriza continuar, el backend crea otra evaluación; si no, cancela la OT. `NO_CONFORME` reabre la calibración para corregir mediciones y anula el informe vigente, si lo había; una conformidad posterior genera otro correlativo.
- La OT pasa a `COMPLETADA` por una acción manual. El cierre del expediente también es manual y hoy no comprueba estados de OT, evaluaciones ni informes. `CERRADO` no se reabre.
- Ventas descarga el PDF firmado solo cuando el informe está `APROBADO` o `ENVIADO`. Descargarlo no marca el envío. Según la convención provisional de Kevin, Ventas lo remite por Gmail y después marca `ENVIADO`; `fechaEnvio` guarda la fecha de ese PATCH, no una confirmación de entrega. Gesmin aún debe confirmar este procedimiento.
- La carga exige un PDF legible, pero el backend no verifica criptográficamente la firma. El reporte de cumplimiento y cierre aparece con línea discontinua porque hoy no existe un endpoint que lo genere.

Contrato de endpoints y estados: [API.md](API.md). Esquema y persistencia de PDFs: [DB.md](DB.md). Estado de hallazgos: [VALIDACION.md](VALIDACION.md).
