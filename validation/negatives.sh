#!/usr/bin/env bash
# negatives.sh — Casos negativos (Regla 2). Reusa IDs de validation/ids.env (sin resembrar).
set -u
cd "$(dirname "$0")/.."
source validation/harness.sh
source validation/ids.env

SESSION=$(date +%H%M%S)

# ---------- CASO 2: revisiones simultáneas sobre la MISMA calibración COMPLETADA ----------
case_hdr "2: Dos revisiones simultáneas sobre calibración $CAL_ID (COMPLETADA)"
reqconcurrent R2a POST /revisiones-tecnicas "{\"calibracionId\":$CAL_ID,\"fechaRevision\":\"2026-09-23\",\"revisor\":\"Concurrente-A\"}" &
reqconcurrent R2b POST /revisiones-tecnicas "{\"calibracionId\":$CAL_ID,\"fechaRevision\":\"2026-09-23\",\"revisor\":\"Concurrente-B\"}" &
wait
concresult R2a POST /revisiones-tecnicas
concresult R2b POST /revisiones-tecnicas
step "Listado de revisiones de la calibración $CAL_ID:"
req GET "/revisiones-tecnicas?calibracionId=$CAL_ID"
REV2_EXTRA=$(jget "$LAST_BODY_FILE" "-1.id")
REV3_EXTRA=$(jget "$LAST_BODY_FILE" "-2.id")
REV_EXTRA_LIST="$REV2_EXTRA,$REV3_EXTRA"
echo "    revisiones totales: $(jget "$LAST_BODY_FILE" 'len')" | tee -a "$RLOG"

# ---------- CASO 3: informe sobre calibración ya ENVIADA (informe ENVIADO) ----------
case_hdr "3: Intentar crear informe sobre revisión ya ENVIADA (informe de $CAL_ID)"
step "Estado previo del informe (via GET):"
req GET "/informes-tecnicos/$INFORME_ID"
step "Intento de regenerar informe vía nueva revisión CONFORME sobre misma calibración:"
req POST /revisiones-tecnicas "{\"calibracionId\":$CAL_ID,\"fechaRevision\":\"2026-09-23\",\"revisor\":\"Regenerador\"}"
REV_ENVIO_ID=$(jget "$LAST_BODY_FILE" id)
req PATCH "/revisiones-tecnicas/$REV_ENVIO_ID/resultado?resultado=CONFORME"
echo "    informes ahora: $(req GET /informes-tecnicos >/dev/null; jget "$LAST_BODY_FILE" 'len') total" | tee -a "$RLOG"

# ---------- CASO 4: segundo informe para la misma revisión ----------
case_hdr "4: Segundo informe para la misma revisión $REV_ID"
step "Buscando endpoint directo de creación de informes:"
req POST /informes-tecnicos "{\"revisionTecnicaId\":$REV_ID,\"fechaEmision\":\"2026-09-23\"}"
step "Re-registrar resultado CONFORME sobre la revisión original $REV_ID (¿regenera informe?):"
req PATCH "/revisiones-tecnicas/$REV_ID/resultado?resultado=CONFORME&observaciones=Segundo%20intento"
req GET /informes-tecnicos
echo "    informes ligados a revision $REV_ID: $(jget "$LAST_BODY_FILE" 'len') total en sistema" | tee -a "$RLOG"
python3 - "$LAST_BODY_FILE" "$REV_ID" <<'PY' | tee -a "$RLOG"
import json, sys
try:
    d = json.load(open(sys.argv[1]))
    n = sum(1 for i in d if i.get("revisionTecnicaId") == int(sys.argv[2]))
    print(f"    informes con revisionTecnicaId={sys.argv[2]}: {n}")
except Exception as e:
    print(f"    (no se pudo analizar: {e})")
PY

