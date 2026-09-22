/** Marcha seleccionada. */
export type Gear = 'P' | 'R' | 'N' | 'D';

/** Presion por neumatico en bar + temperatura opcional. */
export interface TirePressure {
  fl: number;
  fr: number;
  rl: number;
  rr: number;
  unit: 'bar' | 'psi';
}

/** Telemetria normalizada, identica para cualquier proveedor. */
export interface VehicleStatus {
  provider: string;
  online: boolean;
  /** Datos reales del bus vs. ultimo valor conocido / simulado. */
  stale: boolean;
  speedKph: number;
  gear: Gear;
  batteryPercent: number | null;
  fuelPercent: number | null;
  rangeKm: number | null;
  odometerKm: number | null;
  insideTempC: number | null;
  outsideTempC: number | null;
  climateTargetC: number | null;
  climateOn: boolean;
  locked: boolean;
  tires: TirePressure | null;
  doorsOpen: string[];
  updatedAt: string;
}

export type VehicleCommandName =
  | 'set_climate_temp'
  | 'climate_on'
  | 'climate_off'
  | 'lock'
  | 'unlock'
  | 'honk'
  | 'flash_lights';

export interface VehicleCommand {
  name: VehicleCommandName;
  /** Ej: { celsius: 21 } */
  args?: Record<string, number | string | boolean>;
}

export interface VehicleCommandResult {
  ok: boolean;
  command: VehicleCommandName;
  message: string;
  /** Bloqueado por la politica de seguridad de conduccion. */
  blocked?: boolean;
}

/**
 * Capa de abstraccion multi-proveedor.
 * Toda implementacion debe ser NO BLOQUEANTE y fallar rapido: si el bus del
 * coche no contesta, devolvemos el ultimo estado conocido con `stale: true`.
 */
export interface VehicleProvider {
  readonly name: string;
  isAvailable(): Promise<boolean>;
  getStatus(): Promise<VehicleStatus>;
  sendCommand(cmd: VehicleCommand): Promise<VehicleCommandResult>;
}

/** Comandos que jamas se ejecutan con el vehiculo en movimiento. */
export const MOTION_RESTRICTED: ReadonlySet<VehicleCommandName> = new Set([
  'unlock',
  'lock',
  'honk',
  'flash_lights',
]);
