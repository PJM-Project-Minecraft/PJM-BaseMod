import { useEffect, useRef, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'

interface Props {
  label: string
  confirmLabel?: string
  danger?: boolean
  disabled?: boolean
  onConfirm: () => void
}

const EASE = [0.4, 0, 0.2, 1] as const

/** Двухшаговая кнопка: первый клик «взводит», второй (в течение 3с) выполняет. */
export default function ConfirmButton({ label, confirmLabel = 'Точно?', danger, disabled, onConfirm }: Props) {
  const [armed, setArmed] = useState(false)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => () => { if (timerRef.current) clearTimeout(timerRef.current) }, [])

  const click = () => {
    if (!armed) {
      setArmed(true)
      timerRef.current = setTimeout(() => setArmed(false), 3000)
      return
    }
    if (timerRef.current) clearTimeout(timerRef.current)
    setArmed(false)
    onConfirm()
  }

  return (
    <motion.button
      className={`btn ${danger ? 'btn-danger' : ''} ${armed ? 'armed' : ''}`}
      disabled={disabled}
      onClick={click}
      animate={armed ? { boxShadow: `0 0 0 3px var(--danger-dim)` } : { boxShadow: '0 0 0 0 transparent' }}
      transition={{ duration: 0.18, ease: EASE }}
      layout
    >
      <AnimatePresence mode="wait" initial={false}>
        <motion.span
          key={armed ? 'armed' : 'idle'}
          initial={{ opacity: 0, y: 4 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -4 }}
          transition={{ duration: 0.15, ease: EASE }}
        >
          {armed ? confirmLabel : label}
        </motion.span>
      </AnimatePresence>
    </motion.button>
  )
}
