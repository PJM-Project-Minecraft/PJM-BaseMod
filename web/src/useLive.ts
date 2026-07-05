import { useEffect, useRef, useState } from 'react'
import type { MetricsSample, PlayerDto, ProfilerReport } from './api'

export interface LiveState {
  connected: boolean
  sample: MetricsSample | null
  players: PlayerDto[]
  entityCounts: Record<string, number>
  profiler: ProfilerReport | null
  profilerActive: boolean
  history: MetricsSample[]
}

interface Frame {
  type: string
  sample?: MetricsSample | null
  players?: PlayerDto[]
  entityCounts?: Record<string, number>
  profiler?: ProfilerReport
  profilerActive?: boolean
}

const HISTORY_CAP = 7200

/** WebSocket /ws/live с авто-reconnect (экспоненциальная задержка до 15с). */
export function useLive(initialHistory: MetricsSample[], initialProfilerActive = false): LiveState {
  const [state, setState] = useState<LiveState>({
    connected: false,
    sample: initialHistory.length ? initialHistory[initialHistory.length - 1] : null,
    players: [],
    entityCounts: {},
    profiler: null,
    profilerActive: initialProfilerActive,
    history: initialHistory,
  })
  const retryRef = useRef(0)

  useEffect(() => {
    let ws: WebSocket | null = null
    let closed = false
    let timer: ReturnType<typeof setTimeout> | null = null

    const connect = () => {
      const proto = location.protocol === 'https:' ? 'wss' : 'ws'
      ws = new WebSocket(`${proto}://${location.host}/ws/live`)
      ws.onopen = () => {
        retryRef.current = 0
        setState(s => ({ ...s, connected: true }))
      }
      ws.onmessage = event => {
        const frame: Frame = JSON.parse(event.data)
        if (frame.type !== 'tick') return
        setState(s => {
          const history = frame.sample != null
            ? [...s.history, frame.sample].slice(-HISTORY_CAP)
            : s.history
          return {
            connected: true,
            sample: frame.sample != null ? frame.sample : s.sample,
            players: frame.players ?? s.players,
            entityCounts: frame.entityCounts ?? s.entityCounts,
            profiler: frame.profiler ?? s.profiler,
            profilerActive: frame.profilerActive ?? s.profilerActive,
            history,
          }
        })
      }
      ws.onclose = () => {
        setState(s => ({ ...s, connected: false }))
        if (closed) return
        const delay = Math.min(15000, 1000 * 2 ** retryRef.current++)
        timer = setTimeout(connect, delay)
      }
      ws.onerror = () => ws?.close()
    }

    connect()
    return () => {
      closed = true
      if (timer) clearTimeout(timer)
      ws?.close()
    }
  }, [])

  return state
}
