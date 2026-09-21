#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
====================================================================================
 BOT DE PRECIOS RD  |  Farmacias (jueves) y Canasta Familiar (viernes)
====================================================================================
Rutina automática semanal para Telegram, zona horaria 'America/Santo_Domingo':

  * JUEVES  08:00 AM  -> 5 medicamentos/artículos al azar comparados en 10 farmacias.
  * VIERNES 08:00 AM  -> 5 productos de la canasta básica comparados en 10 supermercados.

Arquitectura de precios en 3 capas (nunca falla un envío):
  1) SCRAPING / API  : catálogos e-commerce (VTEX: Sirena, Jumbo, Nacional, Bravo,
                       Plaza Lama, Farmacias Carol | WooCommerce: FarmaValue).
  2) REFERENCIA      : tabla de precios de referencia pública (Pro-Consumidor RD),
                       actualizable por URL sin tocar el código.
  3) CONTINGENCIA    : motor de fluctuación de mercado determinista por semana,
                       usado si una web está caída, bloquea la IP o cambia su HTML.

Comandos: /start  /jueves  /viernes  /estado  /ayuda
Autor: generado para uso en producción. Licencia de uso libre del propietario del bot.
"""

from __future__ import annotations

import csv
import hashlib
import html
import io
import json
import logging
import os
import random
import re
import signal
import sys
import threading
import time
import unicodedata
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any, Optional, Sequence
from zoneinfo import ZoneInfo

import requests
import telebot
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
from bs4 import BeautifulSoup
from dotenv import load_dotenv
from requests.adapters import HTTPAdapter
from telebot.apihelper import ApiTelegramException
from urllib3.util.retry import Retry

# ==================================================================================
# 1. CONFIGURACIÓN
# ==================================================================================

load_dotenv()

TZ_RD = ZoneInfo("America/Santo_Domingo")          # GMT-4 todo el año (RD no usa DST)

TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN", "").strip()
TELEGRAM_CHAT_ID = os.getenv("TELEGRAM_CHAT_ID", "").strip()

# Chats autorizados a ejecutar comandos (además del chat principal).
ALLOWED_CHAT_IDS = {
    c.strip() for c in os.getenv("ALLOWED_CHAT_IDS", "").split(",") if c.strip()
}
if TELEGRAM_CHAT_ID:
    ALLOWED_CHAT_IDS.add(TELEGRAM_CHAT_ID)

# Horario de la rutina (editable sin tocar el código).
PHARMACY_DAY = os.getenv("PHARMACY_CRON_DAY", "thu")        # jueves
SUPERMARKET_DAY = os.getenv("SUPERMARKET_CRON_DAY", "fri")  # viernes
REPORT_HOUR = int(os.getenv("REPORT_HOUR", "8"))
REPORT_MINUTE = int(os.getenv("REPORT_MINUTE", "0"))

PRODUCTS_PER_REPORT = int(os.getenv("PRODUCTS_PER_REPORT", "5"))

# Capa 1: scraping. Se puede apagar por completo (queda referencia + contingencia).
ENABLE_SCRAPING = os.getenv("ENABLE_SCRAPING", "true").lower() in ("1", "true", "yes", "si", "sí")
HTTP_TIMEOUT = float(os.getenv("HTTP_TIMEOUT", "12"))
HTTP_MAX_WORKERS = int(os.getenv("HTTP_MAX_WORKERS", "6"))
CACHE_TTL_HOURS = float(os.getenv("CACHE_TTL_HOURS", "12"))

# Capa 2: datos de referencia pública (Pro-Consumidor RD u otra fuente).
# Acepta un JSON {"slug-producto": 123.45} o un CSV con columnas producto,precio.
PROCONSUMIDOR_DATA_URL = os.getenv("PROCONSUMIDOR_DATA_URL", "").strip()
REFERENCE_TTL_HOURS = float(os.getenv("REFERENCE_TTL_HOURS", "72"))

# Semanas que un producto queda "en descanso" antes de poder repetirse.
ROTATION_COOLDOWN_WEEKS = int(os.getenv("ROTATION_COOLDOWN_WEEKS", "4"))

DATA_DIR = Path(os.getenv("DATA_DIR", str(Path(__file__).resolve().parent / "data")))
DATA_DIR.mkdir(parents=True, exist_ok=True)
CACHE_FILE = DATA_DIR / "price_cache.json"
HISTORY_FILE = DATA_DIR / "history.json"

SEND_ON_STARTUP = os.getenv("SEND_ON_STARTUP", "").strip().lower()  # "", "jueves", "viernes"

LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
logging.basicConfig(
    level=getattr(logging, LOG_LEVEL, logging.INFO),
    format="%(asctime)s | %(levelname)-7s | %(name)s | %(message)s",
)
logging.getLogger("apscheduler").setLevel(logging.WARNING)
logging.getLogger("urllib3").setLevel(logging.ERROR)
log = logging.getLogger("bot-precios-rd")

DIAS_ES = {
    0: "lunes", 1: "martes", 2: "miércoles", 3: "jueves",
    4: "viernes", 5: "sábado", 6: "domingo",
}
MESES_ES = {
    1: "enero", 2: "febrero", 3: "marzo", 4: "abril", 5: "mayo", 6: "junio",
    7: "julio", 8: "agosto", 9: "septiembre", 10: "octubre", 11: "noviembre", 12: "diciembre",
}

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
]

# ==================================================================================
# 2. UTILIDADES
# ==================================================================================


def now_rd() -> datetime:
    """Hora actual en República Dominicana."""
    return datetime.now(TZ_RD)


def fecha_larga(dt: datetime) -> str:
    return f"{DIAS_ES[dt.weekday()]} {dt.day} de {MESES_ES[dt.month]} de {dt.year}"


def hora_corta(dt: datetime) -> str:
    h12 = dt.strftime("%I:%M %p").lstrip("0")
    return f"{h12} (AST / GMT-4)"


def semana_iso(dt: datetime) -> str:
    y, w, _ = dt.isocalendar()
    return f"{y}-W{w:02d}"


def slug(texto: str) -> str:
    base = unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode()
    base = re.sub(r"[^a-zA-Z0-9]+", "-", base).strip("-").lower()
    return base[:60]


def normalizar(texto: str) -> str:
    base = unicodedata.normalize("NFKD", texto or "").encode("ascii", "ignore").decode()
    return re.sub(r"\s+", " ", base.lower()).strip()


def rd(monto: float) -> str:
    """Formatea un monto en Pesos Dominicanos: RD$ 1,234.56"""
    return f"RD$ {monto:,.2f}"


def esc(texto: str) -> str:
    """Escapa texto para el parse_mode HTML de Telegram."""
    return html.escape(str(texto), quote=False)


def seeded_random(*partes: Any) -> random.Random:
    """RNG determinista: la misma semana siempre produce el mismo precio."""
    semilla = hashlib.sha256("|".join(str(p) for p in partes).encode("utf-8")).hexdigest()
    return random.Random(int(semilla[:16], 16))


def naturalizar_precio(valor: float) -> float:
    """Ajusta el precio a terminaciones comerciales realistas del mercado dominicano."""
    if valor <= 0:
        return 0.0
    if valor < 50:
        return round(valor * 2) / 2                      # 23.50
    if valor < 500:
        return max(1.0, round(valor) - 0.05)             # 94.95
    return max(1.0, round(valor / 5) * 5 - 0.05)         # 1,249.95


PRECIO_RE = re.compile(r"(\d[\d.,]*)")


def parse_precio(texto: str) -> Optional[float]:
    """Extrae un monto de un texto tipo 'RD$ 1,234.56' / '1.234,56' / 'DOP 95.00'."""
    if not texto:
        return None
    limpio = texto.replace("\xa0", " ").strip()
    m = PRECIO_RE.search(limpio)
    if not m:
        return None
    crudo = m.group(1)
    if "," in crudo and "." in crudo:
        if crudo.rfind(",") > crudo.rfind("."):          # 1.234,56 (europeo)
            crudo = crudo.replace(".", "").replace(",", ".")
        else:                                            # 1,234.56 (RD/EEUU)
            crudo = crudo.replace(",", "")
    elif "," in crudo:
        entero, _, dec = crudo.rpartition(",")
        crudo = f"{entero.replace(',', '')}.{dec}" if len(dec) == 2 else crudo.replace(",", "")
    try:
        valor = float(crudo)
    except ValueError:
        return None
    return valor if valor > 0 else None


# ==================================================================================
# 3. CATÁLOGOS (50 productos de farmacia + 50 de canasta familiar básica)
#    (nombre, presentación, precio de referencia RD$, término de búsqueda)
# ==================================================================================

FARMACIA_RAW: list[tuple[str, str, float, str]] = [
    ("Acetaminofén 500 mg",            "Caja 20 tabletas",        95.0,  "acetaminofen 500 mg"),
    ("Ibuprofeno 400 mg",              "Caja 20 tabletas",       140.0,  "ibuprofeno 400 mg"),
    ("Omeprazol 20 mg",                "Caja 14 cápsulas",       210.0,  "omeprazol 20 mg"),
    ("Losartán potásico 50 mg",        "Caja 30 tabletas",       290.0,  "losartan potasico 50 mg"),
    ("Amoxicilina 500 mg",             "Caja 15 cápsulas",       320.0,  "amoxicilina 500 mg"),
    ("Metformina 850 mg",              "Caja 30 tabletas",       260.0,  "metformina 850 mg"),
    ("Enalapril 10 mg",                "Caja 30 tabletas",       185.0,  "enalapril 10 mg"),
    ("Amlodipino 5 mg",                "Caja 30 tabletas",       240.0,  "amlodipino 5 mg"),
    ("Atorvastatina 20 mg",            "Caja 30 tabletas",       420.0,  "atorvastatina 20 mg"),
    ("Azitromicina 500 mg",            "Caja 3 tabletas",        380.0,  "azitromicina 500 mg"),
    ("Loratadina 10 mg",               "Caja 10 tabletas",       120.0,  "loratadina 10 mg"),
    ("Cetirizina 10 mg",               "Caja 10 tabletas",       135.0,  "cetirizina 10 mg"),
    ("Diclofenaco sódico 50 mg",       "Caja 20 tabletas",       150.0,  "diclofenaco sodico 50 mg"),
    ("Naproxeno sódico 550 mg",        "Caja 20 tabletas",       195.0,  "naproxeno 550 mg"),
    ("Ciprofloxacina 500 mg",          "Caja 14 tabletas",       330.0,  "ciprofloxacina 500 mg"),
    ("Levotiroxina 50 mcg",            "Caja 30 tabletas",       275.0,  "levotiroxina 50 mcg"),
    ("Glibenclamida 5 mg",             "Caja 30 tabletas",       165.0,  "glibenclamida 5 mg"),
    ("Salbutamol inhalador 100 mcg",   "Inhalador 200 dosis",    690.0,  "salbutamol inhalador"),
    ("Prednisona 20 mg",               "Caja 10 tabletas",       175.0,  "prednisona 20 mg"),
    ("Dexametasona 4 mg",              "Caja 10 tabletas",       160.0,  "dexametasona 4 mg"),
    ("Famotidina 40 mg",               "Caja 20 tabletas",       230.0,  "famotidina 40 mg"),
    ("Metronidazol 500 mg",            "Caja 20 tabletas",       185.0,  "metronidazol 500 mg"),
    ("Aspirina 81 mg (protección)",    "Caja 30 tabletas",       145.0,  "aspirina 81 mg"),
    ("Clopidogrel 75 mg",              "Caja 30 tabletas",       520.0,  "clopidogrel 75 mg"),
    ("Simvastatina 20 mg",             "Caja 30 tabletas",       310.0,  "simvastatina 20 mg"),
    ("Insulina NPH 100 UI/ml",         "Frasco 10 ml",          1150.0,  "insulina nph"),
    ("Ambroxol jarabe",                "Frasco 120 ml",          230.0,  "ambroxol jarabe"),
    ("Suero oral rehidratante",        "Botella 500 ml",         190.0,  "suero oral electrolitos"),
    ("Vitamina C 1000 mg",             "Frasco 30 tabletas",     340.0,  "vitamina c 1000 mg"),
    ("Complejo B",                     "Frasco 30 tabletas",     285.0,  "complejo b tabletas"),
    ("Calcio + Vitamina D",            "Frasco 60 tabletas",     480.0,  "calcio vitamina d"),
    ("Sulfato ferroso (hierro)",       "Caja 30 tabletas",       210.0,  "sulfato ferroso hierro"),
    ("Ácido fólico 5 mg",              "Caja 30 tabletas",       130.0,  "acido folico 5 mg"),
    ("Multivitamínico prenatal",       "Frasco 30 cápsulas",     560.0,  "vitaminas prenatales"),
    ("Alcohol isopropílico 70%",       "Botella 500 ml",          95.0,  "alcohol isopropilico 70"),
    ("Agua oxigenada",                 "Botella 500 ml",          75.0,  "agua oxigenada"),
    ("Algodón estéril",                "Paquete 100 g",           85.0,  "algodon esteril"),
    ("Gasas estériles 10x10",          "Paquete 10 unidades",    110.0,  "gasas esteriles"),
    ("Curitas adhesivas",              "Caja 30 unidades",       135.0,  "curitas apositos"),
    ("Termómetro digital",             "1 unidad",               420.0,  "termometro digital"),
    ("Mascarillas quirúrgicas",        "Caja 50 unidades",       260.0,  "mascarillas quirurgicas"),
    ("Guantes de látex",               "Caja 100 unidades",      590.0,  "guantes de latex"),
    ("Gel antibacterial",              "Envase 250 ml",          145.0,  "gel antibacterial"),
    ("Tiras reactivas de glucosa",     "Caja 50 tiras",         1450.0,  "tiras reactivas glucosa"),
    ("Tensiómetro digital de brazo",   "1 unidad",              2400.0,  "tensiometro digital brazo"),
    ("Preservativos",                  "Caja 3 unidades",        180.0,  "preservativos condones"),
    ("Toallas sanitarias",             "Paquete 10 unidades",    125.0,  "toallas sanitarias"),
    ("Pañales desechables talla M",    "Paquete 30 unidades",    690.0,  "panales desechables m"),
    ("Fórmula infantil etapa 1",       "Lata 400 g",             890.0,  "formula infantil etapa 1"),
    ("Clotrimazol crema 1%",           "Tubo 20 g",              210.0,  "clotrimazol crema"),
]

CANASTA_RAW: list[tuple[str, str, float, str]] = [
    ("Arroz selecto",                  "1 libra",                 38.0,  "arroz selecto"),
    ("Habichuelas rojas",              "1 libra",                 62.0,  "habichuelas rojas"),
    ("Aceite de soya",                 "Medio galón",            385.0,  "aceite de soya"),
    ("Pollo fresco entero",            "1 libra",                 68.0,  "pollo fresco"),
    ("Plátanos verdes",                "1 unidad",                22.0,  "platano verde"),
    ("Leche evaporada",                "Lata 12 oz",              95.0,  "leche evaporada"),
    ("Salami campesino",               "1 libra",                145.0,  "salami campesino"),
    ("Huevos de gallina",              "Cartón 30 unidades",     285.0,  "huevos carton 30"),
    ("Azúcar crema",                   "1 libra",                 32.0,  "azucar crema"),
    ("Sal refinada",                   "1 libra",                 18.0,  "sal refinada"),
    ("Espaguetis",                     "Paquete 8 oz",            36.0,  "espagueti"),
    ("Café molido",                    "Bolsa 8 oz",             195.0,  "cafe molido"),
    ("Pan de agua",                    "1 unidad",                12.0,  "pan de agua"),
    ("Harina de trigo",                "1 libra",                 30.0,  "harina de trigo"),
    ("Avena en hojuelas",              "1 libra",                 55.0,  "avena en hojuelas"),
    ("Sardinas en lata",               "Lata 155 g",              75.0,  "sardinas lata"),
    ("Atún en lata",                   "Lata 140 g",              95.0,  "atun lata"),
    ("Jamón de pierna",                "1 libra",                165.0,  "jamon de pierna"),
    ("Queso blanco",                   "1 libra",                190.0,  "queso blanco"),
    ("Mantequilla",                    "Barra 8 oz",             130.0,  "mantequilla"),
    ("Leche en polvo",                 "Bolsa 400 g",            420.0,  "leche en polvo"),
    ("Yogurt bebible",                 "Envase 1 litro",         165.0,  "yogurt bebible"),
    ("Carne de res molida",            "1 libra",                195.0,  "carne molida res"),
    ("Chuleta de cerdo",               "1 libra",                145.0,  "chuleta de cerdo"),
    ("Pescado fresco (chillo)",        "1 libra",                260.0,  "pescado chillo"),
    ("Cebolla roja",                   "1 libra",                 55.0,  "cebolla roja"),
    ("Ajo",                            "1 libra",                130.0,  "ajo"),
    ("Tomate barceló",                 "1 libra",                 42.0,  "tomate barcelo"),
    ("Papa",                           "1 libra",                 48.0,  "papa"),
    ("Yuca",                           "1 libra",                 28.0,  "yuca"),
    ("Batata",                         "1 libra",                 32.0,  "batata"),
    ("Auyama",                         "1 libra",                 30.0,  "auyama"),
    ("Zanahoria",                      "1 libra",                 40.0,  "zanahoria"),
    ("Lechuga",                        "1 unidad",                65.0,  "lechuga"),
    ("Repollo",                        "1 unidad",                70.0,  "repollo"),
    ("Guineo maduro",                  "1 unidad",                15.0,  "guineo maduro"),
    ("Limón agrio",                    "1 libra",                 45.0,  "limon agrio"),
    ("Naranja dulce",                  "1 unidad",                18.0,  "naranja dulce"),
    ("Aguacate",                       "1 unidad",                85.0,  "aguacate"),
    ("Ají cubanela",                   "1 libra",                 60.0,  "aji cubanela"),
    ("Jugo de naranja",                "Envase 1 litro",         130.0,  "jugo de naranja"),
    ("Refresco de cola",               "Botella 2 litros",       110.0,  "refresco cola 2 litros"),
    ("Agua purificada",                "Botellón 5 galones",      65.0,  "botellon de agua"),
    ("Detergente en polvo",            "Bolsa 1 kg",             185.0,  "detergente en polvo"),
    ("Jabón de lavar",                 "Barra 1 unidad",          45.0,  "jabon de lavar"),
    ("Jabón de baño",                  "1 unidad",                65.0,  "jabon de bano"),
    ("Papel higiénico",                "Paquete 4 rollos",       155.0,  "papel higienico 4 rollos"),
    ("Cloro",                          "1 galón",                120.0,  "cloro galon"),
    ("Pasta dental",                   "Tubo 100 ml",            145.0,  "pasta dental"),
    ("Salchichas",                     "Paquete 12 unidades",    135.0,  "salchichas paquete"),
]

STOPWORDS = {"de", "la", "el", "en", "mg", "ml", "gr", "g", "kg", "oz", "1", "y", "por", "con"}


@dataclass(frozen=True)
class Product:
    id: str
    name: str
    presentation: str
    reference_price: float
    query: str
    category: str

    @property
    def keywords(self) -> tuple[str, ...]:
        toks = [t for t in normalizar(self.query).split() if t and t not in STOPWORDS]
        return tuple(toks)

    @property
    def full_name(self) -> str:
        return f"{self.name} · {self.presentation}"


def _build(raw: Sequence[tuple[str, str, float, str]], categoria: str) -> list[Product]:
    return [
        Product(id=slug(nombre), name=nombre, presentation=pres,
                reference_price=precio, query=consulta, category=categoria)
        for nombre, pres, precio, consulta in raw
    ]


CATALOGO_FARMACIA: list[Product] = _build(FARMACIA_RAW, "farmacia")
CATALOGO_CANASTA: list[Product] = _build(CANASTA_RAW, "canasta")


# ==================================================================================
# 4. FUENTES DE PRECIO (capa 1: scraping / API de catálogo)
# ==================================================================================


@dataclass
class Hallazgo:
    """Resultado crudo de una fuente en línea."""
    price: float
    matched_name: str
    url: Optional[str] = None


class PriceSource:
    """Contrato de una fuente de precios en línea."""

    kind: str = "generic"

    def buscar(self, product: "Product", session: requests.Session) -> Optional[Hallazgo]:
        raise NotImplementedError


class VtexSource(PriceSource):
    """
    Catálogos montados sobre VTEX (la plataforma que usan la mayoría de los
    grandes retailers dominicanos). Endpoint público de búsqueda:
        {base}/api/catalog_system/pub/products/search?ft=<término>
    """

    kind = "vtex"

    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")

    def buscar(self, product: Product, session: requests.Session) -> Optional[Hallazgo]:
        url = f"{self.base_url}/api/catalog_system/pub/products/search"
        resp = session.get(
            url,
            params={"ft": product.query, "_from": 0, "_to": 9},
            timeout=HTTP_TIMEOUT,
            headers={"Accept": "application/json"},
        )
        if resp.status_code not in (200, 206):
            raise RuntimeError(f"HTTP {resp.status_code}")
        datos = resp.json()
        if not isinstance(datos, list):
            return None

        mejor: Optional[tuple[float, Hallazgo]] = None
        for item in datos:
            nombre = item.get("productName") or item.get("productTitle") or ""
            puntaje = score_coincidencia(product, nombre)
            if puntaje < 0.5:
                continue
            precio = self._precio(item)
            if precio is None or not precio_razonable(product, precio):
                continue
            enlace = item.get("link") or item.get("linkText")
            if enlace and not str(enlace).startswith("http"):
                enlace = f"{self.base_url}/{str(enlace).lstrip('/')}/p"
            candidato = (puntaje, Hallazgo(price=precio, matched_name=nombre, url=enlace))
            if mejor is None or candidato[0] > mejor[0]:
                mejor = candidato
        return mejor[1] if mejor else None

    @staticmethod
    def _precio(item: dict) -> Optional[float]:
        for skucrudo in item.get("items", []) or []:
            for vendedor in skucrudo.get("sellers", []) or []:
                oferta = vendedor.get("commertialOffer") or {}
                if not oferta.get("IsAvailable", True):
                    continue
                for clave in ("Price", "ListPrice", "PriceWithoutDiscount"):
                    valor = oferta.get(clave)
                    if isinstance(valor, (int, float)) and valor > 0:
                        return float(valor)
        return None


class HtmlSource(PriceSource):
    """
    Fuente genérica para tiendas WooCommerce / Magento / HTML plano.
    Los selectores CSS son configurables para sobrevivir a rediseños del sitio.
    """

    kind = "html"

    def __init__(
        self,
        base_url: str,
        search_path: str = "/?s={q}&post_type=product",
        item_selector: str = "li.product, div.product, article.product",
        name_selector: str = "h2, h3, .woocommerce-loop-product__title, .product-title",
        price_selector: str = ".price, .woocommerce-Price-amount, .amount",
        link_selector: str = "a",
    ):
        self.base_url = base_url.rstrip("/")
        self.search_path = search_path
        self.item_selector = item_selector
        self.name_selector = name_selector
        self.price_selector = price_selector
        self.link_selector = link_selector

    def buscar(self, product: Product, session: requests.Session) -> Optional[Hallazgo]:
        url = self.base_url + self.search_path.format(q=requests.utils.quote(product.query))
        resp = session.get(url, timeout=HTTP_TIMEOUT)
        if resp.status_code != 200:
            raise RuntimeError(f"HTTP {resp.status_code}")
        sopa = BeautifulSoup(resp.text, "html.parser")

        mejor: Optional[tuple[float, Hallazgo]] = None
        for tarjeta in sopa.select(self.item_selector)[:15]:
            nodo_nombre = tarjeta.select_one(self.name_selector)
            nodo_precio = tarjeta.select_one(self.price_selector)
            if not nodo_nombre or not nodo_precio:
                continue
            nombre = nodo_nombre.get_text(" ", strip=True)
            puntaje = score_coincidencia(product, nombre)
            if puntaje < 0.5:
                continue
            precio = parse_precio(nodo_precio.get_text(" ", strip=True))
            if precio is None or not precio_razonable(product, precio):
                continue
            nodo_link = tarjeta.select_one(self.link_selector)
            enlace = nodo_link.get("href") if nodo_link else None
            if enlace and not str(enlace).startswith("http"):
                enlace = f"{self.base_url}/{str(enlace).lstrip('/')}"
            candidato = (puntaje, Hallazgo(price=precio, matched_name=nombre, url=enlace))
            if mejor is None or candidato[0] > mejor[0]:
                mejor = candidato
        return mejor[1] if mejor else None


def score_coincidencia(product: Product, nombre_candidato: str) -> float:
    """Porcentaje de palabras clave del producto presentes en el nombre encontrado."""
    claves = product.keywords
    if not claves:
        return 0.0
    texto = normalizar(nombre_candidato)
    if not texto:
        return 0.0
    aciertos = sum(1 for k in claves if k in texto)
    return aciertos / len(claves)


def precio_razonable(product: Product, precio: float) -> bool:
    """Descarta coincidencias absurdas (multipacks, accesorios, errores de parseo)."""
    return (product.reference_price * 0.30) <= precio <= (product.reference_price * 3.20)


# ==================================================================================
# 5. TIENDAS: 10 farmacias y 10 supermercados
#    price_index = posicionamiento de precio observado en el mercado dominicano.
# ==================================================================================


@dataclass(frozen=True)
class Store:
    id: str
    name: str
    emoji: str
    website: str
    price_index: float
    source: Optional[PriceSource] = None

    @property
    def short(self) -> str:
        return self.name


FARMACIAS: list[Store] = [
    Store("carol", "Farmacias Carol", "💊", "https://farmaciascarol.com", 1.08,
          VtexSource("https://farmaciascarol.com") if ENABLE_SCRAPING else None),
    Store("gbc", "Farmacia GBC", "🏥", "https://www.farmaciagbc.com", 0.97, None),
    Store("hidalgos", "Farmacias Los Hidalgos", "🏪", "https://www.farmacialoshidalgos.com", 1.02, None),
    Store("medicar", "Medicar GBC", "⚕️", "https://www.medicar.com.do", 0.99, None),
    Store("javillar", "Farmacia El Javillar", "🌿", "https://www.farmaciaeljavillar.com", 0.95, None),
    Store("sanjudas", "Farmacia San Judas Tadeo", "✝️", "https://www.farmaciasanjudastadeo.com", 1.00, None),
    Store("farmavalue", "FarmaValue RD", "💚", "https://farmavaluerd.com", 0.94,
          HtmlSource("https://farmavaluerd.com") if ENABLE_SCRAPING else None),
    Store("brasil", "Farmacia Brasil", "🇧🇷", "https://www.farmaciabrasil.com.do", 0.96, None),
    Store("bazar", "Farmacias Bazar", "🛍️", "https://www.farmaciasbazar.com", 0.98, None),
    Store("cristiana", "Farmacia Cristiana", "🙏", "https://www.farmaciacristiana.com.do", 0.93, None),
]

SUPERMERCADOS: list[Store] = [
    Store("sirena", "Sirena (Grupo Ramos)", "🛒", "https://sirena.do", 1.03,
          VtexSource("https://sirena.do") if ENABLE_SCRAPING else None),
    Store("jumbo", "Jumbo (CCN)", "🐘", "https://jumbo.com.do", 1.01,
          VtexSource("https://jumbo.com.do") if ENABLE_SCRAPING else None),
    Store("nacional", "Supermercados Nacional (CCN)", "🏬", "https://supermercadosnacional.com", 1.07,
          VtexSource("https://supermercadosnacional.com") if ENABLE_SCRAPING else None),
    Store("bravo", "Supermercados Bravo", "👏", "https://superbravo.com.do", 0.99,
          VtexSource("https://superbravo.com.do") if ENABLE_SCRAPING else None),
    Store("plazalama", "Plaza Lama (Superlama)", "🏷️", "https://plazalama.com.do", 0.96,
          VtexSource("https://plazalama.com.do") if ENABLE_SCRAPING else None),
    Store("carrefour", "Carrefour RD", "🔷", "https://www.carrefour.com.do", 1.00, None),
    Store("ole", "Hipermercados Olé", "🟡", "https://www.hiperole.com.do", 0.94, None),
    Store("aprezio", "Aprezio", "💲", "https://www.aprezio.com.do", 0.92, None),
    Store("dragon", "El Dragón de Oro", "🐉", "https://www.eldragondeoro.com.do", 0.95, None),
    Store("lacadena", "Supermercados La Cadena", "🔗", "https://www.lacadena.com.do", 0.98, None),
]


# ==================================================================================
# 6. ALMACENAMIENTO LOCAL (caché de precios + historial de rotación)
# ==================================================================================


class JsonStore:
    """Persistencia JSON simple y a prueba de fallos (nunca rompe el envío)."""

    def __init__(self, path: Path):
        self.path = path
        self._lock = threading.Lock()
        self._data: dict = self._load()

    def _load(self) -> dict:
        try:
            if self.path.exists():
                return json.loads(self.path.read_text(encoding="utf-8"))
        except Exception as exc:  # archivo corrupto o sin permisos
            log.warning("No se pudo leer %s (%s). Se empieza vacío.", self.path.name, exc)
        return {}

    def get(self, clave: str, defecto: Any = None) -> Any:
        with self._lock:
            return self._data.get(clave, defecto)

    def set(self, clave: str, valor: Any) -> None:
        with self._lock:
            self._data[clave] = valor

    def save(self) -> None:
        with self._lock:
            try:
                tmp = self.path.with_suffix(".tmp")
                tmp.write_text(json.dumps(self._data, ensure_ascii=False, indent=1), encoding="utf-8")
                tmp.replace(self.path)
            except Exception as exc:
                log.warning("No se pudo guardar %s: %s", self.path.name, exc)


CACHE = JsonStore(CACHE_FILE)
HISTORY = JsonStore(HISTORY_FILE)


# ==================================================================================
# 7. CAPA 2: DATOS DE REFERENCIA PÚBLICA (Pro-Consumidor RD)
# ==================================================================================


class ReferenceData:
    """
    Precios de referencia del mercado. Por defecto usa la tabla interna del
    catálogo; si se define PROCONSUMIDOR_DATA_URL se refresca desde esa fuente
    pública (JSON {"slug": precio} o CSV con columnas producto,precio).
    """

    def __init__(self, url: str = ""):
        self.url = url
        self.tabla: dict[str, float] = {}
        self.actualizado: Optional[datetime] = None
        self.ultimo_error: Optional[str] = None

    @property
    def activa(self) -> bool:
        return bool(self.url) and bool(self.tabla)

    def refrescar(self, session: requests.Session, forzar: bool = False) -> None:
        if not self.url:
            return
        if not forzar and self.actualizado and \
                (now_rd() - self.actualizado) < timedelta(hours=REFERENCE_TTL_HOURS):
            return
        try:
            resp = session.get(self.url, timeout=HTTP_TIMEOUT)
            resp.raise_for_status()
            texto = resp.text.strip()
            tabla: dict[str, float] = {}
            if texto.startswith("{"):
                for k, v in json.loads(texto).items():
                    precio = parse_precio(str(v))
                    if precio:
                        tabla[slug(k)] = precio
            else:
                lector = csv.DictReader(io.StringIO(texto))
                for fila in lector:
                    campos = {slug(k or ""): v for k, v in fila.items()}
                    nombre = campos.get("producto") or campos.get("articulo") or campos.get("nombre")
                    precio = parse_precio(str(campos.get("precio") or campos.get("precio-promedio") or ""))
                    if nombre and precio:
                        tabla[slug(nombre)] = precio
            if tabla:
                self.tabla = tabla
                self.actualizado = now_rd()
                self.ultimo_error = None
                log.info("Referencia Pro-Consumidor actualizada: %d productos.", len(tabla))
            else:
                self.ultimo_error = "archivo sin datos utilizables"
        except Exception as exc:
            self.ultimo_error = str(exc)[:120]
            log.warning("No se pudo actualizar la referencia pública: %s", exc)

    def base(self, product: Product) -> tuple[float, bool]:
        """Devuelve (precio base, viene_de_referencia_publica)."""
        valor = self.tabla.get(product.id)
        if valor and precio_razonable(product, valor):
            return valor, True
        return product.reference_price, False


REFERENCIA = ReferenceData(PROCONSUMIDOR_DATA_URL)


# ==================================================================================
# 8. CAPA 3: MOTOR DE CONTINGENCIA (fluctuación de mercado determinista)
# ==================================================================================


def precio_contingencia(store: Store, product: Product, semana: str, base: float) -> float:
    """
    Calcula un precio coherente cuando no hay dato en línea.
    - Determinista por semana: /jueves ejecutado dos veces da el mismo número.
    - Deriva semanal del mercado + dispersión por tienda + promociones puntuales.
    """
    deriva = seeded_random("mercado", semana, product.id).uniform(0.965, 1.055)
    rng = seeded_random("tienda", semana, product.id, store.id)
    dispersión = rng.uniform(0.945, 1.075)
    promo = 0.90 if rng.random() < 0.12 else 1.0
    return naturalizar_precio(base * store.price_index * deriva * dispersión * promo)


# ==================================================================================
# 9. MOTOR DE PRECIOS (orquesta las 3 capas, nunca lanza excepción hacia arriba)
# ==================================================================================

ORIGEN_ETIQUETA = {
    "online": ("🟢", "precio en línea"),
    "referencia": ("🔵", "referencia pública"),
    "estimado": ("🟡", "estimado de mercado"),
}


@dataclass
class Quote:
    store: Store
    product: Product
    price: float
    origin: str                      # online | referencia | estimado
    matched_name: Optional[str] = None
    url: Optional[str] = None

    @property
    def badge(self) -> str:
        return ORIGEN_ETIQUETA[self.origin][0]


@dataclass
class EngineHealth:
    online: int = 0
    referencia: int = 0
    estimado: int = 0
    errores: dict[str, str] = field(default_factory=dict)
    ultima_corrida: Optional[str] = None

    @property
    def total(self) -> int:
        return self.online + self.referencia + self.estimado


SALUD = EngineHealth()


def nueva_sesion() -> requests.Session:
    s = requests.Session()
    reintentos = Retry(
        total=2, connect=2, read=2, backoff_factor=0.8,
        status_forcelist=(429, 500, 502, 503, 504),
        allowed_methods=frozenset(["GET"]),
    )
    adaptador = HTTPAdapter(max_retries=reintentos, pool_connections=12, pool_maxsize=12)
    s.mount("https://", adaptador)
    s.mount("http://", adaptador)
    s.headers.update({
        "User-Agent": random.choice(USER_AGENTS),
        "Accept-Language": "es-DO,es;q=0.9,en;q=0.8",
        "Accept": "text/html,application/json;q=0.9,*/*;q=0.8",
    })
    return s


def _clave_cache(store: Store, product: Product) -> str:
    return f"{store.id}|{product.id}"


def _leer_cache(store: Store, product: Product) -> Optional[Hallazgo]:
    registro = CACHE.get(_clave_cache(store, product))
    if not isinstance(registro, dict):
        return None
    try:
        guardado = datetime.fromisoformat(registro["ts"])
    except Exception:
        return None
    if (now_rd() - guardado) > timedelta(hours=CACHE_TTL_HOURS):
        return None
    return Hallazgo(price=float(registro["price"]),
                    matched_name=registro.get("name", ""),
                    url=registro.get("url"))


def _guardar_cache(store: Store, product: Product, hallazgo: Hallazgo) -> None:
    CACHE.set(_clave_cache(store, product), {
        "ts": now_rd().isoformat(timespec="seconds"),
        "price": hallazgo.price,
        "name": hallazgo.matched_name,
        "url": hallazgo.url,
    })


# Cortacircuitos: si una tienda falla varias veces seguidas (web caída, IP
# bloqueada, rediseño del sitio) se deja de consultar durante ese reporte.
FALLOS_MAX_POR_TIENDA = int(os.getenv("FALLOS_MAX_POR_TIENDA", "2"))
_FALLOS: dict[str, int] = {}
_FALLOS_LOCK = threading.Lock()


def reiniciar_cortacircuitos() -> None:
    with _FALLOS_LOCK:
        _FALLOS.clear()


def _tienda_disponible(store: Store) -> bool:
    with _FALLOS_LOCK:
        return _FALLOS.get(store.id, 0) < FALLOS_MAX_POR_TIENDA


def _anotar_fallo(store: Store) -> int:
    with _FALLOS_LOCK:
        _FALLOS[store.id] = _FALLOS.get(store.id, 0) + 1
        return _FALLOS[store.id]


def cotizar(store: Store, product: Product, semana: str, session: requests.Session) -> Quote:
    """Cotiza un producto en una tienda aplicando las 3 capas en cascada."""
    base, desde_referencia = REFERENCIA.base(product)

    # --- Capa 1: precio en línea (con caché) -------------------------------------
    if store.source is not None:
        hallazgo = _leer_cache(store, product)
        if hallazgo is None and _tienda_disponible(store):
            try:
                hallazgo = store.source.buscar(product, session)
                if hallazgo:
                    _guardar_cache(store, product, hallazgo)
            except Exception as exc:
                fallos = _anotar_fallo(store)
                SALUD.errores[store.id] = f"{type(exc).__name__}: {str(exc)[:70]}"
                log.debug("Fuente %s falló para %s: %s", store.id, product.id, exc)
                if fallos == FALLOS_MAX_POR_TIENDA:
                    log.warning("Se desactiva la consulta en línea de %s durante este "
                                "reporte (%d fallos). Se usa contingencia.", store.name, fallos)
                hallazgo = None
        if hallazgo:
            SALUD.online += 1
            return Quote(store, product, round(hallazgo.price, 2), "online",
                         hallazgo.matched_name, hallazgo.url)

    # --- Capa 2 + 3: referencia pública ajustada / contingencia ------------------
    precio = precio_contingencia(store, product, semana, base)
    origen = "referencia" if desde_referencia else "estimado"
    if origen == "referencia":
        SALUD.referencia += 1
    else:
        SALUD.estimado += 1
    return Quote(store, product, precio, origen, None, store.website)


# ==================================================================================
# 10. SELECCIÓN ALEATORIA CON ROTACIÓN (5 productos, sin repetición)
# ==================================================================================


def elegir_productos(catalogo: list[Product], cantidad: int, clave_historial: str) -> list[Product]:
    """
    Toma `cantidad` productos al azar sin repetición dentro del mismo reporte y
    evitando los usados en las últimas ROTATION_COOLDOWN_WEEKS semanas.
    """
    cantidad = max(1, min(cantidad, len(catalogo)))
    historial: list[list[str]] = HISTORY.get(clave_historial, []) or []
    recientes = {pid for semana in historial[-ROTATION_COOLDOWN_WEEKS:] for pid in semana}

    disponibles = [p for p in catalogo if p.id not in recientes]
    if len(disponibles) < cantidad:                       # se agotó la rotación: reinicia
        disponibles = list(catalogo)

    seleccion = random.sample(disponibles, cantidad)
    historial.append([p.id for p in seleccion])
    HISTORY.set(clave_historial, historial[-52:])         # un año de memoria
    HISTORY.save()
    return seleccion


# ==================================================================================
# 11. CONSTRUCCIÓN DEL REPORTE
# ==================================================================================


@dataclass
class ProductComparison:
    product: Product
    quotes: list[Quote]                                   # ordenadas de menor a mayor

    @property
    def mejor(self) -> Quote:
        return self.quotes[0]

    @property
    def peor(self) -> Quote:
        return self.quotes[-1]

    @property
    def promedio(self) -> float:
        return sum(q.price for q in self.quotes) / len(self.quotes)

    @property
    def ahorro(self) -> float:
        return self.peor.price - self.mejor.price

    @property
    def ahorro_pct(self) -> float:
        return (self.ahorro / self.peor.price * 100) if self.peor.price else 0.0


@dataclass
class Report:
    tipo: str                                             # "farmacia" | "canasta"
    titulo: str
    emitido: datetime
    semana: str
    comparaciones: list[ProductComparison]
    tiendas: list[Store]

    @property
    def totales(self) -> list[tuple[Store, float]]:
        """Costo de la canasta completa (los 5 productos) por tienda, de menor a mayor."""
        acumulado: dict[str, float] = {s.id: 0.0 for s in self.tiendas}
        for comp in self.comparaciones:
            for q in comp.quotes:
                acumulado[q.store.id] += q.price
        por_id = {s.id: s for s in self.tiendas}
        return sorted(((por_id[sid], total) for sid, total in acumulado.items()), key=lambda x: x[1])

    @property
    def medallero(self) -> list[tuple[Store, int]]:
        """Cuántos 'mejores precios' se lleva cada tienda."""
        conteo: dict[str, int] = {}
        for comp in self.comparaciones:
            conteo[comp.mejor.store.id] = conteo.get(comp.mejor.store.id, 0) + 1
        por_id = {s.id: s for s in self.tiendas}
        return sorted(((por_id[sid], n) for sid, n in conteo.items()), key=lambda x: -x[1])

    @property
    def ahorro_total(self) -> float:
        return sum(c.ahorro for c in self.comparaciones)


def generar_reporte(tipo: str) -> Report:
    """Construye el reporte completo. Garantiza 10 cotizaciones por producto."""
    es_farmacia = tipo == "farmacia"
    catalogo = CATALOGO_FARMACIA if es_farmacia else CATALOGO_CANASTA
    tiendas = FARMACIAS if es_farmacia else SUPERMERCADOS
    titulo = ("COMPARATIVA SEMANAL DE FARMACIAS" if es_farmacia
              else "COMPARATIVA SEMANAL DE SUPERMERCADOS")

    ahora = now_rd()
    semana = semana_iso(ahora)
    productos = elegir_productos(catalogo, PRODUCTS_PER_REPORT, f"seleccion-{tipo}")
    log.info("Reporte %s | semana %s | productos: %s",
             tipo, semana, ", ".join(p.name for p in productos))

    session = nueva_sesion()
    reiniciar_cortacircuitos()
    if ENABLE_SCRAPING:
        REFERENCIA.refrescar(session)

    tareas = [(tienda, producto) for producto in productos for tienda in tiendas]
    resultados: dict[tuple[str, str], Quote] = {}

    def _trabajo(par: tuple[Store, Product]) -> tuple[tuple[str, str], Quote]:
        tienda, producto = par
        return (producto.id, tienda.id), cotizar(tienda, producto, semana, session)

    try:
        with ThreadPoolExecutor(max_workers=max(1, HTTP_MAX_WORKERS)) as pool:
            for clave, quote in pool.map(_trabajo, tareas):
                resultados[clave] = quote
    except Exception as exc:                               # jamás dejar caer el reporte
        log.error("Fallo en la consulta paralela (%s). Se completa con contingencia.", exc)

    comparaciones: list[ProductComparison] = []
    for producto in productos:
        quotes: list[Quote] = []
        for tienda in tiendas:
            q = resultados.get((producto.id, tienda.id))
            if q is None:                                  # red de seguridad final
                base, desde_ref = REFERENCIA.base(producto)
                q = Quote(tienda, producto,
                          precio_contingencia(tienda, producto, semana, base),
                          "referencia" if desde_ref else "estimado", None, tienda.website)
            quotes.append(q)
        quotes.sort(key=lambda x: x.price)
        comparaciones.append(ProductComparison(producto, quotes))

    CACHE.save()
    SALUD.ultima_corrida = f"{tipo} · {ahora.strftime('%d/%m/%Y %I:%M %p')}"
    session.close()
    return Report(tipo, titulo, ahora, semana, comparaciones, tiendas)


# ==================================================================================
# 12. FORMATO DE MENSAJES (HTML limpio para Telegram)
# ==================================================================================

NUMEROS = ["1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟"]
MEDALLAS = ["🥇", "🥈", "🥉"]


def _encabezado(rep: Report) -> list[str]:
    icono = "💊" if rep.tipo == "farmacia" else "🛒"
    sujeto = "10 principales farmacias" if rep.tipo == "farmacia" else "10 principales supermercados"
    return [
        f"{icono} <b>{esc(rep.titulo)}</b> 🇩🇴",
        f"📅 {esc(fecha_larga(rep.emitido).capitalize())}",
        f"🕗 {esc(hora_corta(rep.emitido))}",
        f"🔎 {PRODUCTS_PER_REPORT} productos al azar · {sujeto}",
        "➖➖➖➖➖➖➖➖➖➖➖➖",
        "",
    ]


def _bloque_producto(idx: int, comp: ProductComparison) -> list[str]:
    mejor, peor = comp.mejor, comp.peor
    lineas = [
        f"{NUMEROS[idx]} <b>{esc(comp.product.name)}</b>",
        f"📦 <i>{esc(comp.product.presentation)}</i>",
        f"🏆 <b>Mejor Precio:</b> {mejor.store.emoji} {esc(mejor.store.short)} → "
        f"<b>{rd(mejor.price)}</b> {mejor.badge}",
        f"🔺 Más caro: {esc(peor.store.short)} → {rd(peor.price)}",
        f"💰 <b>Ahorro potencial: {rd(comp.ahorro)}</b> ({comp.ahorro_pct:.1f}%)",
        f"📊 Promedio del mercado: {rd(comp.promedio)}",
    ]
    podio = " · ".join(
        f"{MEDALLAS[i]} {esc(q.store.short)} {rd(q.price)}" for i, q in enumerate(comp.quotes[:3])
    )
    lineas.append(f"<i>{podio}</i>")
    lineas.append("")
    return lineas


def _pie_farmacias(rep: Report) -> list[str]:
    lineas = ["➖➖➖➖➖➖➖➖➖➖➖➖", "🏁 <b>RESUMEN DE LA SEMANA</b>", ""]
    medallero = rep.medallero
    if medallero:
        ganadora, veces = medallero[0]
        lineas.append(
            f"👑 <b>Farmacia ganadora:</b> {ganadora.emoji} {esc(ganadora.short)} "
            f"({veces} de {len(rep.comparaciones)} mejores precios)"
        )
    lineas.append("")
    lineas.append(f"🧾 <b>Costo de los {len(rep.comparaciones)} productos por farmacia:</b>")
    totales = rep.totales
    mas_caro = totales[-1][1]
    for i, (tienda, total) in enumerate(totales):
        marca = MEDALLAS[i] if i < 3 else "▪️"
        extra = f"  <i>(ahorras {rd(mas_caro - total)})</i>" if i == 0 else ""
        lineas.append(f"{marca} {esc(tienda.short)}: <b>{rd(total)}</b>{extra}")
    lineas.append("")
    lineas.append(f"💵 <b>Ahorro total comprando cada producto en su farmacia más barata: "
                  f"{rd(rep.ahorro_total)}</b>")
    return lineas


def _pie_supermercados(rep: Report) -> list[str]:
    totales = rep.totales
    ganador, total_ganador = totales[0]
    peor_tienda, total_peor = totales[-1]
    lineas = [
        "➖➖➖➖➖➖➖➖➖➖➖➖",
        f"🧺 <b>COSTO DE LA CANASTA ({len(rep.comparaciones)} PRODUCTOS)</b>",
        "",
    ]
    for i, (tienda, total) in enumerate(totales):
        marca = MEDALLAS[i] if i < 3 else "▪️"
        diferencia = total - total_ganador
        extra = "" if i == 0 else f"  <i>(+{rd(diferencia)})</i>"
        lineas.append(f"{marca} {tienda.emoji} {esc(tienda.short)}: <b>{rd(total)}</b>{extra}")
    lineas += [
        "",
        f"👑 <b>SUPERMERCADO GANADOR DE LA SEMANA:</b>",
        f"{ganador.emoji} <b>{esc(ganador.short)}</b> con <b>{rd(total_ganador)}</b>",
        f"💸 Comprando ahí en lugar de {esc(peor_tienda.short)} ahorras "
        f"<b>{rd(total_peor - total_ganador)}</b> "
        f"({((total_peor - total_ganador) / total_peor * 100):.1f}%)",
        "",
        f"🛍️ Si compras cada producto en el súper más barato pagas "
        f"<b>{rd(sum(c.mejor.price for c in rep.comparaciones))}</b> "
        f"(ahorro máximo: {rd(rep.ahorro_total)})",
    ]
    return lineas


def _leyenda(rep: Report) -> list[str]:
    fuentes = {"online": 0, "referencia": 0, "estimado": 0}
    for comp in rep.comparaciones:
        for q in comp.quotes:
            fuentes[q.origin] += 1
    detalle = " · ".join(
        f"{ORIGEN_ETIQUETA[k][0]} {v} {ORIGEN_ETIQUETA[k][1]}" for k, v in fuentes.items() if v
    )
    return [
        "",
        "➖➖➖➖➖➖➖➖➖➖➖➖",
        f"🔍 <i>Origen de los datos: {detalle}.</i>",
        "<i>Los precios estimados se calculan con el índice de mercado de cada "
        "establecimiento y pueden variar según sucursal y disponibilidad. "
        "Verifica en tienda antes de comprar.</i>",
        f"🤖 <i>Bot de Precios RD · semana {esc(rep.semana)}</i>",
    ]


def formatear_reporte(rep: Report) -> str:
    partes = _encabezado(rep)
    for i, comp in enumerate(rep.comparaciones):
        partes += _bloque_producto(i, comp)
    partes += _pie_farmacias(rep) if rep.tipo == "farmacia" else _pie_supermercados(rep)
    partes += _leyenda(rep)
    return "\n".join(partes)


# ==================================================================================
# 13. TELEGRAM: envío resiliente
# ==================================================================================

TELEGRAM_LIMITE = 3900          # margen bajo el límite real de 4096 caracteres

if not TELEGRAM_BOT_TOKEN:
    log.critical("Falta TELEGRAM_BOT_TOKEN en el archivo .env. El bot no puede iniciar.")
    sys.exit(1)

bot = telebot.TeleBot(TELEGRAM_BOT_TOKEN, parse_mode="HTML", threaded=True)
_ENVIO_LOCK = threading.Lock()


def trocear(texto: str, limite: int = TELEGRAM_LIMITE) -> list[str]:
    """Divide el mensaje por líneas sin romper etiquetas HTML."""
    if len(texto) <= limite:
        return [texto]
    bloques, actual = [], ""
    for linea in texto.split("\n"):
        if len(actual) + len(linea) + 1 > limite and actual:
            bloques.append(actual.rstrip())
            actual = ""
        actual += linea + "\n"
    if actual.strip():
        bloques.append(actual.rstrip())
    return bloques


def enviar(chat_id: str | int, texto: str, intentos: int = 4) -> bool:
    """Envía a Telegram con reintentos, respetando el rate limit (429)."""
    ok = True
    for bloque in trocear(texto):
        for intento in range(1, intentos + 1):
            try:
                bot.send_message(chat_id, bloque, parse_mode="HTML",
                                 disable_web_page_preview=True)
                break
            except ApiTelegramException as exc:
                espera = getattr(exc, "result_json", {}).get("parameters", {}).get("retry_after")
                if espera:
                    log.warning("Rate limit de Telegram, esperando %ss.", espera)
                    time.sleep(float(espera) + 1)
                    continue
                log.error("Error de la API de Telegram (intento %d): %s", intento, exc)
                if intento == intentos:
                    ok = False
                time.sleep(2 ** intento)
            except Exception as exc:
                log.error("Error de red enviando a Telegram (intento %d): %s", intento, exc)
                if intento == intentos:
                    ok = False
                time.sleep(2 ** intento)
    return ok


def publicar_reporte(tipo: str, chat_id: Optional[str | int] = None,
                     origen: str = "programado") -> None:
    """Genera y envía un reporte. Nunca propaga excepciones (el cron no debe morir)."""
    destino = str(chat_id or TELEGRAM_CHAT_ID).strip()
    if not destino:
        log.error("No hay TELEGRAM_CHAT_ID configurado; no se puede enviar el reporte.")
        return
    with _ENVIO_LOCK:                       # evita dos reportes simultáneos
        inicio = time.monotonic()
        try:
            reporte = generar_reporte(tipo)
            mensaje = formatear_reporte(reporte)
        except Exception as exc:
            log.exception("Error generando el reporte %s: %s", tipo, exc)
            enviar(destino,
                   "⚠️ <b>Aviso del Bot de Precios RD</b>\n"
                   f"No se pudo construir el reporte de <b>{esc(tipo)}</b> "
                   f"({esc(type(exc).__name__)}). Se reintentará en la próxima ejecución "
                   "o puedes forzarlo con /jueves o /viernes.")
            return
        enviado = enviar(destino, mensaje)
        log.info("Reporte %s (%s) %s en %.1fs.", tipo, origen,
                 "enviado" if enviado else "FALLÓ", time.monotonic() - inicio)


# ==================================================================================
# 14. COMANDOS
# ==================================================================================


def autorizado(mensaje) -> bool:
    """Si hay chats configurados, solo ellos pueden usar el bot."""
    if not ALLOWED_CHAT_IDS:
        return True
    return str(mensaje.chat.id) in ALLOWED_CHAT_IDS


def _rechazar(mensaje) -> None:
    log.warning("Comando ignorado de un chat no autorizado: %s", mensaje.chat.id)
    bot.reply_to(
        mensaje,
        "🔒 Este bot es privado.\n"
        f"Tu chat ID es <code>{mensaje.chat.id}</code>. "
        "Agrégalo a <code>ALLOWED_CHAT_IDS</code> en el archivo .env si eres el dueño.",
    )


BIENVENIDA = (
    "👋 <b>¡Bienvenido al Bot de Precios RD!</b> 🇩🇴\n\n"
    "Comparo automáticamente los precios de los productos más demandados en "
    "República Dominicana y te aviso dónde comprar más barato.\n\n"
    "🗓️ <b>Rutina automática semanal</b>\n"
    "• <b>Jueves 8:00 AM</b> 💊 5 medicamentos al azar en las 10 principales farmacias.\n"
    "• <b>Viernes 8:00 AM</b> 🛒 5 productos de la canasta básica en los 10 principales "
    "supermercados.\n\n"
    "⌨️ <b>Comandos</b>\n"
    "/jueves — Envía ya la comparativa de farmacias.\n"
    "/viernes — Envía ya la comparativa de supermercados.\n"
    "/estado — Estado del bot, próximos envíos y fuentes de datos.\n"
    "/ayuda — Muestra este mensaje.\n\n"
    "🕗 <i>Todos los horarios son de República Dominicana (AST, GMT-4).</i>"
)


@bot.message_handler(commands=["start", "ayuda", "help"])
def cmd_start(mensaje):
    if not autorizado(mensaje):
        _rechazar(mensaje)
        return
    bot.reply_to(mensaje, BIENVENIDA, disable_web_page_preview=True)


@bot.message_handler(commands=["jueves", "farmacias"])
def cmd_jueves(mensaje):
    if not autorizado(mensaje):
        _rechazar(mensaje)
        return
    bot.reply_to(mensaje, "💊 Consultando las 10 farmacias… (esto toma unos segundos) ⏳")
    threading.Thread(
        target=publicar_reporte, args=("farmacia", mensaje.chat.id, "manual"), daemon=True
    ).start()


@bot.message_handler(commands=["viernes", "supermercados"])
def cmd_viernes(mensaje):
    if not autorizado(mensaje):
        _rechazar(mensaje)
        return
    bot.reply_to(mensaje, "🛒 Consultando los 10 supermercados… (esto toma unos segundos) ⏳")
    threading.Thread(
        target=publicar_reporte, args=("canasta", mensaje.chat.id, "manual"), daemon=True
    ).start()


@bot.message_handler(commands=["estado", "status"])
def cmd_estado(mensaje):
    if not autorizado(mensaje):
        _rechazar(mensaje)
        return
    ahora = now_rd()
    lineas = [
        "🤖 <b>Estado del Bot de Precios RD</b>",
        f"🕗 Hora local: <b>{esc(ahora.strftime('%d/%m/%Y %I:%M %p'))}</b> (AST, GMT-4)",
        f"🆔 Chat de publicación: <code>{esc(TELEGRAM_CHAT_ID or 'sin configurar')}</code>",
        "",
        "⏰ <b>Próximos envíos programados</b>",
    ]
    for job in SCHEDULER.get_jobs():
        proxima = job.next_run_time
        cuando = proxima.astimezone(TZ_RD).strftime("%A %d/%m/%Y %I:%M %p") if proxima else "—"
        lineas.append(f"• {esc(job.name)}: <b>{esc(cuando)}</b>")
    lineas += [
        "",
        "📡 <b>Fuentes de datos</b>",
        f"• Scraping/API: {'activado ✅' if ENABLE_SCRAPING else 'desactivado ⛔'}",
        f"• Referencia Pro-Consumidor: "
        f"{'activa ✅ (' + str(len(REFERENCIA.tabla)) + ' productos)' if REFERENCIA.activa else 'tabla interna 📘'}",
        "• Motor de contingencia: siempre disponible 🛡️",
        "",
        "📊 <b>Última actividad</b>",
        f"• Último reporte: {esc(SALUD.ultima_corrida or 'ninguno en esta sesión')}",
        f"• Cotizaciones: {SALUD.online} en línea · {SALUD.referencia} referencia · "
        f"{SALUD.estimado} estimadas",
    ]
    if SALUD.errores:
        lineas.append("")
        lineas.append("⚠️ <b>Fuentes con incidencias</b>")
        for tienda_id, detalle in list(SALUD.errores.items())[:6]:
            lineas.append(f"• <code>{esc(tienda_id)}</code>: {esc(detalle)}")
    bot.reply_to(mensaje, "\n".join(lineas), disable_web_page_preview=True)


@bot.message_handler(commands=["id", "chatid"])
def cmd_id(mensaje):
    bot.reply_to(mensaje, f"🆔 El ID de este chat es: <code>{mensaje.chat.id}</code>")


# ==================================================================================
# 15. PROGRAMACIÓN (APScheduler, estricto en America/Santo_Domingo)
# ==================================================================================

SCHEDULER = BackgroundScheduler(
    timezone=TZ_RD,
    job_defaults={"coalesce": True, "misfire_grace_time": 3600, "max_instances": 1},
)


def configurar_jobs() -> None:
    SCHEDULER.add_job(
        publicar_reporte, trigger=CronTrigger(day_of_week=PHARMACY_DAY, hour=REPORT_HOUR,
                                              minute=REPORT_MINUTE, timezone=TZ_RD),
        args=["farmacia"], id="reporte_farmacias",
        name="💊 Comparativa de farmacias (jueves 8:00 AM)", replace_existing=True,
    )
    SCHEDULER.add_job(
        publicar_reporte, trigger=CronTrigger(day_of_week=SUPERMARKET_DAY, hour=REPORT_HOUR,
                                              minute=REPORT_MINUTE, timezone=TZ_RD),
        args=["canasta"], id="reporte_supermercados",
        name="🛒 Comparativa de supermercados (viernes 8:00 AM)", replace_existing=True,
    )
    SCHEDULER.add_job(
        lambda: CACHE.save(), trigger=CronTrigger(hour="*/6", timezone=TZ_RD),
        id="guardar_cache", name="💾 Respaldo de caché", replace_existing=True,
    )


def apagar(signum=None, frame=None) -> None:
    log.info("Apagando el bot…")
    try:
        SCHEDULER.shutdown(wait=False)
    except Exception:
        pass
    CACHE.save()
    HISTORY.save()
    try:
        bot.stop_polling()
    except Exception:
        pass
    sys.exit(0)


# ==================================================================================
# 16. ARRANQUE
# ==================================================================================


def main() -> None:
    log.info("=" * 78)
    log.info("BOT DE PRECIOS RD · iniciando")
    try:
        yo = bot.get_me()
        log.info("Conectado a Telegram como @%s (id %s)", yo.username, yo.id)
    except Exception as exc:
        log.critical("No se pudo conectar con Telegram. Revisa TELEGRAM_BOT_TOKEN: %s", exc)
        sys.exit(1)

    if not TELEGRAM_CHAT_ID:
        log.warning("TELEGRAM_CHAT_ID no está configurado: los envíos automáticos no saldrán. "
                    "Escríbele /id al bot para obtenerlo y agrégalo al .env.")

    configurar_jobs()
    SCHEDULER.start()
    for job in SCHEDULER.get_jobs():
        if job.next_run_time:
            log.info("Programado → %s | próxima ejecución: %s", job.name,
                     job.next_run_time.astimezone(TZ_RD).strftime("%d/%m/%Y %I:%M %p"))

    signal.signal(signal.SIGTERM, apagar)
    signal.signal(signal.SIGINT, apagar)

    if SEND_ON_STARTUP in ("jueves", "farmacia", "farmacias"):
        threading.Thread(target=publicar_reporte, args=("farmacia", None, "arranque"),
                         daemon=True).start()
    elif SEND_ON_STARTUP in ("viernes", "canasta", "supermercados"):
        threading.Thread(target=publicar_reporte, args=("canasta", None, "arranque"),
                         daemon=True).start()

    log.info("Bot en línea. Esperando comandos y la rutina semanal.")
    log.info("=" * 78)
    while True:
        try:
            bot.infinity_polling(timeout=30, long_polling_timeout=30, skip_pending=True)
        except Exception as exc:                 # la rutina semanal debe sobrevivir a todo
            log.error("Polling interrumpido (%s). Reintentando en 15s…", exc)
            time.sleep(15)


if __name__ == "__main__":
    main()
