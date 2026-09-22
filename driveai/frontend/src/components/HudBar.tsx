import { BatteryMedium, Gauge, Lock, LockOpen, Route, WifiOff } from 'lucide-react';
import type { VehicleStatus } from '../lib/types';

const GEARS = ['P', 'R', 'N', 'D'] as const;

/**
 * HUD superior. Es lo unico que el conductor mira de reojo, asi que:
 * dígitos enormes, tabulares, y un solo color por estado.
 */
export function HudBar({
  status,
  online,
}: {
  status: VehicleStatus | null;
  online: boolean;
}) {
  const speed = status ? Math.round(status.speedKph) : 0;
  const battery = status?.batteryPercent ?? null;
  const batteryColor =
    battery === null
      ? 'text-zinc-500'
      : battery < 15
        ? 'text-cockpit-stop'
        : battery < 35
          ? 'text-cockpit-warn'
          : 'text-cockpit-go';

  return (
    <header className="flex items-stretch gap-3 px-4 pt-3">
      {/* Velocimetro digital */}
      <div className="panel flex items-center gap-4 px-6 py-3">
        <Gauge className="h-8 w-8 text-cockpit-info" aria-hidden />
        <div className="flex items-baseline gap-2">
          <span className="text-hud font-bold text-zinc-50">{speed}</span>
          <span className="text-sm font-semibold uppercase text-zinc-500">km/h</span>
        </div>
      </div>

      {/* Marcha */}
      <div className="panel flex items-center gap-1 px-4 py-3" aria-label="Marcha">
        {GEARS.map((g) => (
          <span
            key={g}
            className={`w-10 rounded-xl py-2 text-center text-2xl font-bold ${
              status?.gear === g
                ? 'bg-cockpit-go/20 text-cockpit-go'
                : 'text-zinc-700'
            }`}
          >
            {g}
          </span>
        ))}
      </div>

      {/* Bateria + autonomia */}
      <div className="panel flex flex-1 items-center justify-around px-4 py-3">
        <div className="flex items-center gap-3">
          <BatteryMedium className={`h-8 w-8 ${batteryColor}`} aria-hidden />
          <span className={`text-3xl font-bold ${batteryColor}`}>
            {battery === null ? '--' : `${Math.round(battery)}%`}
          </span>
        </div>
        <div className="flex items-center gap-3">
          <Route className="h-7 w-7 text-cockpit-info" aria-hidden />
          <span className="text-3xl font-bold text-zinc-100">
            {status?.rangeKm === null || status?.rangeKm === undefined
              ? '--'
              : Math.round(status.rangeKm)}
            <span className="ml-1 text-sm font-semibold uppercase text-zinc-500">km</span>
          </span>
        </div>
        <div className="flex items-center gap-2">
          {status?.locked ? (
            <Lock className="h-7 w-7 text-cockpit-go" aria-label="Seguros cerrados" />
          ) : (
            <LockOpen className="h-7 w-7 text-cockpit-warn" aria-label="Seguros abiertos" />
          )}
          {(!online || status?.stale) && (
            <WifiOff
              className="h-7 w-7 text-cockpit-warn"
              aria-label="Modo local sin conexion"
            />
          )}
        </div>
      </div>
    </header>
  );
}
