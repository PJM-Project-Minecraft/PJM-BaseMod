import { useEffect, useRef, useState } from 'react'

interface Props {
  label: string
  confirmLabel?: string
  danger?: boolean
  disabled?: boolean
  onConfirm: () => void
}

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
    <button className={`btn ${danger ? 'btn-danger' : ''}`} disabled={disabled} onClick={click}>
      {armed ? confirmLabel : label}
    </button>
  )
}