# ---------- CASO 5: idempotencia de PATCH mismo estado ----------
case_hdr "5: Idempotencia — dos PATCH iguales"
step "PATCH estado ENVIADO al informe $INFORME_ID por segunda vez (¿fechaEnvio cambia? ¿falla?):"
req PATCH "/informes-tecnicos/$INFORME_ID/estado?estado=ENVIADO"
echo "    fechaEnvio: $(jget "$LAST_BODY_FILE" fechaEnvio) estado: $(jget "$LAST_BODY_FILE" estado)" | tee -a "$RLOG"
step "PATCH APROBADA a cotización ya APROBADA $COTIZACION_ID:"
req PATCH "/cotizaciones/$COTIZACION_ID/estado?estado=APROBADA"
step "PATCH COMPLETADA a calibración ya COMPLETADA $CAL_ID:"
req PATCH "/calibraciones/$CAL_ID/estado?estado=COMPLETADA"
step "PATCH CERRADO a expediente ya CERRADO $EXPEDIENTE_ID:"
req PATCH "/expedientes/$EXPEDIENTE_ID/estado?estado=CERRADO"

# ---------- CASO 6: saltar estado informe GENERADO → ENVIADO ----------
case_hdr "6: Saltar estados en informe (GENERADO → ENVIADO directo)"
req POST /revisiones-tecnicas "{\"calibracionId\":$CAL_ID,\"fechaRevision\":\"2026-09-23\",\"revisor\":\"SaltoEstado\"}"
REV_SALTO_ID=$(jget "$LAST_BODY_FILE" id)
req PATCH "/revisiones-tecnicas/$REV_SALTO_ID/resultado?resultado=CONFORME"
req GET /informes-tecnicos
INF_SALTO_ID=$(jget "$LAST_BODY_FILE" "-1.id")
echo "    informe nuevo: id=$INF_SALTO_ID estado=$(jget "$LAST_BODY_FILE" '-1.estado')" | tee -a "$RLOG"
step "Intento de salto directo a ENVIADO (sin PDF_CARGADO ni APROBADO):"
req PATCH "/informes-tecnicos/$INF_SALTO_ID/estado?estado=ENVIADO"

# ---------- CASO 7: revisión sobre calibración PROGRAMADA y EN_PROCESO ----------
case_hdr "7: Revisión sobre calibración PROGRAMADA / EN_PROCESO"
step "Crear calibración nueva (queda PROGRAMADA) para evaluación $EVAL_ID:"
req POST /calibraciones "{\"evaluacionAptitudId\":$EVAL_ID,\"tecnico\":\"Kevin\",\"procedimiento\":\"PR-CAL-003\",\"fecha\":\"2026-09-24\",\"patronesIds\":[$PATRON_ID]}"
CAL2_ID=$(jget "$LAST_BODY_FILE" id)
step "Intento de revisión sobre calibración PROGRAMADA $CAL2_ID:"
req POST /revisiones-tecnicas "{\"calibracionId\":$CAL2_ID,\"fechaRevision\":\"2026-09-23\",\"revisor\":\"Guard-Test\"}"
step "Poner calibración $CAL2_ID en EN_PROCESO (sin mediciones):"
req PATCH "/calibraciones/$CAL2_ID/estado?estado=EN_PROCESO"
step "Intento de revisión sobre calibración EN_PROCESO $CAL2_ID:"
req POST /revisiones-tecnicas "{\"calibracionId\":$CAL2_ID,\"fechaRevision\":\"2026-09-23\",\"revisor\":\"Guard-Test\"}"

