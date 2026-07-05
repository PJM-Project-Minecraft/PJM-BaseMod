// Контракт с бэкендом: имена полей = Java records (WebDtos, MetricsSample, ProfilerWindow).

export interface DimSample { dim: string; mspt: number; entities: number; chunks: number }
export interface MetricsSample {
  t: number; mspt: number; tps: number
  heapUsed: number; heapMax: number; online: number; dims: DimSample[]
}
export interface PlayerDto {
  uuid: string; name: string; dim: string; x: number; y: number; z: number
  ping: number; team: string; role: string
  banned: boolean; voiceMuted: boolean; textMuted: boolean; warns: number
}
export interface EntityDto {
  uuid: string; type: string; name: string; dim: string
  x: number; y: number; z: number; category: string
}
export interface EntityTiming {
  uuid: string; type: string; name: string; dim: string
  x: number; y: number; z: number; ticks: number; totalNanos: number
}
export interface TypeTiming { type: string; count: number; totalNanos: number }
export interface ChunkTiming { dim: string; chunkX: number; chunkZ: number; totalNanos: number }
export interface ProfilerReport {
  windowMs: number; totalNanos: number
  topEntities: EntityTiming[]; byType: TypeTiming[]; hotChunks: ChunkTiming[]
}
export interface Overview {
  current: MetricsSample | Record<string, never>
  history: MetricsSample[]
  entityCounts: Record<string, number>
  profilerActive: boolean
  profilerAllowed: boolean
}
export interface HistoryDto {
  type: string; action: string; reason: string
  moderator: string; ts: number; durationMs: number
}
export interface ActionResponse { ok: boolean; message?: string; error?: string }

export class UnauthorizedError extends Error {
  constructor() { super('unauthorized') }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, init)
  if (res.status === 401) throw new UnauthorizedError()
  if (!res.ok) {
    // Тело ошибки бэкенда — {ok:false, error} — пробрасываем как текст.
    const body = await res.json().catch(() => ({ error: `http_${res.status}` }))
    throw new Error(body.error ?? `http_${res.status}`)
  }
  return res.json()
}

export const api = {
  get<T>(path: string): Promise<T> {
    return request<T>(path)
  },
  post<T>(path: string, body?: unknown): Promise<T> {
    return request<T>(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'PJMPanel' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  },
}

export function formatBytes(bytes: number): string {
  const gb = bytes / 1024 / 1024 / 1024
  return gb >= 1 ? `${gb.toFixed(2)} ГБ` : `${(bytes / 1024 / 1024).toFixed(0)} МБ`
}

export function formatMicros(nanos: number, ticks: number): string {
  if (ticks <= 0) return '—'
  return `${(nanos / ticks / 1000).toFixed(1)} мкс/тик`
}

/** Стабильный цвет по строке (команды, категории). */
export function hashColor(value: string): string {
  let h = 0
  for (let i = 0; i < value.length; i++) h = (h * 31 + value.charCodeAt(i)) | 0
  return `hsl(${Math.abs(h) % 360} 60% 60%)`
}
