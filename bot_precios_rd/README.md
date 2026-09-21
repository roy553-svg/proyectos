# 🇩🇴 Bot de Precios RD — Farmacias (jueves) y Canasta Familiar (viernes)

Bot de Telegram que compara automáticamente los precios de los productos más
demandados en República Dominicana y publica cada semana dónde comprar más barato.

| Día | Hora (AST / GMT-4) | Qué envía |
|---|---|---|
| 🗓️ **Jueves** | 08:00 AM | 5 medicamentos/artículos al azar comparados en **10 farmacias** |
| 🗓️ **Viernes** | 08:00 AM | 5 productos de la canasta básica comparados en **10 supermercados** |

* **Farmacias:** Carol · GBC · Los Hidalgos · Medicar GBC · El Javillar · San Judas Tadeo · FarmaValue RD · Brasil · Bazar · Cristiana
* **Supermercados:** Sirena · Jumbo · Nacional · Bravo · Plaza Lama · Carrefour RD · Hipermercados Olé · Aprezio · El Dragón de Oro · La Cadena

---

## ⚡ Instalación en 4 pasos

```bash
cd bot_precios_rd
python3 -m venv venv && source venv/bin/activate     # Windows: venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env                                  # y edita el token y el chat ID
python bot.py
```

Prueba que todo funciona sin gastar mensajes:

```bash
python pruebas.py          # 10 pruebas; agrega -v para ver los reportes completos
```

---

## 🔑 Cómo obtener tu `TELEGRAM_CHAT_ID`

**Opción A — con el propio bot (la más rápida):**
1. Pon a correr el bot (`python bot.py`).
2. Abre tu bot en Telegram y escríbele **`/id`**.
3. Te responde: `🆔 El ID de este chat es: 123456789`. Copia ese número al `.env`.

**Opción B — con @userinfobot:** busca `@userinfobot` en Telegram, escríbele `/start`
y te devuelve tu ID personal.

**Opción C — desde el navegador:** escríbele cualquier mensaje a tu bot y abre
`https://api.telegram.org/bot<TU_TOKEN>/getUpdates`. Busca `"chat":{"id":123456789`.

> **Grupos y canales:** agrega el bot al grupo/canal **como administrador**, envía un
> mensaje ahí y usa `getUpdates` o `/id`. El ID de un grupo empieza con `-100…`
> (ej. `-1001234567890`). Cópialo **con el signo negativo**.

---

## 🖥️ Cómo ponerlo a correr 24/7

### 1) Docker (recomendado, funciona en cualquier VPS)

```bash
docker compose up -d --build     # arranca en segundo plano y se reinicia solo
docker compose logs -f           # ver los logs
docker compose restart           # reiniciar tras cambiar el .env
```

### 2) systemd (VPS Linux sin Docker: DigitalOcean, Contabo, AWS Lightsail…)

```bash
sudo cp bot-precios-rd.service /etc/systemd/system/
sudo nano /etc/systemd/system/bot-precios-rd.service   # ajusta User= y las rutas
sudo systemctl daemon-reload
sudo systemctl enable --now bot-precios-rd
journalctl -u bot-precios-rd -f                        # logs en vivo
```

### 3) Railway / Render / Fly.io (sin servidor propio)

1. Sube esta carpeta a un repositorio de GitHub.
2. Crea un servicio tipo **Worker / Background Worker** apuntando al repo.
3. Comando de inicio: `python bot.py`
4. Agrega las variables `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` y `TZ=America/Santo_Domingo`
   en el panel de variables de entorno.

### 4) Prueba rápida en tu PC (no es 24/7)

```bash
nohup python bot.py > bot.log 2>&1 &     # Linux/Mac
```

> ⚠️ El bot debe estar **siempre encendido** para que dispare los envíos del jueves y
> viernes. Si la máquina estaba apagada a las 8:00 AM, APScheduler recupera el envío
> al encender dentro de la hora siguiente (`misfire_grace_time = 3600`).

---

## ⌨️ Comandos

| Comando | Qué hace |
|---|---|
| `/start` o `/ayuda` | Mensaje de bienvenida con la rutina y los comandos |
| `/jueves` | Fuerza **ya** la comparativa de farmacias (no espera al jueves) |
| `/viernes` | Fuerza **ya** la comparativa de supermercados |
| `/estado` | Hora RD, próximos envíos programados, salud de las fuentes de datos |
| `/id` | Devuelve el ID del chat actual |

