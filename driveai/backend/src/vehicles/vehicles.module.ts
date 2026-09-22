import { Module } from '@nestjs/common';
import { MemoryModule } from '../memory/memory.module';
import { GenericAndroidProvider } from './providers/generic-android.provider';
import { MockVehicleProvider } from './providers/mock-vehicle.provider';
import { TeslaProvider } from './providers/tesla.provider';
import { VehiclesController } from './vehicles.controller';
import { VehiclesService } from './vehicles.service';

@Module({
  imports: [MemoryModule],
  controllers: [VehiclesController],
  providers: [
    VehiclesService,
    MockVehicleProvider,
    GenericAndroidProvider,
    TeslaProvider,
  ],
  exports: [VehiclesService],
})
export class VehiclesModule {}
