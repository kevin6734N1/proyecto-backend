#!/usr/bin/env bash
# harness.sh — helpers para la sesión de validación Gesmin
# Requiere: curl, python3. No imprime nada por sí solo salvo el log de cada request.

BASE=http://localhost:8080/api
RLOG=validation/requests.log
LAST_CODE=""
LAST_BODY_FILE=/tmp/gesmin_last_resp.json

# jget <archivo.json> <ruta.punto.0.subcampo>
jget() {
  python3 - "$1" "$2" <<'PY'
import json, sys
try:
    d = json.load(open(sys.argv[1]))
    cur = d
    for k in sys.argv[2].split('.'):
        if k == '': continue
        if isinstance(cur, list):
            cur = cur[int(k)]
        elif isinstance(cur, dict) and k in cur:
            cur = cur[k]
        else:
            cur = None
        if cur is None: break
    print('null' if cur is None else cur)
except Exception:
    print('null')
PY
}

# logreq METHOD URL CODE BODY
logreq() {
  printf '%-8s %-58s -> %s | %s\n' "$1" "$2" "$3" "$4" | tee -a "$RLOG"
}

# req METHOD URL [JSON_BODY]  — guarda código en LAST_CODE y body en $LAST_BODY_FILE
req() {
  local method=$1 url=$2 data=${3:-}
  local bf; bf=$(mktemp)
  if [ -n "$data" ]; then
    LAST_CODE=$(curl -s -o "$bf" -w "%{http_code}" -X "$method" "$BASE$url" -H "Content-Type: application/json" -d "$data")
  else
    LAST_CODE=$(curl -s -o "$bf" -w "%{http_code}" -X "$method" "$BASE$url")
  fi
  cp "$bf" "$LAST_BODY_FILE"
  logreq "$method" "$url" "$LAST_CODE" "$(cat "$bf")"
  rm -f "$bf"
}

# reqconcurrent LABEL METHOD URL [JSON] — corre en foreground; guarda código en /tmp/gesmin_conc_<LABEL>.code y body en .json
reqconcurrent() {
  local label=$1 method=$2 url=$3 data=${4:-}
  local bf; bf=$(mktemp)
  if [ -n "$data" ]; then
    curl -s -o "$bf" -w "%{http_code}" -X "$method" "$BASE$url" -H "Content-Type: application/json" -d "$data" > "/tmp/gesmin_conc_${label}.code"
  else
    curl -s -o "$bf" -w "%{http_code}" -X "$method" "$BASE$url" > "/tmp/gesmin_conc_${label}.code"
  fi
  mv "$bf" "/tmp/gesmin_conc_${label}.json"
}

# concresult LABEL METHOD URL — loguea el resultado de una llamada concurrente ya terminada
concresult() {
  local label=$1 method=$2 url=$3
  local code body
  code=$(cat "/tmp/gesmin_conc_${label}.code" 2>/dev/null || echo "NO_RESPONSE")
  body=$(cat "/tmp/gesmin_conc_${label}.json" 2>/dev/null || echo "(sin body)")
  logreq "$method" "$url" "$code" "$body"
}

case_hdr() {
  echo "" | tee -a "$RLOG"
  echo "=== CASO $1 ===" | tee -a "$RLOG"
}

step() {
  echo "--- $1" | tee -a "$RLOG"
}
