import { useMemo } from 'react'
import { formatBytes } from '../api'
import type { LiveState } from '../useLive'
import Chart from '../components/Chart'
import StatCard from '../components/StatCard'

export default function Dashboard({ live }: { live: LiveState }) {
  const { sample, history, entityCounts } = live

  const timestamps = useMemo(() => history.map(s => s.t), [history])
  const tpsSeries = useMemo(() => [
    { label: 'TPS', color: '#57d98a', values: history.map(s => s.tps) },
  ], [history])
  const msptSeries = useMemo(() => [
    { label: 'MSPT', color: '#e0b452', values: history.map(s => s.mspt) },
  ], [history])
  const heapSeries = useMemo(() => [
    { label: 'Heap, МБ', color: '#6ea8e0', values: history.map(s => Math.round(s.heapUsed / 1048576)) },
  ], [history])
  const onlineSeries = useMemo(() => [
    { label: 'Онлайн', color: '#c48ae0', values: history.map(s => s.online) },
  ], [history])

  const totalEntities = Object.values(entityCounts).reduce((a, b) => a + b, 0)
  const heapPct = sample ? Math.round((sample.heapUsed / sample.heapMax) * 100) : 0

  return (
    <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap' }}>
        <StatCard label="TPS" value={sample ? sample.tps.toFixed(1) : '—'}
          accent={!sample || sample.tps >= 18 ? 'ok' : sample.tps >= 14 ? 'warn' : 'danger'} />
        <StatCard label="MSPT" value={sample ? `${sample.mspt.toFixed(1)} мс` : '—'}
          accent={!sample || sample.mspt <= 45 ? 'ok' : sample.mspt <= 50 ? 'warn' : 'danger'} />
        <StatCard label="Память" value={sample ? `${heapPct}%` : '—'}
          sub={sample ? `${formatBytes(sample.heapUsed)} из ${formatBytes(sample.heapMax)}` : undefined}
          accent={heapPct <= 75 ? 'ok' : heapPct <= 90 ? 'warn' : 'danger'} />
        <StatCard label="Онлайн" value={sample ? String(sample.online) : '—'} />
        <StatCard label="Entity" value={String(totalEntities)}
          sub={Object.entries(entityCounts).map(([k, v]) => `${k}: ${v}`).join(' · ') || undefined} />
      </div>

      <div className="panel"><h3 style={{ marginTop: 0 }}>TPS</h3>
        <Chart timestamps={timestamps} series={tpsSeries} /></div>
      <div className="panel"><h3 style={{ marginTop: 0 }}>MSPT</h3>
        <Chart timestamps={timestamps} series={msptSeries} unit=" мс" /></div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
        <div className="panel"><h3 style={{ marginTop: 0 }}>Память</h3>
          <Chart timestamps={timestamps} series={heapSeries} height={180} unit=" МБ" /></div>
        <div className="panel"><h3 style={{ marginTop: 0 }}>Онлайн</h3>
          <Chart timestamps={timestamps} series={onlineSeries} height={180} /></div>
      </div>

      {sample && sample.dims.length > 0 && (
        <div className="panel">
          <h3 style={{ marginTop: 0 }}>Дименшены</h3>
          <table className="table">
            <thead><tr><th>Дименшен</th><th>MSPT</th><th>Entity</th><th>Чанки</th></tr></thead>
            <tbody>
              {sample.dims.map(d => (
                <tr key={d.dim} style={{ cursor: 'default' }}>
                  <td className="mono">{d.dim}</td>
                  <td className="mono">{d.mspt.toFixed(2)} мс</td>
                  <td className="mono">{d.entities}</td>
                  <td className="mono">{d.chunks}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
