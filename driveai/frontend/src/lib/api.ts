import type { AssistantReply, Place, VehicleStatus } from './types';

const BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? '';
const DRIVER =
  (import.meta.env.VITE_DRIVER_ID as string | undefined) ?? 'demo-driver';

/**
 * fetch con presupuesto de tiempo.
 * Ningun spinner puede quedarse girando: si el gateway no contesta,
 * la promesa se resuelve como error y la UI muestra su estado offline.
 */
async function call<T>(path: string, init: RequestInit = {}, timeoutMs = 6000): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(`${BASE}${path}`, {
      ...init,
      signal: controller.signal,
      headers: { 'content-type': 'application/json', ...(init.headers ?? {}) },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return (await res.json()) as T;
  } finally {
    clearTimeout(timer);
  }
}

export const api = {
  driverId: DRIVER,

  status: () => call<VehicleStatus>('/api/vehicles/status', {}, 2500),

  ask: (message: string, voice = false) =>
    call<AssistantReply>(
      '/api/assistant/message',
      { method: 'POST', body: JSON.stringify({ message, driverId: DRIVER, voice }) },
      9000,
    ),

  command: (command: string, args?: Record<string, unknown>) =>
    call<{ ok: boolean; blocked?: boolean; message: string }>(
      '/api/vehicles/commands',
      { method: 'POST', body: JSON.stringify({ command, args, driverId: DRIVER }) },
      5000,
    ),

  places: () => call<Place[]>(`/api/memory/places?driverId=${DRIVER}`, {}, 3000),

  memory: () => call<any>(`/api/memory?driverId=${DRIVER}`, {}, 4000),

  exportData: () => call<any>(`/api/memory/export?driverId=${DRIVER}`, {}, 8000),

  purge: () =>
    call<{ purged: boolean }>(`/api/memory?driverId=${DRIVER}`, { method: 'DELETE' }, 8000),

  privacy: (flags: {
    micEnabled?: boolean;
    locationEnabled?: boolean;
    memoryEnabled?: boolean;
  }) =>
    call<any>(
      '/api/memory/privacy',
      { method: 'POST', body: JSON.stringify({ driverId: DRIVER, ...flags }) },
      4000,
    ),
};
