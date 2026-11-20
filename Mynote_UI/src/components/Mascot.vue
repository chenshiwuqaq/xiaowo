<script setup lang="ts">
/**
 * 陪伴小兽：登录页氛围区里的小灯笼/圆尾鸟/暖窝，这里做成可复用组件，
 * 固定在页面角落，给主页与设置页两侧的空旷留白填一点生气。
 *
 * 纯装饰：pointer-events 关闭，不抢任何点击；位置由 side 控制左右。
 */
import { useThemeStore } from '@/stores/theme'

const props = withDefaults(defineProps<{ side?: 'left' | 'right' }>(), { side: 'right' })

const theme = useThemeStore()
</script>

<template>
  <div
    v-if="theme.mascot !== 'none'"
    class="mascot"
    :class="`mascot--${props.side}`"
    aria-hidden="true"
  >
    <template v-if="theme.mascot === 'lantern'">
      <div class="lantern"><div class="lantern-body"></div></div>
    </template>
    <template v-else-if="theme.mascot === 'bird'">
      <div class="bird"></div>
    </template>
    <template v-else-if="theme.mascot === 'nest'">
      <div class="nest"><span class="egg"></span><span class="egg"></span></div>
    </template>
  </div>
</template>

<style scoped>
.mascot {
  position: fixed;
  bottom: 32px;
  z-index: 5;
  pointer-events: none;
}

.mascot--right {
  right: clamp(16px, 5vw, 72px);
}

.mascot--left {
  left: clamp(16px, 5vw, 72px);
}

/* 小灯笼：顶部一根短绳挂起，灯身带呼吸光晕 */
.lantern {
  animation: sway 6s ease-in-out infinite;
  transform-origin: 50% 0;
}

.lantern::before {
  content: '';
  display: block;
  width: 2px;
  height: 44px;
  margin: 0 auto;
  background: var(--brand-line);
}

.lantern-body {
  position: relative;
  width: 32px;
  height: 38px;
  margin: 0 auto;
  border-radius: 46% / 50%;
  background: radial-gradient(
    circle at 50% 30%,
    color-mix(in srgb, var(--peach) 85%, transparent),
    var(--brand-base)
  );
  box-shadow:
    0 0 26px color-mix(in srgb, var(--peach) 35%, transparent),
    inset 0 -6px 10px color-mix(in srgb, var(--brand-deep) 45%, transparent);
  animation: breathe 4.5s var(--ease) infinite;
}

.lantern-body::before,
.lantern-body::after {
  content: '';
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  width: 11px;
  height: 4px;
  border-radius: var(--r-sm);
  background: var(--brand-deep);
}

.lantern-body::before {
  top: -4px;
}

.lantern-body::after {
  bottom: -4px;
}

/* 圆尾鸟：停在角落轻轻起伏 */
.bird {
  position: relative;
  width: 44px;
  height: 32px;
  border-radius: 50% 50% 45% 45%;
  background: var(--brand-soft-strong);
  border: 2px solid var(--brand-line);
  animation: bob 3s ease-in-out infinite;
}

.bird::before {
  content: '';
  position: absolute;
  right: -10px;
  top: 11px;
  width: 0;
  height: 0;
  border-left: 9px solid var(--peach);
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
}

.bird::after {
  content: '';
  position: absolute;
  right: 10px;
  top: 8px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--ink-2);
}

/* 暖窝：两枚蛋安静地窝着 */
.nest {
  position: relative;
  width: 68px;
  height: 30px;
  border: 3px solid var(--brand-line);
  border-top: none;
  border-radius: 6px 6px 36px 36px;
  background: linear-gradient(180deg, var(--brand-soft), var(--paper-raised));
  animation: bob 4s ease-in-out infinite;
}

.egg {
  position: absolute;
  top: -8px;
  width: 16px;
  height: 20px;
  border-radius: 50% 50% 46% 46%;
  background: var(--paper-raised);
  border: 2px solid var(--paper-line);
}

.egg:nth-child(1) {
  left: 13px;
  transform: rotate(-8deg);
}

.egg:nth-child(2) {
  left: 34px;
  transform: rotate(6deg);
}

@keyframes sway {
  0%,
  100% {
    transform: rotate(-3deg);
  }
  50% {
    transform: rotate(3deg);
  }
}

@keyframes bob {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-6px);
  }
}
</style>
