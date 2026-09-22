import { Injectable, Logger } from '@nestjs/common';
import { createHash, randomBytes } from 'crypto';
import { AppConfig } from '../../config/app.config';
import {
  Gear,
  VehicleCommand,
  VehicleCommandResult,
  VehicleProvider,
  VehicleStatus,
} from './vehicle-provider.interface';

interface TeslaTokens {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
}

/**
 * Tesla Fleet API v1 con OAuth 2.0 + PKCE.
 *
 * Flujo:
 *   1. `buildAuthUrl()` -> el conductor autoriza en el navegador del telefono.
 *   2. `exchangeCode()` -> canjea el code + code_verifier por tokens.
 *   3. `getStatus()` / `sendCommand()` refrescan el token automaticamente.
 *
 * Los tokens viven solo en memoria del gateway; nunca se exponen al cliente
 * de cabina ni se escriben en la base de datos.
 */
@Injectable()
export class TeslaProvider implements VehicleProvider {
  readonly name = 'tesla';
  private readonly logger = new Logger(TeslaProvider.name);
  private tokens: TeslaTokens | null = null;
  private readonly verifiers = new Map<string, { verifier: string; createdAt: number }>();
  private lastKnown: VehicleStatus | null = null;

  // ------------------------------------------------------------------
  // OAuth 2.0 PKCE
  // ------------------------------------------------------------------
  buildAuthUrl(scopes = 'openid offline_access vehicle_device_data vehicle_cmds vehicle_charging_cmds'): {
    url: string;
    state: string;
  } {
    const verifier = randomBytes(48).toString('base64url');
    const challenge = createHash('sha256').update(verifier).digest('base64url');
    const state = randomBytes(16).toString('base64url');
    this.verifiers.set(state, { verifier, createdAt: Date.now() });
    this.gcVerifiers();

    const params = new URLSearchParams({
      response_type: 'code',
      client_id: AppConfig.vehicle.tesla.clientId,
      redirect_uri: AppConfig.vehicle.tesla.redirectUri,
      scope: scopes,
      state,
      code_challenge: challenge,
      code_challenge_method: 'S256',
    });
    return { url: `${AppConfig.vehicle.tesla.authBase}/authorize?${params}`, state };
  }

