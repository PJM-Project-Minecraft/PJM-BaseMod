import { useCallback, useEffect, useRef, useState } from 'react'
import { api, hashColor, type EntityDto } from '../api'
import type { LiveState } from '../useLive'

const CATEGORY_COLORS: Record<string, string> = {
  mob: '#e0b452', item: '#6ea8e0', projectile: '#c48ae0', other: '#7d8fa0',
}

interface View { centerX: number; centerZ: number; scale: number }

export default function MapView({ live }: { live: LiveState }) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [dim, setDim] = useState('minecraft:overworld')
  const [entities, setEntities] = useState<EntityDto[]>([])
  const [showEntities, setShowEntities] = useState(true)
  const viewRef = useRef<View>({ centerX: 0, centerZ: 0, scale: 1 })
  const dragRef = useRef<{ x: number; z: number } | null>(null)
  const [tooltip, setTooltip] = useState<{ x: number; y: number; text: string } | null>(null)

  const dims = live.sample?.dims.map(d => d.dim) ?? ['minecraft:overworld']

  useEffect(() => {
    const load = () =>
      api.get<{ entities: EntityDto[] }>(`/api/entities?dim=${dim}&limit=3000`)
        .then(r => setEntities(r.entities), () => undefined)
    load()
    const timer = setInterval(load, 4000)
    return () => clearInterval(timer)
  }, [dim])

  const draw = useCallback(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const { width, height } = canvas
    const view = viewRef.current
    const toScreen = (wx: number, wz: number) => ({
      x: width / 2 + (wx - view.centerX) * view.scale,
      y: height / 2 + (wz - view.centerZ) * view.scale,
    })

    ctx.fillStyle = '#0d1319'
    ctx.fillRect(0, 0, width, height)

    // Сетка по 16 блоков (чанки) — только при достаточном зуме.
    if (view.scale >= 1.5) {
      ctx.strokeStyle = '#16202b'
      ctx.lineWidth = 1
      const step = 16 * view.scale
      const offX = (width / 2 - view.centerX * view.scale) % step
      const offY = (height / 2 - view.centerZ * view.scale) % step
      for (let x = offX; x < width; x += step) {
        ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, height); ctx.stroke()
      }
      for (let y = offY; y < height; y += step) {
        ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(width, y); ctx.stroke()
      }
    }

    // Горячие чанки профайлера.
    const report = live.profiler
    if (report) {
      for (const chunk of report.hotChunks.slice(0, 40)) {
        if (chunk.dim !== dim) continue
        const p = toScreen(chunk.chunkX * 16, chunk.chunkZ * 16)
        const size = 16 * view.scale
        ctx.fillStyle = '#e0525233'
        ctx.fillRect(p.x, p.y, size, size)
      }
    }

    // Entity.
    if (showEntities) {
      for (const e of entities) {
        const p = toScreen(e.x, e.z)
        if (p.x < -5 || p.y < -5 || p.x > width + 5 || p.y > height + 5) continue
        ctx.fillStyle = CATEGORY_COLORS[e.category] ?? CATEGORY_COLORS.other
        ctx.beginPath()
        ctx.arc(p.x, p.y, 2.5, 0, Math.PI * 2)
        ctx.fill()
      }
    }

    // Игроки — поверх, крупнее, с ником.
    for (const player of live.players) {
      if (player.dim !== dim) continue
      const p = toScreen(player.x, player.z)
      ctx.fillStyle = player.team ? hashColor(player.team) : '#57d98a'
      ctx.beginPath()
      ctx.arc(p.x, p.y, 5, 0, Math.PI * 2)
      ctx.fill()
      ctx.strokeStyle = '#0b0f0d'
      ctx.lineWidth = 1.5
      ctx.stroke()
      ctx.fillStyle = '#d7e2ec'
      ctx.font = '11px monospace'
      ctx.fillText(player.name, p.x + 8, p.y + 4)
    }
  }, [entities, live.players, live.profiler, dim, showEntities])

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const resize = () => {
      canvas.width = canvas.clientWidth
      canvas.height = 560
      draw()
    }
    resize()
    const observer = new ResizeObserver(resize)
    observer.observe(canvas)
    return () => observer.disconnect()
  }, [draw])

  useEffect(() => { draw() }, [draw])

  const onWheel = (e: React.WheelEvent) => {
    const view = viewRef.current
    const factor = e.deltaY < 0 ? 1.2 : 1 / 1.2
    view.scale = Math.min(8, Math.max(0.05, view.scale * factor))
    draw()
  }
  const onMouseDown = (e: React.MouseEvent) => { dragRef.current = { x: e.clientX, z: e.clientY } }
  const onMouseUp = () => { dragRef.current = null }
  const onMouseMove = (e: React.MouseEvent) => {
    const canvas = canvasRef.current
    if (!canvas) return
    const view = viewRef.current
    if (dragRef.current) {
      view.centerX -= (e.clientX - dragRef.current.x) / view.scale
      view.centerZ -= (e.clientY - dragRef.current.z) / view.scale
      dragRef.current = { x: e.clientX, z: e.clientY }
      draw()
      return
    }
    // Тултип: ближайший игрок/entity в радиусе 10px.
    const rect = canvas.getBoundingClientRect()
    const mx = e.clientX - rect.left
    const my = e.clientY - rect.top
    const wx = view.centerX + (mx - canvas.width / 2) / view.scale
    const wz = view.centerZ + (my - canvas.height / 2) / view.scale
    const maxDist = 10 / view.scale
    let best: { text: string; dist: number } | null = null
    for (const p of live.players) {
      if (p.dim !== dim) continue
      const d = Math.hypot(p.x - wx, p.z - wz)
      if (d < maxDist && (!best || d < best.dist)) best = { text: `${p.name} (${p.x} ${p.y} ${p.z})`, dist: d }
    }
    if (!best && showEntities) {
      for (const en of entities) {
        const d = Math.hypot(en.x - wx, en.z - wz)
        if (d < maxDist && (!best || d < best.dist)) best = { text: `${en.type} (${en.x} ${en.y} ${en.z})`, dist: d }
      }
    }
    setTooltip(best ? { x: mx + 12, y: my + 12, text: best.text } : null)
  }

  const centerOnPlayers = () => {
    const inDim = live.players.filter(p => p.dim === dim)
    if (inDim.length === 0) return
    const view = viewRef.current
    view.centerX = inDim.reduce((a, p) => a + p.x, 0) / inDim.length
    view.centerZ = inDim.reduce((a, p) => a + p.z, 0) / inDim.length
    draw()
  }

  return (
    <div className="panel fade-in">
      <div style={{ display: 'flex', gap: 10, marginBottom: 10, alignItems: 'center' }}>
        <select className="input" value={dim} onChange={e => setDim(e.target.value)}>
          {dims.map(d => <option key={d} value={d}>{d}</option>)}
        </select>
        <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <input type="checkbox" checked={showEntities} onChange={e => setShowEntities(e.target.checked)} />
          entity
        </label>
        <button className="btn" onClick={centerOnPlayers}>К игрокам</button>
        <span className="muted">колесо — зум, перетаскивание — панорама; красное — горячие чанки профайлера</span>
      </div>
      <div style={{ position: 'relative' }}>
        <canvas ref={canvasRef} style={{ width: '100%', height: 560, borderRadius: 8, cursor: 'crosshair' }}
          onWheel={onWheel} onMouseDown={onMouseDown} onMouseUp={onMouseUp}
          onMouseLeave={onMouseUp} onMouseMove={onMouseMove} />
        {tooltip && (
          <div className="panel mono" style={{
            position: 'absolute', left: tooltip.x, top: tooltip.y,
            padding: '4px 8px', fontSize: 12, pointerEvents: 'none',
          }}>{tooltip.text}</div>
        )}
      </div>
    </div>
  )
}
