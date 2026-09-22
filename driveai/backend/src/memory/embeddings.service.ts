import { Injectable, Logger } from '@nestjs/common';
import { GoogleGenAI } from '@google/genai';
import { AppConfig } from '../config/app.config';

export const EMBEDDING_DIM = 768;

/**
 * Generador de embeddings de 768 dimensiones.
 *
 * 1. Si hay clave de Gemini: usa `text-embedding-004` (semantica real).
 * 2. Si no hay red o clave: cae a un embedding local determinista por
 *    hashing de n-gramas. No es semantico, pero es estable y permite que la
 *    busqueda vectorial siga funcionando offline sin romper el esquema.
 */
@Injectable()
export class EmbeddingsService {
  private readonly logger = new Logger(EmbeddingsService.name);
  private client: GoogleGenAI | null = null;

  constructor() {
    if (AppConfig.gemini.apiKey) {
      this.client = new GoogleGenAI({ apiKey: AppConfig.gemini.apiKey });
    }
  }

  async embed(text: string): Promise<number[]> {
    const clean = (text ?? '').trim();
    if (!clean) return new Array(EMBEDDING_DIM).fill(0);

    if (this.client) {
      try {
        const res = await this.withTimeout(
          this.client.models.embedContent({
            model: AppConfig.gemini.embeddingModel,
            contents: clean.slice(0, 2000),
          }),
          AppConfig.gemini.timeoutMs,
        );
        const values =
          (res as any)?.embeddings?.[0]?.values ?? (res as any)?.embedding?.values;
        if (Array.isArray(values) && values.length) {
          return this.fit(values as number[]);
        }
      } catch (err) {
        this.logger.debug(`Embedding remoto fallido: ${(err as Error).message}`);
      }
    }
    return this.localEmbedding(clean);
  }

  /** Formato literal de pgvector: '[0.1,0.2,...]'. */
  toVectorLiteral(vec: number[]): string {
    return `[${vec.map((v) => (Number.isFinite(v) ? v.toFixed(6) : '0')).join(',')}]`;
  }

  private fit(values: number[]): number[] {
    if (values.length === EMBEDDING_DIM) return values;
    const out = new Array(EMBEDDING_DIM).fill(0);
    for (let i = 0; i < values.length; i++) out[i % EMBEDDING_DIM] += values[i];
    return this.normalize(out);
  }

  /** Bag-of-words hasheado + normalizacion L2. Coste ~0.2 ms. */
  private localEmbedding(text: string): number[] {
    const vec = new Array(EMBEDDING_DIM).fill(0);
    const tokens = text
      .toLowerCase()
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .split(/[^a-z0-9]+/)
      .filter(Boolean);
    for (const token of tokens) {
      vec[this.hash(token) % EMBEDDING_DIM] += 1;
      for (let i = 0; i < token.length - 2; i++) {
        vec[this.hash(token.slice(i, i + 3)) % EMBEDDING_DIM] += 0.5;
      }
    }
    return this.normalize(vec);
  }

  private normalize(vec: number[]): number[] {
    const norm = Math.sqrt(vec.reduce((acc, v) => acc + v * v, 0));
    return norm === 0 ? vec : vec.map((v) => v / norm);
  }

  private hash(input: string): number {
    let h = 2166136261;
    for (let i = 0; i < input.length; i++) {
      h ^= input.charCodeAt(i);
      h = Math.imul(h, 16777619);
    }
    return Math.abs(h);
  }

  private withTimeout<T>(p: Promise<T>, ms: number): Promise<T> {
    return Promise.race([
      p,
      new Promise<T>((_, reject) =>
        setTimeout(() => reject(new Error('embedding timeout')), ms),
      ),
    ]);
  }
}