  async exchangeCode(code: string, state: string): Promise<boolean> {
    const entry = this.verifiers.get(state);
    if (!entry) {
      this.logger.warn('State PKCE desconocido o expirado.');
      return false;
    }
    this.verifiers.delete(state);

    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: AppConfig.vehicle.tesla.clientId,
      code,
      code_verifier: entry.verifier,
      redirect_uri: AppConfig.vehicle.tesla.redirectUri,
      audience: AppConfig.vehicle.tesla.audience,
    });
    if (AppConfig.vehicle.tesla.clientSecret) {
      body.set('client_secret', AppConfig.vehicle.tesla.clientSecret);
    }
    return this.requestTokens(body);
  }

  private async refresh(): Promise<boolean> {
    if (!this.tokens?.refreshToken) return false;
    const body = new URLSearchParams({
      grant_type: 'refresh_token',
      client_id: AppConfig.vehicle.tesla.clientId,
      refresh_token: this.tokens.refreshToken,
    });
    return this.requestTokens(body);
  }

  private async requestTokens(body: URLSearchParams): Promise<boolean> {
    try {
      const res = await this.http(`${AppConfig.vehicle.tesla.authBase}/token`, {
        method: 'POST',
        headers: { 'content-type': 'application/x-www-form-urlencoded' },
        body: body.toString(),
      });
      if (!res) return false;
      this.tokens = {
        accessToken: res.access_token,
        refreshToken: res.refresh_token ?? this.tokens?.refreshToken ?? '',
        expiresAt: Date.now() + (Number(res.expires_in ?? 28800) - 60) * 1000,
      };
      this.logger.log('Tokens de Tesla Fleet API renovados.');
      return true;
    } catch (err) {
      this.logger.warn(`Intercambio de tokens fallido: ${(err as Error).message}`);
      return false;
    }
  }

  private async accessToken(): Promise<string | null> {
    if (!this.tokens) return null;
    if (this.tokens.expiresAt <= Date.now() && !(await this.refresh())) return null;
    return this.tokens.accessToken;
  }

  private gcVerifiers(): void {
    const cutoff = Date.now() - 10 * 60_000;
    for (const [state, entry] of this.verifiers) {
      if (entry.createdAt < cutoff) this.verifiers.delete(state);
    }
  }

  // ------------------------------------------------------------------
  // VehicleProvider
  // ------------------------------------------------------------------
  async isAvailable(): Promise<boolean> {
    return Boolean(await this.accessToken()) && Boolean(AppConfig.vehicle.tesla.vehicleTag);
  }

  async getStatus(): Promise<VehicleStatus> {
    const token = await this.accessToken();
    const tag = AppConfig.vehicle.tesla.vehicleTag;
    if (!token || !tag) return this.degraded();

    const data = await this.http(
      `${AppConfig.vehicle.tesla.audience}/api/1/vehicles/${tag}/vehicle_data` +
        `?endpoints=${encodeURIComponent('charge_state;climate_state;drive_state;vehicle_state')}`,
      { headers: { authorization: `Bearer ${token}` } },
    );
    const r = data?.response;
    if (!r) return this.degraded();

    const drive = r.drive_state ?? {};
    const charge = r.charge_state ?? {};
    const climate = r.climate_state ?? {};
    const vehicle = r.vehicle_state ?? {};
    const mphToKph = (mph: unknown) =>
      Number.isFinite(Number(mph)) ? Math.round(Number(mph) * 1.609344 * 10) / 10 : 0;

    const status: VehicleStatus = {
      provider: this.name,
      online: true,
      stale: false,
      speedKph: mphToKph(drive.speed ?? 0),
      gear: this.gear(drive.shift_state),
      batteryPercent: this.num(charge.battery_level, null),
      fuelPercent: null,
      rangeKm: Number.isFinite(Number(charge.battery_range))
        ? Math.round(Number(charge.battery_range) * 1.609344)
        : null,
      odometerKm: Number.isFinite(Number(vehicle.odometer))
        ? Math.round(Number(vehicle.odometer) * 1.609344)
        : null,
      insideTempC: this.num(climate.inside_temp, null),
      outsideTempC: this.num(climate.outside_temp, null),
      climateTargetC: this.num(climate.driver_temp_setting, null),
      climateOn: Boolean(climate.is_climate_on),
      locked: Boolean(vehicle.locked),
      tires: {
        fl: this.num(vehicle.tpms_pressure_fl, 0)!,
        fr: this.num(vehicle.tpms_pressure_fr, 0)!,
        rl: this.num(vehicle.tpms_pressure_rl, 0)!,
        rr: this.num(vehicle.tpms_pressure_rr, 0)!,
        unit: 'bar',
      },
      doorsOpen: ['df', 'pf', 'dr', 'pr'].filter((d) => Number(vehicle[d]) > 0),
      updatedAt: new Date().toISOString(),
    };
    this.lastKnown = status;
    return status;
  }

  async sendCommand(cmd: VehicleCommand): Promise<VehicleCommandResult> {
    const token = await this.accessToken();
    const tag = AppConfig.vehicle.tesla.vehicleTag;
    if (!token || !tag) {
      return {
        ok: false,
        command: cmd.name,
        message: 'Cuenta Tesla no vinculada. Abre Ajustes y conecta tu cuenta.',
      };
    }

    const map: Record<string, { endpoint: string; body?: Record<string, unknown> }> = {
      lock: { endpoint: 'door_lock' },
      unlock: { endpoint: 'door_unlock' },
      climate_on: { endpoint: 'auto_conditioning_start' },
      climate_off: { endpoint: 'auto_conditioning_stop' },
      honk: { endpoint: 'honk_horn' },
      flash_lights: { endpoint: 'flash_lights' },
      set_climate_temp: {
        endpoint: 'set_temps',
        body: {
          driver_temp: Number(cmd.args?.celsius ?? 22),
          passenger_temp: Number(cmd.args?.celsius ?? 22),
        },
      },
    };
    const target = map[cmd.name];
    if (!target) {
      return { ok: false, command: cmd.name, message: 'Comando no soportado.' };
    }

    const res = await this.http(
      `${AppConfig.vehicle.tesla.audience}/api/1/vehicles/${tag}/command/${target.endpoint}`,
      {
        method: 'POST',
        headers: {
          authorization: `Bearer ${token}`,
          'content-type': 'application/json',
        },
        body: JSON.stringify(target.body ?? {}),
      },
    );
    const ok = Boolean(res?.response?.result);
    return {
      ok,
      command: cmd.name,
      message: ok
        ? 'Listo.'
        : res?.response?.reason ?? 'El vehiculo no confirmo el comando.',
    };
  }

  // ------------------------------------------------------------------
  private async http(url: string, init: RequestInit = {}): Promise<any | null> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), AppConfig.vehicle.tesla.timeoutMs);
    try {
      const res = await fetch(url, { ...init, signal: controller.signal });
      if (!res.ok) {
        this.logger.debug(`Tesla API ${res.status} en ${url}`);
        return null;
      }
      return await res.json();
    } catch (err) {
      this.logger.debug(`Tesla API sin respuesta: ${(err as Error).message}`);
      return null;
    } finally {
      clearTimeout(timer);
    }
  }

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
