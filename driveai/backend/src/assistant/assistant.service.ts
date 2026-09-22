import { Injectable, Logger } from '@nestjs/common';
import { createHash } from 'crypto';
import { AppConfig } from '../config/app.config';
import { CacheService } from '../infra/cache.service';
import { MemoryService } from '../memory/memory.service';
import { EMPTY_CONTEXT } from '../memory/memory.types';
import { VehiclesService } from '../vehicles/vehicles.service';
import { AiRouterService, RoutedAnswer } from './ai-router.service';

export interface AssistantReply extends RoutedAnswer {
  driverId: string;
  /** Sugerencias de 1 toque que la UI muestra tras responder. */
  quickActions: Array<{ label: string; command?: string; args?: Record<string, unknown> }>;
}

@Injectable()
export class AssistantService {
  private readonly logger = new Logger(AssistantService.name);

  constructor(
    private readonly router: AiRouterService,
    private readonly memory: MemoryService,
    private readonly vehicles: VehiclesService,
    private readonly cache: CacheService,
  ) {}

  async ask(
    message: string,
    driverId = 'demo-driver',
    viaVoice = false,
  ): Promise<AssistantReply> {
    const question = message.trim();

    // Rate limiting de voz (protege la cuota gratuita de Gemini).
    if (viaVoice) {
      const allowed = await this.cache.allow(
        `voice:${driverId}`,
        AppConfig.redis.voiceRateLimit,
        AppConfig.redis.voiceRateWindowSeconds,
      );
      if (!allowed) {
        return {
          driverId,
          text: 'Vas muy rapido con las preguntas. Dame un segundo.',
          source: 'offline',
          rule: 'rate-limit',
          latencyMs: 0,
          quickActions: [],
        };
      }
    }

    // Cache de respuestas identicas: latencia ~1 ms y cero cuota consumida.
    const cacheKey = `answer:${driverId}:${createHash('sha1').update(question.toLowerCase()).digest('hex')}`;
    const cached = await this.cache.get(cacheKey);
    if (cached) {
      try {
        const hit = JSON.parse(cached) as AssistantReply;
        return { ...hit, source: 'cache', latencyMs: 0 };
      } catch {
        /* cache corrupto: seguimos de largo */
      }
    }

    // Telemetria y memoria en paralelo; ninguna puede bloquear la respuesta.
    const [status, context] = await Promise.all([
      this.vehicles.getStatus().catch(() => null),
      this.memory.buildContext(driverId, question).catch(() => ({ ...EMPTY_CONTEXT })),
    ]);

    const answer = await this.router.route(question, status, context);

    // Persistencia en segundo plano: el conductor ya tiene su respuesta.
    void this.persist(driverId, question, answer).catch((err) =>
      this.logger.debug(`Persistencia diferida fallida: ${err.message}`),
    );

    const reply: AssistantReply = {
      ...answer,
      driverId,
      quickActions: this.quickActions(answer, context.l3[0]),
    };

    if (answer.source !== 'offline') {
      void this.cache.set(cacheKey, JSON.stringify(reply), AppConfig.redis.answerTtlSeconds);
    }
    return reply;
  }

  private async persist(
    driverId: string,
    question: string,
    answer: RoutedAnswer,
  ): Promise<void> {
    await this.memory.appendTurn(driverId, 'driver', question, 'input');
    await this.memory.appendTurn(driverId, 'assistant', answer.text, answer.source);
    await this.memory.learnFrom(driverId, question);
  }

  /** Botones de 1 toque derivados de la respuesta (sin menus anidados). */
  private quickActions(
    answer: RoutedAnswer,
    place?: { name: string; lat?: number | null; lon?: number | null },
  ): AssistantReply['quickActions'] {
    const actions: AssistantReply['quickActions'] = [];
    if (answer.action?.type === 'navigate') {
      const p = answer.action.payload as any;
      actions.push({ label: `INICIAR RUTA · ${p.name ?? p.address ?? 'Destino'}` });
    } else if (place) {
      actions.push({ label: `INICIAR RUTA · ${place.name}` });
    }
    if (answer.action?.type === 'command') {
      const p = answer.action.payload as any;
      actions.push({ label: 'CONFIRMAR', command: p.command, args: p.args });
    }
    return actions.slice(0, 2);
  }

  /**
   * Voz -> texto. Devuelve `null` cuando no hay IA remota disponible;
   * el cliente entonces usa el reconocimiento local del WebView.
   */
  async transcribe(pcm16: Buffer): Promise<string | null> {
    return this.router.transcribe(pcm16);
  }

  health() {
    return {
      status: 'ok',
      remoteAi: this.router.hasRemote(),
      mode: this.router.hasRemote() ? 'hibrido' : 'determinista-local',
      models: {
        primary: AppConfig.gemini.primaryModel,
        fallback: AppConfig.gemini.fallbackModel,
      },
      vehicleProvider: AppConfig.vehicle.provider,
    };
  }
}
