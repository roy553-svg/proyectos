import { Injectable, Logger } from '@nestjs/common';
import { DatabaseService } from '../infra/database.service';
import { CacheService } from '../infra/cache.service';
import { EmbeddingsService } from './embeddings.service';
import {
  EMPTY_CONTEXT,
  HistoricalFact,
  MemoryContext,
  Place,
  Preference,
  WorkingTurn,
} from './memory.types';

/** Turnos de la Capa 1 que se inyectan al prompt (2 pares conductor/asistente). */
const L1_TURNS = 4;

/**
 * Motor de Memoria Jerarquico en 4 Capas.
 *
 * Anti-alucinacion: cada dato recuperado viaja con su `origin` y su
 * `evidence` textual. El enrutador de IA solo puede afirmar lo que aparece
 * aqui; lo inferido se etiqueta como tal para que el modelo lo matice.
 */
@Injectable()
export class MemoryService {
  private readonly logger = new Logger(MemoryService.name);

  constructor(
    private readonly db: DatabaseService,
    private readonly cache: CacheService,
    private readonly embeddings: EmbeddingsService,
  ) {}

  // ------------------------------------------------------------------
  // Conductor
  // ------------------------------------------------------------------
  async resolveDriverId(externalId: string): Promise<string | null> {
    const cacheKey = `driver:id:${externalId}`;
    const cached = await this.cache.get(cacheKey);
    if (cached) return cached;

    const rows = await this.db.rows<{ id: string }>(
      `INSERT INTO drivers (external_id, display_name)
       VALUES ($1, $1)
       ON CONFLICT (external_id) DO UPDATE SET external_id = EXCLUDED.external_id
       RETURNING id`,
      [externalId],
    );
    const id = rows[0]?.id ?? null;
    if (id) await this.cache.set(cacheKey, id, 3600);
    return id;
  }

  async getPrivacyFlags(driverId: string): Promise<{
    mic_enabled: boolean;
    location_enabled: boolean;
    memory_enabled: boolean;
  }> {
    const rows = await this.db.rows<any>(
      `SELECT mic_enabled, location_enabled, memory_enabled FROM drivers WHERE id = $1`,
      [driverId],
    );
    return (
      rows[0] ?? { mic_enabled: true, location_enabled: true, memory_enabled: true }
    );
  }

  async setPrivacyFlags(
    driverId: string,
    flags: Partial<{
      mic_enabled: boolean;
      location_enabled: boolean;
      memory_enabled: boolean;
    }>,
  ): Promise<void> {
    await this.db.query(
      `UPDATE drivers SET
         mic_enabled      = COALESCE($2, mic_enabled),
         location_enabled = COALESCE($3, location_enabled),
         memory_enabled   = COALESCE($4, memory_enabled)
       WHERE id = $1`,
      [
        driverId,
        flags.mic_enabled ?? null,
        flags.location_enabled ?? null,
        flags.memory_enabled ?? null,
      ],
    );
  }

