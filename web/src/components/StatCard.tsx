import { motion } from 'framer-motion'

interface Props {
  label: string
  value: string
  accent?: 'ok' | 'warn' | 'danger'
  sub?: string
}

const COLORS = { ok: 'var(--accent)', warn: 'var(--warn)', danger: 'var(--danger)' }

export default function StatCard({ label, value, accent = 'ok', sub }: Props) {
  return (
    <div className="panel" style={{ flex: 1, minWidth: 150 }}>
      <div className="muted" style={{ fontSize: 12, textTransform: 'uppercase', letterSpacing: '0.06em' }}>
        {label}
      </div>
      <motion.div key={value} className="mono"
        initial={{ opacity: 0.4, y: 3 }} animate={{ opacity: 1, y: 0 }}
        style={{ fontSize: 26, fontWeight: 600, color: COLORS[accent], marginTop: 4 }}>
        {value}
      </motion.div>
      {sub && <div className="muted" style={{ fontSize: 12, marginTop: 2 }}>{sub}</div>}
    </div>
  )
}
