import { useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { api, UnauthorizedError, type Overview } from './api'
import { useLive } from './useLive'
import Dashboard from './pages/Dashboard'
import Players from './pages/Players'
import Entities from './pages/Entities'
import MapView from './pages/MapView'

type Phase = 'loading' | 'login' | 'ready'
type Tab = 'dashboard' | 'players' | 'entities' | 'map'

const TABS: { id: Tab; label: string }[] = [
  { id: 'dashboard', label: 'Дашборд' },
  { id: 'players', label: 'Игроки' },
  { id: 'entities', label: 'Entity' },
  { id: 'map', label: 'Карта' },
]

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

    // Код из кликабельной ссылки /login?code=XXXX — обменять сразу.
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

  if (phase === 'loading') {
    return <div className="muted" style={{ padding: 60, textAlign: 'center' }}>Загрузка…</div>
  }
  if (phase === 'login' || !overview) {
    return <LoginScreen onSuccess={() => setBootNonce(n => n + 1)} />
  }
  return <Shell overview={overview} />
}

function LoginScreen({ onSuccess }: { onSuccess: () => void }) {
  const [code, setCode] = useState('')
  const [error, setError] = useState('')

  const submit = async () => {
    try {
      await api.post('/api/auth/exchange', { code: code.trim() })
      onSuccess()
    } catch {
      setError('Неверный или истёкший код')
    }
  }

  return (
    <div style={{ display: 'grid', placeItems: 'center', minHeight: '100vh' }}>
      <motion.div className="panel" style={{ width: 360, textAlign: 'center' }}
        initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
        <h2>PJM — Панель управления</h2>
        <p className="muted">Введите код из игры: <span className="mono">/pjm web login</span></p>
        <input className="input mono" style={{ width: '100%', textAlign: 'center', fontSize: 18 }}
          maxLength={8} value={code} placeholder="XXXXXXXX"
          onChange={e => setCode(e.target.value.toUpperCase())}
          onKeyDown={e => e.key === 'Enter' && submit()} autoFocus />
        {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
        <button className="btn btn-accent" style={{ width: '100%', marginTop: 12 }}
          disabled={code.trim().length < 8} onClick={submit}>Войти</button>
      </motion.div>
    </div>
  )
}

function Shell({ overview }: { overview: Overview }) {
  const [tab, setTab] = useState<Tab>('dashboard')
  const live = useLive(overview.history)

  return (
    <div style={{ maxWidth: 1400, margin: '0 auto', padding: '16px 20px 40px' }}>
      <header style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 16 }}>
        <h1 style={{ fontSize: 18, margin: 0 }}>PJM <span className="muted">// панель управления</span></h1>
        <nav style={{ display: 'flex', gap: 6 }}>
          {TABS.map(t => (
            <button key={t.id} onClick={() => setTab(t.id)}
              className={`btn ${tab === t.id ? 'btn-accent' : ''}`}>{t.label}</button>
          ))}
        </nav>
        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 8 }}>
          <span className={`pulse-dot ${live.connected ? 'on' : ''}`} />
          <span className="muted">{live.connected ? 'онлайн' : 'нет связи'}</span>
          <button className="btn" onClick={() => api.post('/api/auth/logout').then(() => location.reload(), () => location.reload())}>
            Выйти
          </button>
        </div>
      </header>

      <AnimatePresence mode="wait">
        <motion.main key={tab}
          initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -8 }} transition={{ duration: 0.18 }}>
          {tab === 'dashboard' && <Dashboard live={live} />}
          {tab === 'players' && <Players live={live} />}
          {tab === 'entities' && <Entities live={live} profilerAllowed={overview.profilerAllowed} />}
          {tab === 'map' && <MapView live={live} />}
        </motion.main>
      </AnimatePresence>
    </div>
  )
}
