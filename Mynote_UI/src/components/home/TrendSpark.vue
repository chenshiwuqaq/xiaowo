<script setup lang="ts">
/**
 * 近 N 天字数趋势：内联 SVG 折线 + 渐变面积，Y 轴按数据最大值自适应。
 * 不依赖任何图表库；悬停用逐点热区（<title> 原生提示 + 自绘 tooltip 与圆点）。
 */
import { computed, getCurrentInstance, ref } from 'vue'
import type { DailyWord } from '@/api/types'

const props = withDefaults(defineProps<{ days: DailyWord[]; label?: string }>(), { label: '近 30 天' })

/** SVG defs 的 id 必须全页唯一（同页可能放多张趋势图），用实例 uid 生成 */
const gid = `mynote-trend-fill-${getCurrentInstance()?.uid ?? 0}`

const W = 640
const H = 180
const PAD = { t: 16, r: 10, b: 26, l: 36 }
const innerW = W - PAD.l - PAD.r
const innerH = H - PAD.t - PAD.b

const sorted = computed(() =>
  [...props.days].sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0)),
)

const yMax = computed(() => {
  const m = Math.max(0, ...sorted.value.map((d) => d.words))
  if (m <= 0) return 100
  // 取整到"好看"的刻度：1 / 2 / 5 × 10^n，并给峰值留 ~15% 的顶部空间
  const scaled = m * 1.15
  const exp = Math.pow(10, Math.floor(Math.log10(scaled)))
  const lead = scaled / exp
  return (lead <= 1 ? 1 : lead <= 2 ? 2 : lead <= 5 ? 5 : 10) * exp
})

const stepX = computed(() => {
  const n = sorted.value.length
  return n <= 1 ? innerW : innerW / (n - 1)
})

function xAt(i: number): number {
  return PAD.l + i * stepX.value
}

function yAt(words: number): number {
  return PAD.t + innerH - (words / yMax.value) * innerH
}

const linePath = computed(() =>
  sorted.value.map((d, i) => `${i === 0 ? 'M' : 'L'}${xAt(i).toFixed(1)} ${yAt(d.words).toFixed(1)}`).join(' '),
)

const areaPath = computed(() => {
  const n = sorted.value.length
  if (!n) return ''
  const base = (PAD.t + innerH).toFixed(1)
  return `${linePath.value} L${xAt(n - 1).toFixed(1)} ${base} L${xAt(0).toFixed(1)} ${base} Z`
})

/** 日期解析："2026-10-04" → [10, 4]；非法返回 null */
function md(iso: string): [number, number] | null {
  const p = iso.split('-')
  const mo = Number(p[1])
  const d = Number(p[2])
  if (!Number.isFinite(mo) || !Number.isFinite(d) || mo <= 0 || d <= 0) return null
  return [mo, d]
}

function pointLabel(d: DailyWord): string {
  const r = md(d.date)
  return r ? `${r[0]}月${r[1]}日 · ${d.words} 字` : `${d.date} · ${d.words} 字`
}

const hoverIndex = ref<number | null>(null)

const activeDay = computed<DailyWord | null>(() => {
  const i = hoverIndex.value
  if (i === null) return null
  return sorted.value[i] ?? null
})

/** 悬停点坐标（viewBox 单位），SVG 按宽度等比缩放，换算成百分比即可跟随 */
const dotPos = computed(() => {
  const i = hoverIndex.value
  const d = activeDay.value
  if (i === null || !d) return null
  return {
    left: `${((xAt(i) / W) * 100).toFixed(2)}%`,
    top: `${((yAt(d.words) / H) * 100).toFixed(2)}%`,
  }
})

const totalWords = computed(() => sorted.value.reduce((s, d) => s + d.words, 0))
const avgWords = computed(() => {
  const n = sorted.value.length
  return n ? Math.round(totalWords.value / n) : 0
})

/** x 轴稀疏刻度（最多 4 个，避免拥挤） */
const axisTicks = computed(() => {
  const n = sorted.value.length
  if (!n) return []
  const count = Math.min(4, n)
  const ticks: { x: number; label: string }[] = []
  for (let k = 0; k < count; k++) {
    const i = Math.round((k * (n - 1)) / Math.max(1, count - 1))
    const d = sorted.value[i]
    if (!d) continue
    const r = md(d.date)
    ticks.push({ x: xAt(i), label: r ? `${r[0]}/${r[1]}` : d.date })
  }
  return ticks
})

const caption = computed(() => {
  const d = activeDay.value
  if (d) return pointLabel(d)
  return `日均 ${avgWords.value} 字 · 共 ${totalWords.value} 字`
})
</script>

