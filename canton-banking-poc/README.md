# Libro de transacciones interbancario sobre Canton Network

**Prueba de concepto ejecutable** de un libro contable segregado por institución,
con liquidación interbancaria atómica y auditoría regulatoria, construida en
**Daml 3.4 / Canton 3.4**.

Está pensada para responder, delante de un comité de dirección y de un CISO, a
una pregunta concreta: *¿por qué un banco pondría operaciones reales sobre un
libro distribuido, después de una década descartando blockchain por motivos de
confidencialidad?*

La respuesta de esta demo no es una lámina. Es un modelo que compila, una red
de cuatro nodos que arranca, y una comprobación que se ejecuta delante del
público.

---

## 1. Qué demuestra, y con qué evidencia

| Afirmación | Cómo se comprueba aquí |
|---|---|
| Cada institución tiene su propio libro y nadie más lo lee | `daml test` y consulta directa a los cuatro nodos |
| Una transferencia entre dos bancos es atómica y bilateral | Aserciones de conservación del valor en el script |
| La contraparte valida sin ver el libro ajeno | Árbol de transacción nodo a nodo en la interfaz |
| Un tercero no se entera de que la operación existe | El nodo de Banco Gamma devuelve 2 contratos, los suyos |
| El regulador audita sin poder ejecutar | `submitMustFail` sobre un intento de cargo del supervisor |

Resultado real de `scripts/verificar-privacidad.py` contra la red levantada,
después de ejecutar una transferencia de 2.500.000 USD de Alfa a Beta:

```
NODO            CONTRATOS  PLANTILLAS
------------------------------------------------------------------------------
Banco Alfa      6          BankLicense x1, InstitutionalAccount x1,
                           InterbankSettlementReceipt x1, TransactionRecord x3
                           libros visibles: BancoAlfa
Banco Beta      5          BankLicense x1, InstitutionalAccount x1,
                           InterbankSettlementReceipt x1, TransactionRecord x2
                           libros visibles: BancoBeta
Banco Gamma     2          BankLicense x1, InstitutionalAccount x1
                           libros visibles: BancoGamma
Banco Central   12         BankLicense x3, InstitutionalAccount x3,
                           InterbankSettlementReceipt x1, TransactionRecord x5
                           libros visibles: BancoAlfa, BancoBeta, BancoGamma
```

Léase despacio la fila de Banco Beta. **Beta fue la contraparte de la
operación**, la confirmó criptográficamente con su propio nodo, y no tiene ni
un solo contrato del libro de Alfa en su almacén. No es que estén cifrados a la
espera de una clave: nunca se le enviaron.

Banco Gamma, un banco que opera en la misma red y en el mismo sincronizador, no
tiene forma de saber que la transferencia ocurrió.

---

## 2. Arrancar la demo

### Requisitos

- Daml SDK **3.4.11** (incluye el binario de Canton).
  El instalador clásico avisa de que quedará sustituido por DPM en la 3.5; para
  esta demo sirve igual.
- Java 17 o superior, Node.js 20 o superior, Python 3 para el verificador.

```bash
curl -sSL https://github.com/digital-asset/daml/releases/download/v3.4.11/daml-sdk-3.4.11-linux.tar.gz \
  | tar xz && ./sdk-3.4.11/install.sh
export PATH="$PATH:$HOME/.daml/bin"
```

### Tres comandos

```bash
daml build                      # compila el modelo
daml test                       # ejecuta las siete verificaciones formales
./scripts/levantar-red.sh       # en otra terminal: arranca los cuatro nodos
./scripts/ejecutar-demo.sh      # opera sobre la red real y comprueba privacidad
```

`ejecutar-demo.sh` puede repetirse sobre la misma red: cada ejecución abre un
juego nuevo de libros, así que los conteos crecen, y las comprobaciones de
privacidad se expresan como propiedades ("todo lo que ve Gamma es suyo") y no
como números fijos, de modo que siguen siendo válidas.

