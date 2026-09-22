import { Body, Controller, Get, Post, Query } from '@nestjs/common';
import { SkipThrottle } from '@nestjs/throttler';
import { VehiclesService } from './vehicles.service';
import { VehicleCommandDto } from './dto/vehicle-command.dto';

@Controller('api/vehicles')
export class VehiclesController {
  constructor(private readonly vehicles: VehiclesService) {}

  /**
   * Telemetria en tiempo real para el HUD.
   * Exenta del limite por IP: el sondeo de 1 Hz es el latido de la cabina
   * y no puede competir por cuota con las consultas de voz.
   */
  @SkipThrottle()
  @Get('status')
  async status() {
    const status = await this.vehicles.getStatus();
    return {
      ...status,
      /** La UI usa esto para atenuar botones antes del toque. */
      safety: {
        lockCommandsAllowed: this.vehicles.evaluateSafety(status, 'unlock').allowed,
        reason: this.vehicles.evaluateSafety(status, 'unlock').reason ?? null,
      },
    };
  }

  /** Comandos seguros (clima y seguros). */
  @Post('commands')
  command(@Body() dto: VehicleCommandDto) {
    return this.vehicles.sendCommand(dto.driverId ?? 'demo-driver', {
      name: dto.command,
      args: dto.args,
    });
  }

  // --------------------------------------------------------------
  // Tesla Fleet API :: OAuth 2.0 PKCE
  // --------------------------------------------------------------
  @Get('tesla/auth-url')
  teslaAuthUrl() {
    return this.vehicles.teslaProvider().buildAuthUrl();
  }

  @Get('tesla/callback')
  async teslaCallback(@Query('code') code: string, @Query('state') state: string) {
    const ok = await this.vehicles.teslaProvider().exchangeCode(code, state);
    return { linked: ok };
  }
}
