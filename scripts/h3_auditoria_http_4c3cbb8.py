"""
Auditoría H3 sobre el código 4c3cbb8 vía HTTP real (Regla 0: request, response,
HTTP code). Contención REAL del mismo prefijo con barrier de 12 > MAX_INTENTOS=3,
E e IT por separado (Regla 5). Base H2 en memoria; no toca ./data/gesmin.

Levantar el backend así:
  SPRING_DATASOURCE_URL='jdbc:h2:mem:gesminh3http;DB_CLOSE_DELAY=-1' \
  SPRING_JPA_HIBERNATE_DDL_AUTO=create SERVER_PORT=18080 ./mvnw spring-boot:run
Correr con:
  GESMIN_BASE=http://localhost:18080 python3 scripts/h3_auditoria_http_4c3cbb8.py
"""
import json
import os
import threading
import time
import urllib.request
import urllib.error
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import date

BASE = os.environ.get("GESMIN_BASE", "http://localhost:18080")
HOY = date.today().isoformat()
EVIDENCIA = []


def req(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method,
                               headers={"Content-Type": "application/json"} if data else {})
    try:
        with urllib.request.urlopen(r, timeout=60) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def paso(titulo, method, path, body=None):
    code, raw = req(method, path, body)
    EVIDENCIA.append({"paso": titulo, "request": f"{method} {path}", "http": code,
                      "response": raw[:200] + ("…[truncado]" if len(raw) > 200 else "")})
    print(f"[{code}] {method} {path} :: {raw[:120]}")
    return code, (json.loads(raw) if raw.strip() else {})


def barrier_http(n, method, path):
    """Dispara n requests HTTP que arrancan en el mismo instante (barrier real)."""
    barrera = threading.Barrier(n)
    out = []

    def uno(_):
        barrera.wait(timeout=30)
        code, raw = req(method, path)
        out.append({"http": code, "body": raw})

    with ThreadPoolExecutor(max_workers=n) as ex:
        list(ex.map(uno, range(n)))
    for r in out:
        EVIDENCIA.append({"paso": f"[barrier x{n}] {method} {path}",
                          "request": f"{method} {path}", "http": r["http"],
                          "response": r["body"][:200]})
    return out


print("=== Cadena base (solo API, sin seeds) ===")
_, cli = paso("cliente", "POST", "/api/clientes", {
    "razonSocial": "Auditor H3", "ruc": "20" + str(int(time.time() * 1000) % 10**9).__str__().zfill(9)})
_, srv = paso("servicio", "POST", "/api/servicios", {
    "codigo": "AUD-" + uuid.uuid4().hex[:6], "nombre": "Calibración", "tipoServicio": "CALIBRACION",
    "tipoEquipoAplicable": "Multímetro", "precioVenta": 100})
_, pat = paso("patron (herramienta)", "POST", "/api/herramientas", {
    "descripcion": "Patrón auditoría", "marca": "Fluke", "modelo": "732B",
    "serie": "AUD-" + uuid.uuid4().hex[:4], "codigoInterno": "CI-" + uuid.uuid4().hex[:6]})
_, coi = paso("cotizacion", "POST", "/api/cotizaciones", {
    "clienteId": cli["id"], "fechaEmision": HOY,
    "detalles": [{"servicioId": srv["id"], "cantidad": 1, "descuentoPorcentaje": 0}]})
paso("aprobar COI", "PATCH", f"/api/cotizaciones/{coi['id']}/estado?estado=APROBADA")
_, ot = paso("orden de trabajo", "POST", "/api/ordenes-trabajo", {
    "cotizacionId": coi["id"], "fecha": HOY, "ejecutor": "Auditor",
    "detalles": [{"actividad": "Calibrar multímetro"}]})
_, ins = paso("instrumento", "POST", "/api/instrumentos", {
    "tipo": "Multímetro", "marca": "Fluke", "modelo": "87V",
    "serie": "AUD-" + uuid.uuid4().hex[:5], "clienteId": cli["id"]})
_, ev = paso("evaluacion aptitud", "POST", "/api/evaluaciones-aptitud", {
    "ordenDeTrabajoId": ot["id"], "instrumentoId": ins["id"], "fechaEvaluacion": HOY})