# ---------- CASO 8: evaluación NO_APTO ----------
case_hdr "8: Evaluación NO_APTO → estado de OT y bloqueo de calibración"
step "Crear instrumento + evaluación nueva sobre OT $OT_ID:"
req POST /instrumentos "{\"tipo\":\"Balanza\",\"marca\":\"Ohaus\",\"modelo\":\"PA214\",\"serie\":\"NOAPTO-$SESSION\",\"clienteId\":$CLIENTE_ID}"
INST_NOAPTO_ID=$(jget "$LAST_BODY_FILE" id)
req POST /evaluaciones-aptitud "{\"ordenDeTrabajoId\":$OT_ID,\"instrumentoId\":$INST_NOAPTO_ID}"
EVAL_NOAPTO_ID=$(jget "$LAST_BODY_FILE" id)
step "Registrar NO_APTO:"
req PATCH "/evaluaciones-aptitud/$EVAL_NOAPTO_ID/resultado?resultado=NO_APTO&observaciones=Fuera%20de%20rango"
echo "    OT estado tras NO_APTO: $(req GET "/ordenes-trabajo/$OT_ID" >/dev/null; jget "$LAST_BODY_FILE" estado)" | tee -a "$RLOG"
step "Intento de crear calibración sobre evaluación NO_APTO $EVAL_NOAPTO_ID:"
req POST /calibraciones "{\"evaluacionAptitudId\":$EVAL_NOAPTO_ID,\"tecnico\":\"Kevin\",\"fecha\":\"2026-09-24\",\"patronesIds\":[$PATRON_ID]}"
cat >> validation/ids.env <<EOF
INST_NOAPTO_ID=$INST_NOAPTO_ID
EVAL_NOAPTO_ID=$EVAL_NOAPTO_ID
CAL2_ID=$CAL2_ID
INF_SALTO_ID=$INF_SALTO_ID
REV_ENVIO_ID=$REV_ENVIO_ID
EOF

# ---------- CASO 9: cierre con OT cancelada y NO_APTO sin respuesta ----------
case_hdr "9: Cierre de expediente con OT cancelada y NO_APTO sin respuesta"
step "Cancelar OT $OT_ID (queda NO_APTO sin respuesta del cliente):"
req PATCH "/ordenes-trabajo/$OT_ID/estado?estado=CANCELADA"
step "PATCH expediente $EXPEDIENTE_ID a CERRADO (ya estaba CERRADO — re-apertura implícita):"
req PATCH "/expedientes/$EXPEDIENTE_ID/estado?estado=EN_PROCESO"
req PATCH "/expedientes/$EXPEDIENTE_ID/estado?estado=CERRADO"

# ---------- CASO 10: loop NO_CONFORME x3 ----------
case_hdr "10: Loop de corrección — 3 revisiones NO_CONFORME seguidas"
step "Iteración 1: revisión sobre $CAL_ID + NO_CONFORME"
req POST /revisiones-tecnicas "{\"calibracionId\":$CAL_ID,\"fechaRevision\":\"2026-09-23\",\"revisor\":\"Loop-1\"}"
REV_L1=$(jget "$LAST_BODY_FILE" id)
req PATCH "/revisiones-tecnicas/$REV_L1/resultado?resultado=NO_CONFORME"
echo "    calibración queda en: $(req GET "/calibraciones/$CAL_ID" >/dev/null; jget "$LAST_BODY_FILE" estado)" | tee -a "$RLOG"
step "Re-ejecutar: mediciones + COMPLETADA"
req PUT "/calibraciones/$CAL_ID/mediciones" "[{\"puntoDescripcion\":\"10V DC\",\"valorPatron\":10.00,\"valorMedido\":10.01,\"unidad\":\"V\",\"dentroDeTolerancia\":true}]"
req PATCH "/calibraciones/$CAL_ID/estado?estado=COMPLETADA"
step "Iteración 2"
req POST /revisiones-tecnicas "{\"calibracionId\":$CAL_ID,\"fechaRevision\":\"2026-09-23\",\"revisor\":\"Loop-2\"}"
REV_L2=$(jget "$LAST_BODY_FILE" id)
req PATCH "/revisiones-tecnicas/$REV_L2/resultado?resultado=NO_CONFORME"
step "Re-ejecución 2 + Iteración 3"
req PUT "/calibraciones/$CAL_ID/mediciones" "[{\"puntoDescripcion\":\"10V DC\",\"valorPatron\":10.00,\"valorMedido\":10.03,\"unidad\":\"V\",\"dentroDeTolerancia\":true}]"
req PATCH "/calibraciones/$CAL_ID/estado?estado=COMPLETADA"
req POST /revisiones-tecnicas "{\"calibracionId\":$CAL_ID,\"fechaRevision\":\"2026-09-23\",\"revisor\":\"Loop-3\"}"
REV_L3=$(jget "$LAST_BODY_FILE" id)
req PATCH "/revisiones-tecnicas/$REV_L3/resultado?resultado=NO_CONFORME"
step "Estado tras 3 NO_CONFORME: calibración=$(req GET "/calibraciones/$CAL_ID" >/dev/null; jget "$LAST_BODY_FILE" estado)"
req GET "/revisiones-tecnicas?calibracionId=$CAL_ID"
echo "    revisiones acumuladas para calibración $CAL_ID: $(jget "$LAST_BODY_FILE" 'len')" | tee -a "$RLOG"
step "¿Puedo crear OT nueva / calibración sobre revisión huérfana? Prueba: PUT mediciones sobre calibración CANCELADA no aplica; prueba de rev huérfana = revisión sin informe:"
python3 - "$LAST_BODY_FILE" "$CAL_ID" <<'PY' | tee -a "$RLOG"
import json, sys
d = json.load(open(sys.argv[1]))
print(f"    revisiones: {[(r['id'], r['resultado']) for r in d]}")
PY

