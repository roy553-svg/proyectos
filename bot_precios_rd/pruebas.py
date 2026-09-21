#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pruebas del Bot de Precios RD (no requieren token real ni internet).
Ejecutar:  python pruebas.py
Verifica: catálogos, motor de 3 capas, parsers de tienda, caché, rotación y formato HTML.
"""
import json
import os
import re
import sys
from pathlib import Path

os.environ.setdefault("TELEGRAM_BOT_TOKEN", "000:TOKEN-DE-PRUEBA")
os.environ.setdefault("TELEGRAM_CHAT_ID", "123456789")
os.environ["ENABLE_SCRAPING"] = "false"
os.environ["DATA_DIR"] = str(Path(__file__).resolve().parent / "data" / "pruebas")
sys.path.insert(0, str(Path(__file__).resolve().parent))

import bot as B  # noqa: E402

VERBOSO = "-v" in sys.argv or "--verbose" in sys.argv


class FakeResp:
    def __init__(self, payload=None, text="", status=200):
        self._p, self.text, self.status_code = payload, text, status

    def json(self):
        return self._p

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError(self.status_code)


class FakeSession:
    """Sesión HTTP simulada: permite probar el scraping sin salir a internet."""

    def __init__(self, resp):
        self.resp, self.calls = resp, []

    def get(self, url, **kw):
        self.calls.append((url, kw))
        return self.resp


def test_catalogos():
    assert len(B.CATALOGO_FARMACIA) == 50
    assert len(B.CATALOGO_CANASTA) == 50
    assert len({p.id for p in B.CATALOGO_FARMACIA}) == 50
    assert len({p.id for p in B.CATALOGO_CANASTA}) == 50
    assert len(B.FARMACIAS) == 10 and len(B.SUPERMERCADOS) == 10
    print("✔ Catálogos: 50 productos de farmacia, 50 de canasta, 10 + 10 establecimientos")


def test_parseo_precios():
    assert B.parse_precio("RD$ 1,234.56") == 1234.56
    assert B.parse_precio("1.234,56") == 1234.56
    assert B.parse_precio("DOP 95.00") == 95.0
    assert B.parse_precio("sin precio") is None
    print("✔ Parseo de montos en formato dominicano, europeo y con moneda")



def test_reportes():
    for tipo in ("farmacia", "canasta"):
        rep = B.generar_reporte(tipo)
        assert len(rep.comparaciones) == B.PRODUCTS_PER_REPORT
        ids = [c.product.id for c in rep.comparaciones]
        assert len(set(ids)) == len(ids), "productos repetidos en el mismo reporte"
        for c in rep.comparaciones:
            assert len(c.quotes) == 10, "faltan cotizaciones"
            assert c.quotes == sorted(c.quotes, key=lambda q: q.price)
            assert c.mejor.price > 0 and c.ahorro >= 0
        msg = B.formatear_reporte(rep)
        abiertas = re.findall(r"<(\w+)>", msg)
        cerradas = re.findall(r"</(\w+)>", msg)
        assert sorted(abiertas) == sorted(cerradas), "HTML desbalanceado"
        for bloque in B.trocear(msg):
            assert len(bloque) <= B.TELEGRAM_LIMITE
        if VERBOSO:
            print("\n" + msg + "\n")
        print(f"✔ Reporte '{tipo}': 5 productos x 10 tiendas, "
              f"{len(msg)} caracteres, {len(B.trocear(msg))} mensaje(s)")


def test_contingencia_determinista():
    semana = B.semana_iso(B.now_rd())
    p, t = B.CATALOGO_FARMACIA[0], B.FARMACIAS[0]
    a = B.precio_contingencia(t, p, semana, p.reference_price)
    assert a == B.precio_contingencia(t, p, semana, p.reference_price)
    assert a != B.precio_contingencia(B.FARMACIAS[1], p, semana, p.reference_price)
    assert a != B.precio_contingencia(t, p, "2000-W01", p.reference_price)
    print("✔ Contingencia: estable dentro de la semana, distinta por tienda y por semana")


def test_rotacion():
    vistos = []
    for _ in range(B.ROTATION_COOLDOWN_WEEKS):
        vistos += [x.id for x in B.elegir_productos(B.CATALOGO_CANASTA, 5, "prueba-rotacion")]
    assert len(set(vistos)) == len(vistos), "la rotación repitió productos"
    print(f"✔ Rotación: {B.ROTATION_COOLDOWN_WEEKS} semanas seguidas sin repetir producto")


def test_parser_vtex():
    prod = B.CATALOGO_FARMACIA[0]
    vtex = [
        {"productName": "Vitamina C 1000 mg 30 tabletas", "link": "https://x.do/vitac/p",
         "items": [{"sellers": [{"commertialOffer": {"Price": 340.0, "IsAvailable": True}}]}]},
        {"productName": "Acetaminofen 500 mg caja 20 tabletas", "linkText": "acetaminofen-500",
         "items": [{"sellers": [{"commertialOffer": {"Price": 88.5, "IsAvailable": True}}]}]},
        {"productName": "Acetaminofen 500 mg display 12 cajas",
         "items": [{"sellers": [{"commertialOffer": {"Price": 990.0, "IsAvailable": True}}]}]},
    ]
    h = B.VtexSource("https://farmaciascarol.com").buscar(prod, FakeSession(FakeResp(payload=vtex)))
    assert h and h.price == 88.5, "no eligió el producto correcto"
    assert h.url == "https://farmaciascarol.com/acetaminofen-500/p"
    agotado = [{"productName": "Acetaminofen 500 mg 20 tabletas",
                "items": [{"sellers": [{"commertialOffer": {"Price": 80, "IsAvailable": False}}]}]}]
    assert B.VtexSource("https://x.do").buscar(prod, FakeSession(FakeResp(payload=agotado))) is None
    try:
        B.VtexSource("https://x.do").buscar(prod, FakeSession(FakeResp(payload=[], status=503)))
        raise AssertionError("una web caída debe lanzar excepción")
    except RuntimeError:
        pass
    print("✔ VTEX: descarta multipacks y agotados, arma el enlace y señala la web caída")


def test_parser_html():
    prod = B.CATALOGO_FARMACIA[0]
    html = """<ul class="products">
     <li class="product"><a href="/producto/acetaminofen-500">
       <h2 class="woocommerce-loop-product__title">Acetaminofén 500 mg (20 tabletas)</h2>
       <span class="price"><span class="woocommerce-Price-amount">RD&#36;&nbsp;79.95</span></span></a></li>
     <li class="product"><a href="/producto/loratadina">
       <h2 class="woocommerce-loop-product__title">Loratadina 10 mg</h2>
       <span class="price">RD$ 120.00</span></a></li></ul>"""
    h = B.HtmlSource("https://farmavaluerd.com").buscar(prod, FakeSession(FakeResp(text=html)))
    assert h and h.price == 79.95
    assert h.url == "https://farmavaluerd.com/producto/acetaminofen-500"
    print("✔ WooCommerce/HTML: extrae nombre, precio y enlace del producto correcto")


def test_cache():
    prod = B.CATALOGO_FARMACIA[0]
    vtex = [{"productName": "Acetaminofen 500 mg caja 20 tabletas", "linkText": "a",
             "items": [{"sellers": [{"commertialOffer": {"Price": 88.5, "IsAvailable": True}}]}]}]
    tienda = B.Store("prueba-cache", "Tienda Test", "🧪", "https://x.do", 1.0,
                     B.VtexSource("https://x.do"))
    ses = FakeSession(FakeResp(payload=vtex))
    q1 = B.cotizar(tienda, prod, "2026-W39", ses)
    q2 = B.cotizar(tienda, prod, "2026-W39", ses)
    assert q1.origin == q2.origin == "online" and q1.price == q2.price == 88.5
    assert len(ses.calls) == 1, "la caché no evitó la segunda llamada HTTP"
    print("✔ Caché: dos cotizaciones con una sola llamada a la web")


def test_referencia_publica():
    prod = B.CATALOGO_FARMACIA[0]
    ref = B.ReferenceData("https://ejemplo.gob.do/canasta.csv")
    ref.refrescar(FakeSession(FakeResp(text="producto,precio\nAcetaminofén 500 mg,101.00\n")),
                  forzar=True)
    assert ref.base(prod) == (101.0, True)
    ref2 = B.ReferenceData("https://ejemplo.gob.do/canasta.json")
    ref2.refrescar(FakeSession(FakeResp(text=json.dumps({"Acetaminofén 500 mg": "RD$ 99.00"}))),
                   forzar=True)
    assert ref2.base(prod) == (99.0, True)
    ref3 = B.ReferenceData("u")
    ref3.tabla = {prod.id: 99999.0}
    assert ref3.base(prod) == (prod.reference_price, False), "no descartó un precio absurdo"
    print("✔ Referencia pública: acepta CSV y JSON, ignora valores fuera de rango")


def test_cortacircuitos():
    class Rota(B.PriceSource):
        def buscar(self, product, session):
            raise RuntimeError("web caída")

    B.reiniciar_cortacircuitos()
    tienda = B.Store("prueba-rota", "Tienda Caída", "💥", "https://x.do", 1.0, Rota())
    for p in B.CATALOGO_CANASTA[:5]:
        q = B.cotizar(tienda, p, "2026-W39", FakeSession(FakeResp(payload=[])))
        assert q.origin in ("estimado", "referencia") and q.price > 0
    assert not B._tienda_disponible(tienda), "el cortacircuitos no se activó"
    print("✔ Cortacircuitos: tras 2 fallos deja de consultar la web y usa contingencia")


if __name__ == "__main__":
    print("=" * 70)
    print("PRUEBAS DEL BOT DE PRECIOS RD")
    print("=" * 70)
    for prueba in (test_catalogos, test_parseo_precios, test_reportes,
                   test_contingencia_determinista, test_rotacion, test_parser_vtex,
                   test_parser_html, test_cache, test_referencia_publica, test_cortacircuitos):
        prueba()
    print("=" * 70)
    print("✅ TODAS LAS PRUEBAS PASARON")