Si modifica el modelo, **reinicie la red** antes de volver a ejecutar: desde
Daml 3.3 un participante rechaza dos paquetes con el mismo nombre y versión, de
forma que un DAR recompilado no puede sustituir al ya desplegado.

### La interfaz ejecutiva

```bash
cd frontend && npm install && npm run dev      # http://localhost:5173
```

Funciona sin la red arrancada, en modo demostración, con el mismo escenario e
idénticos importes que verifica el script. Con la red viva, la pestaña
**Red en vivo** consulta la API JSON de los cuatro nodos y muestra datos
auténticos.

---

## 3. El guion de la demo, paso a paso

Cinco minutos delante de un comité:

1. **Abra la interfaz como *Banco Beta*, pestaña *Privacidad de sub-transacción*,
   paso 3 (*Fondeo atómico*).** El panel dice «2 de 5 nodos». Beta confirmó esa
   transacción y solo recibió dos de sus cinco nodos. Los tres opacos son el
   cargo en la cuenta de Alfa y su asiento contable.
2. **Cambie la perspectiva a *Banco Alfa*.** Los mismos nodos, ahora legibles.
   Es la misma transacción vista desde el otro nodo participante.
3. **Cambie a *Banco Gamma*.** La transacción entera desaparece de la lista.
4. **Cambie a *Banco Central*.** Todo visible. Y en el modelo, un intento de
   ejercer un cargo por parte del supervisor está cubierto por un
   `submitMustFail`: ve, pero no puede mover.
5. **Pestaña *Red en vivo*.** Ya no es la simulación: son los cuatro nodos
   respondiendo sobre su propio almacén.

La pregunta que suele aparecer aquí es *«¿y esto no es simplemente una base de
datos con permisos?»*. No: en el paso 3, el nodo de Beta **participa en el
consenso** de una transacción cuyo contenido no puede leer, y su firma es
indispensable para que se comprometa. Un sistema de permisos sobre una base de
datos compartida necesita un administrador con acceso total; aquí no existe tal
figura.

---

## 4. Arquitectura

```
                       ┌──────────────────────────────┐
                       │      Sincronizador           │
                       │  secuenciador + mediador     │
                       │  ordena sobres cifrados      │
                       │  NO puede leer su contenido  │
                       └──────────────┬───────────────┘
                                      │
         ┌───────────────┬────────────┴────────────┬──────────────┐
         │               │                         │              │
   ┌─────┴─────┐   ┌─────┴─────┐            ┌──────┴─────┐  ┌─────┴──────┐
   │ Banco     │   │ Banco     │            │ Banco      │  │ Banco      │
   │ Alfa      │   │ Beta      │            │ Gamma      │  │ Central    │
   │ :5011     │   │ :5021     │            │ :5031      │  │ :5041      │
   │ libro     │   │ libro     │            │ libro      │  │ observador │
   │ propio    │   │ propio    │            │ propio     │  │ de los 3   │
   └───────────┘   └───────────┘            └────────────┘  └────────────┘
```

Cada nodo participante guarda **únicamente** los contratos en los que su
institución figura como signataria u observadora. No hay estado global
replicado. El sincronizador aporta orden y entrega, no visibilidad.

### Los contratos

| Plantilla | Firman | Observan | Papel |
|---|---|---|---|
| `BankLicense` | banco | regulador | Habilita a abrir cuentas |
| `InstitutionalAccount` | banco | regulador | Saldo vivo, único contrato mutable |
| `TransactionRecord` | banco | regulador | Asiento inmutable, sin ninguna choice |
| `InterbankTransferProposal` | emisor | receptor, regulador | Propuesta, sin fondos comprometidos |
| `TransferMandate` | receptor | emisor, regulador | Compromiso vinculante del receptor |
| `FundsInTransit` | **emisor y receptor** | regulador | Valor en tránsito, firma conjunta |
| `InterbankSettlementReceipt` | **emisor y receptor** | regulador | No repudio bilateral |

