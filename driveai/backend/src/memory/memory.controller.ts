import { Body, Controller, Delete, Get, Param, Post, Query } from '@nestjs/common';
import { MemoryService } from './memory.service';
import {
  PrivacyFlagsDto,
  UpsertFactDto,
  UpsertPlaceDto,
  UpsertPreferenceDto,
} from './dto/memory.dto';

@Controller('api/memory')
export class MemoryController {
  constructor(private readonly memory: MemoryService) {}

  /** Vista jerarquica completa (la usa el panel de privacidad del cockpit). */
  @Get()
  async snapshot(@Query('driverId') driverId = 'demo-driver', @Query('q') q = '') {
    const ctx = await this.memory.buildContext(driverId, q);
    return {
      driverId,
      degraded: ctx.degraded,
      layers: {
        L1_working_memory: ctx.l1,
        L2_preferences: ctx.l2,
        L3_places: ctx.l3,
        L4_facts: ctx.l4,
      },
    };
  }

  @Get('places')
  places(@Query('driverId') driverId = 'demo-driver') {
    return this.memory.topPlaces(driverId);
  }

  @Post('preferences')
  async addPreference(@Body() dto: UpsertPreferenceDto) {
    await this.memory.upsertPreference(dto.driverId, {
      category: dto.category,
      subject: dto.subject,
      sentiment: dto.sentiment,
      origin: dto.origin,
      confidence: dto.confidence ?? (dto.origin === 'declared' ? 1 : 0.6),
      evidence: dto.evidence,
    });
    return { ok: true };
  }

  @Post('places')
  async addPlace(@Body() dto: UpsertPlaceDto) {
    await this.memory.upsertPlace(dto.driverId, dto);
    return { ok: true };
  }

  @Post('facts')
  async addFact(@Body() dto: UpsertFactDto) {
    await this.memory.upsertFact(dto.driverId, {
      fact_key: dto.factKey,
      fact_value: dto.factValue,
      schedule_cron: dto.scheduleCron,
      confidence: dto.confidence ?? 0.9,
      evidence: dto.evidence,
    });
    return { ok: true };
  }

  /** GDPR :: portabilidad de datos. */
  @Get('export')
  export(@Query('driverId') driverId = 'demo-driver') {
    return this.memory.exportAll(driverId);
  }

  /** GDPR :: derecho al olvido (purga total). */
  @Delete()
  purge(@Query('driverId') driverId = 'demo-driver') {
    return this.memory.purgeAll(driverId);
  }

  /** Purga de una sola capa. */
  @Delete('layer/:layer')
  async purgeLayer(
    @Param('layer') layer: 'L1' | 'L2' | 'L3' | 'L4',
    @Query('driverId') driverId = 'demo-driver',
  ) {
    await this.memory.purgeLayer(driverId, layer);
    return { ok: true, layer };
  }

  @Post('privacy')
  async privacy(@Body() dto: PrivacyFlagsDto) {
    const id = await this.memory.resolveDriverId(dto.driverId);
    if (!id) return { ok: false, reason: 'memoria no disponible' };
    await this.memory.setPrivacyFlags(id, {
      mic_enabled: dto.micEnabled,
      location_enabled: dto.locationEnabled,
      memory_enabled: dto.memoryEnabled,
    });
    return { ok: true, flags: await this.memory.getPrivacyFlags(id) };
  }
}
