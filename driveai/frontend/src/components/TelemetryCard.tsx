import { CircleDot, Minus, Plus, Snowflake, ThermometerSun } from 'lucide-react';
import type { VehicleStatus } from '../lib/types';

const lowPressure = (v: number, unit: 'bar' | 'psi') => (unit === 'bar' ? v < 2.2 : v < 32);

/** Tarjeta de telemetria + comandos seguros, todo a un toque. */
export function TelemetryCard({
  status,
  onCommand,
  busy,
}: {
  status: VehicleStatus | null;
  onCommand: (command: string, args?: Record<string, unknown>) => void;
  busy: boolean;
}) {
  const target = status?.climateTargetC ?? 22;
  const tires = status?.tires;
  const lockBlocked = status ? !status.safety.lockCommandsAllowed : true;

  return (
    <section className="panel flex flex-col gap-4 p-5">
      {/* Climatizador */}
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <ThermometerSun className="h-8 w-8 text-cockpit-warn" aria-hidden />
          <div>
            <p className="text-3xl font-bold text-zinc-100">{Math.round(target)}°C</p>
            <p className="text-xs uppercase tracking-wider text-zinc-500">
              cabina {status?.insideTempC ?? '--'}° · exterior {status?.outsideTempC ?? '--'}°
            </p>
          </div>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            disabled={busy}
            aria-label="Bajar un grado"
            onClick={() => onCommand('set_climate_temp', { celsius: Math.max(16, target - 1) })}
            className="btn-mute w-20"
          >
            <Minus className="h-7 w-7" aria-hidden />
          </button>
          <button
            type="button"
            disabled={busy}
            aria-label="Subir un grado"
            onClick={() => onCommand('set_climate_temp', { celsius: Math.min(30, target + 1) })}
            className="btn-mute w-20"
          >
            <Plus className="h-7 w-7" aria-hidden />
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={() => onCommand(status?.climateOn ? 'climate_off' : 'climate_on')}
            className={status?.climateOn ? 'btn-info w-24' : 'btn-mute w-24'}
          >
            <Snowflake className="h-7 w-7" aria-hidden />
          </button>
        </div>
      </div>

      {/* TPMS: 4 neumaticos */}
      <div className="grid grid-cols-4 gap-2">
        {tires ? (
          (
            [
              ['DI', tires.fl],
              ['DD', tires.fr],
              ['TI', tires.rl],
              ['TD', tires.rr],
            ] as const
          ).map(([label, value]) => {
            const low = lowPressure(value, tires.unit);
            return (
              <div
                key={label}
                className={`rounded-2xl border p-3 text-center ${
                  low
                    ? 'border-cockpit-warn/70 bg-amber-500/10'
                    : 'border-cockpit-line bg-zinc-900/60'
                }`}
              >
                <CircleDot
                  className={`mx-auto h-5 w-5 ${low ? 'text-cockpit-warn' : 'text-zinc-500'}`}
                  aria-hidden
                />
                <p
                  className={`mt-1 text-xl font-bold ${
                    low ? 'text-cockpit-warn' : 'text-zinc-100'
                  }`}
                >
                  {value.toFixed(1)}
                </p>
                <p className="text-[11px] uppercase tracking-wider text-zinc-500">
                  {label} {tires.unit}
                </p>
              </div>
            );
          })
        ) : (
          <p className="col-span-4 py-3 text-center text-sm uppercase tracking-wider text-zinc-600">
            TPMS sin datos en este vehículo
          </p>
        )}
      </div>

      {/* Seguros: bloqueados en movimiento por politica de seguridad */}
      <button
        type="button"
        disabled={busy || lockBlocked}
        onClick={() => onCommand(status?.locked ? 'unlock' : 'lock')}
        className={status?.locked ? 'btn-warn' : 'btn-go'}
      >
        {status?.locked ? 'ABRIR SEGUROS' : 'CERRAR SEGUROS'}
      </button>
      {lockBlocked && (
        <p className="-mt-2 text-center text-xs uppercase tracking-wider text-zinc-600">
          {status?.safety.reason ?? 'Disponible con el vehículo detenido'}
        </p>
      )}
    </section>
  );
}
