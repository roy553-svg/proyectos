import { Injectable } from '@nestjs/common';
import { MemoryContext } from '../memory/memory.types';
import { VehicleStatus } from '../vehicles/providers/vehicle-provider.interface';

export interface OfflineAnswer {
  text: string;
  /** Regla que disparo la respuesta (trazabilidad, anti-alucinacion). */
  rule: string;
  /** Accion sugerida para la UI (boton de 1 toque). */
  action?: { type: 'navigate' | 'command'; payload: Record<string, unknown> };
}

type Rule = {
  id: string;
  /** Patron precompilado: el matching es O(n) sobre ~40 regex => < 1 ms. */
  test: RegExp;
  run: (m: RegExpMatchArray, ctx: RuleContext) => OfflineAnswer | null;
};

interface RuleContext {
  status: VehicleStatus | null;
  memory: MemoryContext;
  now: Date;
}

/** "1 vez" / "4 veces": el TTS lee el texto tal cual, la concordancia importa. */
const plural = (n: number, one: string, many: string): string =>
  `${n} ${n === 1 ? one : many}`;

const fmt = (n: number | null | undefined, unit: string, dash = 'sin dato'): string =>
  n === null || n === undefined || !Number.isFinite(n) ? dash : `${Math.round(n)}${unit}`;

/**
 * Motor de Reglas Local Determinista.
 *
 * Se activa cuando no hay red, no hay clave de API o Gemini excede su
 * presupuesto de latencia. Sin I/O, sin red, sin base de datos: solo regex
 * sobre la telemetria y el contexto ya cargado en memoria.
 *
 * Objetivo de latencia: p99 < 15 ms. Nunca devuelve `null` al usuario final;
 * si ninguna regla aplica, responde con un mensaje honesto de cobertura.
 */
