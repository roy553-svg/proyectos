import { INSTITUCIONES, informeesDe, type Contrato, type Rol } from "../lib/tipos";
import { ESTILO_ROL } from "../lib/estilos";
import { bloqueOpaco } from "../lib/cripto";
import { Chip, IconoCandado } from "./Primitivos";

const ORDEN: Record<string, number> = {
  InstitutionalAccount: 0,
  FundsInTransit: 1,
  InterbankSettlementReceipt: 2,
  InterbankTransferProposal: 3,
  TransferMandate: 4,
  TransactionRecord: 5,
  BankLicense: 6,
};

function Fila({ contrato, perspectiva }: { contrato: Contrato; perspectiva: Rol }) {
  const visible = informeesDe(contrato).includes(perspectiva);

  if (!visible) {
    return (
      <li className="opaco rounded-lg border border-dashed border-slate-700/60 bg-slate-900/30 px-3.5 py-3">
        <div className="flex items-center gap-2.5">
          <IconoCandado className="shrink-0 text-slate-500" />
          <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">
            Contrato ausente de este nodo
          </span>
        </div>
        <p className="mt-1.5 truncate font-mono text-[10px] text-slate-600 select-none">
          {bloqueOpaco(contrato.id + perspectiva, 64)}
        </p>
      </li>
    );
  }

  const esCuenta = contrato.plantilla === "InstitutionalAccount";
  const esAsiento = contrato.plantilla === "TransactionRecord";
  const debito = contrato.campos.direction === "DebitEntry";

  return (
    <li className="aparecer rounded-lg border border-[var(--color-borde-vivo)] bg-[var(--color-panel-alto)] px-3.5 py-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="font-mono text-xs font-semibold text-[var(--color-tinta)]">
            {contrato.plantilla}
          </span>
          {contrato.libro ? <Chip rol={contrato.libro} /> : null}
        </div>
        {esCuenta ? (
          <span className="font-mono text-sm font-semibold text-[var(--color-tinta)]">
            {contrato.campos.balance}
          </span>
        ) : null}
        {esAsiento ? (
          <span
            className={`font-mono text-sm font-semibold ${
              debito ? "text-rose-300" : "text-emerald-300"
            }`}
          >
            {debito ? "−" : "+"} {contrato.campos.amount}
          </span>
        ) : null}
      </div>

      <dl className="mt-2 grid grid-cols-1 gap-x-5 gap-y-1 sm:grid-cols-2">
        {Object.entries(contrato.campos)
          .filter(([k]) => !(esCuenta && k === "balance") && !(esAsiento && k === "amount"))
          .map(([clave, valor]) => (
            <div key={clave} className="flex gap-2 text-[11px]">
              <dt className="shrink-0 text-[var(--color-tinta-tenue)]">{clave}</dt>
              <dd className="truncate font-mono text-[var(--color-tinta-suave)]">{valor}</dd>
            </div>
          ))}
      </dl>

      <div className="mt-2 flex flex-wrap items-center gap-1.5 border-t border-[var(--color-borde)] pt-2">
        <span className="text-[10px] uppercase tracking-wider text-[var(--color-tinta-tenue)]">
          firman
        </span>
        {contrato.signatarios.map((r) => (
          <Chip key={r} rol={r} />
        ))}
        {contrato.observadores.length > 0 ? (
          <>
            <span className="ml-2 text-[10px] uppercase tracking-wider text-[var(--color-tinta-tenue)]">
              observan
            </span>
            {contrato.observadores.map((r) => (
              <Chip key={r} rol={r} />
            ))}
          </>
        ) : null}
      </div>
    </li>
  );
}

export function LibroContable({
  contratos,
  perspectiva,
}: {
  contratos: Contrato[];
  perspectiva: Rol;
}) {
  const ordenados = [...contratos].sort(
    (a, b) => (ORDEN[a.plantilla] ?? 9) - (ORDEN[b.plantilla] ?? 9),
  );
  const visibles = ordenados.filter((c) => informeesDe(c).includes(perspectiva));
  const est = ESTILO_ROL[perspectiva];

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs text-[var(--color-tinta-suave)]">
          El nodo de{" "}
          <span className={`font-semibold ${est.texto}`}>
            {INSTITUCIONES[perspectiva].nombreCorto}
          </span>{" "}
          almacena{" "}
          <span className="font-mono font-semibold text-[var(--color-tinta)]">
            {visibles.length}
          </span>{" "}
          de los <span className="font-mono">{ordenados.length}</span> contratos que existen en la red.
        </p>
      </div>
      <ul className="max-h-[540px] space-y-2 overflow-y-auto pr-1">
        {ordenados.map((c) => (
          <Fila key={c.id} contrato={c} perspectiva={perspectiva} />
        ))}
      </ul>
    </div>
  );
}