  // ------------------------------------------------------------------
  // Lectura del contexto jerarquico completo
  // ------------------------------------------------------------------
  async buildContext(externalId: string, query: string): Promise<MemoryContext> {
    if (!this.db.isHealthy()) return { ...EMPTY_CONTEXT };
    const driverId = await this.resolveDriverId(externalId);
    if (!driverId) return { ...EMPTY_CONTEXT };

    const flags = await this.getPrivacyFlags(driverId);
    if (!flags.memory_enabled) return { ...EMPTY_CONTEXT, degraded: false };

    const vector = this.embeddings.toVectorLiteral(await this.embeddings.embed(query));

    const [l1, l2, l3, l4] = await Promise.all([
      this.db.rows<WorkingTurn>(
        `SELECT role, content, source, created_at
           FROM l1_working_memory
          WHERE driver_id = $1
          ORDER BY created_at DESC
          LIMIT $2`,
        [driverId, L1_TURNS],
      ),
      this.db.rows<Preference>(
        `SELECT category, subject, sentiment, origin, confidence, evidence, hits
           FROM l2_preferences
          WHERE driver_id = $1
          ORDER BY (embedding <=> $2::vector) ASC NULLS LAST, hits DESC
          LIMIT 6`,
        [driverId, vector],
      ),
      this.db.rows<Place>(
        `SELECT id, name, kind, address, lat, lon, rating, visit_count, last_visit, notes
           FROM l3_places
          WHERE driver_id = $1
          ORDER BY (embedding <=> $2::vector) ASC NULLS LAST, visit_count DESC
          LIMIT 4`,
        [driverId, vector],
      ),
      this.db.rows<HistoricalFact>(
        `SELECT fact_key, fact_value, schedule_cron, confidence, evidence, consolidated_from
           FROM l4_facts
          WHERE driver_id = $1
          ORDER BY (embedding <=> $2::vector) ASC NULLS LAST, confidence DESC
          LIMIT 5`,
        [driverId, vector],
      ),
    ]);

    return { l1: l1.reverse(), l2, l3, l4, degraded: false };
  }

  // ------------------------------------------------------------------
  // CAPA 1 :: escritura del turno
  // ------------------------------------------------------------------
  async appendTurn(
    externalId: string,
    role: 'driver' | 'assistant',
    content: string,
    source = 'gemini',
  ): Promise<void> {
    if (!this.db.isHealthy()) return;
    const driverId = await this.resolveDriverId(externalId);
    if (!driverId) return;
    const flags = await this.getPrivacyFlags(driverId);
    if (!flags.memory_enabled) return;

    await this.db.query(
      `INSERT INTO l1_working_memory (driver_id, role, content, source)
       VALUES ($1, $2, $3, $4)`,
      [driverId, role, content.slice(0, 2000), source],
    );
    // La memoria de trabajo es una ventana corta: podamos lo viejo.
    await this.db.query(
      `DELETE FROM l1_working_memory
        WHERE driver_id = $1
          AND id NOT IN (
            SELECT id FROM l1_working_memory
             WHERE driver_id = $1 ORDER BY created_at DESC LIMIT 20)`,
      [driverId],
    );
  }

  // ------------------------------------------------------------------
  // CAPA 2 :: preferencias
  // ------------------------------------------------------------------
  async upsertPreference(externalId: string, pref: Preference): Promise<void> {
    const driverId = await this.resolveDriverId(externalId);
    if (!driverId) return;
    const vector = this.embeddings.toVectorLiteral(
      await this.embeddings.embed(`${pref.category} ${pref.subject}`),
    );
    await this.db.query(
      `INSERT INTO l2_preferences
         (driver_id, category, subject, sentiment, origin, confidence, evidence, embedding)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8::vector)
       ON CONFLICT (driver_id, category, subject) DO UPDATE SET
         hits       = l2_preferences.hits + 1,
         sentiment  = EXCLUDED.sentiment,
         -- una declaracion explicita siempre gana sobre una inferencia
         origin     = CASE WHEN EXCLUDED.origin = 'declared' THEN 'declared'
                           ELSE l2_preferences.origin END,
         confidence = GREATEST(l2_preferences.confidence, EXCLUDED.confidence),
         evidence   = COALESCE(EXCLUDED.evidence, l2_preferences.evidence),
         embedding  = EXCLUDED.embedding,
         updated_at = now()`,
      [
        driverId,
        pref.category,
        pref.subject.slice(0, 120),
        pref.sentiment,
        pref.origin,
        pref.confidence,
        pref.evidence ?? null,
        vector,
      ],
    );
  }