La segregación no es una lista de control de acceso que alguien pueda
reconfigurar: es el conjunto de signatarios de cada contrato, y el motor de
Daml rechaza cualquier transacción que no lo respete.

### El ciclo de liquidación

```
  (1) PROPUESTA    Alfa firma, Beta observa       ningún fondo se mueve
  (2) MANDATO      Beta firma, Alfa observa       compromiso vinculante
  (3) FONDEO       Alfa y Beta firman             ATÓMICO: cargo + asiento + tránsito
  (4) LIQUIDACIÓN  Alfa y Beta firman             ATÓMICO: abono + asiento + recibo
```

**Por qué cuatro pasos y no uno.** Es la decisión de diseño más importante del
modelo, y se descubrió ejecutando, no razonando sobre el papel. En Canton, el
nodo que **somete** una transacción debe poder leer todos sus contratos de
entrada. Un primer diseño permitía a Beta debitar directamente la cuenta de
Alfa en una sola transacción; el motor lo rechazó:

```
Attempt to fetch or exercise a contract not visible to the reading parties.
Contract:  #7:1 (BankLedger:InstitutionalAccount)
actAs: 'BancoBeta'
Disclosed to: 'BancoAlfa', 'BancoCentral'
```

Ese rechazo **es** la garantía de privacidad funcionando. Para que Beta pudiera
liquidar en un solo paso, su nodo necesitaría el saldo completo de Alfa. El
modelo correcto hace que el valor salga del libro de Alfa hacia un contrato
portador firmado por ambos, y es ese contrato —no el saldo de Alfa— lo que Beta
liquida.

La garantía económica es exacta: en todo momento el importe está en **uno y solo
uno** de los tres lugares (libro de Alfa, tránsito, libro de Beta). El script lo
verifica en el estado intermedio, no solo al final:

```haskell
(alfaEnTransito.balance + valorEnTransito.amount + betaEnTransito.balance)
  === totalInicial
```

Conviene decirlo con precisión ante una audiencia técnica: la transferencia
completa son **dos transacciones atómicas** encadenadas por un contrato
portador, no una sola. Cada una es indivisible y ambas requieren la
confirmación criptográfica de los dos bancos. No existe ningún instante en que
el dinero esté duplicado o perdido, que es la propiedad que importa para
liquidación.

---

## 5. Verificación formal incluida

Siete scripts de `Daml.Script` que se ejecutan con `daml test`:

| Script | Qué fija |
|---|---|
| `demoCompleta` | Ciclo completo, conservación del valor, privacidad en ambos sentidos, auditoría |
| `demoEnRedReal` | El mismo escenario contra los cuatro nodos Canton reales |
| `testTerceroNoPuedeInterferir` | Ni Gamma ni el propio emisor pueden liquidar; Beta no puede desviar fondos |
| `testDevolucionDeFondos` | El valor devuelto regresa íntegro; Beta nunca vio su libro incrementado |
| `testRechazoTemprano` | Rechazo sin consecuencia patrimonial, con constancia para el regulador |
| `testPropuestaSinFondos` | No se propone lo que no se puede honrar |
| `testCancelacionYRetiro` | Ambas partes pueden abandonar antes del fondeo |

No son documentación: si alguien rompe una garantía editando el modelo, estos
scripts fallan.

---

## 6. Comparación con las alternativas

Esta sección se ciñe a diferencias arquitectónicas comprobables, no a
preferencias de producto.

### Frente a Ethereum y las L1 públicas

El estado es global y replicado: todo validador ejecuta y almacena toda
transacción. La confidencialidad exige superponer pruebas de conocimiento cero o
sacar los datos fuera de la cadena, lo que reintroduce el problema que se
quería resolver. Además existe una **mempool pública**: las órdenes son visibles
antes de liquidarse, con el consiguiente riesgo de adelantamiento. En Canton no
hay cola global; una orden no llega nunca al nodo de quien no participa, de modo
que el front-running carece de superficie.

