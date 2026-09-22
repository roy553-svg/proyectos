import type { Contrato, Rol, NodoTransaccion, Transaccion } from "./tipos";

/**
 * Escenario de la demo. Reproduce paso a paso las MISMAS transacciones que
 * ejecuta `BankLedger:demoCompleta` contra el ledger, con los conjuntos de
 * informees que Daml deriva de los signatarios y observadores de cada
 * contrato. Los saldos y los importes coinciden con los que verifican las
 * aserciones del script.
 */

const USD = (n: number) =>
  new Intl.NumberFormat("es-ES", { style: "currency", currency: "USD", maximumFractionDigits: 2 }).format(n);

const REGULADOR: Rol[] = ["central"];

let contador = 0;
const cid = (prefijo: string) => `00${prefijo}${(contador++).toString(16).padStart(4, "0")}c7de9f1a2b`;

// ---------------------------------------------------------------------------
// Contratos del escenario
// ---------------------------------------------------------------------------

const cuenta = (
  libro: Rol,
  accountId: string,
  nombre: string,
  saldo: number,
): Contrato => ({
  id: cid("a1"),
  plantilla: "InstitutionalAccount",
  libro,
  signatarios: [libro],
  observadores: REGULADOR,
  campos: {
    accountId,
    accountName: nombre,
    currency: "USD",
    balance: USD(saldo),
  },
});

const asiento = (
  libro: Rol,
  entryId: string,
  direccion: "DebitEntry" | "CreditEntry",
  clase: string,
  importe: number,
  contraparte: string,
  referencia: string,
  saldoTras: number,
): Contrato => ({
  id: cid("b2"),
  plantilla: "TransactionRecord",
  libro,
  signatarios: [libro],
  observadores: REGULADOR,
  campos: {
    entryId,
    direction: direccion,
    kind: clase,
    amount: USD(importe),
    counterparty: contraparte,
    reference: referencia,
    balanceAfter: USD(saldoTras),
  },
});

// Estado inicial de los tres libros.
const licenciaAlfa: Contrato = {
  id: cid("c3"),
  plantilla: "BankLicense",
  libro: "alfa",
  signatarios: ["alfa"],
  observadores: REGULADOR,
  campos: { legalName: "Banco Alfa S.A.", bic: "ALFAXXM0" },
};
const licenciaBeta: Contrato = {
  id: cid("c3"),
  plantilla: "BankLicense",
  libro: "beta",
  signatarios: ["beta"],
  observadores: REGULADOR,
  campos: { legalName: "Banco Beta S.A.", bic: "BETAXXM0" },
};
const licenciaGamma: Contrato = {
  id: cid("c3"),
  plantilla: "BankLicense",
  libro: "gamma",
  signatarios: ["gamma"],
  observadores: REGULADOR,
  campos: { legalName: "Banco Gamma S.A.", bic: "GAMMXXM0" },
};

const cuentaAlfa0 = cuenta("alfa", "ALFA-NOSTRO-USD", "Nostro USD - Tesoreria Alfa", 10_000_000);
const cuentaBeta0 = cuenta("beta", "BETA-NOSTRO-USD", "Nostro USD - Tesoreria Beta", 4_000_000);
const cuentaGamma0 = cuenta("gamma", "GAMMA-NOSTRO-USD", "Nostro USD - Tesoreria Gamma", 1_000_000);

const cuentaAlfa1 = cuenta("alfa", "ALFA-NOSTRO-USD", "Nostro USD - Tesoreria Alfa", 10_500_000);
const cuentaAlfa2 = cuenta("alfa", "ALFA-NOSTRO-USD", "Nostro USD - Tesoreria Alfa", 10_380_000);
const cuentaAlfa3 = cuenta("alfa", "ALFA-NOSTRO-USD", "Nostro USD - Tesoreria Alfa", 7_880_000);
const cuentaBeta1 = cuenta("beta", "BETA-NOSTRO-USD", "Nostro USD - Tesoreria Beta", 4_250_000);
const cuentaBeta2 = cuenta("beta", "BETA-NOSTRO-USD", "Nostro USD - Tesoreria Beta", 6_750_000);