  // ------------------------------------------------------------------
  // CAPA 3 :: lugares episodicos
  // ------------------------------------------------------------------
  async upsertPlace(externalId: string, place: Place): Promise<void> {
    const driverId = await this.resolveDriverId(externalId);
    if (!driverId) return;
    const vector = this.embeddings.toVectorLiteral(
      await this.embeddings.embed(`${place.name} ${place.kind ?? ''} ${place.address ?? ''}`),
    );
    await this.db.query(
      `INSERT INTO l3_places
         (driver_id, name, kind, address, lat, lon, rating, notes, embedding)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9::vector)
       ON CONFLICT (driver_id, name) DO UPDATE SET
         visit_count = l3_places.visit_count + 1,
         last_visit  = now(),
         rating      = COALESCE(EXCLUDED.rating, l3_places.rating),
         address     = COALESCE(EXCLUDED.address, l3_places.address),
         lat         = COALESCE(EXCLUDED.lat, l3_places.lat),
         lon         = COALESCE(EXCLUDED.lon, l3_places.lon),
         notes       = COALESCE(EXCLUDED.notes, l3_places.notes),
         embedding   = EXCLUDED.embedding`,
      [
        driverId,
        place.name.slice(0, 160),
        place.kind ?? null,
        place.address ?? null,
        place.lat ?? null,
        place.lon ?? null,
        place.rating ?? null,
        place.notes ?? null,
        vector,
      ],
    );
  }

  async topPlaces(externalId: string, limit = 5): Promise<Place[]> {
    const driverId = await this.resolveDriverId(externalId);
    if (!driverId) return [];
    return this.db.rows<Place>(
      `SELECT id, name, kind, address, lat, lon, rating, visit_count, last_visit, notes
         FROM l3_places WHERE driver_id = $1
        ORDER BY visit_count DESC, last_visit DESC LIMIT $2`,
      [driverId, limit],
    );
  }

  // ------------------------------------------------------------------
  // CAPA 4 :: hechos consolidados
  // ------------------------------------------------------------------
  async upsertFact(externalId: string, fact: HistoricalFact): Promise<void> {
    const driverId = await this.resolveDriverId(externalId);
    if (!driverId) return;
    const vector = this.embeddings.toVectorLiteral(
      await this.embeddings.embed(`${fact.fact_key} ${fact.fact_value}`),
    );
    await this.db.query(
      `INSERT INTO l4_facts
         (driver_id, fact_key, fact_value, schedule_cron, confidence, evidence, embedding)
       VALUES ($1,$2,$3,$4,$5,$6,$7::vector)
       ON CONFLICT (driver_id, fact_key) DO UPDATE SET
         fact_value        = EXCLUDED.fact_value,
         schedule_cron     = COALESCE(EXCLUDED.schedule_cron, l4_facts.schedule_cron),
         confidence        = LEAST(1.0, (l4_facts.confidence + EXCLUDED.confidence) / 2 + 0.1),
         evidence          = COALESCE(EXCLUDED.evidence, l4_facts.evidence),
         consolidated_from = l4_facts.consolidated_from + 1,
         embedding         = EXCLUDED.embedding,
         updated_at        = now()`,
      [
        driverId,
        fact.fact_key,
        fact.fact_value.slice(0, 500),
        fact.schedule_cron ?? null,
        fact.confidence,
        fact.evidence ?? null,
        vector,
      ],
    );
  }

  // ------------------------------------------------------------------
  // Extraccion ligera: declarado vs inferido (sin llamar al LLM)
  // ------------------------------------------------------------------

  /** Verbo de gusto + el objeto de ese gusto (hasta 4 palabras). */
  private static readonly TASTE_RE =
    /\b(?:me\s+(?:encanta[n]?|gusta[n]?|fascina[n]?)|amo|prefiero|odio|detesto|no\s+me\s+gusta[n]?)\s+(?:mucho\s+)?(?:comer\s+|escuchar\s+|manejar\s+por\s+)?((?:\w+[áéíóúñ\w]*(?:\s+|$)){1,4})/iu;

  /**
   * Corta la frase en cuanto empieza una subordinada.
   * "al" solo rompe ante infinitivo ("al llegar"), nunca en "tacos al pastor".
   */
  private static readonly CLAUSE_BREAK =
    /\b(?:cuando|mientras|porque|aunque|si|pero|y\s+tambien|para|despues|antes|al\s+\w+[aei]r)\b/iu;

