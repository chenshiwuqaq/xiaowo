<script setup lang="ts">
/** 404：走错房间。给一条明确的回程，不做无意义的装饰。 */
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="nf">
    <div class="nf__card card anim-pop">
      <p class="nf__code metric">404</p>
      <h1 class="nf__title">这间屋子还没有笔记</h1>
      <p class="muted">地址可能写错了，或者那篇笔记已经被收进回收站。</p>
      <div class="nf__actions">
        <button class="btn btn--primary" type="button" @click="router.push('/home')">回小窝看看</button>
        <button class="btn" type="button" @click="router.push('/notes')">去书写页</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.nf {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: var(--sp-6);
}

.nf__card {
  max-width: 420px;
  text-align: center;
  padding: var(--sp-7) var(--sp-6);
}

.nf__code {
  margin: 0;
  font-size: 40px;
  color: var(--brand-base);
  opacity: 0.55;
}

.nf__title {
  font-size: 18px;
  margin-bottom: var(--sp-2);
}

.nf__actions {
  display: flex;
  gap: var(--sp-2);
  justify-content: center;
  margin-top: var(--sp-5);
}
</style>
