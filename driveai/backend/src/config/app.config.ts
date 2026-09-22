/**
 * Configuracion central de DriveAI.
 * Todo tiene valor por defecto: el gateway DEBE arrancar aunque no exista
 * ninguna variable de entorno (modo determinista / offline puro).
 */
const num = (v: string | undefined, d: number): number => {
  const n = Number(v);
  return Number.isFinite(n) ? n : d;
};
const bool = (v: string | undefined, d: boolean): boolean =>
  v === undefined ? d : /^(1|true|yes|on)$/i.test(v);

export const AppConfig = {
  port: num(process.env.PORT, 8080),

  gemini: {
    apiKey: process.env.GEMINI_API_KEY ?? '',
    /** Modelo primario de alta velocidad. */
    primaryModel: process.env.GEMINI_PRIMARY_MODEL ?? 'gemini-3.8-flash',
    /** Reintento automatico si el primario no esta disponible. */
    fallbackModel: process.env.GEMINI_FALLBACK_MODEL ?? 'gemini-2.5-flash',
    embeddingModel: process.env.GEMINI_EMBEDDING_MODEL ?? 'text-embedding-004',
    /**
     * Presupuesto duro de latencia. Si Gemini no responde dentro de esta
     * ventana se aborta y contesta el motor determinista local.
     * El conductor jamas espera un spinner infinito.
     */
    timeoutMs: num(process.env.GEMINI_TIMEOUT_MS, 3500),
    maxOutputTokens: num(process.env.GEMINI_MAX_OUTPUT_TOKENS, 160),
    temperature: num(process.env.GEMINI_TEMPERATURE, 0.4),
  },

  postgres: {
    url:
      process.env.DATABASE_URL ??
      'postgresql://driveai:driveai@postgres:5432/driveai',
    enabled: bool(process.env.MEMORY_ENABLED, true),
    poolMax: num(process.env.PG_POOL_MAX, 8),
    connectTimeoutMs: num(process.env.PG_CONNECT_TIMEOUT_MS, 2000),
    statementTimeoutMs: num(process.env.PG_STATEMENT_TIMEOUT_MS, 1500),
  },

  redis: {
    url: process.env.REDIS_URL ?? 'redis://redis:6379',
    enabled: bool(process.env.REDIS_ENABLED, true),
    /** TTL del cache de respuestas identicas (ahorra cuota gratuita). */
    answerTtlSeconds: num(process.env.ANSWER_CACHE_TTL, 900),
    /** Rate limit de voz por conductor. */
    voiceRateLimit: num(process.env.VOICE_RATE_LIMIT, 20),
    voiceRateWindowSeconds: num(process.env.VOICE_RATE_WINDOW, 60),
  },

  vehicle: {
    /** generic-android | tesla | mock */
    provider: (process.env.VEHICLE_PROVIDER ?? 'mock').toLowerCase(),
    /** Velocidad a partir de la cual se bloquean comandos no criticos. */
    lockoutSpeedKph: num(process.env.LOCKOUT_SPEED_KPH, 5),
    bridgeUrl: process.env.ANDROID_BRIDGE_URL ?? 'http://127.0.0.1:8099',
    bridgeTimeoutMs: num(process.env.ANDROID_BRIDGE_TIMEOUT_MS, 1200),
    tesla: {
      clientId: process.env.TESLA_CLIENT_ID ?? '',
      clientSecret: process.env.TESLA_CLIENT_SECRET ?? '',
      redirectUri:
        process.env.TESLA_REDIRECT_URI ?? 'https://localhost/auth/tesla/callback',
      audience:
        process.env.TESLA_AUDIENCE ?? 'https://fleet-api.prd.na.vn.cloud.tesla.com',
      authBase: process.env.TESLA_AUTH_BASE ?? 'https://auth.tesla.com/oauth2/v3',
      vehicleTag: process.env.TESLA_VEHICLE_TAG ?? '',
      timeoutMs: num(process.env.TESLA_TIMEOUT_MS, 4000),
    },
  },

  safety: {
    /** Maximo de oraciones que puede devolver el asistente. */
    maxSentences: num(process.env.MAX_SENTENCES, 3),
    /** Corte duro de caracteres (~10 s de audio hablado en es-MX). */
    maxChars: num(process.env.MAX_ANSWER_CHARS, 240),
  },
} as const;

export type AppConfigType = typeof AppConfig;
