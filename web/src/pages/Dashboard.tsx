import { useMemo } from 'react'
import { motion } from 'framer-motion'
import { formatBytes } from '../api'
import type { LiveState } from '../useLive'
import Chart from '../components/Chart'
import StatCard from '../components/StatCard'

const EASE = [0.22, 1, 0.36, 1] as const
type Accent = 'accent' | 'ok' | 'warn' | 'danger' | 'neutral'

export default function Dashboard({ live }: { live: LiveState }) {
  const { sample, history, entityCounts } = live

  const timestamps = useMemo(() => history.map(s => s.t), [history])
  const tpsSeries = useMemo(() => [
    { label: 'TPS', color: '#c7f36b', values: history.map(s => s.tps) },
  ], [history])
  const msptSeries = useMemo(() => [
    { label: 'MSPT', color: '#f0b35b', values: history.map(s => s.mspt) },
  ], [history])
  const heapSeries = useMemo(() => [
    { label: 'Heap, МБ', color: '#b7a0ff', values: history.map(s => Math.round(s.heapUsed / 1048576)) },
  ], [history])
  const onlineSeries = useMemo(() => [
    { label: 'Онлайн', color: '#78d99a', values: history.map(s => s.online) },
  ], [history])

  const totalEntities = Object.values(entityCounts).reduce((a, b) => a + b, 0)
  const heapPct = sample ? Math.round((sample.heapUsed / sample.heapMax) * 100) : 0
  const condition = !sample
    ? { label: 'Ожидание телеметрии', tone: 'neutral', note: 'Канал данных ещё не сформировал первый срез.' }
    : sample.tps < 14 || sample.mspt > 50
      ? { label: 'Критическая нагрузка', tone: 'danger', note: 'Требуется проверить тяжёлые entity и горячие чанки.' }
      : sample.tps < 18 || sample.mspt > 45 || heapPct > 85
        ? { label: 'Повышенная нагрузка', tone: 'warn', note: 'Показатели вышли из оптимального диапазона.' }
        : { label: 'Система в норме', tone: 'ok', note: 'Все ключевые показатели находятся в рабочем диапазоне.' }

  const cards: { label: string; value: string; accent: Accent; sub?: string; spark?: number[]; index: string }[] = [
    {
      index: '01', label: 'Стабильность / TPS', value: sample ? sample.tps.toFixed(1) : '—',
      accent: (!sample || sample.tps >= 18 ? 'ok' : sample.tps >= 14 ? 'warn' : 'danger'),
      sub: 'цель: 20.0',
      spark: history.map(s => s.tps),
    },
    {
      index: '02', label: 'Время тика / MSPT', value: sample ? `${sample.mspt.toFixed(1)}` : '—',
      accent: (!sample || sample.mspt <= 45 ? 'ok' : sample.mspt <= 50 ? 'warn' : 'danger'),
      sub: 'мс · лимит 50',
      spark: history.map(s => s.mspt),
    },
    {
      index: '03', label: 'Память / HEAP', value: sample ? `${heapPct}%` : '—',
      sub: sample ? `${formatBytes(sample.heapUsed)} / ${formatBytes(sample.heapMax)}` : 'нет данных',
      accent: (heapPct <= 75 ? 'ok' : heapPct <= 90 ? 'warn' : 'danger'),
      spark: history.map(s => Math.round((s.heapUsed / s.heapMax) * 100)),
    },
    {
      index: '04', label: 'Личный состав', value: sample ? String(sample.online).padStart(2, '0') : '—',
      accent: 'accent',
      sub: 'игроков онлайн',
      spark: history.map(s => s.online),
    },
  ]

  return (
    <div className="dashboard fade-in">
      <section className={`system-banner tone-${condition.tone}`}>
        <div className="system-banner-copy">
          <div className="eyebrow">Текущий статус</div>
          <h2>{condition.label}</h2>
          <p>{condition.note}</p>
        </div>
        <div className="system-banner-data">
          <div>
            <span>ENTITY</span>
            <strong className="mono">{totalEntities.toLocaleString('ru-RU')}</strong>
          </div>
          <div>
            <span>ДИМЕНШЕНЫ</span>
            <strong className="mono">{sample?.dims.length ?? 0}</strong>
          </div>
          <div>
            <span>КАНАЛ</span>
            <strong className="mono">{live.connected ? 'LIVE' : 'LOST'}</strong>
          </div>
        </div>
      </section>

      <section className="metrics-grid">
        {cards.map((card, i) => (
          <motion.div
            key={card.label}
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * .045, duration: .42, ease: EASE }}
          >
            <StatCard {...card} />
          </motion.div>
        ))}
      </section>

      <section className="charts-grid">
        <ChartCard title="Стабильность сервера" kicker="TPS / последние данные">
          <Chart timestamps={timestamps} series={tpsSeries} height={230} />
        </ChartCard>
        <ChartCard title="Стоимость игрового тика" kicker="MSPT / миллисекунды">
          <Chart timestamps={timestamps} series={msptSeries} height={230} unit=" мс" />
        </ChartCard>
        <ChartCard title="Потребление памяти" kicker="JVM HEAP / мегабайты">
          <Chart timestamps={timestamps} series={heapSeries} height={190} unit=" МБ" />
        </ChartCard>
        <ChartCard title="Динамика онлайна" kicker="PLAYERS / подключено">
          <Chart timestamps={timestamps} series={onlineSeries} height={190} />
        </ChartCard>
      </section>

      {sample && sample.dims.length > 0 && (
        <section className="panel dimensions-panel">
          <div className="panel-header">
            <div>
              <span className="panel-kicker">Миры сервера / живой срез</span>
              <h3>Состояние дименшенов</h3>
            </div>
            <span className="chip chip-accent">{sample.dims.length} активно</span>
          </div>
          <div className="table-shell">
            <table className="table">
              <thead><tr><th>Дименшен</th><th>Нагрузка MSPT</th><th>Entity</th><th>Загружено чанков</th><th>Состояние</th></tr></thead>
              <tbody>
                {sample.dims.map(d => (
                  <tr key={d.dim} style={{ cursor: 'default' }}>
                    <td className="mono">{d.dim}</td>
                    <td className="mono tnum">{d.mspt.toFixed(2)} мс</td>
                    <td className="mono tnum">{d.entities.toLocaleString('ru-RU')}</td>
                    <td className="mono tnum">{d.chunks.toLocaleString('ru-RU')}</td>
                    <td><span className={`chip ${d.mspt > 45 ? 'chip-warn' : 'chip-ok'}`}>{d.mspt > 45 ? 'нагрузка' : 'норма'}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  )
}

function ChartCard({ title, kicker, children }: { title: string; kicker: string; children: React.ReactNode }) {
  return (
    <div className="panel chart-card">
      <div className="chart-card-head">
        <div>
          <span>{kicker}</span>
          <h3>{title}</h3>
        </div>
        <b className="chart-live mono">LIVE</b>
      </div>
      {children}
    </div>
  )
}
