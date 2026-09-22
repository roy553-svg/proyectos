#!/usr/bin/env python3
"""
Verificacion empirica de la privacidad de sub-transaccion en Canton.

Interroga la API JSON de CADA nodo participante por separado y muestra que
contratos tiene realmente en su almacen local. No es una simulacion: son los
cuatro nodos respondiendo sobre su propio estado.

Resultado esperado tras ejecutar la demo:

  * Alfa  ve su propio libro y el recibo bilateral. Nada de Beta.
  * Beta  ve su propio libro y el recibo bilateral. Nada de Alfa, pese a haber
          sido su CONTRAPARTE en la liquidacion.
  * Gamma ve unicamente lo suyo. No tiene constancia de que la operacion
          entre Alfa y Beta haya existido siquiera.
  * Banco Central ve los tres libros, por ser observador declarado.

Uso:
    python3 scripts/verificar-privacidad.py
"""

import collections
import json
import sys
import urllib.error
import urllib.request

NODOS = {
    "bancoAlfa": 5013,
    "bancoBeta": 5023,
    "bancoGamma": 5033,
    "bancoCentral": 5043,
}

ETIQUETAS = {
    "bancoAlfa": "Banco Alfa",
    "bancoBeta": "Banco Beta",
    "bancoGamma": "Banco Gamma",
    "bancoCentral": "Banco Central",
}

# Sin proxy: los nodos son locales.
ABRIDOR = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def llamar(puerto, ruta, cuerpo=None):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    peticion = urllib.request.Request(
        f"http://localhost:{puerto}{ruta}",
        data=datos,
        headers={"Content-Type": "application/json"},
        method="POST" if datos else "GET",
    )
    return json.load(ABRIDOR.open(peticion, timeout=30))


def contratos_activos(puerto, party):
    """Contratos que ESTE nodo tiene para ESTA party."""
    offset = llamar(puerto, "/v2/state/ledger-end")["offset"]
    filtro = {
        "cumulative": [
            {"identifierFilter": {"WildcardFilter": {"value": {"includeCreatedEventBlob": False}}}}
        ]
    }
    return llamar(
        puerto,
        "/v2/state/active-contracts",
        {
            "filter": {"filtersByParty": {party: filtro}},
            "verbose": True,
            "activeAtOffset": offset,
        },
    )


def main():
    try:
        with open("canton/parties.json") as fichero:
            parties = json.load(fichero)
    except FileNotFoundError:
        sys.exit(
            "No se encuentra canton/parties.json.\n"
            "Arranca antes la red:  ./scripts/levantar-red.sh"
        )

    print()
    print("=" * 78)
    print(" PRIVACIDAD DE SUB-TRANSACCION - lo que cada nodo tiene REALMENTE")
    print("=" * 78)
    print(f"{'NODO':<16}{'CONTRATOS':<11}{'PLANTILLAS'}")
    print("-" * 78)

    libros_por_nodo = {}
    for nodo, puerto in NODOS.items():
        party = parties[nodo]
        try:
            activos = contratos_activos(puerto, party)
        except urllib.error.URLError as error:
            print(f"{ETIQUETAS[nodo]:<16}(sin respuesta en el puerto {puerto}: {error.reason})")
            continue

        plantillas = collections.Counter()
        libros = collections.Counter()
        for entrada in activos:
            evento = entrada["contractEntry"]["JsActiveContract"]["createdEvent"]
            plantillas[evento["templateId"].split(":")[-1]] += 1
            propietario = evento["createArgument"].get("bank")
            if propietario:
                libros[propietario.split("::")[0]] += 1

        libros_por_nodo[nodo] = set(libros)
        desglose = ", ".join(f"{k} x{v}" for k, v in sorted(plantillas.items()))
        print(f"{ETIQUETAS[nodo]:<16}{len(activos):<11}{desglose}")
        if libros:
            print(f"{'':<27}libros visibles: {', '.join(sorted(libros))}")

    print("-" * 78)

    # Veredicto automatico sobre la garantia central.
    fallos = []
    if libros_por_nodo.get("bancoBeta", set()) - {"BancoBeta"}:
        fallos.append("Beta ve libros ajenos")
    if libros_por_nodo.get("bancoAlfa", set()) - {"BancoAlfa"}:
        fallos.append("Alfa ve libros ajenos")
    if libros_por_nodo.get("bancoGamma", set()) - {"BancoGamma"}:
        fallos.append("Gamma ve libros ajenos")
    if len(libros_por_nodo.get("bancoCentral", set())) < 3:
        fallos.append("el regulador no ve los tres libros")

    if fallos:
        print(" VEREDICTO: FALLO -> " + "; ".join(fallos))
        print("=" * 78)
        return 1

    print(" VEREDICTO: cada banco ve exclusivamente su propio libro.")
    print("            Beta fue contraparte de Alfa y aun asi no tiene ni un")
    print("            solo contrato del libro de Alfa en su nodo.")
    print("            El regulador ve los tres libros por designacion explicita.")
    print("=" * 78)
    print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
