import { Navigation, Sparkles, WifiOff, Zap } from 'lucide-react';
import type { AssistantReply, Place } from '../lib/types';

const FUENTE: Record<AssistantReply['source'], { label: string; cls: string }> = {
  gemini: { label: 'GEMINI', cls: 'text-cockpit-info' },
  'gemini-fallback': { label: 'GEMINI · RESPALDO', cls: 'text-cockpit-info' },
  offline: { label: 'MOTOR LOCAL', cls: 'text-cockpit-warn' },
  cache: { label: 'CACHÉ', cls: 'text-zinc-500' },
};

/**
 * Respuesta + memoria episodica (Capa 3) con el boton verde de ruta.
 * Nada de historial largo: el conductor solo ve el ultimo turno.
 */
export function AnswerPanel({
  reply,
  place,
  onNavigate,
  onQuickAction,
}: {
  reply: AssistantReply | null;
  place: Place | null;
  onNavigate: (place: Place) => void;
  onQuickAction: (command: string, args?: Record<string, unknown>) => void;
}) {
  const fuente = reply ? FUENTE[reply.source] : null;

  return (
    <section className="panel flex flex-1 flex-col gap-4 p-5">
      <div className="flex items-center gap-2">
        {reply?.source === 'offline' ? (
          <WifiOff className="h-5 w-5 text-cockpit-warn" aria-hidden />
        ) : (
          <Sparkles className="h-5 w-5 text-cockpit-info" aria-hidden />
        )}
        <span className={`text-xs font-bold uppercase tracking-[0.2em] ${fuente?.cls ?? 'text-zinc-600'}`}>
          {fuente?.label ?? 'DRIVEAI LISTO'}
        </span>
        {reply && (
          <span className="ml-auto flex items-center gap-1 text-xs uppercase tracking-wider text-zinc-600">
            <Zap className="h-3.5 w-3.5" aria-hidden />
            {reply.latencyMs} ms
          </span>
        )}
      </div>

      <p className="flex-1 text-[1.6rem] font-medium leading-snug text-zinc-100" aria-live="polite">
        {reply?.text ?? 'Pregúntame lo que quieras. Voy contigo.'}
      </p>

      {reply?.quickActions
        ?.filter((a) => a.command)
        .map((a) => (
          <button
            key={a.label}
            type="button"
            onClick={() => onQuickAction(a.command!, a.args)}
            className="btn-info"
          >
            {a.label}
          </button>
        ))}

      {/* Capa 3 :: lugar recordado, ruta en un solo toque */}
      {place && (
        <button type="button" onClick={() => onNavigate(place)} className="btn-go">
          <Navigation className="h-6 w-6" aria-hidden />
          <span className="truncate">
            INICIAR RUTA · {place.name}
            {place.rating ? ` · ${place.rating}★` : ''}
            {place.visit_count ? ` · ${place.visit_count} visitas` : ''}
          </span>
        </button>
      )}
    </section>
  );
}
