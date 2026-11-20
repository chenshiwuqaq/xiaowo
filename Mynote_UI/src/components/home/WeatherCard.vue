<script setup lang="ts">
/**
 * 天气卡片：纯展示组件，数据由父级（HomeView）传入，保持单向数据流。
 *
 * 产品设定：天气是"可忽略的失败"——取不到时只显示一句温和的占位，
 * 不用错误色、不弹提示，不打扰书写。
 */
import { computed } from 'vue'
import type { Weather } from '@/api/types'

const props = withDefaults(
  defineProps<{
    weather: Weather | null
    loading?: boolean
  }>(),
  { loading: false },
)

/** icon 字段既可能是图片路径，也可能直接是文字/符号，按内容二选一渲染 */
const iconIsImage = computed(() => {
  const icon = props.weather?.icon ?? ''
  return /^(https?:\/\/|\/)/.test(icon) || /\.(svg|png|gif|webp)$/i.test(icon)
})

/** 从 "2026-10-04 08:30" 或 ISO 串里取 HH:mm */
function hhmm(s: string | undefined): string {
  if (!s) return ''
  const m = /(?:T| )(\d{2}:\d{2})/.exec(s)
  return m?.[1] ?? s
}

/** "2026-10-05" → "10月5日" */
function shortCn(iso: string | undefined): string {
  if (!iso) return ''
  const p = iso.split('-')
  const mo = Number(p[1])
  const d = Number(p[2])
  return Number.isFinite(mo) && Number.isFinite(d) && mo > 0 && d > 0 ? `${mo}月${d}日` : iso
}

const forecast = computed(() => (props.weather?.forecast ?? []).slice(0, 4))

function num(v: number | undefined): string {
  return typeof v === 'number' ? String(Math.round(v)) : '—'
}
</script>

<template>
  <section class="card wx">
    <div class="wx__head">
      <h3 class="wx__title">窗外的天气</h3>
      <span v-if="props.weather" class="wx__time muted">{{ hhmm(props.weather.updatedAt) }} 更新</span>
    </div>

    <!-- 首次加载中：骨架 -->
    <div v-if="props.loading && !props.weather" class="wx__skeleton">
      <div class="skeleton" style="height: 54px; width: 70%"></div>
      <div class="skeleton" style="height: 14px; width: 45%; margin-top: var(--sp-3)"></div>
    </div>

    <!-- 取不到：温和占位，不给错误感 -->
    <div v-else-if="!props.weather" class="wx__empty">
      <svg class="wx__empty-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path
          d="M7 17h9.5a3.5 3.5 0 0 0 .4-6.97A5 5 0 0 0 7.6 8.2 3.9 3.9 0 0 0 7 17Z"
          stroke="currentColor"
          stroke-width="1.4"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
      <span>天气没取到，待会儿再看</span>
    </div>

    <template v-else>
      <div class="wx__main">
        <img v-if="iconIsImage" :src="props.weather.icon" class="wx__icon-img" alt="" />
        <span v-else class="wx__icon-text">{{ props.weather.icon }}</span>
        <div class="wx__now">
          <p class="wx__temp metric">
            {{ Math.round(props.weather.temperature) }}<span class="wx__unit">°</span>
          </p>
          <p class="wx__text">{{ props.weather.weatherText }}</p>
        </div>
        <div class="wx__meta">
          <span class="chip">{{ props.weather.city }}</span>
          <span class="wx__line muted">体感 {{ num(props.weather.feelsLike) }}°</span>
          <span class="wx__line muted">湿度 {{ num(props.weather.humidity) }}%</span>
          <span class="wx__line muted">风 {{ num(props.weather.windSpeed) }} km/h</span>
        </div>
      </div>

      <div v-if="forecast.length" class="wx__forecast">
        <div v-for="(f, i) in forecast" :key="f.date || i" class="wx__day">
          <span class="wx__day-date muted">{{ shortCn(f.date) }}</span>
          <span class="wx__day-text">{{ f.text }}</span>
          <span class="wx__day-temp metric">{{ num(f.min) }}° / {{ num(f.max) }}°</span>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.wx {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.wx__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-2);
}

.wx__title {
  font-size: 14px;
}

.wx__time {
  font-size: 12px;
}

.wx__main {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-wrap: wrap;
}

.wx__icon-img {
  width: 42px;
  height: 42px;
  object-fit: contain;
}

.wx__icon-text {
  font-size: 30px;
  line-height: 1;
  color: var(--brand-text);
}

.wx__now {
  min-width: 84px;
}

.wx__temp {
  font-size: 38px;
  font-weight: 700;
  line-height: 1.05;
  color: var(--ink);
}

.wx__unit {
  font-size: 20px;
  color: var(--ink-2);
}

.wx__text {
  font-size: 13px;
  color: var(--ink-2);
}

.wx__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  margin-left: auto;
}

.wx__line {
  white-space: nowrap;
}

.wx__forecast {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(72px, 1fr));
  gap: var(--sp-2);
  border-top: 1px solid var(--paper-line);
  padding-top: var(--sp-2);
}

.wx__day {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-size: 12px;
}

.wx__day-text {
  color: var(--ink-2);
}

.wx__day-temp {
  color: var(--ink-3);
}

.wx__empty {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  color: var(--ink-3);
  font-size: 13px;
  padding: var(--sp-3) 0;
}

.wx__empty-icon {
  width: 22px;
  height: 22px;
  color: var(--ink-4);
  animation: breathe 3.2s var(--ease) infinite;
}
</style>
