export type Gear = 'P' | 'R' | 'N' | 'D';

export interface Tires {
  fl: number;
  fr: number;
  rl: number;
  rr: number;
  unit: 'bar' | 'psi';
}

export interface VehicleStatus {
  provider: string;
  online: boolean;
  stale: boolean;
  speedKph: number;
  gear: Gear;
  batteryPercent: number | null;
  fuelPercent: number | null;
  rangeKm: number | null;
  odometerKm: number | null;
  insideTempC: number | null;
  outsideTempC: number | null;
  climateTargetC: number | null;
  climateOn: boolean;
  locked: boolean;
  tires: Tires | null;
  doorsOpen: string[];
  updatedAt: string;
  safety: { lockCommandsAllowed: boolean; reason: string | null };
}

export interface AssistantReply {
  text: string;
  source: 'gemini' | 'gemini-fallback' | 'offline' | 'cache';
  model?: string;
  rule?: string;
  latencyMs: number;
  action?: { type: 'navigate' | 'command'; payload: Record<string, any> };
  quickActions: Array<{ label: string; command?: string; args?: Record<string, any> }>;
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
}

export type OrbState = 'idle' | 'listening' | 'thinking' | 'speaking';