Por seguridad, solo responden los chats listados en `TELEGRAM_CHAT_ID` y
`ALLOWED_CHAT_IDS`. `/id` responde siempre, para que puedas configurarte.

---

## 🛡️ Arquitectura de precios (por qué nunca falla un jueves)

El bot resuelve **cada precio** en cascada de 3 capas:

| Capa | Fuente | Marca en el mensaje |
|---|---|---|
| 1️⃣ | **Catálogo en línea** — API de búsqueda VTEX (Sirena, Jumbo, Nacional, Bravo, Plaza Lama, Farmacias Carol) y HTML/WooCommerce (FarmaValue) | 🟢 precio en línea |
| 2️⃣ | **Referencia pública** — tabla de Pro-Consumidor RD u otra fuente que configures en `PROCONSUMIDOR_DATA_URL` (JSON o CSV) | 🔵 referencia pública |
| 3️⃣ | **Motor de contingencia** — fluctuación de mercado determinista por semana, con el índice de precios de cada establecimiento | 🟡 estimado de mercado |

Protecciones incluidas:

* **Cortacircuitos:** si una web falla 2 veces seguidas, se deja de consultar durante ese reporte (no se atrasa el envío de las 8:00 AM).
* **Caché de 12 h** en `data/price_cache.json`: menos peticiones, menos riesgo de bloqueo por IP.
* **Validación de coincidencias:** descarta multipacks, accesorios y errores de parseo (rango 0.3× – 3.2× del precio de referencia).
* **Reintentos con backoff** en HTTP y en el envío a Telegram (incluye el `retry_after` del rate limit).
* **Rotación de productos:** un producto no se repite durante 4 semanas (`ROTATION_COOLDOWN_WEEKS`).
* **Precios deterministas por semana:** ejecutar `/jueves` dos veces el mismo día da los mismos números.

### ⚠️ Nota importante y honesta sobre el scraping

Los endpoints de catálogo (`/api/catalog_system/pub/products/search` de VTEX y el
buscador WooCommerce) son los que usan habitualmente estas tiendas, pero **cada sitio
puede cambiar su plataforma, sus selectores o bloquear el tráfico automatizado en
cualquier momento**. Las tiendas sin catálogo público en línea (GBC, Los Hidalgos,
Medicar, El Javillar, San Judas Tadeo, Brasil, Bazar, Cristiana, Carrefour RD, Olé,
Aprezio, El Dragón de Oro, La Cadena) se resuelven siempre por referencia +
contingencia, y así se indica en el mensaje con 🟡/🔵.

**Verifica la capa 1 en tu servidor** ejecutando `/viernes` y luego `/estado`: ahí verás
cuántas cotizaciones salieron 🟢 y qué tiendas reportaron incidencias. Si una tienda
falla siempre, ajusta su fuente en la lista `SUPERMERCADOS` / `FARMACIAS` de `bot.py`
(clase `VtexSource` para tiendas VTEX, `HtmlSource` con selectores CSS para el resto).
El reporte se envía igual: **la rutina semanal nunca se cae por una web caída**.

---

## ⚙️ Personalización rápida

| Qué quieres cambiar | Dónde |
|---|---|
| Hora o día de los envíos | `.env`: `REPORT_HOUR`, `REPORT_MINUTE`, `PHARMACY_CRON_DAY`, `SUPERMARKET_CRON_DAY` |
| Cantidad de productos | `.env`: `PRODUCTS_PER_REPORT` |
| Lista de productos | `bot.py`: `FARMACIA_RAW` y `CANASTA_RAW` (nombre, presentación, precio de referencia, término de búsqueda) |
| Tiendas y su nivel de precio | `bot.py`: `FARMACIAS` y `SUPERMERCADOS` (`price_index`) |
| Apagar el scraping | `.env`: `ENABLE_SCRAPING=false` |
| Fuente de referencia pública | `.env`: `PROCONSUMIDOR_DATA_URL` |

## 📁 Archivos

```
bot_precios_rd/
├── bot.py                    # Código completo del bot
├── pruebas.py                # 10 pruebas automáticas (sin token ni internet)
├── requirements.txt          # Dependencias
├── .env.example              # Plantilla de configuración
├── Dockerfile                # Imagen para 24/7
├── docker-compose.yml        # Arranque con un comando
├── bot-precios-rd.service    # Servicio systemd para VPS
└── data/                     # Caché de precios e historial (se crea solo)
```

> 📌 Los precios marcados 🟡 son estimaciones de mercado, no cotizaciones oficiales.
> El mensaje siempre lo aclara al pie. Verifica en tienda antes de comprar.