@Injectable()
export class OfflineRulesEngine {
  private readonly rules: Rule[] = [
    // ---------------- Emergencias (maxima prioridad) ----------------
    // Van primero a proposito: "se revento la llanta" contiene "llanta" y
    // caeria en la lectura de TPMS. Ante una emergencia el conductor
    // necesita la maniobra, no el dato del sensor.
    {
      id: 'faq.tire.blowout',
      test: /\b(ponchadura|se ponch[oó]|revent[oó] (?:la )?llanta|blowout)\b/i,
      run: () => ({
        rule: 'faq.tire.blowout',
        text: 'Sujeta firme el volante, no frenes bruscamente y suelta el acelerador. Orilla el coche poco a poco.',
      }),
    },
    {
      id: 'faq.overheat',
      test: /\b(se calienta|sobrecalent|temperatura del motor|humo)\b/i,
      run: () => ({
        rule: 'faq.overheat',
        text: 'Apaga el aire, enciende la calefaccion y orilla el coche. No abras el radiador caliente.',
      }),
    },

    // ---------------- Telemetria del vehiculo ----------------
    {
      id: 'battery',
      test: /\b(bater[ií]a|carga|porcentaje|cu[aá]nta bater)/i,
      run: (_m, { status }) =>
        status?.batteryPercent === null || !status
          ? null
          : {
              rule: 'battery',
              text: `Tienes ${fmt(status.batteryPercent, '%')} de bateria, unos ${fmt(status.rangeKm, ' kilometros')} de autonomia.`,
            },
    },
    {
      id: 'range',
      test: /\b(autonom[ií]a|cu[aá]nto (?:me )?(?:falta|alcanza|llego)|rango|kil[oó]metros? me quedan)/i,
      run: (_m, { status }) =>
        !status
          ? null
          : {
              rule: 'range',
              text: `Te quedan aproximadamente ${fmt(status.rangeKm, ' kilometros')} de autonomia.`,
            },
    },
    {
      id: 'speed',
      test: /\b(velocidad|qu[eé] tan r[aá]pido|a cu[aá]nto voy)/i,
      run: (_m, { status }) =>
        !status
          ? null
          : {
              rule: 'speed',
              text: `Vas a ${fmt(status.speedKph, ' kilometros por hora')} en marcha ${status.gear}.`,
            },
    },
    {
      id: 'tires',
      test: /\b(llantas?|neum[aá]ticos?|presi[oó]n|tpms)/i,
      run: (_m, { status }) => {
        if (!status?.tires) return null;
        const t = status.tires;
        const low = Object.entries({ 'delantera izquierda': t.fl, 'delantera derecha': t.fr, 'trasera izquierda': t.rl, 'trasera derecha': t.rr })
          .filter(([, v]) => t.unit === 'bar' ? v < 2.2 : v < 32)
          .map(([k]) => k);
        return {
          rule: 'tires',
          text: low.length
            ? `Presion baja en la ${low.join(' y la ')}. Revisala en cuanto puedas.`
            : `Las cuatro llantas estan en rango normal, alrededor de ${t.fl} ${t.unit}.`,
        };
      },
    },
    {
      id: 'locks',
      test: /\b(seguros?|puertas?|cerrad[oa]|bloquead)/i,
      run: (_m, { status }) =>
        !status
          ? null
          : {
              rule: 'locks',
              text: status.locked
                ? 'Los seguros estan cerrados.'
                : 'Los seguros estan abiertos. Te los cierro cuando te detengas.',
            },
    },
    {
      id: 'climate',
      test: /\b(clima|aire|a\/?c|temperatura|calor|fr[ií]o)\b/i,
      run: (m, { status }) => {
        if (!status) return null;
        const target = m.input?.match(/\b(1[6-9]|2[0-9]|30)\s*(?:grados|°|c\b)/i);
        if (target) {
          const celsius = Number(target[1]);
          return {
            rule: 'climate.set',
            text: `Ajustando el clima a ${celsius} grados.`,
            action: { type: 'command', payload: { command: 'set_climate_temp', args: { celsius } } },
          };
        }
        return {
          rule: 'climate.status',
          text: `La cabina esta a ${fmt(status.insideTempC, ' grados')} y afuera hay ${fmt(status.outsideTempC, ' grados')}.`,
        };
      },
    },
    {
      id: 'gear',
      test: /\b(marcha|en qu[eé] cambio|transmisi[oó]n)\b/i,
      run: (_m, { status }) =>
        !status ? null : { rule: 'gear', text: `Estas en marcha ${status.gear}.` },
    },

    // ---------------- Navegacion local (Capas 3 y 4) ----------------
    // Orden deliberado: un destino nombrado ('a casa', 'la oficina') gana
    // siempre sobre el lugar mas visitado. Sin esto, 'llevame a casa'
    // acabaria enrutando al ultimo restaurante.
    {
      id: 'nav.home',
      test: /\b(a casa|mi casa|hogar|regresar a casa)\b/i,
      run: (_m, { memory }) => {
        const home = memory.l4.find((f) => f.fact_key === 'home_address');
        if (!home) return null;
        return {
          rule: 'nav.home',
          text: `Rumbo a casa: ${home.fact_value}.`,
          action: { type: 'navigate', payload: { address: home.fact_value } },
        };
      },
    },
    {
      id: 'nav.office',
      test: /\b(oficina|trabajo|chamba)\b/i,
      run: (_m, { memory }) => {
        const office = memory.l4.find((f) => f.fact_key === 'office_address');
        const schedule = memory.l4.find((f) => f.fact_key === 'office_schedule');
        if (!office && !schedule) return null;
        return {
          rule: 'nav.office',
          text: office
            ? `Tu oficina: ${office.fact_value}${schedule ? `, horario ${schedule.fact_value}` : ''}.`
            : `Tu horario de oficina es ${schedule!.fact_value}.`,
          action: office
            ? { type: 'navigate', payload: { address: office.fact_value } }
            : undefined,
        };
      },
    },
    {
      id: 'nav.place',
      test: /\b(ll[eé]vame|vamos|ruta|navega|c[oó]mo llego|ir)\b/i,
      run: (_m, { memory }) => {
        const place = memory.l3[0];
        if (!place) return null;
        return {
          rule: 'nav.place',
          text:
            `${place.name}${place.rating ? `, calificado ${place.rating}` : ''}, ` +
            `lo has visitado ${plural(place.visit_count ?? 1, 'vez', 'veces')}. Inicio la ruta.`,
          action: {
            type: 'navigate',
            payload: {
              name: place.name,
              lat: place.lat ?? null,
              lon: place.lon ?? null,
              address: place.address ?? null,
            },
          },
        };
      },
    },

    // ---------------- Preferencias (Capa 2) ----------------
    {
      id: 'pref.recall',
      test: /\b(qu[eé] me gusta|mis gustos|mis preferencias|qu[eé] sabes de m[ií])\b/i,
      run: (_m, { memory }) => {
        if (!memory.l2.length) return { rule: 'pref.empty', text: 'Todavia no registro preferencias tuyas. Cuentame que te gusta.' };
        const top = memory.l2.slice(0, 2).map((p) => `${p.subject}${p.origin === 'inferred' ? ' (creo)' : ''}`);
        return { rule: 'pref.recall', text: `Me consta que te gusta ${top.join(' y ')}.` };
      },
    },
    {
      id: 'pref.food',
      test: /\b(comer|comida|restaurante|hambre|antojo)\b/i,
      run: (_m, { memory }) => {
        const food = memory.l2.find((p) => p.category === 'food' && p.sentiment === 'like');
        const place = memory.l3.find((p) => p.kind === 'restaurante') ?? memory.l3[0];
        if (!food && !place) return null;
        if (place) {
          return {
            rule: 'pref.food',
            text: `${place.name} te gusto antes${place.rating ? `, ${place.rating} estrellas` : ''}. Te llevo?`,
            action: { type: 'navigate', payload: { name: place.name, lat: place.lat ?? null, lon: place.lon ?? null } },
          };
        }
        return { rule: 'pref.food', text: `Se que te gusta ${food!.subject}. Busco algo cerca cuando recupere senal.` };
      },
    },

    // ---------------- Hora y fecha ----------------
    {
      id: 'time',
      test: /\b(qu[eé] hora|hora es)\b/i,
      run: (_m, { now }) => ({
        rule: 'time',
        text: `Son las ${now.getHours()}:${String(now.getMinutes()).padStart(2, '0')}.`,
      }),
    },

    // ---------------- Seguridad vial / FAQ de conduccion ----------------
    {
      id: 'faq.rain',
      test: /\b(lluvia|llueve|mojad[oa]|aquaplaning|hidroplaneo)\b/i,
      run: () => ({
        rule: 'faq.rain',
        text: 'Baja la velocidad un tercio y duplica la distancia de frenado. Si patinas, suelta el acelerador sin frenar de golpe.',
      }),
    },
    {
      id: 'faq.fog',
      test: /\b(niebla|neblina|visibilidad)\b/i,
      run: () => ({
        rule: 'faq.fog',
        text: 'Usa luces bajas o antiniebla, nunca altas. Reduce velocidad y guiate por la linea derecha del carril.',
      }),
    },
    {
      id: 'faq.warning.light',
      test: /\b(testigo|luz del tablero|check engine|foco (?:rojo|amarillo))\b/i,
      run: () => ({
        rule: 'faq.warning.light',
        text: 'Un testigo rojo pide detenerte cuanto antes; uno ambar permite seguir con precaucion hasta el taller.',
      }),
    },
    {
      id: 'faq.brakes',
      test: /\b(frenos?|frenar|pastillas)\b/i,
      run: () => ({
        rule: 'faq.brakes',
        text: 'Si el pedal se siente esponjoso o chirria al frenar, revisa pastillas y liquido antes de tu proximo viaje largo.',
      }),
    },
    {
      id: 'faq.fuel',
      test: /\b(gasolina|combustible|gasolinera|cargar)\b/i,
      run: (_m, { status }) => ({
        rule: 'faq.fuel',
        text: status?.rangeKm
          ? `Con ${fmt(status.rangeKm, ' kilometros')} de autonomia conviene cargar antes de bajar de cincuenta.`
          : 'Te aviso cuando recupere la senal para buscar la gasolinera mas cercana.',
      }),
    },
    // Red de seguridad de navegacion: si hubo intencion de ruta pero no hay
    // nada guardado, lo decimos claro en vez de caer al mensaje generico.
    {
      id: 'nav.unknown',
      test: /\b(ll[eé]vame|navega|ruta|c[oó]mo llego|a casa|oficina)\b/i,
      run: () => ({
        rule: 'nav.unknown',
        text: 'No tengo esa direccion guardada todavia. Dictamela una vez y la recuerdo para la proxima.',
      }),
    },
    {
      id: 'greeting',
      test: /^\s*(hola|buenos d[ií]as|buenas tardes|buenas noches|hey|oye)\b/i,
      run: () => ({ rule: 'greeting', text: 'Aqui estoy. Que necesitas?' }),
    },
  ];

  /** Respuesta determinista. Nunca lanza, nunca devuelve null. */
  answer(question: string, ctx: RuleContext): OfflineAnswer {
    const q = (question ?? '').normalize('NFC').trim();
    for (const rule of this.rules) {
      const m = q.match(rule.test);
      if (!m) continue;
      try {
        const res = rule.run(m, ctx);
        if (res) return res;
      } catch {
        /* una regla rota nunca tumba el motor */
      }
    }
    return {
      rule: 'fallback',
      text: ctx.status
        ? `Sin conexion no puedo resolver eso. Ahora vas a ${fmt(ctx.status.speedKph, ' kilometros por hora')} y todo esta normal.`
        : 'Estoy sin conexion en este momento. Preguntame por el coche, la ruta o tus lugares guardados.',
    };
  }
}
