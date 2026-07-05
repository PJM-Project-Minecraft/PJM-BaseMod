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
    <div className="fade-in" style={{ display: 'grid', gridTemplateColumns: player ? '1fr 380px' : '1fr', gap: 14 }}>
      <div className="panel">
        <div style={{ display: 'flex', gap: 10, marginBottom: 10 }}>
          <input className="input" placeholder="Поиск по нику…" value={query}
            onChange={e => setQuery(e.target.value)} style={{ width: 240 }} />
          <span className="chip">онлайн: {live.players.length}</span>
        </div>
        <table className="table">
          <thead>
            <tr><th>Ник</th><th>Команда</th><th>Роль</th><th>Дименшен</th><th>Координаты</th><th>Пинг</th><th>Статус</th></tr>
          </thead>
          <tbody>
            {players.map(p => (
              <tr key={p.uuid} className={p.uuid === selected ? 'selected' : ''}
                onClick={() => setSelected(p.uuid === selected ? null : p.uuid)}>
                <td>{p.name}</td>
                <td>{p.team
                  ? <span className="chip" style={{ color: hashColor(p.team) }}>{p.team}</span>
                  : <span className="muted">—</span>}</td>
                <td className="muted">{p.role || '—'}</td>
                <td className="mono muted">{p.dim}</td>
                <td className="mono">{p.x} {p.y} {p.z}</td>
                <td className="mono">{p.ping} мс</td>
                <td>
                  {p.warns > 0 && <span className="chip chip-danger">варны: {p.warns}</span>}{' '}
                  {p.textMuted && <span className="chip chip-danger">текст</span>}{' '}
                  {p.voiceMuted && <span className="chip chip-danger">войс</span>}
                </td>
              </tr>
            ))}
            {players.length === 0 && (
              <tr style={{ cursor: 'default' }}><td colSpan={7} className="muted">Никого нет</td></tr>
            )}
          </tbody>
        </table>
      </div>

      <AnimatePresence>
        {player && (
          <motion.div key={player.uuid}
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
    <div className="panel" style={{ position: 'sticky', top: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h3 style={{ margin: 0 }}>{player.name}</h3>
        <button className="btn" onClick={onClose}>✕</button>
      </div>
      <p className="mono muted" style={{ fontSize: 12 }}>{player.uuid}</p>

      <h4>Наказание</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
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

      <h4>Снять наказание</h4>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <button className="btn" onClick={() => run(api.post('/api/actions/pardon',
          { uuid: player.uuid, name: player.name, type: 'ban' }))}>Разбан</button>
        <button className="btn" onClick={() => run(api.post('/api/actions/pardon',
          { uuid: player.uuid, name: player.name, type: 'mute_voice' }))}>Снять мут войса</button>
        <button className="btn" onClick={() => run(api.post('/api/actions/pardon',
          { uuid: player.uuid, name: player.name, type: 'mute_text' }))}>Снять мут текста</button>
      </div>

      <h4>Действия</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
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

      {status && <p className="mono" style={{ marginTop: 10 }}>{status}</p>}

      <h4>История наказаний</h4>
      <div style={{ maxHeight: 240, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 6 }}>
        {history.length === 0 && <span className="muted">Пусто</span>}
        {[...history].reverse().map((h, i) => (
          <div key={i} style={{ fontSize: 12, borderBottom: '1px solid var(--border)', paddingBottom: 4 }}>
            <span className={`chip ${h.action === 'apply' ? 'chip-danger' : ''}`}>{h.type}/{h.action}</span>{' '}
            <span className="muted">{new Date(h.ts).toLocaleString('ru-RU')} · {h.moderator}</span>
            {h.reason && <div className="muted">{h.reason}</div>}
          </div>
        ))}
      </div>
    </div>
  )
}
