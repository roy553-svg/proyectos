import type { Rol } from "./tipos";

/**
 * Clases Tailwind por institucion. Se declaran literales -y no compuestas en
 * tiempo de ejecucion- para que el compilador de Tailwind las conserve.
 */
export const ESTILO_ROL: Record<Rol, {
  texto: string;
  fondo: string;
  borde: string;
  punto: string;
  botonActivo: string;
  barra: string;
}> = {
  alfa: {
    texto: "text-cyan-300",
    fondo: "bg-cyan-500/10",
    borde: "border-cyan-500/40",
    punto: "bg-cyan-400",
    botonActivo: "bg-cyan-500/15 border-cyan-400/60 text-cyan-200",
    barra: "bg-cyan-400",
  },
  beta: {
    texto: "text-violet-300",
    fondo: "bg-violet-500/10",
    borde: "border-violet-500/40",
    punto: "bg-violet-400",
    botonActivo: "bg-violet-500/15 border-violet-400/60 text-violet-200",
    barra: "bg-violet-400",
  },
  gamma: {
    texto: "text-amber-300",
    fondo: "bg-amber-500/10",
    borde: "border-amber-500/40",
    punto: "bg-amber-400",
    botonActivo: "bg-amber-500/15 border-amber-400/60 text-amber-200",
    barra: "bg-amber-400",
  },
  central: {
    texto: "text-emerald-300",
    fondo: "bg-emerald-500/10",
    borde: "border-emerald-500/40",
    punto: "bg-emerald-400",
    botonActivo: "bg-emerald-500/15 border-emerald-400/60 text-emerald-200",
    barra: "bg-emerald-400",
  },
};

export const PANEL =
  "rounded-xl border border-[var(--color-borde)] bg-[var(--color-panel)]/80 backdrop-blur-sm";
export const PANEL_ALTO =
  "rounded-lg border border-[var(--color-borde)] bg-[var(--color-panel-alto)]";