paso("resultado evaluacion=APTO", "PATCH",
     f"/api/evaluaciones-aptitud/{ev['id']}/resultado?resultado=APTO")
_, cal = paso("calibracion", "POST", "/api/calibraciones", {
    "evaluacionAptitudId": ev["id"], "fecha": HOY, "tecnico": "Auditor",
    "patronesIds": [pat["id"]]})
paso("calibracion COMPLETADA", "PATCH", f"/api/calibraciones/{cal['id']}/estado?estado=COMPLETADA")

n = 12  # MAX_INTENTOS=3 -> N = 4x el presupuesto

print(f"\n=== H3 E: {n} POST simultáneos, mismo prefijo E, barrier ===")
respuestas_e = barrier_http(n, "POST", f"/api/expedientes?clienteId={cli['id']}")
codes_e = [r["http"] for r in respuestas_e]
numeros_e = sorted(json.loads(r["body"])["numero"] for r in respuestas_e if r["http"] == 200)
print("HTTP codes E:", codes_e)
print("Correlativos E:", numeros_e)

print(f"\n=== H3 IT: {n} CONFORME simultáneos sobre {n} revisiones, mismo prefijo IT ===")
revisiones = []
for i in range(n):
    _, cal_i = paso(f"calibracion extra {i+2}", "POST", "/api/calibraciones", {
        "evaluacionAptitudId": ev["id"], "fecha": HOY, "tecnico": "Auditor",
        "patronesIds": [pat["id"]]})
    paso(f"calibracion extra {i+2} COMPLETADA", "PATCH",
         f"/api/calibraciones/{cal_i['id']}/estado?estado=COMPLETADA")
    _, rev_i = paso(f"revision {i+2}", "POST", "/api/revisiones-tecnicas",
                    {"calibracionId": cal_i["id"], "fechaRevision": HOY, "revisor": "Auditor"})
    revisiones.append(rev_i)

barrera = threading.Barrier(n)
out_it = []


def emite_it(idx):
    barrera.wait(timeout=30)
    code, raw = req("PATCH",
                    f"/api/revisiones-tecnicas/{revisiones[idx]['id']}/resultado"
                    "?resultado=CONFORME")
    out_it.append({"http": code, "body": raw})


with ThreadPoolExecutor(max_workers=n) as ex:
    list(ex.map(emite_it, range(n)))
for r in out_it:
    EVIDENCIA.append({"paso": "[barrier] PATCH resultado CONFORME",
                      "request": "PATCH /api/revisiones-tecnicas/{{id}}/resultado",
                      "http": r["http"], "response": r["body"][:200]})

codes_it = [r["http"] for r in out_it]
print("HTTP codes IT:", codes_it)
_, informes = paso("informes finales", "GET", "/api/informes-tecnicos")
rev_ids = {r["id"] for r in revisiones}
numeros_it = sorted(i["numero"] for i in informes if i["revisionTecnicaId"] in rev_ids)
print("Correlativos IT:", numeros_it)

print("\n=== VERIFICACIÓN ===")
esperados_e = [f"E{HOY[2:4]}{HOY[5:7]}{i:02d}" for i in range(1, n + 1)]
esperados_it = [f"IT{HOY[2:4]}{HOY[5:7]}{i:02d}" for i in range(1, n + 1)]
ok_e = codes_e == [200] * n and numeros_e == esperados_e
ok_it = codes_it == [200] * n and numeros_it == esperados_it
print(f"E:  exitosos={codes_e.count(200)}/{n} correlativos={numeros_e} consecutivos="
      f"{numeros_e == esperados_e}")
print(f"IT: exitosos={codes_it.count(200)}/{n} correlativos={numeros_it}")
print(f"VEREDICTO HTTP: E={'SIN PERDIDAS' if ok_e else 'HALLAZGO'} "
      f"IT={'SIN PERDIDAS' if ok_it else 'HALLAZGO'}")

with open("validation/h3_http_evidencia_4c3cbb8.json", "w", encoding="utf-8") as f:
    json.dump(EVIDENCIA, f, indent=2, ensure_ascii=False)
print("\nEvidencia cruda guardada en validation/h3_http_evidencia_4c3cbb8.json")
