<script setup lang="ts">
/**
 * 书写热力日历：纯 CSS Grid 手绘（一列=一周，一行=一个星期几）。
 *
 * 约定：后端已把缺失日期补 0，这里直接按数组渲染；
 * 为稳妥仍先按日期排序，并用首日的星期数垫出左上角的空格，
 * 让每个格子落在正确的"星期几行"上。
 */
import { computed, nextTick, ref } from 'vue'
import type { DailyWord } from '@/api/types'

const props = defineProps<{ days: DailyWord[] }>()

const WEEK_ROWS = ['日', '一', '二', '三', '四', '五', '六']

function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

const sorted = computed(() =>
  [...props.days].sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0)),
)

const maxWords = computed(() => Math.max(0, ...sorted.value.map((d) => d.words)))
const wroteDays = computed(() => sorted.value.filter((d) => d.words > 0).length)
const totalWords = computed(() => sorted.value.reduce((sum, d) => sum + d.words, 0))

const todayIso = computed(() => {
  const t = new Date()
  return `${t.getFullYear()}-${pad2(t.getMonth() + 1)}-${pad2(t.getDate())}`
})

/** 解析 "2026-10-04" 的三段；非法时返回 null */
function parts(iso: string): [number, number, number] | null {
  const p = iso.split('-')
  const y = Number(p[0])
  const m = Number(p[1])
  const d = Number(p[2])
  if (!Number.isFinite(y) || !Number.isFinite(m) || !Number.isFinite(d) || y <= 0 || m <= 0 || d <= 0) {
    return null
  }
  return [y, m, d]
}

/** "2026-10-04" → "10 月 4 日"（tooltip 用） */
function tipOf(d: DailyWord): string {
  const p = parts(d.date)
  const label = p ? `${p[1]} 月 ${p[2]} 日` : d.date
  return `${label} · ${d.words} 字`
}

function weekdayOf(iso: string): number {
  const p = parts(iso)
  if (!p) return 0
  return new Date(p[0], p[1] - 1, p[2]).getDay()
}

/** 5 档深浅（0 = 没写；1 最浅接近 --brand-soft，5 最深到 --brand-base） */
function levelOf(words: number): number {
  if (words <= 0 || maxWords.value <= 0) return 0
  return Math.min(5, Math.max(1, Math.ceil((words / maxWords.value) * 5)))
}

/** 首日前的空格数：让第一格落在它星期几所在的那一行 */
const leadingBlanks = computed(() => {
  const first = sorted.value[0]
  return first ? weekdayOf(first.date) : 0
})

/** 悬浮提示：用 fixed 定位并 teleport 到 body，避免被 .heat__scroll 的 overflow 裁剪 */
const tipText = ref('')
const tipPos = ref<{ left: number; top: number } | null>(null)
const tipEl = ref<HTMLElement | null>(null)

function showTip(d: DailyWord, event: MouseEvent) {
  tipText.value = tipOf(d)
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const centerX = rect.left + rect.width / 2
  tipPos.value = { left: centerX, top: rect.top }
  // 渲染后按实际宽度把提示夹回视口内，避免左右边缘被裁
  void nextTick(() => {
    if (!tipEl.value || !tipPos.value) return
    const half = tipEl.value.offsetWidth / 2
    const clampedX = Math.min(Math.max(centerX, half + 8), window.innerWidth - half - 8)
    tipPos.value = { left: clampedX, top: rect.top }
  })
}

function hideTip() {
  tipText.value = ''
  tipPos.value = null
}
</script>

