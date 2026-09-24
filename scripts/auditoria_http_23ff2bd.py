"""
Auditoría HTTP adversarial del commit 23ff2bd (H1/H2/H3) contra el backend corriendo
con base H2 EN MEMORIA (no toca ./data/gesmin).

Evidencia cruda: método, endpoint, HTTP code y cuerpo de cada paso.

Levantar el backend así:
  SPRING_DATASOURCE_URL='jdbc:h2:mem:gesminhttp;DB_CLOSE_DELAY=-1' \
  SPRING_JPA_HIBERNATE_DDL_AUTO=create ./mvnw spring-boot:run
"""
import json
import threading
import time
import urllib.request
import urllib.error
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import date

BASE = "http://localhost:8080"
HOY = date.today().isoformat()
EVIDENCIA = []


def req(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method,
                               headers={"Content-Type": "application/json"} if data else {})
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def paso(titulo, method, path, body=None):
    code, raw = req(method, path, body)
    EVIDENCIA.append({"paso": titulo, "request": f"{method} {path}", "http": code,
                      "response": raw[:300] + ("…[truncado]" if len(raw) > 300 else "")})
    print(f"[{code}] {method} {path} :: {raw[:120]}")
    return code, (json.loads(raw) if raw.strip() else {})


def barrier_http(n, method, path, body=None):
    """Dispara n requests HTTP que arrancan en el mismo instante (barrier real)."""
    barrera = threading.Barrier(n)
    out = []

    def uno(_):
        barrera.wait(timeout=30)
        code, raw = req(method, path, body)
        out.append({"http": code, "body": raw})

    with ThreadPoolExecutor(max_workers=n) as ex:
        list(ex.map(uno, range(n)))
    for r in out:
        EVIDENCIA.append({"paso": f"[barrier x{n}] {method} {path}",
                          "request": f"{method} {path}", "http": r["http"], "response": r["body"][:300]})
    return out


print("=== Cadena base ===")
_, cli = paso("cliente", "POST", "/api/clientes", {
    "razonSocial": "Auditor HTTP", "ruc": "20" + str(int(time.time() * 1000) % 10**9).__str__().zfill(9)})
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

print("\n=== H2 (parte 1): dos revisiones ANTES del informe ===")
_, r1 = paso("revision r1", "POST", "/api/revisiones-tecnicas",
             {"calibracionId": cal["id"], "fechaRevision": HOY, "revisor": "Auditor"})
_, r2 = paso("revision r2 (queda PENDIENTE)", "POST", "/api/revisiones-tecnicas",
             {"calibracionId": cal["id"], "fechaRevision": HOY, "revisor": "Auditor"})

print("\n=== H1: emitir CONFORME y repetir el PATCH ===")
_, it1 = paso("r1 CONFORME (1er PATCH)", "PATCH",
              f"/api/revisiones-tecnicas/{r1['id']}/resultado?resultado=CONFORME&observaciones=Original")
paso("r1 CONFORME (PATCH repetido con obs nuevas)", "PATCH",
     f"/api/revisiones-tecnicas/{r1['id']}/resultado?resultado=CONFORME&observaciones=IGNORAME")
_, informes = paso("listar informes (debe haber 1)", "GET", "/api/informes-tecnicos")

print("\n=== H2 (parte 2): ciclo NO_CONFORME con informe vivo ===")
paso("crear revision r3 tras informe (esperado 400)", "POST", "/api/revisiones-tecnicas",
     {"calibracionId": cal["id"], "fechaRevision": HOY, "revisor": "Auditor"})
paso("r2 PENDIENTE -> NO_CONFORME con informe vivo (¿debería bloquearse?)", "PATCH",
     f"/api/revisiones-tecnicas/{r2['id']}/resultado?resultado=NO_CONFORME&observaciones=Falla-post-emitido")
paso("calibracion -> COMPLETADA (corregida)", "PATCH",
     f"/api/calibraciones/{cal['id']}/estado?estado=COMPLETADA")
paso("crear revision r4 tras corregir (¿ciclo roto?)", "POST", "/api/revisiones-tecnicas",
     {"calibracionId": cal["id"], "fechaRevision": HOY, "revisor": "Auditor"})
paso("certificar r2 (CONFORME, esperado 400)", "PATCH",
     f"/api/revisiones-tecnicas/{r2['id']}/resultado?resultado=CONFORME")

print("\n=== H3-B: 4 expedientes HTTP simultáneos (barrier real) ===")
res_exp = barrier_http(4, "POST", f"/api/expedientes?clienteId={cli['id']}")
codigos = sorted(r["http"] for r in res_exp)
print(f">>> HTTP codes: {codigos}  (cada 400 = commit perdido por retry agotado)")
paso("listar expedientes (verificar duplicados/huecos)", "GET", "/api/expedientes")

print("\n=== H3-A2: 4 CONFORME HTTP simultáneos sobre calibraciones frescas ===")
objetivos = []
for i in range(4):
    _, evi = paso(f"evaluacion extra {i+1}", "POST", "/api/evaluaciones-aptitud", {
        "ordenDeTrabajoId": ot["id"], "instrumentoId": ins["id"], "fechaEvaluacion": HOY})
    paso(f"resultado ev{i+1}=APTO", "PATCH",
         f"/api/evaluaciones-aptitud/{evi['id']}/resultado?resultado=APTO")
    _, cali = paso(f"calibracion extra {i+1}", "POST", "/api/calibraciones", {
        "evaluacionAptitudId": evi["id"], "fecha": HOY, "tecnico": "Auditor",
        "patronesIds": [pat["id"]]})
    paso(f"calibracion extra {i+1} COMPLETADA", "PATCH",
         f"/api/calibraciones/{cali['id']}/estado?estado=COMPLETADA")
    _, ri = paso(f"revision extra {i+1}", "POST", "/api/revisiones-tecnicas",
                 {"calibracionId": cali["id"], "fechaRevision": HOY, "revisor": "Auditor"})
    objetivos.append(ri["id"])

# barrier sobre el mismo endpoint con ids distintos: se resuelven por polling de resultado
resultados = [None] * 4
barrera = threading.Barrier(4)
lock = threading.Lock()


def conforme(idx):
    barrera.wait(timeout=30)
    code, raw = req("PATCH", f"/api/revisiones-tecnicas/{objetivos[idx]}/resultado?resultado=CONFORME")
    with lock:
        resultados[idx] = {"http": code, "body": raw}
        EVIDENCIA.append({"paso": f"[barrier x4] PATCH CONFORME rev={objetivos[idx]}",
                          "request": f"PATCH /api/revisiones-tecnicas/{objetivos[idx]}/resultado?resultado=CONFORME",
                          "http": code, "response": raw[:300]})


with ThreadPoolExecutor(max_workers=4) as ex:
    list(ex.map(conforme, range(4)))
print(">>> HTTP codes:", sorted(r["http"] for r in resultados),
      " (cada 400 = commit perdido por retry agotado)")
paso("listar informes finales (huecos/duplicados)", "GET", "/api/informes-tecnicos")

print("\n================ EVIDENCIA CRUDA ================\n")
print(json.dumps(EVIDENCIA, indent=2, ensure_ascii=False))
