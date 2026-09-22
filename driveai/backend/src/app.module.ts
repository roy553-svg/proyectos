import { Module } from '@nestjs/common';
import { ThrottlerGuard, ThrottlerModule } from '@nestjs/throttler';
import { APP_GUARD } from '@nestjs/core';
import { AssistantModule } from './assistant/assistant.module';
import { InfraModule } from './infra/infra.module';
import { MemoryModule } from './memory/memory.module';
import { VehiclesModule } from './vehicles/vehicles.module';

@Module({
  imports: [
    InfraModule,
    /**
     * Cortafuegos de trafico: una radio colgada no debe inundar el gateway.
     *
     * El limite se dimensiona sobre el consumo real de UNA cabina: el HUD
     * sondea la telemetria cada segundo (60/min) y varias cabinas pueden
     * compartir IP tras el NAT del coche o de un taller. Con 120/min el
     * propio HUD agotaba la mitad del presupuesto y el conductor veia
     * caerse el velocimetro. La ruta de telemetria queda ademas exenta
     * (@SkipThrottle) por ser una lectura local y barata; el gasto caro
     * -la voz- lo limita Redis por conductor, no por IP.
     */
    ThrottlerModule.forRoot([{ ttl: 60_000, limit: 300 }]),
    MemoryModule,
    VehiclesModule,
    AssistantModule,
  ],
  providers: [{ provide: APP_GUARD, useClass: ThrottlerGuard }],
})
export class AppModule {}
