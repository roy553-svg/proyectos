import { INSTITUCIONES, type Rol } from "./tipos";

/**
 * Cliente de la API JSON v2 de Canton. Consulta el almacen de contratos de
 * CADA nodo participante por separado, que es justamente lo que permite
 * comprobar la privacidad sobre datos reales en lugar de simulados.
 */

export interface ContratoReal {
  contractId: string;
  templateId: string;
  plantilla: string;
  signatarios: string[];
  observadores: string[];
  testigos: string[];
  campos: Record<string, unknown>;
  creadoEn: string;
  offset: number;
}

export interface EstadoNodo {
  rol: Rol;
  disponible: boolean;
  error?: string;
  offset?: number;
  contratos: ContratoReal[];
}

const pedir = async <T,>(ruta: string, cuerpo?: unknown): Promise<T> => {
  const respuesta = await fetch(ruta, {
    method: cuerpo === undefined ? "GET" : "POST",
    headers: { "Content-Type": "application/json" },
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
  });
  if (!respuesta.ok) {
    throw new Error(`${respuesta.status} ${respuesta.statusText}`);
  }
  return (await respuesta.json()) as T;
};

/** Identificador de Party de cada institucion, leido del propio nodo. */
async function partyDe(base: string, prefijo: string): Promise<string | undefined> {
  const datos = await pedir<{ partyDetails?: { party: string }[] }>(
    `${base}/v2/parties`,
  );
  return datos.partyDetails?.map((p) => p.party).find((p) => p.startsWith(prefijo));
}

/** Contratos activos que ESTE nodo tiene para SU propia party. */
export async function leerNodo(rol: Rol): Promise<EstadoNodo> {
  const institucion = INSTITUCIONES[rol];
  const base = institucion.rutaProxy;
  try {
    const prefijo = { alfa: "BancoAlfa", beta: "BancoBeta", gamma: "BancoGamma", central: "BancoCentral" }[rol];
    const party = await partyDe(base, prefijo);
    if (!party) {
      return { rol, disponible: false, error: "El nodo no tiene la party esperada", contratos: [] };
    }

    const { offset } = await pedir<{ offset: number }>(`${base}/v2/state/ledger-end`);
    const bruto = await pedir<RespuestaAcs[]>(`${base}/v2/state/active-contracts`, {
      filter: {
        filtersByParty: {
          [party]: {
            cumulative: [
              { identifierFilter: { WildcardFilter: { value: { includeCreatedEventBlob: false } } } },
            ],
          },
        },
      },
      verbose: true,
      activeAtOffset: offset,
    });

    const contratos = bruto
      .map((entrada) => entrada.contractEntry?.JsActiveContract?.createdEvent)
      .filter((evento): evento is EventoCreado => Boolean(evento))
      .map(
        (evento): ContratoReal => ({
          contractId: evento.contractId,
          templateId: evento.templateId,
          plantilla: evento.templateId.split(":").pop() ?? evento.templateId,
          signatarios: evento.signatories ?? [],
          observadores: evento.observers ?? [],
          testigos: evento.witnessParties ?? [],
          campos: evento.createArgument ?? {},
          creadoEn: evento.createdAt,
          offset: evento.offset,
        }),
      );

    return { rol, disponible: true, offset, contratos };
  } catch (error) {
    return {
      rol,
      disponible: false,
      error: error instanceof Error ? error.message : String(error),
      contratos: [],
    };
  }
}

interface EventoCreado {
  contractId: string;
  templateId: string;
  createArgument?: Record<string, unknown>;
  signatories?: string[];
  observers?: string[];
  witnessParties?: string[];
  createdAt: string;
  offset: number;
}

interface RespuestaAcs {
  contractEntry?: { JsActiveContract?: { createdEvent?: EventoCreado } };
}

/** Lee los cuatro nodos en paralelo. */
export const leerRed = (roles: Rol[]): Promise<EstadoNodo[]> => Promise.all(roles.map(leerNodo));
