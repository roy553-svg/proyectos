import type { ReactNode } from "react";
import { INSTITUCIONES, type Rol } from "../lib/tipos";
import { ESTILO_ROL } from "../lib/estilos";

export function Chip({ rol, sufijo }: { rol: Rol; sufijo?: string }) {
  const est = ESTILO_ROL[rol];
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-md border px-2 py-0.5 text-[11px] font-medium ${est.fondo} ${est.borde} ${est.texto}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${est.punto}`} />
      {INSTITUCIONES[rol].nombreCorto}
      {sufijo ? <span className="opacity-70">{sufijo}</span> : null}
    </span>
  );
}

export function Etiqueta({ children }: { children: ReactNode }) {
  return (
    <span className="text-[10px] font-semibold uppercase tracking-[0.14em] text-[var(--color-tinta-tenue)]">
      {children}
    </span>
  );
}

export function Panel({
  titulo,
  descripcion,
  accion,
  children,
  className = "",
}: {
  titulo: string;
  descripcion?: string;
  accion?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section
      className={`rounded-xl border border-[var(--color-borde)] bg-[var(--color-panel)]/80 ${className}`}
    >
      <header className="flex items-start justify-between gap-4 border-b border-[var(--color-borde)] px-5 py-3.5">
        <div>
          <h2 className="text-sm font-semibold tracking-tight text-[var(--color-tinta)]">{titulo}</h2>
          {descripcion ? (
            <p className="mt-1 max-w-2xl text-xs leading-relaxed text-[var(--color-tinta-suave)]">
              {descripcion}
            </p>
          ) : null}
        </div>
        {accion}
      </header>
      <div className="px-5 py-4">{children}</div>
    </section>
  );
}

export function IconoCandado({ className = "" }: { className?: string }) {
  return (
    <svg viewBox="0 0 16 16" aria-hidden className={`h-3.5 w-3.5 ${className}`} fill="currentColor">
      <path d="M8 1a3 3 0 0 0-3 3v2H4.5A1.5 1.5 0 0 0 3 7.5v6A1.5 1.5 0 0 0 4.5 15h7a1.5 1.5 0 0 0 1.5-1.5v-6A1.5 1.5 0 0 0 11.5 6H11V4a3 3 0 0 0-3-3Zm2 5H6V4a2 2 0 1 1 4 0v2Z" />
    </svg>
  );
}

export function IconoOjo({ className = "" }: { className?: string }) {
  return (
    <svg viewBox="0 0 16 16" aria-hidden className={`h-3.5 w-3.5 ${className}`} fill="currentColor">
      <path d="M8 3C4.5 3 1.7 5.4.6 7.6a.9.9 0 0 0 0 .8C1.7 10.6 4.5 13 8 13s6.3-2.4 7.4-4.6a.9.9 0 0 0 0-.8C14.3 5.4 11.5 3 8 3Zm0 8a3 3 0 1 1 0-6 3 3 0 0 1 0 6Zm0-1.5a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Z" />
    </svg>
  );
}
