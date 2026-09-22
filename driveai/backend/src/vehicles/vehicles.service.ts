import { Injectable, Logger } from '@nestjs/common';
import { AppConfig } from '../config/app.config';
import { DatabaseService } from '../infra/database.service';
import { MemoryService } from '../memory/memory.service';
import { GenericAndroidProvider } from './providers/generic-android.provider';
import { MockVehicleProvider } from './providers/mock-vehicle.provider';
import { TeslaProvider } from './providers/tesla.provider';
import {
  MOTION_RESTRICTED,
  VehicleCommand,
  VehicleCommandResult,
  VehicleProvider,
  VehicleStatus,
} from './providers/vehicle-provider.interface';

/**
 * Orquestador de la capa de abstraccion vehicular + politica de seguridad.
 *
 * Regla no negociable: ningun comando fisico se ejecuta con el coche en
 * movimiento. La decision se toma SIEMPRE con telemetria fresca leida justo
 * antes del comando, nunca con el estado cacheado del HUD.
 */
@Injectable()
export class VehiclesService {
  private readonly logger = new Logger(VehiclesService.name);
  private readonly providers: Record<string, VehicleProvider>;

  constructor(
    private readonly mock: MockVehicleProvider,
    private readonly android: GenericAndroidProvider,
    private readonly tesla: TeslaProvider,
    private readonly db: DatabaseService,
    private readonly memory: MemoryService,
  ) {
    this.providers = {
      mock: this.mock,
      'generic-android': this.android,
      tesla: this.tesla,
    };
  }

  private active(): VehicleProvider {
    return this.providers[AppConfig.vehicle.provider] ?? this.mock;
  }

  teslaProvider(): TeslaProvider {
    return this.tesla;
  }

  /**
   * Telemetria para el HUD. Si el proveedor configurado esta caido caemos al
   * simulador para que la pantalla nunca quede en blanco ni con spinner.
   */
  async getStatus(): Promise<VehicleStatus> {
    const provider = this.active();
    try {
      const status = await provider.getStatus();
      if (status.online || provider === this.mock) return status;
      if (status.stale) return status; // ultimo valor conocido: mejor que nada
      return status;
    } catch (err) {
      this.logger.warn(`Proveedor ${provider.name} fallo: ${(err as Error).message}`);
      return { ...(await this.mock.getStatus()), provider: provider.name, stale: true };
    }
  }

  /**
   * Evalua la politica de seguridad sin ejecutar nada.
   * Expuesto para que la UI atenue los botones antes de que el conductor
   * los presione.
   */
  evaluateSafety(
    status: VehicleStatus,
    command: VehicleCommand['name'],
  ): { allowed: boolean; reason?: string } {
    const moving =
      status.speedKph > AppConfig.vehicle.lockoutSpeedKph || status.gear === 'D' || status.gear === 'R';

    if (MOTION_RESTRICTED.has(command) && moving) {
      return {
        allowed: false,
        reason: 'El vehiculo esta en movimiento. Detente y pon la marcha en P.',
      };
    }
    if (command === 'unlock' && status.speedKph > 0) {
      return { allowed: false, reason: 'No se abren seguros con el coche rodando.' };
    }
    return { allowed: true };
  }

  async sendCommand(
    driverId: string,
    cmd: VehicleCommand,
  ): Promise<VehicleCommandResult> {
    const provider = this.active();
    const status = await this.getStatus(); // telemetria fresca, no cacheada
    const verdict = this.evaluateSafety(status, cmd.name);

    await this.audit(driverId, provider.name, cmd, verdict.allowed, verdict.reason, status);

    if (!verdict.allowed) {
      return {
        ok: false,
        blocked: true,
        command: cmd.name,
        message: verdict.reason ?? 'Comando bloqueado por seguridad.',
      };
    }

    try {
      return await provider.sendCommand(cmd);
    } catch (err) {
      this.logger.warn(`Comando ${cmd.name} fallo: ${(err as Error).message}`);
      return {
        ok: false,
        command: cmd.name,
        message: 'No pude comunicarme con el vehiculo.',
      };
    }
  }

  private async audit(
    externalId: string,
    provider: string,
    cmd: VehicleCommand,
    allowed: boolean,
    reason: string | undefined,
    status: VehicleStatus,
  ): Promise<void> {
    if (!this.db.isHealthy()) return;
    const driverId = await this.memory.resolveDriverId(externalId);
    await this.db.query(
      `INSERT INTO vehicle_command_log
         (driver_id, provider, command, payload, allowed, reason, speed_kph, gear)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
      [
        driverId,
        provider,
        cmd.name,
        JSON.stringify(cmd.args ?? {}),
        allowed,
        reason ?? null,
        status.speedKph,
        status.gear,
      ],
    );
  }
}
