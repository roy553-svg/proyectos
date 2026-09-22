import { Global, Module } from '@nestjs/common';
import { CacheService } from './cache.service';
import { DatabaseService } from './database.service';

@Global()
@Module({
  providers: [DatabaseService, CacheService],
  exports: [DatabaseService, CacheService],
})
export class InfraModule {}