const IMPORTE = 2_500_000;
const REFERENCIA = "Liquidacion RTGS mayorista";
const STL = "STL-2026-0001";

const propuesta: Contrato = {
  id: cid("d4"),
  plantilla: "InterbankTransferProposal",
  signatarios: ["alfa"],
  observadores: ["beta", "central"],
  campos: {
    settlementId: STL,
    amount: USD(IMPORTE),
    currency: "USD",
    senderAccountId: "ALFA-NOSTRO-USD",
    receiverAccountId: "BETA-NOSTRO-USD",
    reference: REFERENCIA,
  },
};

const mandato: Contrato = {
  id: cid("e5"),
  plantilla: "TransferMandate",
  signatarios: ["beta"],
  observadores: ["alfa", "central"],
  campos: {
    settlementId: STL,
    amount: USD(IMPORTE),
    currency: "USD",
    receiverAccountId: "BETA-NOSTRO-USD",
    reference: REFERENCIA,
  },
};

const enTransito: Contrato = {
  id: cid("f6"),
  plantilla: "FundsInTransit",
  signatarios: ["alfa", "beta"],
  observadores: REGULADOR,
  campos: {
    settlementId: STL,
    amount: USD(IMPORTE),
    currency: "USD",
    receiverAccountId: "BETA-NOSTRO-USD",
    reference: REFERENCIA,
  },
};

const recibo: Contrato = {
  id: cid("a7"),
  plantilla: "InterbankSettlementReceipt",
  signatarios: ["alfa", "beta"],
  observadores: REGULADOR,
  campos: {
    settlementId: STL,
    amount: USD(IMPORTE),
    currency: "USD",
    reference: REFERENCIA,
  },
};

// ---------------------------------------------------------------------------
// Constructores de nodos
// ---------------------------------------------------------------------------

let nodoSeq = 0;
const nid = () => `n${nodoSeq++}`;

const crear = (c: Contrato, detalle: string): NodoTransaccion => ({
  id: nid(),
  tipo: "creacion",
  titulo: `create ${c.plantilla}`,
  detalle,
  informees: [...c.signatarios, ...c.observadores],
  hijos: [],
  crea: c,
});

const ejercer = (
  titulo: string,
  detalle: string,
  informees: Rol[],
  hijos: NodoTransaccion[],
  archiva?: string,
): NodoTransaccion => ({
  id: nid(),
  tipo: archiva ? "ejercicio" : "ejercicio",
  titulo,
  detalle,
  informees,
  hijos,
  archiva,
});

const consultar = (titulo: string, detalle: string, informees: Rol[]): NodoTransaccion => ({
  id: nid(),
  tipo: "consulta",
  titulo,
  detalle,
  informees,
  hijos: [],
});

// ---------------------------------------------------------------------------
// Las transacciones, en orden
// ---------------------------------------------------------------------------

