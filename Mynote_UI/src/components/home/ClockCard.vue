<script setup lang="ts">
/**
 * 时钟卡片：本地时间每秒一跳，附带日期、星期与时段问候。
 *
 * 数据全部来自本地 Date，不依赖接口；组件卸载时清理 interval，
 * 避免离开主页后仍在后台空转（也防止 now 引用在卸载后继续触发更新）。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'

const props = withDefaults(defineProps<{ username?: string }>(), { username: '' })

const now = ref(new Date())
let timer: ReturnType<typeof setInterval> | undefined

function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

const WEEK_NAMES = ['日', '一', '二', '三', '四', '五', '六']

/** 时段划分（0-5 凌晨 / 5-8 清晨 / 8-12 上午 / 12-14 午后 / 14-18 下午 / 18-21 傍晚 / 21-24 深夜） */
interface Period {
  label: string
  hint: string
}

function periodOf(hour: number): Period {
  if (hour < 5) return { label: '凌晨', hint: '夜还深，写完这一行就去睡。' }
  if (hour < 8) return { label: '清晨', hint: '早上的第一笔，往往最诚实。' }
  if (hour < 12) return { label: '上午', hint: '脑子正清醒，适合推进主线。' }
  if (hour < 14) return { label: '午后', hint: '饭后容易困，写点轻松的也无妨。' }
  if (hour < 18) return { label: '下午', hint: '顺着上午的节奏，再写一段。' }
  if (hour < 21) return { label: '傍晚', hint: '天快黑了，给今天收个尾。' }
  return { label: '深夜', hint: '深夜适合写日记，也适合原谅自己。' }
}

const period = computed(() => periodOf(now.value.getHours()))
const hh = computed(() => pad2(now.value.getHours()))
const mm = computed(() => pad2(now.value.getMinutes()))
const ss = computed(() => pad2(now.value.getSeconds()))

const dateLine = computed(() => {
  const d = now.value
  const week = WEEK_NAMES[d.getDay()] ?? '日'
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 星期${week}`
})

const fullTime = computed(() => `${hh.value}:${mm.value}:${ss.value}`)

onMounted(() => {
  now.value = new Date()
  timer = setInterval(() => {
    now.value = new Date()
  }, 1000)
})

onUnmounted(() => {
  if (timer !== undefined) {
    clearInterval(timer)
    timer = undefined
  }
})
</script>

<template>
  <section class="card card--brand clock">
    <p class="clock__period">
      {{ period.label }}好<span v-if="props.username">，{{ props.username }}</span>
    </p>
    <div class="clock__time">
      <span class="clock__hm metric">{{ hh }}:{{ mm }}</span>
      <span class="clock__ss metric">{{ ss }}</span>
    </div>
    <p class="clock__full metric muted">{{ fullTime }} · {{ dateLine }}</p>
    <p class="clock__hint">{{ period.hint }}</p>
  </section>
</template>

<style scoped>
.clock {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--sp-2);
}

.clock__period {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--brand-text);
  letter-spacing: 0.02em;
}

.clock__time {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
}

.clock__hm {
  font-size: 46px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--ink);
}

.clock__ss {
  font-size: 16px;
  color: var(--brand-text);
}

.clock__full {
  margin: 0;
  font-size: 12px;
}

.clock__hint {
  margin: 0;
  font-size: 13px;
  color: var(--ink-2);
}
</style>
