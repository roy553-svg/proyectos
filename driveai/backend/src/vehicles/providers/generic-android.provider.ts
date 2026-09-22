import { Injectable, Logger } from '@nestjs/common';
import { AppConfig } from '../../config/app.config';
import {
  Gear,
  VehicleCommand,
  VehicleCommandResult,
  VehicleProvider,
  VehicleStatus,
} from './vehicle-provider.interface';

/**
 * Radios Android 9+ (Rockchip / Allwinner / MediaTek) conectadas al CAN-Bus
 * del coche o a un adaptador OBD-II Bluetooth.
 *
 * El modulo nativo Kotlin expone un puente HTTP local (`CanBusBridgeService`)
 * en `ANDROID_BRIDGE_URL`. Este proveedor solo normaliza ese JSON. Si el
 * puente no responde dentro de 1.2 s devolvemos el ultimo estado conocido
 * marcado como `stale` -- nunca dejamos al HUD sin datos.
 */
@Injectable()
export class GenericAndroidProvider implements VehicleProvider {
  readonly name = 'generic-android';
  private readonly logger = new Logger(GenericAndroidProvider.name);
  private lastKnown: VehicleStatus | null = null;

  async isAvailable(): Promise<boolean> {
    try {
      const res = await this.fetchBridge('/health');
      return res !== null;
    } catch {
      return false;
    }
  }

  async getStatus(): Promise<VehicleStatus> {
    const raw = await this.fetchBridge('/obd/status');
    if (!raw) return this.degraded();

    const status: VehicleStatus = {
      provider: this.name,
      online: true,
      stale: false,
      speedKph: this.num(raw.speed_kph ?? raw.vehicle_speed, 0)!,
      gear: this.gear(raw.gear),
      batteryPercent: this.num(raw.battery_percent ?? raw.soc, null),
      fuelPercent: this.num(raw.fuel_level ?? raw.fuel_percent, null),
      rangeKm: this.num(raw.range_km ?? raw.dte_km, null),
      odometerKm: this.num(raw.odometer_km, null),
      insideTempC: this.num(raw.inside_temp_c ?? raw.cabin_temp, null),
      outsideTempC: this.num(raw.outside_temp_c ?? raw.ambient_temp, null),
      climateTargetC: this.num(raw.climate_target_c, null),
      climateOn: Boolean(raw.climate_on),
      locked: raw.locked === undefined ? true : Boolean(raw.locked),
      tires: raw.tires
        ? {
            fl: this.num(raw.tires.fl, 0)!,
            fr: this.num(raw.tires.fr, 0)!,
            rl: this.num(raw.tires.rl, 0)!,
            rr: this.num(raw.tires.rr, 0)!,
            unit: raw.tires.unit === 'psi' ? 'psi' : 'bar',
          }
        : null,
      doorsOpen: Array.isArray(raw.doors_open) ? raw.doors_open : [],
      updatedAt: new Date().toISOString(),
    };
    this.lastKnown = status;
    return status;
  }

  async sendCommand(cmd: VehicleCommand): Promise<VehicleCommandResult> {
    const res = await this.fetchBridge('/can/command', {
      method: 'POST',
      body: JSON.stringify({ command: cmd.name, args: cmd.args ?? {} }),
      headers: { 'content-type': 'application/json' },
    });
    if (!res) {
      return {
        ok: false,
        command: cmd.name,
        message: 'El bus del vehiculo no respondio. Intenta de nuevo.',
      };
    }
    return {
      ok: res.ok !== false,
      command: cmd.name,
      message: typeof res.message === 'string' ? res.message : 'Comando enviado.',
    };
  }

  // ------------------------------------------------------------------
  private async fetchBridge(
    path: string,
    init: RequestInit = {},
  ): Promise<any | null> {
    const controller = new AbortController();
    const timer = setTimeout(
      () => controller.abort(),
      AppConfig.vehicle.bridgeTimeoutMs,
    );
    try {
      const res = await fetch(`${AppConfig.vehicle.bridgeUrl}${path}`, {
        ...init,
        signal: controller.signal,
      });
      if (!res.ok) return null;
      return await res.json();
    } catch (err) {
      this.logger.debug(`Puente CAN sin respuesta: ${(err as Error).message}`);
      return null;
    } finally {
      clearTimeout(timer);
    }
  }

  /** Sin bus: ultimo estado conocido o un estado seguro en P. */
  private degraded(): VehicleStatus {
    if (this.lastKnown) return { ...this.lastKnown, online: false, stale: true };
    return {
      provider: this.name,
      online: false,
      stale: true,
      speedKph: 0,
      gear: 'P',
      batteryPercent: null,
      fuelPercent: null,
      rangeKm: null,
      odometerKm: null,
      insideTempC: null,
      outsideTempC: null,
      climateTargetC: null,
      climateOn: false,
      locked: true,
      tires: null,
      doorsOpen: [],
      updatedAt: new Date().toISOString(),
    };
  }

  private gear(value: unknown): Gear {
    const g = String(value ?? 'P').toUpperCase().charAt(0);
    return (['P', 'R', 'N', 'D'].includes(g) ? g : 'P') as Gear;
  }

  private num<T extends number | null>(value: unknown, fallback: T): number | T {
    const n = Number(value);
    return Number.isFinite(n) ? n : fallback;
  }
}