# ---------- CASO 11: correlativos concurrentes ----------
case_hdr "11: Correlativos concurrentes — 2 calibraciones nuevas simultáneas + cierre de loop con CONFORME"
step "Cerrar el loop: revisión 4 CONFORME sobre $CAL_ID (debe generar informe)"
req POST /revisiones-tecnicas "{\"calibracionId\":$CAL_ID,\"fechaRevision\":\"2026-09-23\",\"revisor\":\"Loop-Close\"}"
REV_L4=$(jget "$LAST_BODY_FILE" id)
req PATCH "/revisiones-tecnicas/$REV_L4/resultado?resultado=CONFORME"
step "Dos CONFORME concurrentes (cada una crea su revisión primero):"
req POST /revisiones-tecnicas "{\"calibracionId\":$CAL_ID,\"fechaRevision\":\"2026-09-23\",\"revisor\":\"Conc-Inf-1\"}"
REV_C1=$(jget "$LAST_BODY_FILE" id)
req POST /revisiones-tecnicas "{\"calibracionId\":$CAL_ID,\"fechaRevision\":\"2026-09-23\",\"revisor\":\"Conc-Inf-2\"}"
REV_C2=$(jget "$LAST_BODY_FILE" id)
reqconcurrent C1 PATCH "/revisiones-tecnicas/$REV_C1/resultado?resultado=CONFORME" &
reqconcurrent C2 PATCH "/revisiones-tecnicas/$REV_C2/resultado?resultado=CONFORME" &
wait
concresult "C1" PATCH "/revisiones-tecnicas/$REV_C1/resultado?resultado=CONFORME"
concresult "C2" PATCH "/revisiones-tecnicas/$REV_C2/resultado?resultado=CONFORME"
step "Listado de informes tras los 2 CONFORME concurrentes:"
req GET /informes-tecnicos
python3 - "$LAST_BODY_FILE" <<'PY' | tee -a "$RLOG"
import json, sys
d = json.load(open(sys.argv[1]))
nums = sorted(i["numero"] for i in d)
dups = {n for n in nums if nums.count(n) > 1}
print(f"    total informes: {len(d)} numeros: {nums} DUPLICADOS: {dups if dups else 'ninguno'}")
PY

# ---------- CASO 12: GET con ID inexistente en 3 entidades ----------
case_hdr "12: GET con ID inexistente (99999) en 3 entidades"
req GET "/clientes/99999"
req GET "/calibraciones/99999"
req GET "/informes-tecnicos/99999"

echo "" | tee -a "$RLOG"
echo "### FIN CASOS NEGATIVOS ###" | tee -a "$RLOG"
