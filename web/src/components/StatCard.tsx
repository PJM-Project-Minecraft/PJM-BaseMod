import { useId } from 'react'
import { motion } from 'framer-motion'

interface Props {
  index?: string
  label: string
  value: string
  accent?: 'accent' | 'ok' | 'warn' | 'danger' | 'neutral'
  sub?: string
  spark?: number[]
}

const COLORS: Record<NonNullable<Props['accent']>, string> = {
  accent: 'var(--accent)',
  ok: 'var(--ok)',
  warn: 'var(--warn)',
  danger: 'var(--danger)',
  neutral: 'var(--text)',
}

export default function StatCard({ index = '—', label, value, accent = 'neutral', sub, spark }: Props) {
  const color = COLORS[accent]
  return (
    <div className={`stat-card panel stat-${accent}`}>
      <div className="stat-topline">
        <span className="mono">{index}</span>
        <i style={{ background: color }} />
      </div>
      <div className="stat-label">{label}</div>
      <motion.div
        key={value}
        className="stat-value mono tnum"
        initial={{ opacity: .45, y: 4 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: .3, ease: [0.22, 1, 0.36, 1] }}
        style={{ color }}
      >
        {value}
      </motion.div>
      <div className="stat-footer">
        <span>{sub || 'текущий срез'}</span>
        {spark && spark.length > 1 && <Sparkline data={spark} color={color} />}
      </div>
    </div>
  )
}

function Sparkline({ data, color }: { data: number[]; color: string }) {
  const uid = useId().replace(/:/g, '')
  const w = 110
  const h = 28
  const min = Math.min(...data)
  const max = Math.max(...data)
  const range = max - min || 1
  const pts = data.map((value, index) =>
    `${(index / (data.length - 1)) * w},${h - ((value - min) / range) * h}`,
  ).join(' ')

  return (
    <svg className="stat-sparkline" viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" aria-hidden="true">
      <defs>
        <linearGradient id={uid} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity=".25" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <polyline points={`0,${h} ${pts} ${w},${h}`} fill={`url(#${uid})`} stroke="none" />
      <polyline points={pts} fill="none" stroke={color} strokeWidth="1.4" vectorEffect="non-scaling-stroke" />
    </svg>
  )
}
