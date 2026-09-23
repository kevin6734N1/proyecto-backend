#!/usr/bin/env bash
# correlativos.sh — CASO 11 corregido: correlativos concurrentes tras cerrar el loop del CASO 10.
set -u
cd "$(dirname "$0")/.."
source validation/harness.sh
source validation/ids.env

case_hdr "11b: Correlativos concurrentes (re-test correcto)"
step "La calibración $CAL_ID quedó EN_PROCESO tras el 3er NO_CONFORME (CASO 10). Re-completar:"
req PUT "/calibraciones/$CAL_ID/mediciones" "[{\"puntoDescripcion\":\"10V DC\",\"valorPatron\":10.00,\"valorMedido\":10.00,\"unidad\":\"V\",\"dentroDeTolerancia\":true}]"
req PATCH "/calibraciones/$CAL_ID/estado?estado=COMPLETADA"

step "Crear 2 revisiones (secuencial, ambas deben ser 201):"
req POST /revisiones-tecnicas "{\"calibracionId\":$CAL_ID,\"fechaRevision\":\"2026-09-23\",\"revisor\":\"Conc-Inf-1\"}"
REV_C1=$(jget "$LAST_BODY_FILE" id)
req POST /revisiones-tecnicas "{\"calibracionId\":$CAL_ID,\"fechaRevision\":\"2026-09-23\",\"revisor\":\"Conc-Inf-2\"}"
REV_C2=$(jget "$LAST_BODY_FILE" id)
echo "    revisiones para concurrencia: $REV_C1 y $REV_C2" | tee -a "$RLOG"

step "Marcar ambas CONFORME SIMULTÁNEAMENTE (2 hilos, cada transacción hace count+1):"
reqconcurrent C1 PATCH "/revisiones-tecnicas/$REV_C1/resultado?resultado=CONFORME" &
reqconcurrent C2 PATCH "/revisiones-tecnicas/$REV_C2/resultado?resultado=CONFORME" &
wait
concresult C1 PATCH "/revisiones-tecnicas/$REV_C1/resultado?resultado=CONFORME"
concresult C2 PATCH "/revisiones-tecnicas/$REV_C2/resultado?resultado=CONFORME"

step "Verificación de números de informe:"
req GET /informes-tecnicos
python3 - "$LAST_BODY_FILE" <<'PY' | tee -a "$RLOG"
import json, sys
d = json.load(open(sys.argv[1]))
nums = sorted(i["numero"] for i in d)
dups = sorted({n for n in nums if nums.count(n) > 1})
print(f"    total informes: {len(d)}")
print(f"    numeros: {nums}")
print(f"    DUPLICADOS: {dups if dups else 'ninguno'}")
PY
