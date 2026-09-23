#!/usr/bin/env bash
# persistence.sh — Regla 4: reinicio deliberado del server y verificación de estado persistido.
set -u
cd "$(dirname "$0")/.."
source validation/harness.sh
source validation/ids.env

case_hdr "13: Persistencia tras reinicio (H2 file) + continuidad de correlativo"

step "Reinicios provocados por devtools durante la corrida (deben ser 0):"
grep -c "Restarting due to" /tmp/gesmin-server.log || echo "0 (sin reinicios de devtools)"

step "Deteniendo server (PID $(pgrep -f 'spring-boot:run' | head -1))..."
pkill -f "spring-boot:run" || true
for i in $(seq 1 20); do pgrep -f "spring-boot:run" >/dev/null || break; sleep 1; done
pgrep -f "spring-boot:run" >/dev/null && echo "WARN: server sigue vivo" || echo "Server detenido."

step "Reiniciando server (misma base en archivo, SIN reseed):"
nohup ./mvnw spring-boot:run > /tmp/gesmin-server-2.log 2>&1 &
for i in $(seq 1 60); do
  if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/clientes 2>/dev/null | grep -q 200; then
    echo "Server UP tras reinicio"; break
  fi
  sleep 2
done

step "GET expediente $EXPEDIENTE_ID (debe seguir CERRADO con datos intactos):"
req GET "/expedientes/$EXPEDIENTE_ID"

step "GET informe $INFORME_ID (debe seguir ENVIADO):"
req GET "/informes-tecnicos/$INFORME_ID"

step "GET calibración $CAL_ID (debe seguir COMPLETADA):"
req GET "/calibraciones/$CAL_ID"

step "Continuidad de correlativo: crear nueva revisión CONFORME tras reinicio (debe ser IT260906, no reiniciar en 001):"
req POST /revisiones-tecnicas "{\"calibracionId\":$CAL_ID,\"fechaRevision\":\"2026-09-23\",\"revisor\":\"PostRestart\"}"
REV_POST=$(jget "$LAST_BODY_FILE" id)
req PATCH "/revisiones-tecnicas/$REV_POST/resultado?resultado=CONFORME"
req GET /informes-tecnicos
python3 - "$LAST_BODY_FILE" <<'PY' | tee -a "$RLOG"
import json, sys
d = json.load(open(sys.argv[1]))
ultimo = max(i["numero"] for i in d)
print(f"    total informes: {len(d)} | ultimo numero: {ultimo}")
PY

step "Reinicios provocados por devtools en la 2da corrida (deben ser 0):"
grep -c "Restarting due to" /tmp/gesmin-server-2.log || echo "0 (sin reinicios de devtools)"
