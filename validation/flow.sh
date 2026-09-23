#!/usr/bin/env bash
# flow.sh — Cadena completa en UNA sola corrida (Regla 1). Sin seeds: todo por API.
set -u
cd "$(dirname "$0")/.."
source validation/harness.sh

SESSION=$(date +%H%M%S)
echo "### SESION $SESSION — flujo completo ###" | tee -a "$RLOG"

case_hdr "1: Cadena completa Cliente→Cotización→OT→Evaluación→Calibración→Revisión→Informe→ENVIADO→Cierre"

step "1.1 Crear cliente"
req POST /clientes "{\"razonSocial\":\"Acme Val $SESSION\",\"ruc\":\"20${SESSION}001\",\"direccion\":\"Av. Test 123\",\"rubro\":\"Metalurgica\"}"
CLIENTE_ID=$(jget "$LAST_BODY_FILE" id)

step "1.2 Crear instrumento del cliente"
req POST /instrumentos "{\"tipo\":\"Multimetro\",\"marca\":\"Fluke\",\"modelo\":\"87V\",\"serie\":\"SN-$SESSION\",\"clienteId\":$CLIENTE_ID}"
INSTRUMENTO_ID=$(jget "$LAST_BODY_FILE" id)

step "1.3 Crear servicio (catálogo)"
req POST /servicios "{\"codigo\":\"SV-$SESSION\",\"nombre\":\"Calibracion multimetro $SESSION\",\"tipoServicio\":\"CALIBRACION\",\"tipoEquipoAplicable\":\"Multimetro\",\"precioVenta\":150.00}"
SERVICIO_ID=$(jget "$LAST_BODY_FILE" id)

step "1.4 Crear expediente"
req POST "/expedientes?clienteId=$CLIENTE_ID"
EXPEDIENTE_ID=$(jget "$LAST_BODY_FILE" id)
EXPEDIENTE_NUM=$(jget "$LAST_BODY_FILE" numero)
echo "    expediente: id=$EXPEDIENTE_ID numero=$EXPEDIENTE_NUM" | tee -a "$RLOG"

step "1.5 Crear cotización (asociada al expediente)"
req POST /cotizaciones "{\"clienteId\":$CLIENTE_ID,\"expedienteId\":$EXPEDIENTE_ID,\"fechaEmision\":\"2026-09-23\",\"detalles\":[{\"servicioId\":$SERVICIO_ID,\"cantidad\":1,\"descuentoPorcentaje\":0,\"costoAdicional\":0}]}"
COTIZACION_ID=$(jget "$LAST_BODY_FILE" id)
echo "    codigo cotizacion: $(jget "$LAST_BODY_FILE" codigo) montoTotal: $(jget "$LAST_BODY_FILE" montoTotal)" | tee -a "$RLOG"

step "1.6 Aprobar cotización"
req PATCH "/cotizaciones/$COTIZACION_ID/estado?estado=APROBADA"

step "1.7 Crear orden de trabajo"
req POST /ordenes-trabajo "{\"cotizacionId\":$COTIZACION_ID,\"fecha\":\"2026-09-23\",\"hora\":\"09:00:00\",\"ejecutor\":\"Tecnico Validacion\",\"area\":\"Laboratorio\",\"lugar\":\"Taller\",\"detalles\":[{\"actividad\":\"Calibracion de multimetro\"}]}"
OT_ID=$(jget "$LAST_BODY_FILE" id)
echo "    numero OT: $(jget "$LAST_BODY_FILE" numero) estado: $(jget "$LAST_BODY_FILE" estado)" | tee -a "$RLOG"

step "1.8 Crear evaluación de aptitud"
req POST /evaluaciones-aptitud "{\"ordenDeTrabajoId\":$OT_ID,\"instrumentoId\":$INSTRUMENTO_ID}"
EVAL_ID=$(jget "$LAST_BODY_FILE" id)