  /** Articulos iniciales: "el rock en espanol" -> "rock en espanol". */
  private static readonly LEADING_ARTICLE = /^(?:el|la|los|las|un|una|unos|unas)\s+/iu;

  /** Conectores que no deben quedar al final del sujeto capturado. */
  private static readonly TRAILING_NOISE =
    /\s+(?:en|de|del|la|el|los|las|un|una|con|a|que|por|para|y|o)$/iu;

  /** Lexico de clasificacion: primera coincidencia gana. */
  private static readonly CATEGORY_LEXICON: Array<[Preference['category'], RegExp]> = [
    ['music', /\b(m[uú]sic\w*|rock|pop|salsa|cumbia|banda|regg\w+|jazz|blues|corridos?|baladas?|bachata|merengue|electr[oó]nica|rap|hip\s?hop|norte[nñ]\w*|ranchera\w*|podcasts?|radio|canci[oó]n\w*)\b/iu],
    ['food', /\b(com\w+|tacos?|pizza|sushi|mariscos?|hamburguesas?|italian\w+|china|japonesa|mexicana|caf[eé]|desayun\w+|cen\w+|antojitos?|birria|pozole|ramen|ensaladas?|postres?)\b/iu],
    ['route', /\b(rutas?|autopistas?|carreteras?|avenidas?|perif[eé]rico|libramiento|viaducto|atajos?|calles?)\b/iu],
    ['climate', /\b(fr[ií]o|calor|aire|clima|ventilaci[oó]n|calefacci[oó]n|temperatura)\b/iu],
  ];

  private classify(subject: string): Preference['category'] {
    for (const [category, re] of MemoryService.CATEGORY_LEXICON) {
      if (re.test(subject)) return category;
    }
    return 'other';
  }

  /** Limpia el sujeto capturado sin perder frases de varias palabras. */
  private cleanSubject(raw: string): string {
    let subject = raw.split(MemoryService.CLAUSE_BREAK)[0];
    subject = subject.replace(/\s+/g, ' ').trim().toLowerCase();
    subject = subject.replace(MemoryService.LEADING_ARTICLE, '');
    // Puede quedar mas de un conector encadenado ("rock en la ").
    let previous: string;
    do {
      previous = subject;
      subject = subject.replace(MemoryService.TRAILING_NOISE, '').trim();
    } while (subject !== previous);
    return subject;
  }

