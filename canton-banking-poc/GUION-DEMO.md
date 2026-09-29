# Guion de demostración

Cómo presentar esta PoC ante un comité de dirección o un equipo de
ciberseguridad. Dos versiones según la audiencia, con los tiempos y los clics
exactos.

---

## Antes de entrar en la sala (15 minutos)

```bash
cd canton-banking-poc
daml build && daml test          # confirma que todo pasa
./scripts/levantar-red.sh        # terminal 1 — tarda 60-120 s
./scripts/ejecutar-demo.sh       # terminal 2 — deja datos en los nodos
cd frontend && npm run dev       # terminal 3 — http://localhost:5173
```

Tres cosas que se olvidan y arruinan la demo:

- **Ejecuta la demo antes de presentar.** Si no, los nodos están vacíos y la
  pestaña *Red en vivo* no enseña nada.
- **Los nodos son en memoria.** Si el portátil se suspende o muere el proceso,
  se pierde el estado. Ensaya el reinicio completo al menos una vez.
- **No toques el modelo durante la presentación.** Un DAR recompilado exige
  reiniciar la red: desde Daml 3.3 un participante rechaza dos paquetes con el
  mismo nombre y versión.

---

## Versión comité de dirección — 5 minutos

**No empieces por el terminal.** Abre el navegador.

### 1. Plantea la pregunta · 30 s

> «Llevamos una década descartando blockchain por confidencialidad.
> ¿Qué ha cambiado?»

### 2. Perspectiva Banco Beta, pestaña *Privacidad*, paso 3 · 90 s

Señala el contador: **«2 de 5 nodos»**.

> «Banco Beta acaba de confirmar criptográficamente esta transacción. Su firma
> era imprescindible para que se liquidara. Y de los cinco nodos que la
> componen, solo ha recibido dos. Esos tres bloques opacos son el cargo en la
> cuenta de Alfa y su asiento contable.»

### 3. Cambia a Banco Alfa · 30 s

Un clic. Los mismos nodos, ahora legibles.

**Este es el momento que convence**: misma transacción, distinto nodo, distinta
visibilidad. No lo expliques; deja que lo vean.

### 4. Cambia a Banco Gamma · 30 s

La transacción desaparece de la lista.

> «Gamma opera en la misma red y en el mismo sincronizador. No tiene forma de
> saber que esto ocurrió.»

### 5. Cambia a Banco Central · 30 s

Todo visible.

> «Ve porque los contratos lo declaran observador. Y no puede mover un céntimo:
> el modelo incluye una prueba que lo verifica.»

### 6. Pestaña *Red en vivo* · 60 s

El cierre:

> «Esto ya no es la simulación. Son cuatro nodos Canton respondiendo sobre su
> propio almacén. Alfa: 6 contratos. Beta: 5. Y ninguno de esos 5 es del libro
> de Alfa.»

---

## Versión CISO y arquitectos — 20 minutos

Aquí sí se empieza por el terminal, y se invierte el orden.

### 1. `daml test` delante de ellos

Siete scripts en verde.

> «Si alguien rompe una garantía editando el modelo, esto falla.»

### 2. Abre `daml/BankLedger.daml` y enseña tres líneas

```haskell
template InstitutionalAccount
  where
    signatory bank        -- toda la segregación está aquí
    observer auditors
```

> «La segregación no es una lista de control de acceso que un administrador
> pueda reconfigurar. Es el signatario del contrato, y el motor rechaza
> cualquier transacción que no lo respete.»

### 3. El momento más creíble para esta audiencia: enseña el error

Está en el README, sección 4:

```
Attempt to fetch or exercise a contract not visible to the reading parties.
actAs: 'BancoBeta'   Disclosed to: 'BancoAlfa', 'BancoCentral'
```

> «El primer diseño era el obvio: Beta debita la cuenta de Alfa en una sola
> transacción atómica. La plataforma lo rechazó. No es que la aplicación sea
> cuidadosa: es que el motor no deja. Ese rechazo es la garantía funcionando.»

Un responsable de seguridad valora mucho más una plataforma que le impide
equivocarse que una que confía en que el desarrollador acierte.

### 4. `python3 scripts/verificar-privacidad.py` en vivo

La tabla y el veredicto, sobre los cuatro nodos reales.

### 5. Saca tú las limitaciones

Sección 8 del README: memoria, sin autenticación, secuenciador de referencia.

Si las sacan ellos, pierdes; si las sacas tú, ganas credibilidad para todo lo
demás.

---

## Las cinco objeciones que van a salir

### «Esto es una base de datos con permisos»

La respuesta está en el paso 3: el nodo de Beta participa en el consenso de una
transacción cuyo contenido no puede leer, y su firma es indispensable. Una base
de datos con permisos necesita un administrador con acceso total. Aquí no existe
esa figura.

### «¿Y si el operador del sincronizador es malicioso?»

Sé preciso, no defensivo: puede **censurar o retrasar** mensajes — es un riesgo
de disponibilidad. No puede **leerlos**: recibe sobres cifrados extremo a
extremo. Por eso en producción el secuenciador es BFT y está repartido entre
operadores. Esa distinción entre confidencialidad y disponibilidad te la van a
agradecer.

### «¿Rendimiento?»

Esta PoC no prueba nada de rendimiento: cuatro nodos en una sola JVM. El dato de
producción es Broadridge, con 357.000 millones de dólares diarios en repos sobre
Canton.

### «¿Es atómico de verdad?»

No digas que sí a secas. Son **dos transacciones atómicas encadenadas** por un
contrato portador firmado por ambos bancos. En ningún instante el dinero está
duplicado ni perdido, que es lo que importa para liquidación. Si dices «una sola
transacción», un arquitecto lo verá en el código y perderás el resto de la
sesión.

### «¿Qué pasa si un banco se cae?»

Honestidad: no está demostrado aquí. Canton lo resuelve; esta PoC no lo enseña.

---

## Plan B

Si Canton no arranca —conflicto de puertos, memoria insuficiente—, **el frontend
funciona solo**, en modo demostración, con el mismo escenario e idénticos
importes. Se pierde la pestaña *Red en vivo*.

Si alguien pregunta, dilo: «ahora mismo es la simulación; la verificación contra
nodos reales la enseño después». Fingirlo se nota.
