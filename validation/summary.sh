#!/usr/bin/env bash
# summary.sh — Verificación final de estado accesible (Regla 4). No modifica nada.
set -u
cd "$(dirname "$0")/.."
source validation/harness.sh
source validation/ids.env

echo "### ESTADO FINAL ACCESIBLE (Regla 4) ###" | tee -a "$RLOG"
req GET /clientes
req GET /expedientes
req GET /cotizaciones
req GET /ordenes-trabajo
req GET /evaluaciones-aptitud?ordenDeTrabajoId=$OT_ID
req GET /calibraciones
req GET "/revisiones-tecnicas?calibracionId=$CAL_ID"
req GET /informes-tecnicos
echo "### FIN RESUMEN — revisar validation/requests.log ###" | tee -a "$RLOG"