### Frente a Hyperledger Fabric

Fabric segmenta con *channels* y *private data collections*. La privacidad es de
grano grueso: el canal. Dos contrapartes que necesiten confidencialidad mutua
requieren su propio canal, y el número de canales crece de forma combinatoria
con las relaciones bilaterales. Lo más limitante es que **la atomicidad no cruza
canales**: una operación que toque dos canales necesita orquestación externa,
que es precisamente donde aparece el riesgo de liquidación. Canton mantiene
atomicidad entre aplicaciones e instituciones sin renunciar al need-to-know.

### Frente a R3 Corda

Es el competidor más próximo: también evita el estado global y comparte punto a
punto. La diferencia decisiva es la **resolución de backchain**. Para validar un
estado recibido, un nodo Corda debe obtener y verificar las transacciones que lo
originaron, de modo que hereda historial: propietarios anteriores del activo y
detalles de operaciones pasadas. La propia documentación de R3 lo trata como un
*privacy hazard* y ofrece mitigaciones —reemisión de estados, notarios no
validadores, SGX—, cada una con su coste.

Canton no necesita recorrer el backchain: la validación la realizan los
interesados en el momento del compromiso, y el sincronizador aporta el orden. Un
participante recibe las porciones de transacción que le conciernen y nada más,
**hoy y también retrospectivamente**. En esta demo eso es visible: el recibo de
liquidación que comparten Alfa y Beta no arrastra consigo el historial de
movimientos internos de Alfa.

### Resumen

| | Ethereum | Fabric | Corda | **Canton** |
|---|---|---|---|---|
| Unidad de privacidad | ninguna | canal | transacción | **sub-transacción** |
| Historial heredado al recibir | todo público | por canal | backchain completo | **nada** |
| Atomicidad entre dominios | sí, todo público | no entre canales | limitada | **sí, con privacidad** |
| Cola pública de órdenes | sí | no | no | **no** |
| Finalidad | probabilística | determinista | determinista | **determinista** |

---

## 7. Por qué los grandes bancos lo están adoptando

Hechos públicos, con su fuente. Las cifras son las reportadas por las propias
entidades o por prensa especializada; no han sido auditadas de forma
independiente en este documento.

