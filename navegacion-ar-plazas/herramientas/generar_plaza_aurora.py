#!/usr/bin/env python3
"""Genera los datos FICTICIOS de la plaza de demostracion "Plaza Aurora".

Salida: nucleo/src/main/resources/plazas/plaza_aurora.json

Los datos no corresponden a ningun centro comercial real. El script existe para
que la geometria (pasillos, locales, nodos y aristas) quede siempre coherente:
si se cambia la distribucion, se regenera el JSON en lugar de editarlo a mano.

    python3 herramientas/generar_plaza_aurora.py

Convenciones (ver nucleo/.../modelo/Geometria.kt):
  - metros, X a la derecha, Y hacia arriba, origen en la esquina inferior izquierda
  - rumbo = grados horarios desde +Y
"""
import json
import os
import unicodedata

ANCHO, ALTO = 80.0, 50.0
Y_PASILLO = 25.0          # eje del pasillo principal
X_PASILLO = 40.0          # eje del pasillo central
Y_SERVICIOS = 45.0        # eje del pasillo de servicios (baños/ascensor/escalera)
RUMBO_NORTE = 12.0        # el eje +Y del plano apunta 12 grados al este del norte
MEDIO_ANCHO_LOCAL = 5.0
COSTE_ASCENSOR = 18.0
COSTE_ESCALERA = 26.0

pisos, establecimientos, nodos, aristas, qrs = [], [], [], [], []


def slug(texto):
    base = unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode()
    return "".join(c if c.isalnum() else "_" for c in base.lower()).strip("_")


def punto(x, y):
    return {"x": round(float(x), 2), "y": round(float(y), 2)}


def nodo(id_, piso, x, y, tipo="PASILLO", nombre=None):
    nodos.append({"id": id_, "pisoId": piso, "posicion": punto(x, y), "tipo": tipo,
                  "nombre": nombre})
    return id_


def arista(a, b, tipo="PASILLO", coste=0.0):
    aristas.append({"desdeId": a, "hastaId": b, "tipo": tipo,
                    "bidireccional": True, "costeExtraMetros": coste})


def establecimiento(nombre, categoria, piso, x, y, nodo_destino, descripcion=None):
    establecimientos.append({
        "id": f"est_{slug(nombre)}", "nombre": nombre, "categoria": categoria,
        "pisoId": piso, "posicion": punto(x, y), "nodoDestinoId": nodo_destino,
        "activo": True, "descripcion": descripcion,
    })


# Locales de cada piso: (nombre, categoria, lado)  ->  lado "N" (norte) o "S" (sur)
LOCALES = {
    "p1": [
        # x centro se asigna por orden dentro de cada lado
        ("Moda Lumen", "TIENDA", "N"),
        ("Tecno Órbita", "TIENDA", "N"),
        ("Deportes Vértice", "TIENDA", "N"),
        ("Joyería Solsticio", "TIENDA", "N"),
        ("Óptica Cenit", "TIENDA", "N"),
        ("Café Aurora", "RESTAURANTE", "N"),
        ("Zapatos Nómada", "TIENDA", "S"),
        ("Libros Arena", "TIENDA", "S"),
        ("Farmacia Aurora", "FARMACIA", "S"),
        ("Cajero Banco Norte", "CAJERO", "S"),
        ("Perfumería Luna", "TIENDA", "S"),
        ("Pizzería Brasa", "RESTAURANTE", "S"),
    ],
    "p2": [
        ("Juguetes Cometa", "TIENDA", "N"),
        ("Hogar Ámbar", "TIENDA", "N"),
        ("Mascotas Patitas", "TIENDA", "N"),
        ("Sushi Koi", "RESTAURANTE", "N"),
        ("Heladería Nube", "RESTAURANTE", "S"),
        ("Tacos El Faro", "RESTAURANTE", "S"),
        ("Farmacia Central", "FARMACIA", "S"),
        ("Cajero Banco Sur", "CAJERO", "S"),
    ],
}
X_CENTROS = {
    ("p1", "N"): [10.0, 22.0, 32.0, 48.0, 58.0, 70.0],
    ("p1", "S"): [10.0, 22.0, 32.0, 48.0, 58.0, 70.0],
    ("p2", "N"): [12.0, 26.0, 52.0, 66.0],
    ("p2", "S"): [12.0, 26.0, 52.0, 66.0],
}


