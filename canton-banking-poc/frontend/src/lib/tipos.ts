/** Identidades que pueden adoptarse como perspectiva en la demo. */
export type Rol = "alfa" | "beta" | "gamma" | "central";

export interface Institucion {
  rol: Rol;
  nombre: string;
  nombreCorto: string;
  bic: string;
  papel: string;
  acento: string;      // clase Tailwind de color de acento
  puertoJson: number;  // API JSON de su nodo participante
  rutaProxy: string;   // ruta del proxy de Vite hacia ese nodo
}

export const INSTITUCIONES: Record<Rol, Institucion> = {
  alfa: {
    rol: "alfa",
    nombre: "Banco Alfa S.A.",
    nombreCorto: "Banco Alfa",
    bic: "ALFAXXM0",
    papel: "Institucion emisora",
    acento: "cyan",
    puertoJson: 5013,
    rutaProxy: "/api/alfa",
  },
  beta: {
    rol: "beta",
    nombre: "Banco Beta S.A.",
    nombreCorto: "Banco Beta",
    bic: "BETAXXM0",
    papel: "Institucion receptora",
    acento: "violet",
    puertoJson: 5023,
    rutaProxy: "/api/beta",
  },
  gamma: {
    rol: "gamma",
    nombre: "Banco Gamma S.A.",
    nombreCorto: "Banco Gamma",
    bic: "GAMMXXM0",
    papel: "Tercero no implicado",
    acento: "amber",
    puertoJson: 5033,
    rutaProxy: "/api/gamma",
  },
  central: {
    rol: "central",
    nombre: "Banco Central",
    nombreCorto: "Banco Central",
    bic: "CENTXXM0",
    papel: "Regulador, solo lectura",
    acento: "emerald",
    puertoJson: 5043,
    rutaProxy: "/api/central",
  },
};

export const ROLES: Rol[] = ["alfa", "beta", "gamma", "central"];

/** Un contrato activo en el ledger, con su conjunto de informees. */
export interface Contrato {
  id: string;
  plantilla: string;
  /** Institucion cuyo libro contable representa, si aplica. */
  libro?: Rol;
  signatarios: Rol[];
  observadores: Rol[];
  campos: Record<string, string>;
}

/** Quien tiene derecho a ver un contrato. */
export const informeesDe = (c: Contrato): Rol[] => [...c.signatarios, ...c.observadores];

export type TipoNodo = "ejercicio" | "creacion" | "consulta" | "archivado";

/**
 * Un nodo del arbol de una transaccion Daml. La privacidad de Canton opera a
 * ESTE nivel: cada nodo tiene su propio conjunto de informees, y un
 * participante recibe unicamente los nodos en los que figura.
 */
export interface NodoTransaccion {
  id: string;
  tipo: TipoNodo;
  titulo: string;
  detalle: string;
  informees: Rol[];
  hijos: NodoTransaccion[];
  /** Contrato creado por este nodo, si es una creacion. */
  crea?: Contrato;
  /** Identificador del contrato archivado por este nodo, si consume. */
  archiva?: string;
}

export interface Transaccion {
  id: string;
  paso: number;
  titulo: string;
  resumen: string;
  /** Institucion que somete la transaccion a la red. */
  emisor: Rol;
  /** Nodos participantes que deben confirmar criptograficamente. */
  confirmadores: Rol[];
  instante: string;
  raiz: NodoTransaccion;
}

/** Recorre el arbol de una transaccion en preorden. */
export function recorrer(nodo: NodoTransaccion): NodoTransaccion[] {
  return [nodo, ...nodo.hijos.flatMap(recorrer)];
}

/** Nodos de una transaccion que un rol concreto llega a ver. */
export function nodosVisibles(tx: Transaccion, rol: Rol): NodoTransaccion[] {
  return recorrer(tx.raiz).filter((n) => n.informees.includes(rol));
}

/** Una transaccion es invisible para quien no figura en ninguno de sus nodos. */
export function transaccionVisible(tx: Transaccion, rol: Rol): boolean {
  return nodosVisibles(tx, rol).length > 0;
}
