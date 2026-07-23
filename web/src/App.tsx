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

interface TabDefinition {
  id: Tab
  label: string
  kicker: string
  title: string
  icon: string
}

const TABS: TabDefinition[] = [
  {
    id: 'dashboard',
    label: 'Обзор',
    kicker: '01 / Состояние системы',
    title: 'Командный центр',
    icon: 'M4 4h6v7H4V4zm10 0h6v4h-6V4zM4 15h6v5H4v-5zm10-3h6v8h-6v-8z',
  },
  {
    id: 'players',
    label: 'Состав',
    kicker: '02 / Личный состав',
    title: 'Игроки онлайн',
    icon: 'M16 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM8 12a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zm8 2c-3.3 0-6 1.6-6 3.5V21h12v-3.5c0-1.9-2.7-3.5-6-3.5zM8 14c-3.9 0-7 1.7-7 3.8V21h7v-3.5c0-1.2.5-2.3 1.4-3.2A9.9 9.9 0 0 0 8 14z',
  },
  {
    id: 'entities',
    label: 'Объекты',
    kicker: '03 / Диагностика мира',
    title: 'Entity и нагрузка',
    icon: 'M12 2 2.5 7 12 12l9.5-5L12 2zm-7.5 9L12 15l7.5-4M4.5 16 12 20l7.5-4',
  },
  {
    id: 'map',
    label: 'Карта',
    kicker: '04 / Оперативная обстановка',
    title: 'Тактическая карта',
    icon: 'm3 5 6-2 6 2 6-2v16l-6 2-6-2-6 2V5zm6-2v16m6-14v16',
  },
  {
    id: 'logs',
    label: 'Журнал',
    kicker: '05 / Аудит событий',
    title: 'Оперативный журнал',
    icon: 'M5 4h14M5 9h14M5 14h9M5 19h11',
  },
]

const EASE = [0.22, 1, 0.36, 1] as const

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
        () => { if (!cancelled) setPhase('login') },
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
    <AnimatePresence mode="sync">
      {phase === 'loading' && <LoadingScreen key="loading" />}
      {phase === 'login' || (phase === 'ready' && !overview)
        ? <LoginScreen key="login" onSuccess={() => setBootNonce(n => n + 1)} />
        : phase === 'ready' && overview && <Shell key="shell" overview={overview} />}
    </AnimatePresence>
  )
}

function Mark({ compact = false }: { compact?: boolean }) {
  return (
    <div className={`brand-mark ${compact ? 'compact' : ''}`} aria-hidden="true">
      <svg viewBox="0 0 40 40" fill="none">
        <path d="M6 10.5 20 3l14 7.5v19L20 37 6 29.5v-19Z" stroke="currentColor" strokeWidth="1.6" />
        <path d="m12 14 8-4.2 8 4.2v11.5l-8 4.2-8-4.2V14Z" stroke="currentColor" strokeWidth="1.6" opacity=".55" />
        <path d="m15.5 18 4.5-2.4 4.5 2.4v6L20 26.4 15.5 24v-6Z" fill="currentColor" />
      </svg>
    </div>
  )
}

