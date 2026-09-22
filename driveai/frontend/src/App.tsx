import { useCallback, useEffect, useRef, useState } from 'react';
import { Settings2 } from 'lucide-react';
import { AnswerPanel } from './components/AnswerPanel';
import { HudBar } from './components/HudBar';
import { QueryBar } from './components/QueryBar';
import { SettingsModal } from './components/SettingsModal';
import { TelemetryCard } from './components/TelemetryCard';
import { VoiceOrb } from './components/VoiceOrb';
import { api } from './lib/api';
import { createListener, isNativeBridge, shutUp, speak } from './lib/speech';
import type { AssistantReply, OrbState, Place, VehicleStatus } from './lib/types';

/** Refresco del HUD. 1 s basta y mantiene la CPU de la radio por debajo del 3%. */
const TELEMETRY_MS = 1000;

export default function App() {
  const [status, setStatus] = useState<VehicleStatus | null>(null);
  const [online, setOnline] = useState(true);
  const [orb, setOrb] = useState<OrbState>('idle');
  const [reply, setReply] = useState<AssistantReply | null>(null);
  const [place, setPlace] = useState<Place | null>(null);
  const [settings, setSettings] = useState(false);
  const [health, setHealth] = useState({ mode: 'desconocido', provider: 'mock' });
  const [flags, setFlags] = useState({
    micEnabled: true,
    locationEnabled: true,
    memoryEnabled: true,
  });

  const listenerRef = useRef<ReturnType<typeof createListener> | null>(null);
  /** Evita re-suscribir los eventos nativos en cada render. */
  const pressOrbRef = useRef<() => void>(() => undefined);
  const busy = orb === 'thinking';

  // ------------------------------------------------------------------
  // Telemetria: un solo intervalo, sin re-render en cascada.
  // Se pausa al ocultar la pantalla para no gastar bateria ni CPU.
  // ------------------------------------------------------------------
  useEffect(() => {
    let alive = true;
    let timer: number | undefined;

    const tick = async () => {
      if (document.hidden) return;
      try {
        const s = await api.status();
        if (!alive) return;
        setStatus(s);
        setOnline(true);
      } catch {
        if (alive) setOnline(false); // nunca mostramos error: solo cambiamos el icono
      }
    };

    void tick();
    timer = window.setInterval(tick, TELEMETRY_MS);
    return () => {
      alive = false;
      if (timer) window.clearInterval(timer);
    };
  }, []);

  // Estado del gateway y lugar mas visitado (Capa 3), una sola vez.
  useEffect(() => {
    void (async () => {
      try {
        const h = await fetch('/api/assistant/health').then((r) => r.json());
        setHealth({ mode: h.mode, provider: h.vehicleProvider });
      } catch {
        setHealth({ mode: 'determinista-local', provider: 'desconocido' });
      }
      try {
        const places = await api.places();
        if (places.length) setPlace(places[0]);
      } catch {
        /* sin memoria: la tarjeta simplemente no aparece */
      }
    })();
  }, []);

  // ------------------------------------------------------------------
  // Ciclo de pregunta -> respuesta -> voz
  // ------------------------------------------------------------------
  const ask = useCallback(
    async (text: string, viaVoice = false) => {
      shutUp();
      setOrb('thinking');
      try {
        const r = await api.ask(text, viaVoice);
        setReply(r);
        setOnline(true);

        if (r.action?.type === 'navigate') {
          setPlace({
            name: (r.action.payload.name as string) ?? (r.action.payload.address as string),
            lat: (r.action.payload.lat as number) ?? null,
            lon: (r.action.payload.lon as number) ?? null,
            address: (r.action.payload.address as string) ?? null,
          });
        }

        setOrb('speaking');
        await speak(r.text);
      } catch {
        // El gateway no contesta: respondemos en cabina, sin pantallas de error.
        setOnline(false);
        const fallback: AssistantReply = {
          text: 'Estoy sin conexión con el gateway. Sigo mostrando la telemetría del coche.',
          source: 'offline',
          rule: 'client-fallback',
          latencyMs: 0,
          quickActions: [],
        };
        setReply(fallback);
        setOrb('speaking');
        await speak(fallback.text);
      } finally {
        setOrb('idle');
      }
    },
    [],
  );

  const pressOrb = useCallback(() => {
    if (!flags.micEnabled) {
      setSettings(true);
      return;
    }
    if (orb === 'speaking') {
      shutUp();
      setOrb('idle');
      return;
    }
    if (orb === 'listening') {
      if (isNativeBridge()) window.DriveAINative?.stopRecording?.();
      else listenerRef.current?.stop();
      return;
    }
    if (orb === 'thinking') return;

    // App nativa: capturamos PCM de 16 kHz con VoiceRecorder.kt (mejor
    // cancelacion de ruido de cabina que el reconocedor del WebView).
    if (isNativeBridge()) {
      setOrb('listening');
      window.DriveAINative?.startRecording?.();
      return;
    }

    const listener = createListener(
      (text) => void ask(text, true),
      () => setOrb((s) => (s === 'listening' ? 'idle' : s)),
      () => {
        setOrb('idle');
        setReply({
          text: 'No pude usar el micrófono. Escribe tu pregunta abajo.',
          source: 'offline',
          rule: 'mic-unavailable',
          latencyMs: 0,
          quickActions: [],
        });
      },
    );
    listenerRef.current = listener;
    setOrb('listening');
    listener.start();
  }, [ask, flags.micEnabled, orb]);

  // ------------------------------------------------------------------
  // Puente nativo (MainActivity.kt): boton de voz del volante y
  // transcripcion hecha por VoiceRecorder.kt + gateway.
  // ------------------------------------------------------------------
  useEffect(() => {
    const onWake = () => pressOrbRef.current();
    const onTranscript = (e: Event) => {
      const text = (e as CustomEvent<string>).detail?.trim();
      if (text) void ask(text, true);
      else setOrb('idle');
    };
    const onMicError = () => setOrb('idle');

    window.addEventListener('driveai:wake', onWake);
    window.addEventListener('driveai:transcript', onTranscript as EventListener);
    window.addEventListener('driveai:mic-error', onMicError);
    window.addEventListener('driveai:mic-denied', onMicError);
    return () => {
      window.removeEventListener('driveai:wake', onWake);
      window.removeEventListener('driveai:transcript', onTranscript as EventListener);
      window.removeEventListener('driveai:mic-error', onMicError);
      window.removeEventListener('driveai:mic-denied', onMicError);
    };
  }, [ask]);

  const runCommand = useCallback(async (command: string, args?: Record<string, unknown>) => {
    try {
      const res = await api.command(command, args);
      setReply({
        text: res.message,
        source: 'offline',
        rule: res.blocked ? 'safety-lockout' : 'vehicle-command',
        latencyMs: 0,
        quickActions: [],
      });
      if (res.blocked) void speak(res.message);
      const s = await api.status();
      setStatus(s);
    } catch {
      setOnline(false);
    }
  }, []);

  /** Ruta en 1 toque: delega en el navegador nativo del coche. */
  const navigate = useCallback((p: Place) => {
    const query =
      p.lat && p.lon ? `${p.lat},${p.lon}` : encodeURIComponent(p.address ?? p.name);
    window.location.href = `geo:0,0?q=${query}`;
  }, []);

  pressOrbRef.current = pressOrb;

  return (
    <main className="flex h-full flex-col gap-4 pb-4">
      <HudBar status={status} online={online} />

      {/* Cabina de UNA sola pantalla: sin pestañas, sin menús anidados. */}
      <div className="grid min-h-0 flex-1 grid-cols-1 gap-4 px-4 lg:grid-cols-[1.15fr_1fr]">
        <div className="flex min-h-0 flex-col gap-4">
          <div className="flex flex-1 items-center justify-center">
            <VoiceOrb state={orb} micEnabled={flags.micEnabled} onPress={pressOrb} />
          </div>
          <QueryBar onAsk={(t) => void ask(t, false)} disabled={busy} />
        </div>

        <div className="flex min-h-0 flex-col gap-4">
          <AnswerPanel
            reply={reply}
            place={place}
            onNavigate={navigate}
            onQuickAction={(c, a) => void runCommand(c, a)}
          />
          <TelemetryCard status={status} onCommand={(c, a) => void runCommand(c, a)} busy={busy} />
        </div>
      </div>

      {/* Acceso discreto a los ajustes: fuera del alcance visual del conductor. */}
      <button
        type="button"
        onClick={() => setSettings(true)}
        aria-label="Ajustes y privacidad"
        className="fixed bottom-4 right-4 flex h-14 w-14 items-center justify-center
                   rounded-full border border-cockpit-line bg-zinc-900/90 text-zinc-500
                   active:scale-95"
      >
        <Settings2 className="h-6 w-6" aria-hidden />
      </button>

      <SettingsModal
        open={settings}
        flags={flags}
        mode={health.mode}
        provider={health.provider}
        onFlags={setFlags}
        onClose={() => setSettings(false)}
      />
    </main>
  );
}
