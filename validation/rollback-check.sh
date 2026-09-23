#!/usr/bin/env bash
# rollback-check.sh — ¿La revisión 9 (CONFORME que chocó con unique violation) quedó consistente?
set -u
cd "$(dirname "$0")/.."
source validation/harness.sh
source validation/ids.env

case_hdr "11c: Consistencia post-colisión"
step "GET revisión 9 (la que falló con unique violation):"
req GET "/revisiones-tecnicas/9"
echo "    resultado de revision 9: $(jget "$LAST_BODY_FILE" resultado)" | tee -a "$RLOG"
step "GET revisión 10 (la que ganó la carrera):"
req GET "/revisiones-tecnicas/10"
echo "    resultado de revision 10: $(jget "$LAST_BODY_FILE" resultado)" | tee -a "$RLOG"
step "¿Existe informe para revisión 9?"
req GET /informes-tecnicos
python3 - "$LAST_BODY_FILE" <<'PY' | tee -a "$RLOG"
import json, sys
d = json.load(open(sys.argv[1]))
con_inf9 = [i for i in d if i.get("revisionTecnicaId") == 9]
con_inf10 = [i for i in d if i.get("revisionTecnicaId") == 10]
print(f"    informes para revision 9: {len(con_inf9)} | para revision 10: {len(con_inf10)}")
PY