def construir_piso(piso_id, nivel, nombre_piso):
    prefijo = piso_id.upper()
    locales_rect, accesos = [], []

    # --- locales y nodos de acceso ---
    por_lado = {"N": [], "S": []}
    for nombre, categoria, lado in LOCALES[piso_id]:
        por_lado[lado].append((nombre, categoria))

    for lado, lista in por_lado.items():
        centros = X_CENTROS[(piso_id, lado)]
        assert len(lista) <= len(centros), f"faltan huecos en {piso_id}/{lado}"
        for indice, (nombre, categoria) in enumerate(lista):
            cx = centros[indice]
            ancho = 2 * MEDIO_ANCHO_LOCAL if piso_id == "p1" else 12.0
            if lado == "N":
                rect_y, puerta_y, centro_y = 28.0, 28.0, 36.0
            else:
                rect_y, puerta_y, centro_y = 6.0, 22.0, 14.0
            locales_rect.append({"x": round(cx - ancho / 2, 2), "y": rect_y,
                                 "ancho": ancho, "alto": 16.0, "etiqueta": nombre})
            id_acceso = f"{prefijo}_A_{slug(nombre)[:14]}"
            nodo(id_acceso, piso_id, cx, puerta_y, "ACCESO", f"Puerta de {nombre}")
            establecimiento(nombre, categoria, piso_id, cx, centro_y, id_acceso)
            accesos.append((cx, id_acceso))

    # --- pasillo principal: un nodo por cada x que haga falta ---
    xs_principal = sorted({cx for cx, _ in accesos} | {6.0, X_PASILLO, 76.0})
    nodos_principal = {}
    for cx in xs_principal:
        id_nodo = f"{prefijo}_P{int(cx):02d}"
        nodo(id_nodo, piso_id, cx, Y_PASILLO, "PASILLO")
        nodos_principal[cx] = id_nodo
    for a, b in zip(xs_principal, xs_principal[1:]):
        arista(nodos_principal[a], nodos_principal[b])
    for cx, id_acceso in accesos:
        arista(id_acceso, nodos_principal[cx])

    # --- pasillo central (vertical) ---
    id_sur = f"{prefijo}_C05"
    id_medio_sur = f"{prefijo}_C14"
    id_medio_norte = f"{prefijo}_C36"
    id_servicios = f"{prefijo}_C45"
    nodo(id_sur, piso_id, X_PASILLO, 5.0, "PASILLO")
    nodo(id_medio_sur, piso_id, X_PASILLO, 14.0, "PASILLO")
    nodo(id_medio_norte, piso_id, X_PASILLO, 36.0, "PASILLO")
    nodo(id_servicios, piso_id, X_PASILLO, Y_SERVICIOS, "PASILLO")
    arista(id_sur, id_medio_sur)
    arista(id_medio_sur, nodos_principal[X_PASILLO])
    arista(nodos_principal[X_PASILLO], id_medio_norte)
    arista(id_medio_norte, id_servicios)

    # --- pasillo de servicios: baños, ascensor, escalera ---
    id_bano = f"{prefijo}_A_bano"
    id_ascensor = f"{prefijo}_A_ascensor"
    id_escalera = f"{prefijo}_A_escalera"
    nodo(id_bano, piso_id, 30.0, Y_SERVICIOS, "ACCESO", "Baños")
    nodo(id_ascensor, piso_id, X_PASILLO, 46.5, "ASCENSOR", "Ascensor")
    nodo(id_escalera, piso_id, 50.0, Y_SERVICIOS, "ESCALERA", "Escalera")
    arista(id_bano, id_servicios)
    arista(id_servicios, id_ascensor)
    arista(id_servicios, id_escalera)
    establecimiento(f"Baños {nombre_piso}", "BANO", piso_id, 28.0, 47.0, id_bano,
                    "Baños de hombres, mujeres y accesible")
    establecimiento(f"Ascensor {nombre_piso}", "ASCENSOR", piso_id, X_PASILLO, 47.5, id_ascensor)
    establecimiento(f"Escalera {nombre_piso}", "ESCALERA", piso_id, 50.0, 47.5, id_escalera)

    pasillos = [
        {"x": 5.0, "y": 22.0, "ancho": 71.0, "alto": 6.0, "etiqueta": "Pasillo principal"},
        {"x": 37.0, "y": 4.0, "ancho": 6.0, "alto": 43.0, "etiqueta": "Pasillo central"},
        {"x": 28.0, "y": 43.0, "ancho": 24.0, "alto": 4.0, "etiqueta": "Pasillo de servicios"},
    ]
    pisos.append({
        "id": piso_id, "nivel": nivel, "nombre": nombre_piso,
        "anchoMetros": ANCHO, "altoMetros": ALTO, "rumboNorteGrados": RUMBO_NORTE,
        "planoImagen": None, "pasillos": pasillos, "locales": locales_rect,
    })
    return {"sur": id_sur, "principal": nodos_principal, "ascensor": id_ascensor,
            "escalera": id_escalera, "cruce": nodos_principal[X_PASILLO]}