- **Broadridge** opera sobre tecnología Canton su plataforma de repos DLR, que
  en junio de 2026 procesó **7,5 billones de dólares** con una media diaria de
  357.000 millones ([Broadridge](https://www.broadridge.com/press-release/2026/broadridges-dlr-processes-over-7-trillion-in-june)).
  Es el argumento más difícil de rebatir: ya no es un piloto.
- **BNP Paribas y HSBC** se incorporaron a la red en septiembre de 2025, después
  de que lo hicieran **Goldman Sachs**, Hong Kong FMI Services y Moody's Ratings
  ([CoinDesk](https://www.coindesk.com/business/2025/09/09/bnp-paribas-and-hsbc-join-privacy-focused-blockchain-canton)).
- **JPMorgan** anunció en enero de 2026 el despliegue nativo de su token de
  depósito sobre Canton.
- La red nació de un consorcio en el que figuran **Goldman Sachs, BNP Paribas,
  DTCC, Deloitte** y otros
  ([nota de prensa fundacional](https://www.canton.network/canton-network-press-releases/canton-network-press-release)).

El patrón es constante: entidades que llevaban años rechazando libros
distribuidos por confidencialidad, adoptándolos cuando la confidencialidad pasó
a ser una propiedad del lenguaje en lugar de una capa añadida.

---

## 8. Limitaciones honestas de esta PoC

Conviene anticiparlas: un CISO las encontrará igualmente, y es mejor que salgan
de quien presenta.

- **Almacenamiento en memoria.** Los cuatro nodos usan `storage.type = memory`.
  En producción, cada institución opera su PostgreSQL y sus claves en HSM/KMS.
- **Sin autenticación.** La API del ledger está abierta para simplificar la
  demo. Una instalación real exige JWT, mTLS y gestión de usuarios.
- **Secuenciador de referencia.** Se usa el monolítico de desarrollo. En la red
  real el secuenciador es BFT y está distribuido entre operadores, que es lo que
  elimina el punto único de confianza en el ordenamiento.
- **Cuatro nodos en una sola JVM y una sola máquina.** Suficiente para probar
  las propiedades de privacidad, no para medir latencia ni rendimiento.
- **Sin ciclo de vida de actualización.** Un despliegue real usa *Smart Contract
  Upgrades* para evolucionar los contratos sin interrumpir el servicio.
- **Modelo contable deliberadamente simple.** Una sola divisa por cuenta, sin
  partida doble completa, sin husos horarios ni días valor, sin conciliación con
  los sistemas centrales.
- **Los scripts conviven con el modelo en el mismo paquete.** El DAR depende
  por ello de `daml-script`, y el compilador lo advierte
  (`-Wtemplate-interface-depends-on-daml-script`): obligaría a desplegar la
  librería de pruebas en los nodos productivos. Se mantiene así para que la
  demo sea un único proyecto legible; el remedio en producción es extraer las
  pruebas a un paquete propio que dependa del modelo, con `multi-package.yaml`.
- **`Decimal` para importes.** Correcto en Daml, que usa decimal de precisión
  fija y no coma flotante binaria, pero un sistema real necesita además política
  explícita de redondeo por divisa.

Ninguna de estas limitaciones afecta a la propiedad que la demo pretende
probar. Todas son trabajo conocido de industrialización.

---

## 9. Qué haría falta para llevarlo a producción

1. **Piloto bilateral** con una contraparte real sobre un caso acotado y de bajo
   riesgo: confirmación de saldos nostro, conciliación, o repos intradía.
2. **Integración con el core bancario** mediante la API del ledger, tratando
   Canton como registro de liquidación y no como sustituto del sistema central.
3. **Custodia de claves en HSM** y separación de funciones sobre las identidades
   que autorizan movimientos.
4. **Incorporación del supervisor** como observador, que es un cambio normativo
   y contractual antes que técnico.
5. **Conexión al Global Synchronizer** cuando se busque componer con activos y
   aplicaciones de terceros de forma atómica.

---

## 10. Estructura del repositorio

```
canton-banking-poc/
├── daml.yaml                      Proyecto Daml 3.4.11
├── daml/BankLedger.daml           Modelo completo + 7 scripts de verificación
├── canton.conf                    Sincronizador + 4 nodos participantes
├── canton/bootstrap.canton        Arranque, despliegue e identidades
├── scripts/
│   ├── levantar-red.sh            Arranca la red local
│   ├── ejecutar-demo.sh           Opera sobre la red real y verifica
│   └── verificar-privacidad.py    Interroga los 4 nodos y emite veredicto
└── frontend/                      Interfaz ejecutiva React + TypeScript + Tailwind
    └── src/
        ├── lib/escenario.ts       Escenario con informees derivados del modelo
        ├── lib/ledgerReal.ts      Cliente de la API JSON v2 de Canton
        └── components/            Árbol de transacción, libro, panel criptográfico
```

### Nota para quien venga de Daml 2.x

**Daml 3.x no soporta contract keys.** El compilador rechaza `key`/`maintainer`
con un error explícito. Las referencias entre contratos son, por tanto,
explícitas por `ContractId`, que es como está escrito este modelo.

---

## Licencia y alcance

Material de demostración, sin garantía de idoneidad para uso en producción.
Las marcas citadas pertenecen a sus titulares; su mención documenta adopción
pública de la tecnología y no implica relación alguna con este trabajo.
