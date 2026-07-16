import { useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { api, UnauthorizedError, type Overview } from './api'
import { useLive } from './useLive'
import Dashboard from './pages/Dashboard'
import Players from './pages/Players'
import Entities from './pages/Entities'
import MapView from './pages/MapView'
import Logs from './pages/Logs'

type Phase = 'loading' | 'login' | 'ready'
type Tab = 'dashboard' | 'players' | 'entities' | 'map' | 'logs'

const TABS: { id: Tab; label: string; icon: string }[] = [
  { id: 'dashboard', label: 'Дашборд', icon: 'M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z' },
  { id: 'players', label: 'Игроки', icon: 'M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5s-3 1.34-3 3 1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z' },
  { id: 'entities', label: 'Entity', icon: 'M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5' },
  { id: 'map', label: 'Карта', icon: 'M20.5 3l-.16.03L15 5.1 9 3 3.36 4.9c-.21.07-.36.25-.36.48V20.5c0 .28.22.5.5.5l.16-.03L9 18.9l6 2.1 5.64-1.9c.21-.07.36-.25.36-.48V3.5c0-.28-.22-.5-.5-.5zM15 19l-6-2.11V5l6 2.11V19z' },
  { id: 'logs', label: 'Логи', icon: 'M3 4h18v2H3V4zm0 5h12v2H3V9zm0 5h18v2H3v-2zm0 5h12v2H3v-2z' },
]

const EASE = [0.4, 0, 0.2, 1] as const

export default function App() {
  const [phase, setPhase] = useState<Phase>('loading')
  const [overview, setOverview] = useState<Overview | null>(null)
  const [bootNonce, setBootNonce] = useState(0)

  useEffect(() => {
    let cancelled = false
    let timer: ReturnType<typeof setTimeout> | null = null

    const go = async () => {
      try {
        const ov = await api.get<Overview>('/api/overview')
        if (cancelled) return
        setOverview(ov)
        setPhase('ready')
      } catch (e) {
        if (cancelled) return
        if (e instanceof UnauthorizedError) setPhase('login')
        else timer = setTimeout(go, 3000)
      }
    }

    const code = new URLSearchParams(location.search).get('code')
    if (code) {
      history.replaceState(null, '', '/')
      api.post('/api/auth/exchange', { code }).then(
        () => { if (!cancelled) go() },
        () => { if (!cancelled) setPhase('login') }
      )
    } else {
      go()
    }

    return () => {
      cancelled = true
      if (timer) clearTimeout(timer)
    }
  }, [bootNonce])

  return (
    <>
      {phase === 'loading' && <LoadingScreen />}
      {phase === 'login' || (phase === 'ready' && !overview)
        ? <LoginScreen onSuccess={() => setBootNonce(n => n + 1)} />
        : phase === 'ready' && overview && <Shell overview={overview} />}
    </>
  )
}

function LoadingScreen() {
  return (
    <div style={{ position: 'relative', zIndex: 1, display: 'grid', placeItems: 'center', height: '100vh' }}>
      <motion.div className="muted" style={{ fontSize: 13, letterSpacing: '0.04em' }}
        animate={{ opacity: [0.4, 1, 0.4] }} transition={{ duration: 1.6, repeat: Infinity, ease: EASE }}>
        Загрузка…
      </motion.div>
    </div>
  )
}

function LoginScreen({ onSuccess }: { onSuccess: () => void }) {
  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [shake, setShake] = useState(0)

  const submit = async () => {
    if (code.trim().length < 8) return
    try {
      await api.post('/api/auth/exchange', { code: code.trim() })
      onSuccess()
    } catch {
      setError('Неверный или истёкший код')
      setShake(s => s + 1)
    }
  }

  return (
    <div style={{ position: 'relative', zIndex: 1, display: 'grid', placeItems: 'center', minHeight: '100vh', padding: 20 }}>
      <motion.div
        key={shake}
        className="glass"
        initial={{ opacity: 0, scale: 0.96, y: 12 }}
        animate={{
          opacity: 1, scale: 1, y: 0,
          x: shake > 0 ? [0, -10, 10, -6, 6, 0] : 0,
        }}
        transition={{ duration: shake > 0 ? 0.4 : 0.5, ease: EASE }}
        style={{ width: 380, textAlign: 'center', padding: '40px 36px' }}
      >
        <motion.div
          initial={{ scale: 0.8, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ delay: 0.1, duration: 0.5, ease: EASE }}
          style={{
            width: 64, height: 64, margin: '0 auto 20px',
            borderRadius: 14,
            background: 'var(--accent)',
            display: 'grid', placeItems: 'center',
          }}
        >
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none">
            <path d="M12 2L2 7l10 5 10-5-10-5z" stroke="#fff" strokeWidth="1.5" strokeLinejoin="round"/>
            <path d="M2 17l10 5 10-5" stroke="#fff" strokeWidth="1.5" strokeLinejoin="round" opacity="0.7"/>
            <path d="M2 12l10 5 10-5" stroke="#fff" strokeWidth="1.5" strokeLinejoin="round" opacity="0.85"/>
          </svg>
        </motion.div>

        <h2 style={{ fontSize: 20, fontWeight: 600, marginBottom: 6 }}>PJM Panel</h2>
        <p className="muted" style={{ fontSize: 13, margin: '0 0 28px' }}>
          Введите код из игры: <span className="mono" style={{ color: 'var(--accent)' }}>/pjm web login</span>
        </p>

        <input
          className="input mono"
          style={{
            width: '100%', textAlign: 'center', fontSize: 26, letterSpacing: 10,
            padding: '14px 12px', fontWeight: 500,
          }}
          maxLength={8}
          value={code}
          placeholder="········"
          onChange={e => { setCode(e.target.value.toUpperCase()); setError('') }}
          onKeyDown={e => e.key === 'Enter' && submit()}
          autoFocus
        />

        <AnimatePresence>
          {error && (
            <motion.p
              initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }}
              style={{ color: 'var(--danger)', fontSize: 13, marginTop: 12, overflow: 'hidden' }}
            >
              {error}
            </motion.p>
          )}
        </AnimatePresence>

        <button
          className="btn btn-accent"
          style={{ width: '100%', marginTop: 20, padding: '12px 16px', fontSize: 14 }}
          disabled={code.trim().length < 8}
          onClick={submit}
        >
          Войти
        </button>
      </motion.div>
    </div>
  )
}