p1 = construir_piso("p1", 1, "Piso 1")
p2 = construir_piso("p2", 2, "Piso 2")

# --- entradas (solo en el piso 1) ---
nodo("P1_ENT_SUR", "p1", X_PASILLO, 2.0, "ENTRADA", "Entrada Sur")
arista("P1_ENT_SUR", p1["sur"])
establecimiento("Entrada Sur", "ENTRADA", "p1", X_PASILLO, 1.0, "P1_ENT_SUR")
nodo("P1_ENT_ESTE", "p1", 79.0, Y_PASILLO, "ENTRADA", "Entrada Este")
arista("P1_ENT_ESTE", p1["principal"][76.0])
establecimiento("Entrada Este", "ENTRADA", "p1", 79.5, Y_PASILLO, "P1_ENT_ESTE")

# --- enlaces verticales ---
arista(p1["ascensor"], p2["ascensor"], "ASCENSOR", COSTE_ASCENSOR)
arista(p1["escalera"], p2["escalera"], "ESCALERA", COSTE_ESCALERA)

# --- puntos QR de posicionamiento ---
def qr(codigo, piso, nodo_id, rumbo, descripcion):
    origen = next(n for n in nodos if n["id"] == nodo_id)
    qrs.append({"codigo": codigo, "plazaId": "plaza_aurora", "pisoId": piso,
                "nodoId": nodo_id, "posicion": origen["posicion"],
                "rumboGrados": rumbo, "descripcion": descripcion})


qr("QR_001", "p1", "P1_ENT_SUR", 0.0, "Entrada Sur - mirando al interior")
qr("QR_002", "p1", "P1_ENT_ESTE", 270.0, "Entrada Este - mirando al interior")
qr("QR_003", "p1", p1["cruce"], 0.0, "Piso 1 - cruce del pasillo central")
qr("QR_004", "p1", p1["ascensor"], 180.0, "Piso 1 - junto al ascensor")
qr("QR_005", "p2", p2["ascensor"], 180.0, "Piso 2 - junto al ascensor")
qr("QR_006", "p2", p2["cruce"], 0.0, "Piso 2 - cruce del pasillo central")

plaza = {
    "id": "plaza_aurora",
    "nombre": "Plaza Aurora",
    "ciudad": "Ciudad Demo",
    "ficticia": True,
    "pisos": pisos,
    "establecimientos": establecimientos,
    "nodos": nodos,
    "aristas": aristas,
    "puntosQr": qrs,
}

destino = os.path.join(os.path.dirname(__file__), "..", "nucleo", "src", "main",
                       "resources", "plazas", "plaza_aurora.json")
os.makedirs(os.path.dirname(destino), exist_ok=True)
with open(destino, "w", encoding="utf-8") as f:
    json.dump(plaza, f, ensure_ascii=False, indent=2)
    f.write("\n")

comerciales = [e for e in establecimientos
               if e["categoria"] in ("TIENDA", "RESTAURANTE", "FARMACIA", "CAJERO")]
print(f"{os.path.normpath(destino)}")
print(f"  pisos={len(pisos)} establecimientos={len(establecimientos)} "
      f"(comerciales={len(comerciales)}) nodos={len(nodos)} aristas={len(aristas)} qr={len(qrs)}")