<template>
  <section class="card trend">
    <div class="trend__head">
      <h3 class="trend__title">{{ props.label }}字数</h3>
      <span class="trend__cap" :class="{ 'trend__cap--live': activeDay !== null }">{{ caption }}</span>
    </div>

    <div v-if="sorted.length" class="trend__plot" @pointerleave="hoverIndex = null">
      <svg :viewBox="`0 0 ${W} ${H}`" class="trend__svg" role="img" aria-label="字数趋势折线图">
        <defs>
          <linearGradient :id="gid" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="var(--brand-base)" stop-opacity="0.26" />
            <stop offset="100%" stop-color="var(--brand-base)" stop-opacity="0.02" />
          </linearGradient>
        </defs>

        <!-- 网格与 y 轴刻度 -->
        <line :x1="PAD.l" :x2="W - PAD.r" :y1="PAD.t + innerH" :y2="PAD.t + innerH" class="trend__axis" />
        <line :x1="PAD.l" :x2="W - PAD.r" :y1="PAD.t + innerH / 2" :y2="PAD.t + innerH / 2" class="trend__grid" />
        <line :x1="PAD.l" :x2="W - PAD.r" :y1="PAD.t" :y2="PAD.t" class="trend__grid" />
        <text :x="PAD.l - 7" :y="PAD.t + 4" class="trend__tick" text-anchor="end">{{ yMax }}</text>
        <text :x="PAD.l - 7" :y="PAD.t + innerH / 2 + 4" class="trend__tick" text-anchor="end">
          {{ Math.round(yMax / 2) }}
        </text>
        <text :x="PAD.l - 7" :y="PAD.t + innerH + 4" class="trend__tick" text-anchor="end">0</text>

        <path :d="areaPath" :fill="`url(#${gid})`" stroke="none" />
        <path
          :d="linePath"
          fill="none"
          stroke="var(--brand-base)"
          stroke-width="2"
          stroke-linejoin="round"
          stroke-linecap="round"
        />

        <!-- 悬停辅助线与点 -->
        <template v-if="dotPos">
          <line
            :x1="((parseFloat(dotPos.left) / 100) * W).toFixed(1)"
            :x2="((parseFloat(dotPos.left) / 100) * W).toFixed(1)"
            :y1="PAD.t"
            :y2="PAD.t + innerH"
            class="trend__guide"
          />
          <circle
            :cx="((parseFloat(dotPos.left) / 100) * W).toFixed(1)"
            :cy="((parseFloat(dotPos.top) / 100) * H).toFixed(1)"
            r="4"
            class="trend__dot"
          />
        </template>

        <!-- 逐点热区 -->
        <rect
          v-for="(d, i) in sorted"
          :key="d.date"
          :x="(xAt(i) - stepX / 2).toFixed(1)"
          :y="0"
          :width="stepX.toFixed(1)"
          :height="H"
          fill="transparent"
          class="trend__hit"
          @pointerenter="hoverIndex = i"
        >
          <title>{{ pointLabel(d) }}</title>
        </rect>

        <text
          v-for="t in axisTicks"
          :key="t.label + '-' + t.x"
          :x="t.x.toFixed(1)"
          :y="H - 7"
          class="trend__tick"
          text-anchor="middle"
        >
          {{ t.label }}
        </text>
      </svg>
    </div>
    <p v-else class="muted trend__none">还没有可画的数据。</p>
  </section>
</template>

<style scoped>
.trend {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.trend__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-2);
}

.trend__title {
  font-size: 14px;
  white-space: nowrap;
}

.trend__cap {
  font-size: 12px;
  color: var(--ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trend__cap--live {
  color: var(--brand-text);
}

.trend__plot {
  position: relative;
}

.trend__svg {
  width: 100%;
  height: auto;
  display: block;
}

.trend__axis {
  stroke: var(--paper-line);
  stroke-width: 1;
}

.trend__grid {
  stroke: var(--paper-line-soft);
  stroke-width: 1;
  stroke-dasharray: 3 4;
}

.trend__tick {
  fill: var(--ink-4);
  font-size: 10px;
}

.trend__guide {
  stroke: var(--brand-line);
  stroke-width: 1;
  stroke-dasharray: 2 3;
}

.trend__dot {
  fill: var(--brand-base);
  stroke: var(--paper-surface);
  stroke-width: 1.5;
}

.trend__hit {
  cursor: crosshair;
}

.trend__none {
  margin: 0;
  font-size: 13px;
}
</style>
