import { useMemo } from 'react'
import { motion } from 'framer-motion'
import { formatBytes } from '../api'
import type { LiveState } from '../useLive'
import Chart from '../components/Chart'
import StatCard from '../components/StatCard'

const EASE = [0.4, 0, 0.2, 1] as const

type Accent = 'accent' | 'ok' | 'warn' | 'danger' | 'neutral'

export default function Dashboard({ live }: { live: LiveState }) {
  const { sample, history, entityCounts } = live

  const timestamps = useMemo(() => history.map(s => s.t), [history])
  const tpsSeries = useMemo(() => [
    { label: 'TPS', color: '#0A84FF', values: history.map(s => s.tps) },
  ], [history])
  const msptSeries = useMemo(() => [
    { label: 'MSPT', color: '#FF9F0A', values: history.map(s => s.mspt) },
  ], [history])
  const heapSeries = useMemo(() => [
    { label: 'Heap, МБ', color: '#BF5AF2', values: history.map(s => Math.round(s.heapUsed / 1048576)) },
  ], [history])
  const onlineSeries = useMemo(() => [
    { label: 'Онлайн', color: '#30D158', values: history.map(s => s.online) },
  ], [history])

  const totalEntities = Object.values(entityCounts).reduce((a, b) => a + b, 0)
  const heapPct = sample ? Math.round((sample.heapUsed / sample.heapMax) * 100) : 0

  const cards: { label: string; value: string; accent: Accent; sub?: string; spark?: number[] }[] = [
    {
      label: 'TPS', value: sample ? sample.tps.toFixed(1) : '—',
      accent: (!sample || sample.tps >= 18 ? 'ok' : sample.tps >= 14 ? 'warn' : 'danger'),
      spark: history.map(s => s.tps),
    },
    {
      label: 'MSPT', value: sample ? `${sample.mspt.toFixed(1)} мс` : '—',
      accent: (!sample || sample.mspt <= 45 ? 'ok' : sample.mspt <= 50 ? 'warn' : 'danger'),
      spark: history.map(s => s.mspt),
    },
    {
      label: 'Память', value: sample ? `${heapPct}%` : '—',
      sub: sample ? `${formatBytes(sample.heapUsed)} из ${formatBytes(sample.heapMax)}` : undefined,
      accent: (heapPct <= 75 ? 'ok' : heapPct <= 90 ? 'warn' : 'danger'),
      spark: history.map(s => Math.round((s.heapUsed / s.heapMax) * 100)),
    },
    {
      label: 'Онлайн', value: sample ? String(sample.online) : '—',
      accent: 'accent',
      spark: history.map(s => s.online),
    },
    {
      label: 'Entity', value: String(totalEntities),
      sub: Object.entries(entityCounts).map(([k, v]) => `${k}: ${v}`).join(' · ') || undefined,
      accent: 'neutral',
    },
  ]

  return (
    <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap' }}>
        {cards.map((c, i) => (
          <motion.div key={c.label}
            initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.04, duration: 0.4, ease: EASE }}
            style={{ flex: 1, minWidth: 160 }}
          >
            <StatCard {...c} />
          </motion.div>
        ))}
      </div>

      <ChartCard title="TPS">
        <Chart timestamps={timestamps} series={tpsSeries} />
      </ChartCard>
      <ChartCard title="MSPT">
        <Chart timestamps={timestamps} series={msptSeries} unit=" мс" />
      </ChartCard>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
        <ChartCard title="Память">
          <Chart timestamps={timestamps} series={heapSeries} height={180} unit=" МБ" />
        </ChartCard>
        <ChartCard title="Онлайн">
          <Chart timestamps={timestamps} series={onlineSeries} height={180} />
        </ChartCard>
      </div>

      {sample && sample.dims.length > 0 && (
        <div className="panel">
          <h3 style={{ marginBottom: 14 }}>Дименшены</h3>
          <table className="table">
            <thead><tr><th>Дименшен</th><th>MSPT</th><th>Entity</th><th>Чанки</th></tr></thead>
            <tbody>
              {sample.dims.map(d => (
                <tr key={d.dim} style={{ cursor: 'default' }}>
                  <td className="mono">{d.dim}</td>
                  <td className="mono tnum">{d.mspt.toFixed(2)} мс</td>
                  <td className="mono tnum">{d.entities}</td>
                  <td className="mono tnum">{d.chunks}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function ChartCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="panel">
      <h3 style={{ marginBottom: 14 }}>{title}</h3>
      {children}
    </div>
  )
}
