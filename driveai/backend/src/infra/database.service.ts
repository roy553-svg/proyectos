import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { Pool, QueryResult, QueryResultRow } from 'pg';
import { AppConfig } from '../config/app.config';

/**
 * Pool de PostgreSQL tolerante a fallos.
 *
 * Regla de oro de DriveAI: la base de datos NUNCA puede tumbar al asistente.
 * Si Postgres no esta disponible, `query()` devuelve `null` y las capas
 * superiores degradan a "sin memoria" en lugar de lanzar un error al conductor.
 */
@Injectable()
export class DatabaseService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(DatabaseService.name);
  private pool: Pool | null = null;
  private healthy = false;

  async onModuleInit(): Promise<void> {
    if (!AppConfig.postgres.enabled) {
      this.logger.warn('Memoria persistente deshabilitada por configuracion.');
      return;
    }
    this.pool = new Pool({
      connectionString: AppConfig.postgres.url,
      max: AppConfig.postgres.poolMax,
      connectionTimeoutMillis: AppConfig.postgres.connectTimeoutMs,
      idleTimeoutMillis: 30_000,
      statement_timeout: AppConfig.postgres.statementTimeoutMs,
    });
    this.pool.on('error', (err) => {
      this.healthy = false;
      this.logger.warn(`Pool PostgreSQL degradado: ${err.message}`);
    });
    await this.probe();
  }

  async onModuleDestroy(): Promise<void> {
    await this.pool?.end().catch(() => undefined);
  }

  isHealthy(): boolean {
    return this.healthy;
  }

  private async probe(): Promise<void> {
    try {
      await this.pool?.query('SELECT 1');
      this.healthy = true;
      this.logger.log('PostgreSQL + pgvector conectado.');
    } catch (err) {
      this.healthy = false;
      this.logger.warn(
        `PostgreSQL no disponible, DriveAI opera sin memoria: ${(err as Error).message}`,
      );
    }
  }

  /** Ejecuta una consulta. Devuelve null si la base no esta operativa. */
  async query<T extends QueryResultRow = QueryResultRow>(
    text: string,
    params: unknown[] = [],
  ): Promise<QueryResult<T> | null> {
    if (!this.pool) return null;
    try {
      const res = await this.pool.query<T>(text, params);
      this.healthy = true;
      return res;
    } catch (err) {
      this.healthy = false;
      this.logger.warn(`Query fallida (degradando): ${(err as Error).message}`);
      return null;
    }
  }

  /** Atajo: filas o arreglo vacio. */
  async rows<T extends QueryResultRow = QueryResultRow>(
    text: string,
    params: unknown[] = [],
  ): Promise<T[]> {
    const res = await this.query<T>(text, params);
    return res?.rows ?? [];
  }
}
