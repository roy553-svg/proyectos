import { Injectable, Logger } from '@nestjs/common';
import {
  Gear,
  VehicleCommand,
  VehicleCommandResult,
  VehicleProvider,
  VehicleStatus,
} from './vehicle-provider.interface';

/**
 * Simulador automotriz para desarrollo y demos sin hardware.
 * Recorre un ciclo de conduccion realista (arranque, crucero, frenado).
 */
@Injectable()
export class MockVehicleProvider implements VehicleProvider {
  readonly name = 'mock';
  private readonly logger = new Logger(MockVehicleProvider.name);
  private readonly bootedAt = Date.now();

  private state = {
    battery: 78,
    rangeKm: 342,
    odometerKm: 48_210,
    climateTargetC: 22,
    climateOn: true,
    locked: true,
  };

  async isAvailable(): Promise<boolean> {
    return true;
  }

  async getStatus(): Promise<VehicleStatus> {
    const t = (Date.now() - this.bootedAt) / 1000;
    // Ciclo suave de 120 s: 0 -> 95 km/h -> 0
    const speed = Math.max(0, Math.round(47 * (1 - Math.cos(t / 19)) * 10) / 10);
    const gear: Gear = speed > 0.5 ? 'D' : 'P';
    return {
      provider: this.name,
      online: true,
      stale: false,
      speedKph: speed,
      gear,
      batteryPercent: this.state.battery,
      fuelPercent: null,
      rangeKm: this.state.rangeKm,
      odometerKm: this.state.odometerKm,
      insideTempC: this.state.climateOn ? this.state.climateTargetC : 28,
      outsideTempC: 24,
      climateTargetC: this.state.climateTargetC,
      climateOn: this.state.climateOn,
      locked: this.state.locked,
      tires: { fl: 2.4, fr: 2.4, rl: 2.3, rr: 2.2, unit: 'bar' },
      doorsOpen: [],
      updatedAt: new Date().toISOString(),
    };
  }

  async sendCommand(cmd: VehicleCommand): Promise<VehicleCommandResult> {
    switch (cmd.name) {
      case 'set_climate_temp': {
        const c = Number(cmd.args?.celsius);
        if (!Number.isFinite(c) || c < 16 || c > 30) {
          return { ok: false, command: cmd.name, message: 'Temperatura fuera de rango (16-30 C).' };
        }
        this.state.climateTargetC = Math.round(c);
        this.state.climateOn = true;
        return { ok: true, command: cmd.name, message: `Clima a ${this.state.climateTargetC} grados.` };
      }
      case 'climate_on':
        this.state.climateOn = true;
        return { ok: true, command: cmd.name, message: 'Climatizador encendido.' };
      case 'climate_off':
        this.state.climateOn = false;
        return { ok: true, command: cmd.name, message: 'Climatizador apagado.' };
      case 'lock':
        this.state.locked = true;
        return { ok: true, command: cmd.name, message: 'Seguros cerrados.' };
      case 'unlock':
        this.state.locked = false;
        return { ok: true, command: cmd.name, message: 'Seguros abiertos.' };
      default:
        return { ok: true, command: cmd.name, message: 'Comando simulado.' };
    }
  }
}
