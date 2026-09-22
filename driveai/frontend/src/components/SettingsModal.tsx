import { useState } from 'react';
import {
  Cpu,
  Download,
  MapPin,
  Mic,
  ShieldCheck,
  Trash2,
  X,
} from 'lucide-react';
import { api } from '../lib/api';

interface Flags {
  micEnabled: boolean;
  locationEnabled: boolean;
  memoryEnabled: boolean;
}

/**
 * Panel modal de configuracion tecnica y privacidad.
 *
 * Deliberadamente fuera de la pantalla principal: el conductor no lo
 * necesita en marcha. Aqui vive todo lo que la filosofia "tipo DOOM"
 * prohibe mostrar en cabina.
 */
export function SettingsModal({
  open,
  flags,
  mode,
  provider,
  onFlags,
  onClose,
}: {
  open: boolean;
  flags: Flags;
  mode: string;
  provider: string;
  onFlags: (next: Flags) => void;
  onClose: () => void;
}) {
  const [confirmPurge, setConfirmPurge] = useState(false);
  const [note, setNote] = useState<string | null>(null);

  if (!open) return null;

  const toggle = async (key: keyof Flags) => {
    const next = { ...flags, [key]: !flags[key] };
    onFlags(next);
    try {
      await api.privacy(next);
    } catch {
      setNote('Preferencia guardada solo en este dispositivo (sin conexión).');
    }
  };

  const exportar = async () => {
    try {
      const data = await api.exportData();
      const url = URL.createObjectURL(
        new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }),
      );
      const a = document.createElement('a');
      a.href = url;
      a.download = `driveai-memoria-${new Date().toISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(url);
      setNote('Exportación descargada.');
    } catch {
      setNote('No se pudo exportar: el gateway no responde.');
    }
  };

  const purgar = async () => {
    try {
      await api.purge();
      setNote('Memoria borrada por completo.');
    } catch {
      setNote('No se pudo borrar: el gateway no responde.');
    } finally {
      setConfirmPurge(false);
    }
  };

  const Row = ({
    icon,
    label,
    hint,
    value,
    onToggle,
  }: {
    icon: React.ReactNode;
    label: string;
    hint: string;
    value: boolean;
    onToggle: () => void;
  }) => (
    <button
      type="button"
      onClick={onToggle}
      className="flex min-h-touch w-full items-center gap-4 rounded-2xl border border-cockpit-line
                 bg-zinc-900/60 px-5 text-left active:scale-[0.99]"
    >
      <span className={value ? 'text-cockpit-go' : 'text-zinc-600'}>{icon}</span>
      <span className="flex-1">
        <span className="block text-lg font-semibold text-zinc-100">{label}</span>
        <span className="block text-xs text-zinc-500">{hint}</span>
      </span>
      <span
        className={`h-8 w-14 shrink-0 rounded-full p-1 transition-colors ${
          value ? 'bg-cockpit-go/80' : 'bg-zinc-700'
        }`}
      >
        <span
          className={`block h-6 w-6 rounded-full bg-zinc-950 transition-transform ${
            value ? 'translate-x-6' : ''
          }`}
        />
      </span>
    </button>
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 p-4">
      <div className="panel max-h-full w-full max-w-2xl overflow-y-auto p-6">
        <div className="mb-5 flex items-center gap-3">
          <ShieldCheck className="h-7 w-7 text-cockpit-go" aria-hidden />
          <h2 className="flex-1 text-2xl font-bold">Privacidad y sistema</h2>
          <button type="button" onClick={onClose} aria-label="Cerrar" className="btn-mute w-16">
            <X className="h-7 w-7" aria-hidden />
          </button>
        </div>

        <div className="flex flex-col gap-3">
          <Row
            icon={<Mic className="h-7 w-7" aria-hidden />}
            label="Micrófono"
            hint="Apágalo y el orbe deja de capturar audio al instante."
            value={flags.micEnabled}
            onToggle={() => toggle('micEnabled')}
          />
          <Row
            icon={<MapPin className="h-7 w-7" aria-hidden />}
            label="Ubicación"
            hint="Sin ubicación no se registran lugares nuevos (Capa 3)."
            value={flags.locationEnabled}
            onToggle={() => toggle('locationEnabled')}
          />
          <Row
            icon={<Cpu className="h-7 w-7" aria-hidden />}
            label="Memoria del asistente"
            hint="Apagada, DriveAI responde sin recordar nada de ti."
            value={flags.memoryEnabled}
            onToggle={() => toggle('memoryEnabled')}
          />
        </div>

        <div className="mt-5 grid grid-cols-2 gap-3">
          <button type="button" onClick={exportar} className="btn-info">
            <Download className="h-6 w-6" aria-hidden />
            EXPORTAR JSON
          </button>
          {confirmPurge ? (
            <button
              type="button"
              onClick={purgar}
              className="btn-cockpit border-cockpit-stop bg-red-500/20 text-red-300"
            >
              CONFIRMAR BORRADO
            </button>
          ) : (
            <button
              type="button"
              onClick={() => setConfirmPurge(true)}
              className="btn-cockpit border-cockpit-stop/60 bg-red-500/10 text-red-300"
            >
              <Trash2 className="h-6 w-6" aria-hidden />
              BORRAR TODO
            </button>
          )}
        </div>

        {note && <p className="mt-4 text-center text-sm text-cockpit-info">{note}</p>}

        <dl className="mt-6 grid grid-cols-2 gap-2 text-sm text-zinc-500">
          <dt>Modo de IA</dt>
          <dd className="text-right text-zinc-300">{mode}</dd>
          <dt>Proveedor vehicular</dt>
          <dd className="text-right text-zinc-300">{provider}</dd>
          <dt>Datos en el dispositivo</dt>
          <dd className="text-right text-zinc-300">solo la sesión en curso</dd>
        </dl>

        <p className="mt-4 text-xs leading-relaxed text-zinc-600">
          El audio se procesa y se descarta: no se almacena ninguna grabación. Las cuatro
          capas de memoria viven cifradas en tu gateway, nunca en la radio del coche. El
          borrado es inmediato e irreversible (derecho al olvido, GDPR art. 17).
        </p>
      </div>
    </div>
  );
}
