import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api'

interface LogsResponse { total: number; lines: string[] }

const CATEGORIES = [
  'KILL', 'VEHICLE', 'CHAT', 'COMMAND', 'FACTION', 'CAPTURE',
  'JOIN', 'LEFT', 'WAREHOUSE', 'GARAGE', 'BASEZONE', 'REPORT', 'MOD',
] as const

const TAG_COLORS: Record<string, string> = {
  KILL: 'var(--danger)',
  VEHICLE: 'var(--warn)',
  CHAT: 'var(--info)',
  COMMAND: 'var(--accent)',
  FACTION: 'var(--ok)',
  CAPTURE: 'var(--warn)',
  JOIN: 'var(--ok)',
  LEFT: 'var(--muted)',
  WAREHOUSE: 'var(--info)',
  GARAGE: 'var(--info)',
  BASEZONE: 'var(--warn)',
  REPORT: 'var(--danger)',
  MOD: 'var(--muted)',
}

const REFRESH_MS = 5000

/** Строка лога: [HH:mm:ss] [TAG] текст. Нераспарсенное показываем как есть. */
function parseLine(line: string): { time: string; tag: string; text: string } {
  const m = line.match(/^\[(\d{2}:\d{2}:\d{2})\] \[([A-Z_]+)\] (.*)$/)
  return m ? { time: m[1], tag: m[2], text: m[3] } : { time: '', tag: '', text: line }
}

export default function Logs() {
  const [days, setDays] = useState<string[]>([])
  const [day, setDay] = useState('')
  const [category, setCategory] = useState('')
  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [data, setData] = useState<LogsResponse>({ total: 0, lines: [] })
  const [loading, setLoading] = useState(false)
  const [follow, setFollow] = useState(true)
  const listRef = useRef<HTMLDivElement>(null)

  // Список дней — один раз при открытии; выбираем последний.
  useEffect(() => {
    api.get<string[]>('/api/logs/days').then(list => {
      setDays(list)
      if (list.length > 0) setDay(d => d || list[0])
    }).catch(() => setDays([]))
  }, [])

  useEffect(() => {
    const t = setTimeout(() => setDebouncedQuery(query.trim()), 350)
    return () => clearTimeout(t)
  }, [query])

  const load = useCallback(async (silent: boolean) => {
    if (!day) return
    if (!silent) setLoading(true)
    try {
      const params = new URLSearchParams({ day })
      if (category) params.set('category', category)
      if (debouncedQuery) params.set('q', debouncedQuery)
      const res = await api.get<LogsResponse>(`/api/logs?${params}`)
      setData(res)
    } catch {
      // сеть/сессия — статус покажет капсула связи
    } finally {
      if (!silent) setLoading(false)
    }
  }, [day, category, debouncedQuery])

  useEffect(() => { load(false) }, [load])

  // Живое обновление только для последнего дня (в него идёт запись).
  const isLatest = days.length > 0 && day === days[0]
  useEffect(() => {
    if (!isLatest) return
    const t = setInterval(() => load(true), REFRESH_MS)
    return () => clearInterval(t)
  }, [isLatest, load])

  // Автоскролл вниз при новых строках, если включён follow.
  useEffect(() => {
    if (follow && listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight
    }
  }, [data, follow])

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12, height: 'calc(100vh - 64px)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
        <h3 style={{ marginRight: 8 }}>Логи действий</h3>
        <select className="input" value={day} onChange={e => setDay(e.target.value)} style={{ width: 150 }}>
          {days.length === 0 && <option value="">нет файлов</option>}
          {days.map(d => <option key={d} value={d}>{d}</option>)}
        </select>
        <input
          className="input"
          style={{ width: 240 }}
          placeholder="Поиск: ник, текст…"
          value={query}
          onChange={e => setQuery(e.target.value)}
        />
        <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--muted)', cursor: 'pointer' }}>
          <input type="checkbox" checked={follow} onChange={e => setFollow(e.target.checked)} />
          автоскролл
        </label>
        <span className="muted" style={{ fontSize: 12, marginLeft: 'auto' }}>
          {loading ? 'загрузка…' : `${data.lines.length} из ${data.total}`}
          {isLatest && ' · live'}
        </span>
      </div>

      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
        <FilterChip label="Все" active={category === ''} onClick={() => setCategory('')} />
        {CATEGORIES.map(c => (
          <FilterChip key={c} label={c} active={category === c} color={TAG_COLORS[c]}
            onClick={() => setCategory(category === c ? '' : c)} />
        ))}
      </div>

      <div ref={listRef} className="panel mono"
        style={{ flex: 1, overflowY: 'auto', padding: '10px 14px', fontSize: 12.5, lineHeight: 1.7 }}>
        {data.lines.length === 0 && (
          <div className="muted" style={{ padding: 20, textAlign: 'center' }}>
            {day ? 'Нет записей по выбранным фильтрам' : 'Файлы логов ещё не созданы'}
          </div>
        )}
        {data.lines.map((line, i) => {
          const { time, tag, text } = parseLine(line)
          return (
            <div key={i} style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
              {time && <span style={{ color: 'var(--muted-2)' }}>{time} </span>}
              {tag && (
                <span style={{ color: TAG_COLORS[tag] ?? 'var(--muted)', fontWeight: 600 }}>
                  {tag.padEnd(9)}
                </span>
              )}
              <span style={{ color: 'var(--text-2)' }}>{text}</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function FilterChip({ label, active, color, onClick }: {
  label: string; active: boolean; color?: string; onClick: () => void
}) {
  return (
    <button
      onClick={onClick}
      className="chip"
      style={{
        cursor: 'pointer',
        fontFamily: 'inherit',
        borderColor: active ? (color ?? 'var(--accent)') : undefined,
        color: active ? (color ?? 'var(--accent)') : undefined,
        background: active ? 'rgba(255,255,255,0.05)' : undefined,
      }}
    >
      {label}
    </button>
  )
}