function Shell({ overview }: { overview: Overview }) {
  const [tab, setTab] = useState<Tab>('dashboard')
  const [hovered, setHovered] = useState(false)
  const live = useLive(overview.history, overview.profilerActive)

  return (
    <div style={{ position: 'relative', zIndex: 1, display: 'flex', height: '100vh' }}>
      {/* — Сайдбар-рельса — */}
      <motion.aside
        className="glass"
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
        animate={{ width: hovered ? 220 : 64 }}
        transition={{ duration: 0.3, ease: EASE }}
        style={{
          position: 'fixed', left: 12, top: 12, bottom: 12, zIndex: 20,
          padding: '14px 10px', display: 'flex', flexDirection: 'column',
          gap: 4, overflow: 'hidden',
          borderRadius: 20,
        }}
      >
        {/* Лого */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '4px 6px', marginBottom: 8, height: 44 }}>
          <div style={{
            width: 32, height: 32, borderRadius: 8, flexShrink: 0,
            background: 'var(--accent)',
            display: 'grid', placeItems: 'center',
          }}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path d="M12 2L2 7l10 5 10-5-10-5z" stroke="#fff" strokeWidth="1.5" strokeLinejoin="round"/>
            </svg>
          </div>
          <AnimatePresence>
            {hovered && (
              <motion.span
                initial={{ opacity: 0, x: -8 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -8 }}
                transition={{ duration: 0.18, ease: EASE }}
                style={{ fontWeight: 600, fontSize: 15, whiteSpace: 'nowrap' }}
              >
                PJM
              </motion.span>
            )}
          </AnimatePresence>
        </div>

        {/* Навигация */}
        <nav style={{ display: 'flex', flexDirection: 'column', gap: 2, flex: 1 }}>
          {TABS.map(t => (
            <NavButton key={t.id} tab={t} active={tab === t.id} expanded={hovered} onClick={() => setTab(t.id)} />
          ))}
        </nav>

        {/* Выход */}
        <NavButton
          tab={{ id: 'logout', label: 'Выйти', icon: 'M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z' }}
          active={false} expanded={hovered}
          onClick={() => api.post('/api/auth/logout').then(() => location.reload(), () => location.reload())}
        />
      </motion.aside>

      {/* — Контент — */}
      <div style={{ flex: 1, marginLeft: 88, padding: '20px 24px 24px', overflowY: 'auto', minWidth: 0 }}>
        {/* Статус-капсула */}
        <div style={{ position: 'fixed', top: 20, right: 24, zIndex: 15, display: 'flex', alignItems: 'center', gap: 8 }}>
          <motion.div
            className="glass-2"
            initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3, ease: EASE }}
            style={{
              display: 'flex', alignItems: 'center', gap: 8,
              padding: '7px 14px', borderRadius: 999, fontSize: 12,
            }}
          >
            <span className={`pulse-dot ${live.connected ? 'on' : ''}`} />
            <span className={live.connected ? '' : 'muted'} style={{ fontWeight: 500 }}>
              {live.connected ? 'онлайн' : 'нет связи'}
            </span>
          </motion.div>
        </div>

        <AnimatePresence mode="wait">
          <motion.main
            key={tab}
            initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.3, ease: EASE }}
            style={{ maxWidth: 1400, margin: '0 auto', paddingTop: 12 }}
          >
            {tab === 'dashboard' && <Dashboard live={live} />}
            {tab === 'players' && <Players live={live} />}
            {tab === 'entities' && <Entities live={live} profilerAllowed={overview.profilerAllowed} />}
            {tab === 'map' && <MapView live={live} />}
            {tab === 'logs' && <Logs />}
          </motion.main>
        </AnimatePresence>
      </div>
    </div>
  )
}

