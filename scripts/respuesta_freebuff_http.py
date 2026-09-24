"""
Respuesta HTTP a la auditoría de FreeBuff (H1/H2/H3) contra el backend corriendo
con base H2 EN MEMORIA (no toca ./data/gesmin).

Evidencia cruda: método, endpoint, HTTP code y cuerpo de cada paso.

Levantar el backend así:
  SPRING_DATASOURCE_URL='jdbc:h2:mem:gesminhttp;DB_CLOSE_DELAY=-1' \
  SPRING_JPA_HIBERNATE_DDL_AUTO=create ./mvnw spring-boot:run
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

BASE = os.environ.get("GESMIN_BASE", "http://localhost:8080")
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
    EVIDENCIA.append({"paso": titulo, "request": f"{method} {path}", "body": body,
                      "http": code, "response": raw})
    print(f"[{code}] {method} {path} :: {raw}")
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
                          "request": f"{method} {path}", "http": r["http"], "response": r["body"]})
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

print("\n=== H1/H2: informe, reapertura y recertificación ===")
_, revision = paso("r1", "POST", "/api/revisiones-tecnicas",
                   {"calibracionId": cal["id"], "fechaRevision": HOY, "revisor": "Auditor"})
ruta = f"/api/revisiones-tecnicas/{revision['id']}/resultado"
code, _ = paso("primera emisión", "PATCH", ruta + "?resultado=CONFORME&observaciones=Original")
assert code == 200
_, lista1 = paso("informe original", "GET", "/api/informes-tecnicos")
originales = [i for i in lista1 if i["revisionTecnicaId"] == revision["id"]]
assert len(originales) == 1
original = originales[0]

code, _ = paso("PATCH idéntico", "PATCH", ruta + "?resultado=CONFORME&observaciones=Original")
assert code == 200
_, lista2 = paso("sin duplicado", "GET", "/api/informes-tecnicos")
assert len([i for i in lista2 if i["revisionTecnicaId"] == revision["id"]]) == 1

code, _ = paso("reabrir por falla", "PATCH",
               ruta + "?resultado=NO_CONFORME&observaciones=Falla-confirmada")
assert code == 200
_, anulado = paso("certificado anulado", "GET", f"/api/informes-tecnicos/{original['id']}")
assert anulado["estado"] == "ANULADO" and anulado["motivoAnulacion"]
code, _ = paso("PDF anulado no descargable", "GET",
               f"/api/informes-tecnicos/{original['id']}/pdf")
assert code == 400
code, _ = paso("informe anulado no aprobable", "PATCH",
               f"/api/informes-tecnicos/{original['id']}/estado?estado=APROBADO")
assert code == 400
code, _ = paso("corrección completada", "PATCH",
               f"/api/calibraciones/{cal['id']}/estado?estado=COMPLETADA")
assert code == 200
code, _ = paso("recertificar misma revisión", "PATCH",
               ruta + "?resultado=CONFORME&observaciones=Corregida")
assert code == 200
_, lista3 = paso("historial con nuevo informe", "GET", "/api/informes-tecnicos")
historial = [i for i in lista3 if i["revisionTecnicaId"] == revision["id"]]
assert len(historial) == 2
assert len([i for i in historial if i["estado"] != "ANULADO"]) == 1
assert historial[0]["numero"] != historial[1]["numero"]
code, _ = paso("PATCH idéntico tras recertificar", "PATCH",
               ruta + "?resultado=CONFORME&observaciones=Corregida")
assert code == 200
_, lista4 = paso("sigue sin duplicado", "GET", "/api/informes-tecnicos")
assert len([i for i in lista4 if i["revisionTecnicaId"] == revision["id"]]) == 2

print("\n=== H3: 8 POST simultáneos, mismo prefijo E y CyclicBarrier ===")
n = 8
respuestas = barrier_http(n, "POST", f"/api/expedientes?clienteId={cli['id']}")
codes = [r["http"] for r in respuestas]
numeros = sorted(json.loads(r["body"])["numero"] for r in respuestas if r["http"] == 200)
print("HTTP codes:", codes)
print("Correlativos:", numeros)
assert codes == [200] * n, "Se perdió un commit por colisión"
assert len(set(numeros)) == n

print("\n=== EVIDENCIA CRUDA ===")
print(json.dumps(EVIDENCIA, indent=2, ensure_ascii=False))
