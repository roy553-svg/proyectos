export type MemoryLayer = 'L1' | 'L2' | 'L3' | 'L4';

export interface WorkingTurn {
  role: 'driver' | 'assistant';
  content: string;
  source?: string;
  created_at?: string;
}

export interface Preference {
  id?: number;
  category: 'music' | 'food' | 'route' | 'climate' | 'other' | string;
  subject: string;
  sentiment: 'like' | 'dislike' | 'neutral';
  origin: 'declared' | 'inferred';
  confidence: number;
  evidence?: string | null;
  hits?: number;
}

export interface Place {
  id?: string;
  name: string;
  kind?: string | null;
  address?: string | null;
  lat?: number | null;
  lon?: number | null;
  rating?: number | null;
  visit_count?: number;
  last_visit?: string;
  notes?: string | null;
}

export interface HistoricalFact {
  fact_key: string;
  fact_value: string;
  schedule_cron?: string | null;
  confidence: number;
  evidence?: string | null;
  consolidated_from?: number;
}

/** Contexto jerarquico entregado al enrutador de IA. */
export interface MemoryContext {
  l1: WorkingTurn[];
  l2: Preference[];
  l3: Place[];
  l4: HistoricalFact[];
  degraded: boolean; // true = sin base de datos, respondemos sin memoria
}

export const EMPTY_CONTEXT: MemoryContext = {
  l1: [],
  l2: [],
  l3: [],
  l4: [],
  degraded: true,
};
