import { Module } from '@nestjs/common';
import { EmbeddingsService } from './embeddings.service';
import { MemoryController } from './memory.controller';
import { MemoryService } from './memory.service';

@Module({
  controllers: [MemoryController],
  providers: [MemoryService, EmbeddingsService],
  exports: [MemoryService, EmbeddingsService],
})
export class MemoryModule {}
