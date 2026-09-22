import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import Redis from 'ioredis';
import { AppConfig } from '../config/app.config';

/**
 * Cache de baja latencia + rate limiting de voz.
 *
 * Incluye un cache LRU en memoria como ultimo recurso para que, sin Redis,
 * el gateway siga sirviendo respuestas repetidas en microsegundos.
 */
@Injectable()
export class CacheService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(CacheService.name);
  private redis: Redis | null = null;
  private readonly local = new Map<string, { value: string; expiresAt: number }>();
  private readonly localMax = 256;

  async onModuleInit(): Promise<void> {
    if (!AppConfig.redis.enabled) {
      this.logger.warn('Redis deshabilitado, usando cache en memoria.');
      return;
    }
    this.redis = new Redis(AppConfig.redis.url, {
      maxRetriesPerRequest: 1,
      enableOfflineQueue: false,
      connectTimeout: 1000,
      lazyConnect: true,
      retryStrategy: (times) => Math.min(times * 500, 10_000),
    });
    this.redis.on('error', (err) =>
      this.logger.warn(`Redis degradado: ${err.message}`),
    );
    try {
      await this.redis.connect();
      this.logger.log('Redis conectado.');
    } catch (err) {
      this.logger.warn(`Redis no disponible: ${(err as Error).message}`);
    }
  }

  async onModuleDestroy(): Promise<void> {
    this.redis?.disconnect();
  }

  async get(key: string): Promise<string | null> {
    try {
      if (this.redis?.status === 'ready') return await this.redis.get(key);
    } catch {
      /* degradar a memoria */
    }
    const hit = this.local.get(key);
    if (!hit) return null;
    if (hit.expiresAt < Date.now()) {
      this.local.delete(key);
      return null;
    }
    return hit.value;
  }

  async set(key: string, value: string, ttlSeconds: number): Promise<void> {
    try {
      if (this.redis?.status === 'ready') {
        await this.redis.set(key, value, 'EX', ttlSeconds);
        return;
      }
    } catch {
      /* degradar a memoria */
    }
    if (this.local.size >= this.localMax) {
      const oldest = this.local.keys().next().value;
      if (oldest) this.local.delete(oldest);
    }
    this.local.set(key, { value, expiresAt: Date.now() + ttlSeconds * 1000 });
  }

  /**
   * Rate limit por ventana fija. Devuelve `true` si la peticion se permite.
   * Sin Redis se permite siempre (no castigamos al conductor por infraestructura).
   */
  async allow(key: string, limit: number, windowSeconds: number): Promise<boolean> {
    if (this.redis?.status !== 'ready') return true;
    try {
      const bucket = `rl:${key}:${Math.floor(Date.now() / (windowSeconds * 1000))}`;
      const count = await this.redis.incr(bucket);
      if (count === 1) await this.redis.expire(bucket, windowSeconds);
      return count <= limit;
    } catch {
      return true;
    }
  }

  async purgePrefix(prefix: string): Promise<void> {
    for (const key of [...this.local.keys()]) {
      if (key.startsWith(prefix)) this.local.delete(key);
    }
    if (this.redis?.status !== 'ready') return;
    try {
      let cursor = '0';
      do {
        const [next, keys] = await this.redis.scan(
          cursor,
          'MATCH',
          `${prefix}*`,
          'COUNT',
          200,
        );
        cursor = next;
        if (keys.length) await this.redis.del(...keys);
      } while (cursor !== '0');
    } catch {
      /* best effort */
    }
  }
}
