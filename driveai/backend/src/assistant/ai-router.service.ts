import { Injectable, Logger } from '@nestjs/common';
import { GoogleGenAI } from '@google/genai';
import { AppConfig } from '../config/app.config';
import { MemoryContext } from '../memory/memory.types';
import { VehicleStatus } from '../vehicles/providers/vehicle-provider.interface';
import { OfflineRulesEngine } from './offline-rules.engine';

export type AnswerSource = 'gemini' | 'gemini-fallback' | 'offline' | 'cache';

export interface RoutedAnswer {
  text: string;
  source: AnswerSource;
  model?: string;
  rule?: string;
  latencyMs: number;
  action?: { type: 'navigate' | 'command'; payload: Record<string, unknown> };
}

/**
 * Enrutador de IA.
 *
 * Cascada: gemini-3.8-flash -> gemini-2.5-flash -> motor determinista local.
 * En cualquier escalon hay un presupuesto de latencia duro; si se agota,
 * bajamos un nivel. El conductor SIEMPRE recibe una respuesta.
 */
@Injectable()
export class AiRouterService {
  private readonly logger = new Logger(AiRouterService.name);
  private readonly client: GoogleGenAI | null;

  constructor(private readonly offline: OfflineRulesEngine) {
    this.client = AppConfig.gemini.apiKey
      ? new GoogleGenAI({ apiKey: AppConfig.gemini.apiKey })
      : null;
    if (!this.client) {
      this.logger.warn(
        'GEMINI_API_KEY ausente: DriveAI opera 100% con el motor determinista local.',
      );
    }
  }

  hasRemote(): boolean {
    return this.client !== null;
  }

  async route(
    question: string,
    status: VehicleStatus | null,
    memory: MemoryContext,
  ): Promise<RoutedAnswer> {
    const startedAt = Date.now();

    if (this.client) {
      for (const model of [
        AppConfig.gemini.primaryModel,
        AppConfig.gemini.fallbackModel,
      ]) {
        const text = await this.askGemini(model, question, status, memory);
        if (text) {
          return {
            text: this.enforceBrevity(text),
            source: model === AppConfig.gemini.primaryModel ? 'gemini' : 'gemini-fallback',
            model,
            latencyMs: Date.now() - startedAt,
          };
        }
      }
    }

    // Motor determinista local: tunel, estacionamiento, zona rural o sin clave.
    const local = this.offline.answer(question, { status, memory, now: new Date() });
    return {
      text: this.enforceBrevity(local.text),
      source: 'offline',
      rule: local.rule,
      action: local.action,
      latencyMs: Date.now() - startedAt,
    };
  }

