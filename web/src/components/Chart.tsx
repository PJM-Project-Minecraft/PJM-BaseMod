import { useEffect, useRef } from 'react'
import uPlot from 'uplot'
import 'uplot/dist/uPlot.min.css'

export interface ChartSeries {
  label: string
  color: string
  values: (number | null)[]
}

interface Props {
  timestamps: number[] // epoch мс
  series: ChartSeries[]
  height?: number
  unit?: string
}

/** Обёртка uPlot: пересоздание при смене состава серий, setData при обновлении данных. */
export default function Chart({ timestamps, series, height = 220, unit = '' }: Props) {
  const rootRef = useRef<HTMLDivElement>(null)
  const plotRef = useRef<uPlot | null>(null)
  const seriesKey = series.map(s => s.label).join('|')

  useEffect(() => {
    const root = rootRef.current
    if (!root) return
    const opts: uPlot.Options = {
      width: root.clientWidth,
      height,
      series: [
        {},
        ...series.map(s => ({
          label: s.label,
          stroke: s.color,
          width: 1.5,
          fill: `${s.color}22`,
          value: (_u: uPlot, v: number | null) => (v == null ? '—' : `${v}${unit}`),
        })),
      ],
      axes: [
        { stroke: '#7d8fa0', grid: { stroke: '#1a2430' }, ticks: { stroke: '#1a2430' } },
        { stroke: '#7d8fa0', grid: { stroke: '#1a2430' }, ticks: { stroke: '#1a2430' } },
      ],
      cursor: { points: { size: 6 } },
      legend: { live: true },
    }
    const plot = new uPlot(opts, [[], ...series.map(() => [])], root)
    plotRef.current = plot

    const observer = new ResizeObserver(() => {
      plot.setSize({ width: root.clientWidth, height })
    })
    observer.observe(root)
    return () => {
      observer.disconnect()
      plot.destroy()
      plotRef.current = null
    }
    // Пересоздаём график только при смене состава серий.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [seriesKey, height])

  useEffect(() => {
    plotRef.current?.setData([
      timestamps.map(t => t / 1000),
      ...series.map(s => s.values),
    ] as uPlot.AlignedData)
  }, [timestamps, series])

  return <div ref={rootRef} />
}
