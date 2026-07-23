import { useEffect, useMemo, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { api, hashColor, type ActionResponse, type HistoryDto, type PlayerDto } from '../api'
import type { LiveState } from '../useLive'
import ConfirmButton from '../components/ConfirmButton'

type PunishType = 'warn' | 'ban' | 'mute_voice' | 'mute_text'

const PUNISH_LABELS: Record<PunishType, string> = {
  warn: 'Варн', ban: 'Бан', mute_voice: 'Мут войса', mute_text: 'Мут текста',
}

export default function Players({ live }: { live: LiveState }) {
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<string | null>(null)

  const players = useMemo(() => {
    const q = query.trim().toLowerCase()
    return live.players.filter(p => !q || p.name.toLowerCase().includes(q))
  }, [live.players, query])

  const player = live.players.find(p => p.uuid === selected) ?? null

  return (
    <div className={`players-layout fade-in ${player ? 'has-selection' : ''}`}>
      <div className="panel">
        <div className="panel-header">
          <div>
            <span className="panel-kicker">Активные подключения / real-time</span>
            <h3>Личный состав сервера</h3>
          </div>
          <span className="chip chip-ok">{live.players.length} в сети</span>
        </div>
        <div className="toolbar players-toolbar">
          <input className="input search-input" placeholder="Найти игрока по нику…" value={query}
            onChange={e => setQuery(e.target.value)} />
          <span className="muted mono list-counter">ПОКАЗАНО {players.length.toString().padStart(2, '0')}</span>
        </div>
        <div className="table-shell players-table">
          <table className="table">
            <thead>
              <tr><th>Игрок</th><th>Фракция</th><th>Роль</th><th>Дименшен</th><th>Координаты</th><th>Пинг</th><th>Статус</th></tr>
            </thead>
            <tbody>
              {players.map(p => (
                <tr key={p.uuid} className={p.uuid === selected ? 'selected' : ''}
                  onClick={() => setSelected(p.uuid === selected ? null : p.uuid)}>
                  <td>
                    <div className="player-cell">
                      <span className="player-monogram">{p.name.slice(0, 2).toUpperCase()}</span>
                      <strong className="mono">{p.name}</strong>
                    </div>
                  </td>
                  <td>{p.team
                    ? <span className="chip" style={{ color: hashColor(p.team), borderColor: hashColor(p.team) + '55' }}>{p.team}</span>
                    : <span className="muted">—</span>}</td>
                  <td className="muted">{p.role || '—'}</td>
                  <td className="mono muted">{p.dim}</td>
                  <td className="mono tnum">{p.x} / {p.y} / {p.z}</td>
                  <td className={`mono tnum ${p.ping > 150 ? 'ping-high' : ''}`}>{p.ping} мс</td>
                  <td>
                    <div className="status-chips">
                      {p.warns === 0 && !p.textMuted && !p.voiceMuted && <span className="chip chip-ok">чисто</span>}
                      {p.warns > 0 && <span className="chip chip-warn">варны {p.warns}</span>}
                      {p.textMuted && <span className="chip chip-danger">текст</span>}
                      {p.voiceMuted && <span className="chip chip-danger">войс</span>}
                    </div>
                  </td>
                </tr>
              ))}
              {players.length === 0 && (
                <tr style={{ cursor: 'default' }}><td colSpan={7} className="muted empty-cell">Игроки по запросу не найдены</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <AnimatePresence>
        {player && (
          <motion.div className="player-drawer-wrap" key={player.uuid}
            initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 20 }}>
            <PlayerCard player={player} players={live.players} onClose={() => setSelected(null)} />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

function PlayerCard({ player, players, onClose }: {
  player: PlayerDto; players: PlayerDto[]; onClose: () => void
}) {
  const [reason, setReason] = useState('')
  const [duration, setDuration] = useState('30m')
  const [punishType, setPunishType] = useState<PunishType>('warn')
  const [status, setStatus] = useState('')
  const [history, setHistory] = useState<HistoryDto[]>([])
  const [tpTarget, setTpTarget] = useState('')

  useEffect(() => {
    api.get<HistoryDto[]>(`/api/moderation/history?player=${player.uuid}`)
      .then(setHistory, () => setHistory([]))
  }, [player.uuid])

  const run = async (call: Promise<ActionResponse>) => {
    setStatus('…')
    try {
      const res = await call
      setStatus(res.ok ? '✓ выполнено' : `✗ ${res.error}`)
      api.get<HistoryDto[]>(`/api/moderation/history?player=${player.uuid}`)
        .then(setHistory, () => undefined)
    } catch (e) {
      setStatus(`✗ ${(e as Error).message}`)
    }
  }

  return (
    <div className="panel player-drawer">
      <div className="player-drawer-head">
        <div className="player-identity">
          <span className="player-avatar">{player.name.slice(0, 2).toUpperCase()}</span>
          <div>
            <span className="panel-kicker">Карточка оператора</span>
            <h3>{player.name}</h3>
          </div>
        </div>
        <button className="btn btn-icon" onClick={onClose} aria-label="Закрыть">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M18 6L6 18M6 6l12 12" strokeLinecap="round" />
          </svg>
        </button>
      </div>
      <p className="mono muted player-uuid">{player.uuid}</p>

      <div className="drawer-section">
        <h4>Выдать наказание</h4>
        <div className="drawer-fields">
          <select className="input" value={punishType}
            onChange={e => setPunishType(e.target.value as PunishType)}>
            {Object.entries(PUNISH_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
          {punishType !== 'warn' && (
            <input className="input mono" value={duration} onChange={e => setDuration(e.target.value)}
              placeholder="Срок: 30m / 1d / permanent" />
          )}
          <input className="input" value={reason} onChange={e => setReason(e.target.value)}
            placeholder="Причина" />
          <ConfirmButton danger label={PUNISH_LABELS[punishType]}
            disabled={!reason.trim()}
            onConfirm={() => run(api.post('/api/actions/punish', {
              uuid: player.uuid, name: player.name, type: punishType,
              duration: punishType === 'warn' ? '' : duration, reason,
            }))} />
        </div>
      </div>

      <div className="drawer-section">
        <h4>Снять наказание</h4>
        <div className="toolbar">
          <button className="btn" onClick={() => run(api.post('/api/actions/pardon',
            { uuid: player.uuid, name: player.name, type: 'ban' }))}>Разбан</button>
          <button className="btn" onClick={() => run(api.post('/api/actions/pardon',
            { uuid: player.uuid, name: player.name, type: 'mute_voice' }))}>Снять мут войса</button>
          <button className="btn" onClick={() => run(api.post('/api/actions/pardon',
            { uuid: player.uuid, name: player.name, type: 'mute_text' }))}>Снять мут текста</button>
        </div>
      </div>

      <div className="drawer-section">
        <h4>Оперативные действия</h4>
        <div className="drawer-fields">
          <ConfirmButton danger label="Кикнуть"
            onConfirm={() => run(api.post('/api/actions/kick',
              { uuid: player.uuid, reason: reason || 'Кик через панель' }))} />
          <div style={{ display: 'flex', gap: 8 }}>
            <select className="input" style={{ flex: 1 }} value={tpTarget}
              onChange={e => setTpTarget(e.target.value)}>
              <option value="">Телепорт к игроку…</option>
              {players.filter(p => p.uuid !== player.uuid).map(p =>
                <option key={p.uuid} value={p.uuid}>{p.name}</option>)}
            </select>
            <button className="btn" disabled={!tpTarget}
              onClick={() => run(api.post('/api/actions/teleport',
                { uuid: player.uuid, toPlayer: tpTarget }))}>ТП</button>
          </div>
        </div>
      </div>

      {status && (
        <p className={`status-message ${status.startsWith('✓') ? 'success' : 'error'}`}>{status}</p>
      )}

      <div className="drawer-section">
        <h4>История наказаний</h4>
        <div className="punishment-history">
          {history.length === 0 && <span className="muted">Пусто</span>}
          {[...history].reverse().map((h, i) => (
            <div className="history-entry" key={i}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                <span className={`chip ${h.action === 'apply' ? 'chip-danger' : 'chip-ok'}`}>{h.type}/{h.action}</span>
                <span className="muted">{new Date(h.ts).toLocaleString('ru-RU')} · {h.moderator}</span>
              </div>
              {h.reason && <div className="muted" style={{ marginTop: 4 }}>{h.reason}</div>}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