  /**
   * Transcribe PCM 16 kHz mono usando la entrada multimodal de Gemini.
   * Sin clave o sin red devuelve `null` y el cliente cae al reconocimiento
   * del WebView, que funciona sin gateway.
   */
  async transcribe(pcm16: Buffer): Promise<string | null> {
    if (!this.client || pcm16.length === 0) return null;

    const wav = this.wrapPcmAsWav(pcm16).toString('base64');
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), AppConfig.gemini.timeoutMs * 2);
    try {
      const res = await this.client.models.generateContent({
        model: AppConfig.gemini.fallbackModel,
        contents: [
          {
            role: 'user',
            parts: [
              { inlineData: { mimeType: 'audio/wav', data: wav } },
              {
                text:
                  'Transcribe literalmente el audio en espanol. ' +
                  'Devuelve solo la transcripcion, sin comillas ni comentarios.',
              },
            ],
          },
        ] as any,
        config: { temperature: 0, maxOutputTokens: 120, abortSignal: controller.signal } as any,
      });
      const text = (res as any)?.text?.trim?.() ?? '';
      return text || null;
    } catch (err) {
      this.logger.warn(`Transcripcion fallida: ${(err as Error).message}`);
      return null;
    } finally {
      clearTimeout(timer);
    }
  }

  /** Cabecera WAV de 44 bytes sobre PCM 16 kHz mono de 16 bit. */
  private wrapPcmAsWav(pcm: Buffer, sampleRate = 16_000): Buffer {
    const header = Buffer.alloc(44);
    header.write('RIFF', 0);
    header.writeUInt32LE(36 + pcm.length, 4);
    header.write('WAVE', 8);
    header.write('fmt ', 12);
    header.writeUInt32LE(16, 16);
    header.writeUInt16LE(1, 20);              // PCM
    header.writeUInt16LE(1, 22);              // mono
    header.writeUInt32LE(sampleRate, 24);
    header.writeUInt32LE(sampleRate * 2, 28); // byte rate
    header.writeUInt16LE(2, 32);              // block align
    header.writeUInt16LE(16, 34);             // bits por muestra
    header.write('data', 36);
    header.writeUInt32LE(pcm.length, 40);
    return Buffer.concat([header, pcm]);
  }

  // ------------------------------------------------------------------
  private async askGemini(
    model: string,
    question: string,
    status: VehicleStatus | null,
    memory: MemoryContext,
  ): Promise<string | null> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), AppConfig.gemini.timeoutMs);
    try {
      const res = await this.client!.models.generateContent({
        model,
        contents: [
          ...memory.l1.map((t) => ({
            role: t.role === 'driver' ? 'user' : ('model' as const),
            parts: [{ text: t.content }],
          })),
          { role: 'user', parts: [{ text: question }] },
        ] as any,
        config: {
          systemInstruction: this.systemPrompt(status, memory),
          maxOutputTokens: AppConfig.gemini.maxOutputTokens,
          temperature: AppConfig.gemini.temperature,
          abortSignal: controller.signal,
        } as any,
      });
      const text = (res as any)?.text ?? '';
      return typeof text === 'string' && text.trim() ? text.trim() : null;
    } catch (err) {
      this.logger.warn(`Modelo ${model} no respondio: ${(err as Error).message}`);
      return null;
    } finally {
      clearTimeout(timer);
    }
  }

  /**
   * Prompt de sistema. Dos objetivos duros:
   *   1. Brevedad de seguridad vial (2-3 oraciones, < 10 s hablados).
   *   2. Anti-alucinacion: solo puede afirmar lo que aparece en el contexto,
   *      y debe matizar lo marcado como "inferido".
   */
  private systemPrompt(status: VehicleStatus | null, memory: MemoryContext): string {
    const lines: string[] = [
      'Eres DriveAI, el copiloto de voz del conductor. Hablas espanol neutro de Mexico.',
      'REGLA DE SEGURIDAD VIAL: responde en 2 o 3 oraciones cortas, menos de 10 segundos hablados.',
      'Nunca uses introducciones, disculpas, listas, markdown ni emojis. Ve directo al dato.',
      'El conductor va manejando: si la respuesta es larga, da solo lo esencial y ofrece detalle despues.',
      'ANTI-ALUCINACION: sobre el vehiculo o el usuario, afirma unicamente lo que aparece en el CONTEXTO.',
      'Si un dato esta marcado como inferido, matizalo ("creo que", "me parece"). Si no lo sabes, dilo en una frase.',
      'Nunca sugieras maniobras peligrosas ni acciones que exijan soltar el volante.',
    ];

    if (status) {
      lines.push(
        '--- TELEMETRIA ACTUAL ---',
        `velocidad=${status.speedKph} km/h; marcha=${status.gear}; ` +
          `bateria=${status.batteryPercent ?? 'n/d'}%; autonomia=${status.rangeKm ?? 'n/d'} km; ` +
          `cabina=${status.insideTempC ?? 'n/d'}C; exterior=${status.outsideTempC ?? 'n/d'}C; ` +
          `seguros=${status.locked ? 'cerrados' : 'abiertos'}` +
          (status.tires
            ? `; llantas(${status.tires.unit})=FL ${status.tires.fl}/FR ${status.tires.fr}/RL ${status.tires.rl}/RR ${status.tires.rr}`
            : ''),
        status.stale ? 'AVISO: telemetria no fresca, matiza tus afirmaciones.' : '',
      );
    }

    if (memory.l2.length) {
      lines.push(
        '--- CAPA 2: PREFERENCIAS ---',
        ...memory.l2.map(
          (p) =>
            `[${p.origin === 'declared' ? 'DECLARADO' : 'INFERIDO'} ${Math.round(p.confidence * 100)}%] ` +
            `${p.category}: ${p.sentiment === 'dislike' ? 'no le gusta' : 'le gusta'} ${p.subject}`,
        ),
      );
    }
    if (memory.l3.length) {
      lines.push(
        '--- CAPA 3: LUGARES VISITADOS ---',
        ...memory.l3.map(
          (p) =>
            `${p.name}${p.rating ? `, calificado ${p.rating}` : ''}, visitado ${p.visit_count ?? 1} veces` +
            (p.address ? ` (${p.address})` : ''),
        ),
      );
    }
    if (memory.l4.length) {
      lines.push(
        '--- CAPA 4: RUTINAS CONSOLIDADAS ---',
        ...memory.l4.map((f) => `${f.fact_key} = ${f.fact_value}`),
      );
    }
    if (memory.degraded) {
      lines.push('AVISO: memoria no disponible. No inventes historial del conductor.');
    }

    return lines.filter(Boolean).join('\n');
  }

  /**
   * Cinturon de seguridad de brevedad aplicado en servidor.
   * El prompt puede ignorarse; este recorte no.
   */
  enforceBrevity(text: string): string {
    let clean = text
      .replace(/[*_`#>]+/g, '')
      .replace(/^\s*[-•]\s*/gm, '')
      .replace(/\s+/g, ' ')
      .trim();

    // Cortamos por fin de oracion real (punto + espacio), nunca dentro de un
    // decimal como "2.4 bar" ni de una abreviatura.
    const sentences = clean.split(/(?<=[.!?])\s+/).filter(Boolean);
    clean = sentences.slice(0, AppConfig.safety.maxSentences).join(' ').trim();

    if (clean.length > AppConfig.safety.maxChars) {
      const cut = clean.slice(0, AppConfig.safety.maxChars);
      const lastStop = Math.max(cut.lastIndexOf('. '), cut.lastIndexOf(', '));
      clean = (lastStop > 60 ? cut.slice(0, lastStop) : cut).trim().replace(/[,;:]$/, '') + '.';
    }
    return clean;
  }
}
