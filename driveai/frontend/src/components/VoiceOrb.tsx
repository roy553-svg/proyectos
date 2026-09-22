import { Loader2, Mic, MicOff, Volume2 } from 'lucide-react';
import type { OrbState } from '../lib/types';

const COPY: Record<OrbState, { label: string; ring: string; text: string }> = {
  idle: { label: 'TOCA PARA HABLAR', ring: 'border-zinc-700', text: 'text-zinc-400' },
  listening: { label: 'ESCUCHANDO', ring: 'border-cockpit-go', text: 'text-cockpit-go' },
  thinking: { label: 'PROCESANDO', ring: 'border-cockpit-info', text: 'text-cockpit-info' },
  speaking: { label: 'HABLANDO', ring: 'border-cockpit-warn', text: 'text-cockpit-warn' },
};

/**
 * Orbe de voz central: el unico control que el conductor necesita acertar
 * sin mirar. 208 px de diametro, muy por encima del minimo tactil.
 */
export function VoiceOrb({
  state,
  micEnabled,
  onPress,
}: {
  state: OrbState;
  micEnabled: boolean;
  onPress: () => void;
}) {
  const copy = COPY[state];
  const busy = state === 'thinking';

  return (
    <div className="flex flex-col items-center gap-3">
      <button
        type="button"
        onClick={onPress}
        aria-label={micEnabled ? copy.label : 'Microfono apagado'}
        aria-live="polite"
        className={`relative flex h-52 w-52 items-center justify-center rounded-full
                    border-4 bg-zinc-900/80 transition-transform duration-100
                    active:scale-95 ${micEnabled ? copy.ring : 'border-cockpit-stop'}`}
      >
        {/* Anillo de latido: feedback visual periferico, sin leer texto */}
        {(state === 'listening' || state === 'speaking') && (
          <span
            className={`absolute inset-0 rounded-full border-4 ${copy.ring} animate-pulseRing`}
            aria-hidden
          />
        )}

        {!micEnabled ? (
          <MicOff className="h-24 w-24 text-cockpit-stop" aria-hidden />
        ) : busy ? (
          <Loader2 className="h-24 w-24 animate-spin text-cockpit-info" aria-hidden />
        ) : state === 'speaking' ? (
          <Volume2 className="h-24 w-24 text-cockpit-warn" aria-hidden />
        ) : (
          <Mic
            className={`h-24 w-24 ${state === 'listening' ? 'text-cockpit-go' : 'text-zinc-300'}`}
            aria-hidden
          />
        )}
      </button>

      <p
        className={`text-cta font-bold uppercase tracking-[0.18em] ${
          micEnabled ? copy.text : 'text-cockpit-stop'
        }`}
      >
        {micEnabled ? copy.label : 'MICROFONO APAGADO'}
      </p>
    </div>
  );
}
