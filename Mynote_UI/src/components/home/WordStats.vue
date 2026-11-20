<script setup lang="ts">
/**
 * 今日字数与书写概览：目标进度环、连续打卡、两个字数口径、类型分布。
 *
 * 刻意保留两个口径（产品设定，不合并成一个数字）：
 *   - 作品总字数 totalWords：现存笔记的字数之和；
 *   - 累计书写 writtenWords：写过的每个字都算，含删改重写，所以总是更大。
 */
import { computed } from 'vue'
import type { TypeCount } from '@/api/types'

const props = withDefaults(
  defineProps<{
    todayWords: number
    dailyGoal: number
    goalReached: boolean
    streakDays: number
    noteCount: number
    totalWords: number
    writtenWords: number
    lastMilestone?: string | null
    typeDistribution?: TypeCount[]
  }>(),
  { lastMilestone: null, typeDistribution: () => [] },
)

/** 圆环几何：r=52，周长 ≈ 326.7 */
const R = 52
const CIRC = 2 * Math.PI * R

const ratio = computed(() => {
  if (props.dailyGoal > 0) return Math.min(1, Math.max(0, props.todayWords / props.dailyGoal))
  return props.todayWords > 0 ? 1 : 0
})

const dashArray = computed(() => `${(ratio.value * CIRC).toFixed(2)} ${CIRC.toFixed(2)}`)
const percent = computed(() => Math.round(ratio.value * 100))

const goalText = computed(() =>
  props.dailyGoal > 0 ? `每日目标 ${props.dailyGoal} 字 · 已完成 ${percent.value}%` : '还没设每日目标，去设置里定一个？',
)

const maxTypeCount = computed(() => Math.max(1, ...(props.typeDistribution ?? []).map((t) => t.count)))

function barWidth(count: number): string {
  return `${Math.max(3, Math.round((count / maxTypeCount.value) * 100))}%`
}

function fmt(n: number): string {
  return n.toLocaleString('zh-CN')
}
</script>

<template>
  <section class="card stats">
    <div class="stats__top">
      <div class="ring">
        <svg viewBox="0 0 120 120" class="ring__svg" aria-hidden="true">
          <circle cx="60" cy="60" :r="R" class="ring__track" fill="none" stroke-width="10" />
          <circle
            cx="60"
            cy="60"
            :r="R"
            class="ring__bar"
            fill="none"
            stroke-width="10"
            stroke-linecap="round"
            :stroke-dasharray="dashArray"
            transform="rotate(-90 60 60)"
          />
        </svg>
        <div class="ring__center">
          <span class="ring__num metric">{{ props.todayWords }}</span>
          <span class="ring__unit muted">/ {{ props.dailyGoal > 0 ? props.dailyGoal : '—' }}</span>
        </div>
      </div>

      <div class="stats__facts">
        <p class="stats__headline">今天写了 <b class="metric">{{ props.todayWords }}</b> 字</p>
        <p class="stats__goal muted">{{ goalText }}</p>
        <p v-if="props.goalReached" class="stats__done chip chip--brand anim-pop">
          <svg viewBox="0 0 16 16" width="12" height="12" fill="none" aria-hidden="true">
            <path d="M3 8.5 6.2 12 13 4.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          今日目标达成 · 多写的字，是给明天的小费
        </p>
        <div class="stats__chips">
          <span class="chip chip--brand">连续打卡 {{ props.streakDays }} 天</span>
          <span class="chip">笔记 {{ props.noteCount }} 篇</span>
          <span v-if="props.lastMilestone" class="chip">里程碑：{{ props.lastMilestone }}</span>
        </div>
      </div>
    </div>

    <!-- 两个口径并排，各配一行小字解释（刻意的产品设计，勿合并） -->
    <div class="calibers">
      <div class="caliber">
        <p class="caliber__label">作品总字数</p>
        <p class="caliber__num metric">{{ fmt(props.totalWords) }}</p>
        <p class="caliber__note muted">现存笔记的字数之和——删掉的不会算在这里。</p>
      </div>
      <div class="caliber">
        <p class="caliber__label">累计书写</p>
        <p class="caliber__num metric">{{ fmt(props.writtenWords) }}</p>
        <p class="caliber__note muted">写过的每个字都算数，删改重写也计入，所以它更大。</p>
      </div>
    </div>

    <div v-if="props.typeDistribution && props.typeDistribution.length" class="types">
      <p class="types__title muted">笔记类型</p>
      <div v-for="t in props.typeDistribution" :key="t.noteType" class="types__row">
        <span class="types__name">{{ t.name }}</span>
        <span class="types__bar"><i :style="{ width: barWidth(t.count) }"></i></span>
        <span class="types__num metric">{{ t.count }}</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.stats {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.stats__top {
  display: flex;
  align-items: center;
  gap: var(--sp-5);
}

.ring {
  position: relative;
  width: 116px;
  height: 116px;
  flex: none;
}

.ring__svg {
  width: 100%;
  height: 100%;
  display: block;
}

.ring__track {
  stroke: var(--paper-muted);
}

.ring__bar {
  stroke: var(--brand-base);
  transition: stroke-dasharray var(--dur-slow) var(--ease);
}

.ring__center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0;
}

.ring__num {
  font-size: 26px;
  font-weight: 700;
  color: var(--ink);
  line-height: 1.15;
}

.ring__unit {
  font-size: 12px;
}

.stats__facts {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  min-width: 0;
}

.stats__headline {
  margin: 0;
  font-size: 14px;
  color: var(--ink-2);
}

.stats__headline b {
  font-size: 24px;
  color: var(--brand-text);
}

.stats__goal {
  margin: 0;
  font-size: 12px;
}

.stats__done {
  align-self: flex-start;
}

.stats__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-top: var(--sp-1);
}

.calibers {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-3);
  border-top: 1px solid var(--paper-line);
  padding-top: var(--sp-3);
}

.caliber__label {
  margin: 0;
  font-size: 12px;
  color: var(--ink-2);
}

.caliber__num {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--ink);
  line-height: 1.3;
}

.caliber__note {
  margin: 0;
  font-size: 11px;
  line-height: 1.5;
}

.types {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.types__title {
  margin: 0;
  font-size: 12px;
}

.types__row {
  display: grid;
  grid-template-columns: 72px 1fr 34px;
  align-items: center;
  gap: var(--sp-2);
  font-size: 12px;
}

.types__name {
  color: var(--ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.types__bar {
  height: 6px;
  border-radius: var(--r-pill);
  background: var(--paper-muted);
  overflow: hidden;
}

.types__bar i {
  display: block;
  height: 100%;
  border-radius: var(--r-pill);
  background: var(--brand-base);
  opacity: 0.8;
  transition: width var(--dur-base) var(--ease);
}

.types__num {
  text-align: right;
  color: var(--ink-3);
}
</style>