  /**
   * Analiza la frase del conductor y persiste lo aprendido.
   * Nunca inventa: el texto exacto queda guardado como `evidence`.
   */
  async learnFrom(externalId: string, utterance: string): Promise<void> {
    const text = utterance.trim();
    if (text.length < 6) return;

    // Capa 2: gusto declarado explicitamente.
    const taste = text.match(MemoryService.TASTE_RE);
    if (taste?.[1]) {
      const subject = this.cleanSubject(taste[1]);
      if (subject.length >= 3) {
        const dislike = /\b(no me gusta|odio|detesto)\b/i.test(taste[0]);
        await this.upsertPreference(externalId, {
          category: this.classify(subject),
          subject,
          sentiment: dislike ? 'dislike' : 'like',
          origin: 'declared',
          confidence: 1.0,
          evidence: text.slice(0, 300),
        });
      }
    }

    // Capa 4: rutinas fijas declaradas ("trabajo de 9 a 6", "mi casa esta en ...")
    const office = text.match(
      /\b(?:entro|trabajo)\D{0,12}(\d{1,2})(?::(\d{2}))?\s*(?:a|hasta)\s*(?:las\s*)?(\d{1,2})/i,
    );
    if (office) {
      await this.upsertFact(externalId, {
        fact_key: 'office_schedule',
        fact_value: `${office[1]}:${office[2] ?? '00'} - ${office[3]}:00`,
        schedule_cron: `0 ${office[1]} * * 1-5`,
        confidence: 0.95,
        evidence: text.slice(0, 300),
      });
    }

    const address = text.match(
      /\bmi\s+(casa|oficina|trabajo)\s+(?:esta|queda|es)\s+(?:en|por)\s+(.{4,80})/i,
    );
    if (address) {
      await this.upsertFact(externalId, {
        fact_key: /casa/i.test(address[1]) ? 'home_address' : 'office_address',
        fact_value: address[2].trim(),
        confidence: 0.9,
        evidence: text.slice(0, 300),
      });
    }

    // Capa 3: mencion de un lugar visitado
    const place = text.match(
      /\b(?:fui|estuve|com[ií]|visit[eé])\s+(?:a|en)\s+(?:el |la |los |las )?([A-ZÁÉÍÓÚÑ][\w'’\- ]{2,40})/,
    );
    if (place?.[1]) {
      await this.upsertPlace(externalId, {
        name: place[1].trim(),
        notes: text.slice(0, 300),
      });
    }
  }

  // ------------------------------------------------------------------
  // GDPR :: exportacion y derecho al olvido
  // ------------------------------------------------------------------
  async exportAll(externalId: string): Promise<Record<string, unknown>> {
    const driverId = await this.resolveDriverId(externalId);
    if (!driverId) {
      return { driver: externalId, exported_at: new Date().toISOString(), layers: {} };
    }
    const [driver, l1, l2, l3, l4, commands] = await Promise.all([
      this.db.rows(`SELECT * FROM drivers WHERE id = $1`, [driverId]),
      this.db.rows(`SELECT role, content, source, created_at FROM l1_working_memory WHERE driver_id=$1 ORDER BY created_at`, [driverId]),
      this.db.rows(`SELECT category, subject, sentiment, origin, confidence, evidence, hits, updated_at FROM l2_preferences WHERE driver_id=$1`, [driverId]),
      this.db.rows(`SELECT name, kind, address, lat, lon, rating, visit_count, last_visit, notes FROM l3_places WHERE driver_id=$1`, [driverId]),
      this.db.rows(`SELECT fact_key, fact_value, schedule_cron, confidence, evidence, consolidated_from, updated_at FROM l4_facts WHERE driver_id=$1`, [driverId]),
      this.db.rows(`SELECT provider, command, payload, allowed, reason, created_at FROM vehicle_command_log WHERE driver_id=$1 ORDER BY created_at DESC LIMIT 200`, [driverId]),
    ]);
    return {
      schema: 'driveai.export.v1',
      exported_at: new Date().toISOString(),
      driver: driver[0] ?? { external_id: externalId },
      layers: {
        L1_working_memory: l1,
        L2_preferences: l2,
        L3_places: l3,
        L4_facts: l4,
      },
      vehicle_command_log: commands,
    };
  }

  /** Borrado total e irreversible (derecho al olvido). */
  async purgeAll(externalId: string): Promise<{ purged: boolean }> {
    const driverId = await this.resolveDriverId(externalId);
    if (!driverId) return { purged: false };
    await this.db.query(`DELETE FROM drivers WHERE id = $1`, [driverId]); // ON DELETE CASCADE
    await this.cache.purgePrefix(`driver:id:${externalId}`);
    await this.cache.purgePrefix(`answer:${externalId}`);
    this.logger.log(`Memoria purgada para ${externalId}`);
    return { purged: true };
  }

  /** Borrado selectivo de una capa. */
  async purgeLayer(externalId: string, layer: 'L1' | 'L2' | 'L3' | 'L4'): Promise<void> {
    const driverId = await this.resolveDriverId(externalId);
    if (!driverId) return;
    const table = {
      L1: 'l1_working_memory',
      L2: 'l2_preferences',
      L3: 'l3_places',
      L4: 'l4_facts',
    }[layer];
    await this.db.query(`DELETE FROM ${table} WHERE driver_id = $1`, [driverId]);
  }
}
