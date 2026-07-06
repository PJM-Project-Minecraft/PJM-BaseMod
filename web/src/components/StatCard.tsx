import { motion } from 'framer-motion'

interface Props {
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

export default function StatCard({ label, value, accent = 'neutral', sub, spark }: Props) {
  const color = COLORS[accent]
  return (
    <div className="panel" style={{ flex: 1, minWidth: 160, padding: 16, position: 'relative', overflow: 'hidden' }}>
      {/* цветовая полоска-акцент сверху */}
      <div style={{
        position: 'absolute', top: 0, left: 0, right: 0, height: 2,
        background: `linear-gradient(90deg, ${color}, transparent 80%)`,
        opacity: accent === 'neutral' ? 0.3 : 1,
      }} />
      <div className="muted" style={{
        fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.08em',
        fontWeight: 500, marginBottom: 8,
      }}>
        {label}
      </div>
      <motion.div
        key={value}
        className="tnum"
        initial={{ opacity: 0.5, y: 3 }} animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, ease: [0.4, 0, 0.2, 1] }}
        style={{ fontSize: 28, fontWeight: 600, color, lineHeight: 1.1, letterSpacing: '-0.02em' }}
      >
        {value}
      </motion.div>
      {sub && <div className="muted" style={{ fontSize: 11, marginTop: 6, letterSpacing: '0.01em' }}>{sub}</div>}
      {spark && spark.length > 1 && <Sparkline data={spark} color={color} />}
    </div>
  )
}

function Sparkline({ data, color }: { data: number[]; color: string }) {
  const w = 120, h = 28
  const min = Math.min(...data), max = Math.max(...data)
  const range = max - min || 1
  const pts = data.map((v, i) => `${(i / (data.length - 1)) * w},${h - ((v - min) / range) * h}`).join(' ')
  const id = `spark-${color.replace(/[^a-z0-9]/gi, '')}`
  return (
    <svg width={w} height={h} style={{ position: 'absolute', bottom: 10, right: 12, opacity: 0.7 }}>
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.3" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <polyline points={`0,${h} ${pts} ${w},${h}`} fill={`url(#${id})`} stroke="none" />
      <polyline points={pts} fill="none" stroke={color} strokeWidth="1.5" strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  )
}
