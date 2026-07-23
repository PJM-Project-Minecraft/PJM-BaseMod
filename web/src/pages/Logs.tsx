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
    <div className="logs-page fade-in">
      <section className="logs-control panel">
        <div className="panel-header">
          <div>
            <span className="panel-kicker">Архив действий / серверное время</span>
            <h3>Фильтры журнала</h3>
          </div>
          <span className={`chip ${isLatest ? 'chip-ok' : ''}`}>
            <span className={`pulse-dot ${isLatest ? 'on' : ''}`} />
            {isLatest ? 'live запись' : 'архив'}
          </span>
        </div>
        <div className="toolbar logs-toolbar">
          <select className="input mono" value={day} onChange={e => setDay(e.target.value)}>
            {days.length === 0 && <option value="">нет файлов</option>}
            {days.map(d => <option key={d} value={d}>{d}</option>)}
          </select>
          <input
            className="input logs-search"
            placeholder="Поиск по нику или содержимому…"
            value={query}
            onChange={e => setQuery(e.target.value)}
          />
          <label className="logs-follow">
            <span className="switch">
              <input type="checkbox" checked={follow} onChange={e => setFollow(e.target.checked)} />
              <span className="switch-slider" />
            </span>
            Следить за новыми строками
          </label>
          <span className="toolbar-spacer muted mono logs-count">
            {loading ? 'ЗАГРУЗКА…' : `${data.lines.length} / ${data.total} ЗАПИСЕЙ`}
          </span>
        </div>

        <div className="log-filters">
          <FilterChip label="Все события" active={category === ''} onClick={() => setCategory('')} />
          {CATEGORIES.map(c => (
            <FilterChip key={c} label={c} active={category === c} color={TAG_COLORS[c]}
              onClick={() => setCategory(category === c ? '' : c)} />
          ))}
        </div>
      </section>

      <section className="panel log-terminal">
        <div className="terminal-head">
          <span className="mono">EVENT_STREAM / {day || 'NO_DATA'}</span>
          <span className="terminal-lights"><i /><i /><i /></span>
        </div>
        <div ref={listRef} className="log-lines mono">
        {data.lines.length === 0 && (
          <div className="muted log-empty">
            {day ? 'Нет записей по выбранным фильтрам' : 'Файлы логов ещё не созданы'}
          </div>
        )}
        {data.lines.map((line, i) => {
          const { time, tag, text } = parseLine(line)
          return (
            <div className="log-line" key={i}>
              <span className="log-seq">{String(i + 1).padStart(4, '0')}</span>
              {time && <span className="log-time">{time}</span>}
              {tag && (
                <span className="log-tag" style={{ color: TAG_COLORS[tag] ?? 'var(--muted)' }}>
                  {tag}
                </span>
              )}
              <span className="log-text">{text}</span>
            </div>
          )
        })}
        </div>
      </section>
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
        fontFamily: 'inherit',
        borderColor: active ? (color ?? 'var(--accent)') : undefined,
        color: active ? (color ?? 'var(--accent)') : undefined,
        background: active ? 'rgba(199,243,107,0.06)' : undefined,
      }}
    >
      {label}
    </button>
  )
}