step "1.9 Registrar resultado APTO"
req PATCH "/evaluaciones-aptitud/$EVAL_ID/resultado?resultado=APTO&observaciones=Sin%20novedades"
echo "    OT queda en estado: $(jget "$LAST_BODY_FILE" ordenDeTrabajoNumero) (ver GET)" | tee -a "$RLOG"

step "1.10 Crear patrón (herramienta)"
req POST /herramientas "{\"descripcion\":\"Patron multical $SESSION\",\"marca\":\"Fluke\",\"modelo\":\"5522A\",\"serie\":\"PAT-$SESSION\",\"codigoInterno\":\"HER-$SESSION\"}"
PATRON_ID=$(jget "$LAST_BODY_FILE" id)

step "1.11 Crear calibración (PROGRAMADA)"
req POST /calibraciones "{\"evaluacionAptitudId\":$EVAL_ID,\"tecnico\":\"Kevin\",\"procedimiento\":\"PR-CAL-003\",\"fecha\":\"2026-09-24\",\"patronesIds\":[$PATRON_ID]}"
CAL_ID=$(jget "$LAST_BODY_FILE" id)

step "1.12 Registrar mediciones (→ EN_PROCESO)"
req PUT "/calibraciones/$CAL_ID/mediciones" "[{\"puntoDescripcion\":\"10V DC\",\"valorPatron\":10.00,\"valorMedido\":10.02,\"unidad\":\"V\",\"dentroDeTolerancia\":true}]"

step "1.13 Completar calibración"
req PATCH "/calibraciones/$CAL_ID/estado?estado=COMPLETADA"

step "1.14 Crear revisión técnica"
req POST /revisiones-tecnicas "{\"calibracionId\":$CAL_ID,\"fechaRevision\":\"2026-09-23\",\"revisor\":\"Supervisor\"}"
REV_ID=$(jget "$LAST_BODY_FILE" id)

step "1.15 Registrar CONFORME → genera informe automáticamente"
req PATCH "/revisiones-tecnicas/$REV_ID/resultado?resultado=CONFORME&observaciones=Sin%20hallazgos"

step "1.16 Obtener informe generado (lista, último)"
req GET /informes-tecnicos
INFORME_ID=$(jget "$LAST_BODY_FILE" "-1.id")
echo "    informe: id=$INFORME_ID numero=$(jget "$LAST_BODY_FILE" '-1.numero') estado=$(jget "$LAST_BODY_FILE" '-1.estado')" | tee -a "$RLOG"

step "1.17 Marcar PDF cargado"
req PATCH "/informes-tecnicos/$INFORME_ID/pdf-cargado"

step "1.18 Informe APROBADO"
req PATCH "/informes-tecnicos/$INFORME_ID/estado?estado=APROBADO"

step "1.19 Informe ENVIADO (rellena fechaEnvio)"
req PATCH "/informes-tecnicos/$INFORME_ID/estado?estado=ENVIADO"

step "1.20 Cierre del expediente (CERRADO)"
req PATCH "/expedientes/$EXPEDIENTE_ID/estado?estado=CERRADO"

step "1.21 GET finales para inspección (Regla 4)"
req GET "/expedientes/$EXPEDIENTE_ID"
req GET "/ordenes-trabajo/$OT_ID"
req GET "/calibraciones/$CAL_ID"
req GET "/informes-tecnicos/$INFORME_ID"

cat > validation/ids.env <<EOF
SESSION=$SESSION
CLIENTE_ID=$CLIENTE_ID
INSTRUMENTO_ID=$INSTRUMENTO_ID
SERVICIO_ID=$SERVICIO_ID
EXPEDIENTE_ID=$EXPEDIENTE_ID
COTIZACION_ID=$COTIZACION_ID
OT_ID=$OT_ID
EVAL_ID=$EVAL_ID
PATRON_ID=$PATRON_ID
CAL_ID=$CAL_ID
REV_ID=$REV_ID
INFORME_ID=$INFORME_ID
EOF
echo "IDs guardados en validation/ids.env" | tee -a "$RLOG"
