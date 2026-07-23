import { useCallback, useEffect, useState } from 'react'
import { api, formatMicros, type EntityDto } from '../api'
import type { LiveState } from '../useLive'
import StatCard from '../components/StatCard'
import ConfirmButton from '../components/ConfirmButton'

interface EntitiesResponse { total: number; entities: EntityDto[] }

function categoryColor(category: string): string {
  switch (category) {
    case 'mob': return 'var(--warn)'
    case 'item': return 'var(--info)'
    case 'projectile': return 'var(--purple)'
    default: return 'var(--muted)'
  }
}

export default function Entities({ live, profilerAllowed }: { live: LiveState; profilerAllowed: boolean }) {
  const [dim, setDim] = useState('')
  const [type, setType] = useState('')
  const [data, setData] = useState<EntitiesResponse>({ total: 0, entities: [] })
  const [checked, setChecked] = useState<Set<string>>(new Set())
  const [status, setStatus] = useState('')
  const [bulkRadius, setBulkRadius] = useState('')

  const refresh = useCallback(() => {
    const params = new URLSearchParams()
    if (dim) params.set('dim', dim)
    if (type) params.set('type', type.trim())
    api.get<EntitiesResponse>(`/api/entities?${params}`).then(setData, () => undefined)
  }, [dim, type])

  useEffect(() => {
    refresh()
    const timer = setInterval(refresh, 5000)
    return () => clearInterval(timer)
  }, [refresh])

  const dims = live.sample?.dims.map(d => d.dim) ?? []
  const toggle = (uuid: string) => setChecked(prev => {
    const next = new Set(prev)
    if (next.has(uuid)) next.delete(uuid); else next.add(uuid)
    return next
  })

  const removeChecked = async () => {
    try {
      const res = await api.post<{ ok: boolean; message?: string; error?: string }>(
        '/api/actions/entities/remove', { uuids: [...checked] })
      setStatus(res.ok ? `✓ ${res.message}` : `✗ ${res.error ?? 'ошибка'}`)
      setChecked(new Set())
      refresh()
    } catch (e) {
      setStatus(`✗ ${(e as Error).message}`)
    }
  }

  const removeBulk = async () => {
    if (!dim) { setStatus('✗ для массового удаления выберите дименшен'); return }
    const radius = bulkRadius.trim() ? Number(bulkRadius) : undefined
    if (radius !== undefined && (Number.isNaN(radius) || radius <= 0)) {
      setStatus('✗ радиус должен быть положительным числом')
      return
    }
    try {
      const res = await api.post<{ ok: boolean; message?: string; error?: string }>(
        '/api/actions/entities/remove-bulk',
        { type: type.trim() || null, dim, x: radius ? 0 : null, z: radius ? 0 : null, radius: radius ?? null })
      setStatus(res.ok ? `✓ ${res.message}` : `✗ ${res.error ?? 'ошибка'}`)
      refresh()
    } catch (e) {
      setStatus(`✗ ${(e as Error).message}`)
    }
  }

  return (
    <div className="entities-page fade-in">
      <section className="entity-metrics">
        {Object.entries(live.entityCounts).map(([category, count], index) => (
          <StatCard key={category} index={String(index + 1).padStart(2, '0')} label={`Категория / ${category}`} value={String(count)} sub="объектов загружено" />
        ))}
      </section>

      {profilerAllowed && <ProfilerPanel live={live} />}

      <section className="panel">
        <div className="panel-header">
          <div>
            <span className="panel-kicker">Реестр мира / выборочное управление</span>
            <h3>Загруженные объекты</h3>
          </div>
          <span className="chip">показано {data.entities.length} / {data.total}</span>
        </div>
        <div className="toolbar entity-toolbar">
          <select className="input" value={dim} onChange={e => setDim(e.target.value)}>
            <option value="">Все дименшены</option>
            {dims.map(d => <option key={d} value={d}>{d}</option>)}
          </select>
          <input className="input mono" placeholder="Фильтр типа: minecraft:zombie"
            value={type} onChange={e => setType(e.target.value)} style={{ width: 260 }} />
          <button className="btn" onClick={refresh}>Обновить</button>
          <div className="toolbar-spacer entity-danger-actions">
            {checked.size > 0 && (
              <ConfirmButton danger label={`Удалить выбранные (${checked.size})`} onConfirm={removeChecked} />
            )}
            <input className="input mono" placeholder="радиус (пусто = весь мир)" style={{ width: 190 }}
              value={bulkRadius} onChange={e => setBulkRadius(e.target.value)} />
            <ConfirmButton danger label="Массовое удаление по фильтру" onConfirm={removeBulk} />
          </div>
        </div>
        {status && (
          <p className={`status-message entity-status ${status.startsWith('✓') ? 'success' : 'error'}`}>{status}</p>
        )}
        <div className="table-shell entity-table">
          <table className="table">
            <thead>
              <tr><th>Выбор</th><th>Тип объекта</th><th>Имя</th><th>Категория</th><th>Дименшен</th><th>Координаты</th></tr>
            </thead>
            <tbody>
              {data.entities.map(ent => (
                <tr key={ent.uuid} onClick={() => toggle(ent.uuid)}
                  className={checked.has(ent.uuid) ? 'selected' : ''}>
                  <td>
                    <span className="switch sm" onClick={(ev) => { ev.stopPropagation(); toggle(ent.uuid) }}>
                      <input type="checkbox" readOnly checked={checked.has(ent.uuid)} />
                      <span className="switch-slider" style={{ borderRadius: 999 }} />
                    </span>
                  </td>
                  <td className="mono">{ent.type}</td>
                  <td>{ent.name}</td>
                  <td><span className="chip" style={{
                    color: categoryColor(ent.category),
                    borderColor: categoryColor(ent.category) + '40',
                    background: categoryColor(ent.category) + '14',
                  }}>{ent.category}</span></td>
                  <td className="mono muted">{ent.dim}</td>
                  <td className="mono">{ent.x} {ent.y} {ent.z}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}

function ProfilerPanel({ live }: { live: LiveState }) {
  const [busy, setBusy] = useState(false)
  const report = live.profiler

  const toggleProfiler = async () => {
    setBusy(true)
    try { await api.post('/api/profiler/toggle') } finally { setBusy(false) }
  }

  return (
    <section className={`panel profiler-panel ${live.profilerActive ? 'is-active' : ''}`}>
      <div className="panel-header profiler-header">
        <div>
          <span className="panel-kicker">Диагностический модуль / окно 30 секунд</span>
          <h3>Профайлер тика entity</h3>
        </div>
        <div className="toolbar">
          <span className={`chip ${live.profilerActive ? 'chip-ok' : ''}`}>
            <span className={`pulse-dot ${live.profilerActive ? 'on' : ''}`} />
            {live.profilerActive ? 'идёт замер' : 'выключен'}
          </span>
          <button className={`btn ${live.profilerActive ? 'btn-danger' : 'btn-accent'}`}
            disabled={busy} onClick={toggleProfiler}>
            {live.profilerActive ? 'Остановить замер' : 'Запустить профайлер'}
          </button>
        </div>
      </div>

      {live.profilerActive && report && report.topEntities.length > 0 && (
        <div className="profiler-grid">
          <div>
            <h4>Топ тяжёлых entity</h4>
            <div className="table-shell profiler-table">
              <table className="table">
                <thead><tr><th>Тип</th><th>Нагрузка</th><th>Координаты</th></tr></thead>
                <tbody>
                  {report.topEntities.slice(0, 25).map(e => (
                    <tr key={e.uuid} style={{ cursor: 'default' }}>
                      <td className="mono">{e.type}</td>
                      <td className="mono" style={{ color: 'var(--warn)' }}>{formatMicros(e.totalNanos, e.ticks)}</td>
                      <td className="mono muted">{Math.round(e.x)} {Math.round(e.y)} {Math.round(e.z)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <div>
            <h4>Агрегация по типам</h4>
            <div className="table-shell profiler-table">
              <table className="table">
                <thead><tr><th>Тип</th><th>Кол-во</th><th>Доля окна</th></tr></thead>
                <tbody>
                  {report.byType.slice(0, 25).map(t => (
                    <tr key={t.type} style={{ cursor: 'default' }}>
                      <td className="mono">{t.type}</td>
                      <td className="mono">{t.count}</td>
                      <td className="mono" style={{ color: 'var(--warn)' }}>
                        {report.totalNanos > 0 ? ((t.totalNanos / report.totalNanos) * 100).toFixed(1) : '0'}%
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
      {live.profilerActive && (!report || report.topEntities.length === 0) && (
        <div className="profiler-wait mono">Сбор данных · первый отчёт появится после завершения окна</div>
      )}
    </section>
  )
}
