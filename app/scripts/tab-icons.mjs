// 生成底部页签图标（81x81 PNG），纯 Node，无外部依赖。用法：node scripts/tab-icons.mjs src/static/tab
import { deflateSync } from 'node:zlib'
import { writeFileSync, mkdirSync } from 'node:fs'
import { join } from 'node:path'

const N = 81, SS = 4
const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0 })
const crc = (buf) => { let c = 0xffffffff; for (const b of buf) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0 }
function chunk(type, data) { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([len, td, c]) }
function png(rgba) {
  const raw = Buffer.alloc((N * 4 + 1) * N)
  for (let y = 0; y < N; y++) { raw[y * (N * 4 + 1)] = 0; rgba.copy(raw, y * (N * 4 + 1) + 1, y * N * 4, (y + 1) * N * 4) }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(N, 0); ihdr.writeUInt32BE(N, 4); ihdr[8] = 8; ihdr[9] = 6
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))])
}
const segDist = (px, py, ax, ay, bx, by) => { const dx = bx - ax, dy = by - ay; const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy))); return Math.hypot(px - ax - t * dx, py - ay - t * dy) }
const W = 6 // 线宽
const line = (ax, ay, bx, by, w = W) => (x, y) => segDist(x, y, ax, ay, bx, by) <= w / 2
const poly = (pts, w = W) => { const segs = pts.map((p, i) => line(p[0], p[1], pts[(i + 1) % pts.length][0], pts[(i + 1) % pts.length][1], w)); return (x, y) => segs.some((s) => s(x, y)) }
const ring = (cx, cy, r, w = W) => (x, y) => Math.abs(Math.hypot(x - cx, y - cy) - r) <= w / 2
const arc = (cx, cy, r, from, to, w = W) => (x, y) => { const a = Math.atan2(y - cy, x - cx); return a >= from && a <= to && Math.abs(Math.hypot(x - cx, y - cy) - r) <= w / 2 }
const rect = (x0, y0, x1, y1) => (x, y) => x >= x0 && x <= x1 && y >= y0 && y <= y1
const any = (...fs) => (x, y) => fs.some((f) => f(x, y))

const icons = {
  home: any(poly([[14, 38], [40.5, 14], [67, 38]]), line(22, 34, 22, 66), line(59, 34, 59, 66), line(22, 66, 59, 66), line(34, 66, 34, 48), line(47, 66, 47, 48), line(34, 48, 47, 48)),
  exam: any(poly([[20, 12], [61, 12], [61, 69], [20, 69]]), line(30, 28, 51, 28), line(30, 40, 51, 40), line(30, 52, 43, 52)),
  stats: any(line(14, 68, 67, 68), rect(19, 42, 28, 64), rect(36, 26, 45, 64), rect(53, 34, 62, 64)),
  me: any(ring(40.5, 28, 12), arc(40.5, 72, 25, -Math.PI, 0)),
}
const colors = { normal: [0x8a, 0x91, 0x9e], active: [0x3a, 0x6c, 0xf4] }
const out = process.argv[2]; mkdirSync(out, { recursive: true })
for (const [name, shape] of Object.entries(icons)) {
  for (const [variant, [r, g, b]] of Object.entries(colors)) {
    const buf = Buffer.alloc(N * N * 4)
    for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
      let hit = 0
      for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) if (shape(x + (sx + 0.5) / SS, y + (sy + 0.5) / SS)) hit++
      const i = (y * N + x) * 4; buf[i] = r; buf[i + 1] = g; buf[i + 2] = b; buf[i + 3] = Math.round((255 * hit) / (SS * SS))
    }
    writeFileSync(join(out, variant === 'normal' ? `${name}.png` : `${name}-active.png`), png(buf))
  }
}
console.log('done', Object.keys(icons).length * 2, 'icons')