export const TRANSACCIONES: Transaccion[] = [
  // --- Paso 0: onboarding y operativa interna ------------------------------
  {
    id: "tx-0",
    paso: 0,
    titulo: "Onboarding de las tres instituciones",
    resumen:
      "Cada banco emite su licencia y abre su cuenta nostro. Tres libros independientes, sin ningun punto de contacto entre ellos.",
    emisor: "alfa",
    confirmadores: ["alfa", "beta", "gamma"],
    instante: "09:00:00",
    raiz: ejercer(
      "Alta de participantes",
      "Tres transacciones independientes, una por institucion.",
      ["alfa", "beta", "gamma", "central"],
      [
        crear(licenciaAlfa, "Licencia bancaria de Alfa."),
        crear(cuentaAlfa0, "Cuenta nostro de Alfa con saldo de apertura."),
        crear(licenciaBeta, "Licencia bancaria de Beta."),
        crear(cuentaBeta0, "Cuenta nostro de Beta con saldo de apertura."),
        crear(licenciaGamma, "Licencia bancaria de Gamma."),
        crear(cuentaGamma0, "Cuenta nostro de Gamma con saldo de apertura."),
      ],
    ),
  },
  {
    id: "tx-1",
    paso: 1,
    titulo: "Movimientos internos de Banco Alfa",
    resumen:
      "Alfa registra un deposito de cliente y una reposicion de encaje. Nadie fuera de Alfa y su regulador llega a saber que esto ha ocurrido.",
    emisor: "alfa",
    confirmadores: ["alfa"],
    instante: "09:12:04",
    raiz: ejercer(
      "exercise PostCredit / PostDebit on InstitutionalAccount",
      "Dos movimientos consecutivos sobre el libro propio de Alfa.",
      ["alfa", "central"],
      [
        crear(cuentaAlfa1, "Saldo tras abonar 500.000 USD de un cliente corporativo."),
        crear(
          asiento("alfa", "ALFA-0001", "CreditEntry", "ClientMovement", 500_000,
            "Cliente corporativo 8841", "Deposito nomina trimestral", 10_500_000),
          "Asiento inmutable del abono.",
        ),
        crear(cuentaAlfa2, "Saldo tras cargar 120.000 USD de tesoreria."),
        crear(
          asiento("alfa", "ALFA-0002", "DebitEntry", "TreasuryMovement", 120_000,
            "Mesa de dinero interna", "Reposicion de encaje", 10_380_000),
          "Asiento inmutable del cargo.",
        ),
      ],
      cuentaAlfa0.id,
    ),
  },
  {
    id: "tx-2",
    paso: 2,
    titulo: "Movimiento interno de Banco Beta",
    resumen: "Beta registra liquidez overnight en su propio libro, invisible para Alfa y Gamma.",
    emisor: "beta",
    confirmadores: ["beta"],
    instante: "09:18:41",
    raiz: ejercer(
      "exercise PostCredit on InstitutionalAccount",
      "Movimiento sobre el libro propio de Beta.",
      ["beta", "central"],
      [
        crear(cuentaBeta1, "Saldo tras abonar 250.000 USD."),
        crear(
          asiento("beta", "BETA-0001", "CreditEntry", "TreasuryMovement", 250_000,
            "Mesa de dinero interna", "Liquidez overnight", 4_250_000),
          "Asiento inmutable del abono.",
        ),
      ],
      cuentaBeta0.id,
    ),
  },

  // --- Paso 3: propuesta ---------------------------------------------------
  {
    id: "tx-3",
    paso: 3,
    titulo: "1. Alfa propone la transferencia",
    resumen:
      "Primer contacto entre las dos instituciones. Observe el detalle decisivo: Beta recibe la propuesta, pero NO el nodo padre que se ejecuto sobre la cuenta de Alfa.",
    emisor: "alfa",
    confirmadores: ["alfa"],
    instante: "10:02:00",
    raiz: ejercer(
      "exercise ProposeInterbankTransfer on InstitutionalAccount",
      "Se comprueba la disponibilidad de fondos CONTRA EL SALDO DE ALFA. Este nodo solo lo ven Alfa y su regulador.",
      ["alfa", "central"],
      [crear(propuesta, "Propuesta firmada por Alfa y dirigida a Beta. Ningun fondo se ha movido.")],
    ),
  },

  // --- Paso 4: mandato -----------------------------------------------------
  {
    id: "tx-4",
    paso: 4,
    titulo: "2. Beta valida y se compromete",
    resumen:
      "Beta contrasta la operacion con sus limites de contraparte y emite un mandato vinculante. Sigue sin moverse un centavo.",
    emisor: "beta",
    confirmadores: ["alfa", "beta"],
    instante: "10:02:11",
    raiz: ejercer(
      "exercise AcceptProposal on InterbankTransferProposal",
      "Consume la propuesta. Ambas partes confirman criptograficamente.",
      ["alfa", "beta", "central"],
      [crear(mandato, "Compromiso firmado por Beta, observado por Alfa.")],
      propuesta.id,
    ),
  },

  // --- Paso 5: fondeo ------------------------------------------------------
  {
    id: "tx-5",
    paso: 5,
    titulo: "3. Fondeo atomico",
    resumen:
      "Una sola transaccion indivisible: se carga la cuenta de Alfa, se escribe su asiento privado y nace el valor en transito. Beta CONFIRMA esta transaccion sin poder ver el saldo ni el asiento de Alfa.",
    emisor: "alfa",
    confirmadores: ["alfa", "beta"],
    instante: "10:02:14",
    raiz: ejercer(
      "exercise FundTransfer on TransferMandate",
      "Autoridad combinada: Beta por firmar el mandato, Alfa por controlar la choice.",
      ["alfa", "beta", "central"],
      [
        ejercer(
          "exercise PostDebit on InstitutionalAccount",
          "SUBARBOL PRIVADO DE ALFA. Beta confirma la transaccion que lo contiene, pero jamas recibe estos nodos.",
          ["alfa", "central"],
          [
            crear(cuentaAlfa3, "Saldo de Alfa tras el cargo de 2.500.000 USD."),
            crear(
              asiento("alfa", "STL-2026-0001-OUT", "DebitEntry", "InterbankOutgoing", IMPORTE,
                REFERENCIA, REFERENCIA, 7_880_000),
              "Asiento de salida en el libro de Alfa.",
            ),
          ],
          cuentaAlfa2.id,
        ),
        crear(enTransito, "Valor en transito firmado por AMBOS bancos. Revela el importe, nada mas."),
      ],
      mandato.id,
    ),
  },

  // --- Paso 6: liquidacion -------------------------------------------------
  {
    id: "tx-6",
    paso: 6,
    titulo: "4. Liquidacion final",
    resumen:
      "Beta consume el valor en transito y acredita su libro. Simetricamente, Alfa confirma la transaccion sin ver el saldo de Beta. Se emite el recibo bilateral.",
    emisor: "beta",
    confirmadores: ["alfa", "beta"],
    instante: "10:02:16",
    raiz: ejercer(
      "exercise SettleIncoming on FundsInTransit",
      "Consume el valor en transito con la autoridad de ambas instituciones.",
      ["alfa", "beta", "central"],
      [
        consultar(
          "fetch InstitutionalAccount",
          "Beta valida SU PROPIA cuenta. En ningun momento se consulta el libro de Alfa.",
          ["beta", "central"],
        ),
        ejercer(
          "exercise PostCredit on InstitutionalAccount",
          "SUBARBOL PRIVADO DE BETA. Alfa confirma la transaccion que lo contiene, pero jamas recibe estos nodos.",
          ["beta", "central"],
          [
            crear(cuentaBeta2, "Saldo de Beta tras el abono de 2.500.000 USD."),
            crear(
              asiento("beta", "STL-2026-0001-IN", "CreditEntry", "InterbankIncoming", IMPORTE,
                REFERENCIA, REFERENCIA, 6_750_000),
              "Asiento de entrada en el libro de Beta.",
            ),
          ],
          cuentaBeta1.id,
        ),
        crear(recibo, "Prueba bilateral de no repudio. Unico contrato comun a los dos libros."),
      ],
      enTransito.id,
    ),
  },
];

export const PASO_MAXIMO = TRANSACCIONES.length - 1;

/** Contratos activos tras aplicar las transacciones hasta `paso` inclusive. */
export function estadoEn(paso: number): Contrato[] {
  const activos = new Map<string, Contrato>();
  for (const tx of TRANSACCIONES.filter((t) => t.paso <= paso)) {
    const nodos = [tx.raiz, ...tx.raiz.hijos.flatMap(function bajar(n): NodoTransaccion[] {
      return [n, ...n.hijos.flatMap(bajar)];
    })];
    for (const nodo of nodos) {
      if (nodo.archiva) activos.delete(nodo.archiva);
      if (nodo.crea) activos.set(nodo.crea.id, nodo.crea);
    }
  }
  return [...activos.values()];
}

export const DATOS_OPERACION = {
  settlementId: STL,
  importe: IMPORTE,
  importeFormateado: USD(IMPORTE),
  referencia: REFERENCIA,
  divisa: "USD",
};
