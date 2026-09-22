import { INSTITUCIONES, type NodoTransaccion, type Rol, type Transaccion } from "../lib/tipos";
import { ESTILO_ROL } from "../lib/estilos";
import { bloqueOpaco } from "../lib/cripto";
import { Chip, IconoCandado, IconoOjo } from "./Primitivos";

const ICONO_TIPO: Record<NodoTransaccion["tipo"], string> = {
  ejercicio: "⎇",
  creacion: "+",
  consulta: "?",
  archivado: "×",
};

function Nodo({
  nodo,
  perspectiva,
  profundidad,
}: {
  nodo: NodoTransaccion;
  perspectiva: Rol;
  profundidad: number;
}) {
  const visible = nodo.informees.includes(perspectiva);

  return (
    <li className="relative">
      <div
        className={`relative rounded-lg border px-3 py-2.5 transition-colors ${
          visible
            ? "border-[var(--color-borde-vivo)] bg-[var(--color-panel-alto)]"
            : "opaco border-dashed border-slate-700/60 bg-slate-900/30"
        }`}
      >
        {visible ? (
          <>
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-[11px] text-[var(--color-tinta-tenue)]">
                {ICONO_TIPO[nodo.tipo]}
              </span>
              <span className="font-mono text-xs font-medium text-[var(--color-tinta)]">
                {nodo.titulo}
              </span>
              {nodo.archiva ? (
                <span className="rounded border border-rose-500/30 bg-rose-500/10 px-1.5 py-0.5 text-[10px] text-rose-300">
                  consume
                </span>
              ) : null}
            </div>
            <p className="mt-1.5 text-xs leading-relaxed text-[var(--color-tinta-suave)]">
              {nodo.detalle}
            </p>
            <div className="mt-2 flex flex-wrap items-center gap-1.5">
              <span className="mr-1 inline-flex items-center gap-1 text-[10px] uppercase tracking-wider text-[var(--color-tinta-tenue)]">
                <IconoOjo /> informees
              </span>
              {nodo.informees.map((r) => (
                <Chip key={r} rol={r} />
              ))}
            </div>
          </>
        ) : (
          <div className="flex items-start gap-3">
            <IconoCandado className="mt-0.5 shrink-0 text-slate-500" />
            <div className="min-w-0 flex-1">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">
                No entregado al nodo de {INSTITUCIONES[perspectiva].nombreCorto}
              </p>
              <p className="mt-1 break-all font-mono text-[10px] leading-relaxed text-slate-600 select-none">
                {bloqueOpaco(nodo.id + perspectiva, 120)}
              </p>
              <p className="mt-1.5 text-[11px] text-slate-500">
                Este subarbol existe en la transaccion, pero el sincronizador no lo
                envia a este participante.
              </p>
            </div>
          </div>
        )}
      </div>

      {nodo.hijos.length > 0 ? (
        <ul
          className="mt-2 space-y-2 border-l border-[var(--color-borde)] pl-4"
          style={{ marginLeft: profundidad === 0 ? 8 : 4 }}
        >
          {nodo.hijos.map((h) => (
            <Nodo key={h.id} nodo={h} perspectiva={perspectiva} profundidad={profundidad + 1} />
          ))}
        </ul>
      ) : null}
    </li>
  );
}

export function ArbolTransaccion({
  tx,
  perspectiva,
}: {
  tx: Transaccion;
  perspectiva: Rol;
}) {
  const todos = contarNodos(tx.raiz);
  const vistos = contarVisibles(tx.raiz, perspectiva);
  const est = ESTILO_ROL[perspectiva];
  const porcentaje = Math.round((vistos / todos) * 100);

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-[var(--color-borde)] bg-[var(--color-panel-alto)] px-4 py-3">
        <div>
          <p className="text-[10px] uppercase tracking-[0.14em] text-[var(--color-tinta-tenue)]">
            Visibilidad desde el nodo de {INSTITUCIONES[perspectiva].nombreCorto}
          </p>
          <p className="mt-1 text-sm text-[var(--color-tinta)]">
            <span className="font-mono text-lg font-semibold">{vistos}</span>
            <span className="text-[var(--color-tinta-tenue)]"> de </span>
            <span className="font-mono text-lg font-semibold">{todos}</span>
            <span className="text-[var(--color-tinta-suave)]"> nodos de la transaccion</span>
          </p>
        </div>
        <div className="flex min-w-[180px] flex-1 items-center gap-3">
          <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-800">
            <div
              className={`h-full rounded-full transition-all duration-500 ${est.barra}`}
              style={{ width: `${porcentaje}%` }}
            />
          </div>
          <span className="font-mono text-xs text-[var(--color-tinta-suave)]">{porcentaje}%</span>
        </div>
      </div>

      <ul className="space-y-2">
        <Nodo nodo={tx.raiz} perspectiva={perspectiva} profundidad={0} />
      </ul>

      <div className="mt-4 flex flex-wrap gap-x-6 gap-y-2 rounded-lg border border-[var(--color-borde)] bg-[var(--color-panel-alto)] px-4 py-3 text-xs">
        <div>
          <span className="text-[var(--color-tinta-tenue)]">Somete: </span>
          <Chip rol={tx.emisor} />
        </div>
        <div className="flex items-center gap-1.5">
          <span className="text-[var(--color-tinta-tenue)]">Confirman: </span>
          {tx.confirmadores.map((r) => (
            <Chip key={r} rol={r} />
          ))}
        </div>
        <div className="text-[var(--color-tinta-tenue)]">
          Instante <span className="font-mono text-[var(--color-tinta-suave)]">{tx.instante}</span>
        </div>
      </div>
    </div>
  );
}

function contarNodos(n: NodoTransaccion): number {
  return 1 + n.hijos.reduce((acc, h) => acc + contarNodos(h), 0);
}

function contarVisibles(n: NodoTransaccion, rol: Rol): number {
  return (
    (n.informees.includes(rol) ? 1 : 0) +
    n.hijos.reduce((acc, h) => acc + contarVisibles(h, rol), 0)
  );
}
