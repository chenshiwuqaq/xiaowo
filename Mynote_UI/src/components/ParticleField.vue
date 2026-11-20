<script setup lang="ts">
/**
 * 背景漂浮粒子。
 *
 * 只做 CSS 动画、不上 canvas：十几颗光点用 div + animation 足够，
 * 且不会和编辑器抢主线程。粒子完全 pointer-events:none，不干扰书写。
 */
import { computed } from 'vue'

const props = withDefaults(defineProps<{ count?: number }>(), { count: 14 })

/** 位置、大小、时长用固定表而非 Math.random，避免每次热更新粒子跳动 */
const SEEDS = [
  { left: 6, delay: 0, dur: 26, size: 5 },
  { left: 14, delay: 6, dur: 32, size: 3 },
  { left: 23, delay: 12, dur: 24, size: 6 },
  { left: 31, delay: 3, dur: 30, size: 4 },
  { left: 40, delay: 16, dur: 28, size: 3 },
  { left: 48, delay: 9, dur: 34, size: 5 },
  { left: 56, delay: 21, dur: 26, size: 4 },
  { left: 63, delay: 1, dur: 31, size: 6 },
  { left: 71, delay: 14, dur: 27, size: 3 },
  { left: 78, delay: 7, dur: 33, size: 5 },
  { left: 85, delay: 19, dur: 25, size: 4 },
  { left: 92, delay: 11, dur: 30, size: 3 },
  { left: 19, delay: 24, dur: 35, size: 4 },
  { left: 67, delay: 28, dur: 29, size: 5 },
]

const dots = computed(() => {
  const total = Math.min(props.count, SEEDS.length)
  return SEEDS.slice(0, total).map((seed, index) => ({
    key: `p-${index}`,
    style: {
      left: `${seed.left}%`,
      width: `${seed.size}px`,
      height: `${seed.size}px`,
      animationDelay: `-${seed.delay}s`,
      animationDuration: `${seed.dur}s`,
    },
  }))
})
</script>

<template>
  <div class="particles" aria-hidden="true">
    <span v-for="dot in dots" :key="dot.key" class="particles__dot" :style="dot.style" />
  </div>
</template>

<style scoped>
.particles {
  position: fixed;
  inset: 0;
  z-index: -1;
  overflow: hidden;
  pointer-events: none;
}

.particles__dot {
  position: absolute;
  bottom: -8vh;
  border-radius: 50%;
  background: radial-gradient(circle, color-mix(in srgb, var(--peach) 80%, #fff), transparent 70%);
  filter: blur(0.4px);
  animation-name: float-up;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
  opacity: 0;
}

html[data-theme='dark'] .particles__dot {
  background: radial-gradient(circle, color-mix(in srgb, var(--brand-base) 70%, #ffd9a8), transparent 70%);
}
</style>