<template>
  <section class="card heat">
    <div class="heat__head">
      <h3 class="heat__title">近 {{ props.days.length }} 天</h3>
      <p class="heat__stats muted">
        写了 <b class="metric">{{ wroteDays }}</b> 天 · 共 <b class="metric">{{ totalWords }}</b> 字
      </p>
    </div>

    <div v-if="sorted.length" class="heat__scroll">
      <div class="heat__body">
        <div class="heat__weeks" aria-hidden="true">
          <span v-for="(w, i) in WEEK_ROWS" :key="i" class="heat__week">{{ i % 2 === 1 ? w : '' }}</span>
        </div>
        <div class="heat__grid">
          <div v-for="n in leadingBlanks" :key="'gap-' + n" class="heat__cell heat__cell--gap"></div>
          <div
            v-for="d in sorted"
            :key="d.date"
            class="heat__cell"
            :class="[`lvl-${levelOf(d.words)}`, { 'heat__cell--today': d.date === todayIso }]"
            @mouseenter="showTip(d, $event)"
            @mouseleave="hideTip"
          ></div>
        </div>
      </div>
    </div>
    <p v-else class="muted heat__none">还没有书写记录。</p>

    <div class="heat__legend muted">
      <span>少</span>
      <i class="heat__cell lvl-0"></i>
      <i class="heat__cell lvl-1"></i>
      <i class="heat__cell lvl-2"></i>
      <i class="heat__cell lvl-3"></i>
      <i class="heat__cell lvl-4"></i>
      <i class="heat__cell lvl-5"></i>
      <span>多</span>
    </div>

    <Teleport to="body">
      <div
        v-if="tipPos"
        ref="tipEl"
        class="heat__tip"
        :style="{ left: `${tipPos.left}px`, top: `${tipPos.top}px` }"
      >
        {{ tipText }}
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.heat {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  --hc: 14px;
}

.heat__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-2);
}

.heat__title {
  font-size: 14px;
}

.heat__stats {
  margin: 0;
  font-size: 12px;
}

.heat__stats b {
  color: var(--brand-text);
}

.heat__scroll {
  overflow-x: auto;
  padding-bottom: var(--sp-3); /* 给悬停 tooltip 留出不被裁掉的余量 */
}

.heat__body {
  display: flex;
  gap: var(--sp-1);
  width: max-content;
}

.heat__weeks {
  display: grid;
  grid-template-rows: repeat(7, var(--hc));
  gap: 4px;
}

.heat__week {
  font-size: 9px;
  color: var(--ink-4);
  align-self: center;
  line-height: 1;
}

.heat__grid {
  display: grid;
  grid-template-rows: repeat(7, var(--hc));
  grid-auto-flow: column;
  grid-auto-columns: var(--hc);
  gap: 4px;
}

.heat__cell {
  width: var(--hc);
  height: var(--hc);
  border-radius: var(--r-sm);
  position: relative;
  border: 1px solid color-mix(in srgb, var(--ink) 5%, transparent);
}

.heat__cell--gap {
  visibility: hidden;
}

/* 5 档深浅：--brand-soft → --brand-base */
.lvl-0 {
  background: var(--paper-muted);
}
.lvl-1 {
  background: var(--brand-soft);
}
.lvl-2 {
  background: color-mix(in srgb, var(--brand-base) 30%, var(--brand-soft));
}
.lvl-3 {
  background: color-mix(in srgb, var(--brand-base) 52%, var(--brand-soft));
}
.lvl-4 {
  background: color-mix(in srgb, var(--brand-base) 76%, var(--brand-soft));
}
.lvl-5 {
  background: var(--brand-base);
}

.heat__cell--today {
  outline: 1.5px solid var(--brand-text);
  outline-offset: 1px;
}

/* 悬浮提示：fixed 定位在格子上方，teleport 到 body 后不再受滚动容器裁剪 */
.heat__tip {
  position: fixed;
  transform: translate(-50%, calc(-100% - 8px));
  background: var(--ink);
  color: var(--paper-page);
  font-size: 11px;
  line-height: 1;
  padding: 5px 9px;
  border-radius: var(--r-sm);
  white-space: nowrap;
  pointer-events: none;
  box-shadow: var(--sh-2);
  z-index: 200;
}

.heat__none {
  margin: 0;
  font-size: 13px;
}

.heat__legend {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  margin-top: calc(-1 * var(--sp-2));
}

.heat__legend span {
  margin: 0 4px;
}

.heat__legend .heat__cell {
  width: 11px;
  height: 11px;
}
</style>