function LoadingScreen() {
  return (
    <motion.div
      className="loading-screen"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0 }}
    >
      <div className="loading-lockup">
        <Mark />
        <div>
          <div className="eyebrow">PJM / SERVER CONTROL</div>
          <strong>Инициализация канала</strong>
        </div>
      </div>
      <div className="loading-track"><motion.span animate={{ x: ['-100%', '320%'] }} transition={{ duration: 1.3, repeat: Infinity, ease: 'linear' }} /></div>
      <span className="mono muted loading-note">SECURE HANDSHAKE IN PROGRESS</span>
    </motion.div>
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
      setError('Код недействителен или время доступа истекло')
      setShake(s => s + 1)
    }
  }

  return (
    <motion.div className="login-screen" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
      <section className="login-brief">
        <div className="login-brand">
          <Mark />
          <div>
            <strong>PJM</strong>
            <span>BASEMOD CONTROL</span>
          </div>
        </div>

        <div className="login-copy">
          <div className="eyebrow">Закрытый контур управления</div>
          <h1>Сервер под<br />полным контролем.</h1>
          <p>Живая телеметрия, модерация состава и диагностика мира в едином оперативном интерфейсе.</p>
        </div>

        <div className="login-capabilities">
          <span><b>01</b> Телеметрия 1 Гц</span>
          <span><b>02</b> Защищённая сессия</span>
          <span><b>03</b> Действия в реальном времени</span>
        </div>
        <div className="login-grid-code mono">NODE / PJM-01&nbsp;&nbsp;&nbsp; ACCESS / OPERATOR</div>
      </section>

      <section className="login-access">
        <motion.div
          key={shake}
          className="login-card"
          initial={{ opacity: 0, y: 24 }}
          animate={{
            opacity: 1,
            y: 0,
            x: shake > 0 ? [0, -9, 9, -5, 5, 0] : 0,
          }}
          transition={{ duration: shake > 0 ? 0.38 : 0.6, ease: EASE }}
        >
          <div className="login-card-index mono">AUTH / 01</div>
          <div className="eyebrow">Авторизация оператора</div>
          <h2>Введите код доступа</h2>
          <p>Получите одноразовый код в игровом чате командой:</p>
          <code>/pjm web login</code>

          <label className="field-label" htmlFor="access-code">Код из восьми символов</label>
          <input
            id="access-code"
            className="input code-input mono"
            maxLength={8}
            value={code}
            placeholder="••••••••"
            onChange={e => { setCode(e.target.value.toUpperCase()); setError('') }}
            onKeyDown={e => e.key === 'Enter' && submit()}
            autoFocus
            autoComplete="one-time-code"
          />

          <div className="login-message" aria-live="polite">
            {error && <motion.span initial={{ opacity: 0 }} animate={{ opacity: 1 }}>{error}</motion.span>}
          </div>

          <button className="btn btn-accent login-submit" disabled={code.trim().length < 8} onClick={submit}>
            Установить соединение
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 5 7 7-7 7" /></svg>
          </button>

          <div className="login-security">
            <span className="security-dot" />
            Код одноразовый и действует пять минут
          </div>
        </motion.div>
      </section>
    </motion.div>
  )
}

function Shell({ overview }: { overview: Overview }) {
  const [tab, setTab] = useState<Tab>('dashboard')
  const live = useLive(overview.history, overview.profilerActive, overview.entityCounts)
  const activeTab = TABS.find(item => item.id === tab) ?? TABS[0]

  const logout = () =>
    api.post('/api/auth/logout').then(() => location.reload(), () => location.reload())

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <Mark compact />
          <div className="sidebar-brand-copy">
            <strong>PJM</strong>
            <span>SERVER CONTROL</span>
          </div>
        </div>

        <div className="sidebar-section-label">Навигация</div>
        <nav className="sidebar-nav" aria-label="Основная навигация">
          {TABS.map(item => (
            <NavButton key={item.id} tab={item} active={tab === item.id} onClick={() => setTab(item.id)} />
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="sidebar-node">
            <span className={`pulse-dot ${live.connected ? 'on' : ''}`} />
            <div>
              <strong>{live.connected ? 'Канал активен' : 'Нет соединения'}</strong>
              <span className="mono">PJM-NODE / 01</span>
            </div>
          </div>
          <button className="logout-button" onClick={logout}>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M10 5H4v14h6M14 8l4 4-4 4m4-4H8" /></svg>
            Завершить сессию
          </button>
        </div>
      </aside>

      <div className="workspace">
        <header className="topbar">
          <div className="mobile-brand"><Mark compact /></div>
          <div className="page-heading">
            <div className="eyebrow">{activeTab.kicker}</div>
            <h1>{activeTab.title}</h1>
          </div>
          <div className="topbar-actions">
            <div className={`connection-badge ${live.connected ? 'is-online' : ''}`}>
              <span className="pulse-dot" />
              <span>{live.connected ? 'LIVE' : 'OFFLINE'}</span>
            </div>
            <button className="mobile-logout btn btn-icon" onClick={logout} aria-label="Выйти">
              <svg viewBox="0 0 24 24"><path d="M10 5H4v14h6M14 8l4 4-4 4m4-4H8" /></svg>
            </button>
          </div>
        </header>

        <div className="content-scroll">
          <AnimatePresence mode="wait">
            <motion.main
              key={tab}
              className="page-content"
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.28, ease: EASE }}
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
    </div>
  )
}

function NavButton({ tab, active, onClick }: {
  tab: TabDefinition
  active: boolean
  onClick: () => void
}) {
  return (
    <button className={`nav-button ${active ? 'active' : ''}`} onClick={onClick} aria-current={active ? 'page' : undefined}>
      <span className="nav-index mono">{tab.kicker.slice(0, 2)}</span>
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d={tab.icon} />
      </svg>
      <span className="nav-label">{tab.label}</span>
      <span className="nav-arrow">↗</span>
    </button>
  )
}
