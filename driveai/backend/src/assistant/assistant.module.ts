import { Module } from '@nestjs/common';
import { MemoryModule } from '../memory/memory.module';
import { VehiclesModule } from '../vehicles/vehicles.module';
import { AiRouterService } from './ai-router.service';
import { AssistantController } from './assistant.controller';
import { AssistantService } from './assistant.service';
import { OfflineRulesEngine } from './offline-rules.engine';

@Module({
  imports: [MemoryModule, VehiclesModule],
  controllers: [AssistantController],
  providers: [AssistantService, AiRouterService, OfflineRulesEngine],
  exports: [AssistantService, OfflineRulesEngine],
})
export class AssistantModule {}