function NavButton({ tab, active, expanded, onClick }: {
  tab: { id: string; label: string; icon: string }
  active: boolean
  expanded: boolean
  onClick: () => void
}) {
  return (
    <button
      onClick={onClick}
      style={{
        position: 'relative', display: 'flex', alignItems: 'center', gap: 12,
        padding: '10px 10px', borderRadius: 12, cursor: 'pointer',
        background: 'transparent', border: 'none', color: active ? 'var(--text)' : 'var(--muted)',
        fontFamily: 'inherit', fontSize: 14, fontWeight: 500,
        transition: `background var(--t-micro) var(--ease), color var(--t-micro) var(--ease)`,
        textAlign: 'left', width: '100%',
      }}
      onMouseEnter={e => { if (!active) e.currentTarget.style.background = 'rgba(255,255,255,0.06)' }}
      onMouseLeave={e => { if (!active) e.currentTarget.style.background = 'transparent' }}
    >
      {active && (
        <motion.div
          layoutId="nav-active"
          transition={{ duration: 0.3, ease: EASE }}
          style={{
            position: 'absolute', inset: 0, borderRadius: 12,
            background: 'var(--accent-active-bg)',
            boxShadow: 'inset 0 0 0 1px var(--accent-dim)',
          }}
        />
      )}
      <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" style={{ flexShrink: 0, position: 'relative', zIndex: 1 }}>
        <path d={tab.icon} />
      </svg>
      <AnimatePresence>
        {expanded && (
          <motion.span
            initial={{ opacity: 0, x: -6 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -6 }}
            transition={{ duration: 0.18, ease: EASE }}
            style={{ whiteSpace: 'nowrap', position: 'relative', zIndex: 1 }}
          >
            {tab.label}
          </motion.span>
        )}
      </AnimatePresence>
    </button>
  )
}
