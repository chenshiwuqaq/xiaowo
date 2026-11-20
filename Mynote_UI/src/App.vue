<script setup lang="ts">
/**
 * 应用外壳。
 *
 * 负责三件事：氛围层（纸感噪点 + 装饰图案 + 漂浮粒子）、顶栏导航、路由出口。
 * 登录页与 404 不显示顶栏，避免"还没进门就看到一整套后台导航"。
 */
import { computed, onMounted, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useUserStore } from '@/stores/user'
import ThemeDrawer from '@/components/ThemeDrawer.vue'
import ParticleField from '@/components/ParticleField.vue'

const route = useRoute()
const router = useRouter()
const theme = useThemeStore()
const user = useUserStore()

const drawerOpen = ref(false)

/** 登录页/404 用纯净布局 */
const bare = computed(() => route.meta.public === true)

const NAV = [
  { path: '/home', label: '小窝', icon: 'home' },
  { path: '/notes', label: '书写', icon: 'pen' },
  { path: '/market', label: '市场', icon: 'market' },
  { path: '/settings', label: '设置', icon: 'gear' },
]

const avatarUrl = computed(() => {
  const raw = user.profile?.avatar
  if (!raw) return ''
  return raw.startsWith('http') || raw.startsWith('/upload') ? raw : `/upload/${raw}`
})

function initials() {
  const name = user.displayName
  return name ? name.slice(0, 1) : '窝'
}

onMounted(async () => {
  // 有令牌就先补一次身份，主题与昵称才能立刻正确；失败已由 store 内部吞掉
  if (user.hasToken()) {
    await user.fetchProfile().catch(() => undefined)
  }
})
</script>

<template>
  <div class="shell paper-grain" :class="[theme.currentPatternClass, `mode-${theme.mode}`]">
    <!-- 装饰图案层：跟随 themeStore，不参与交互 -->
    <div class="shell__pattern" :style="theme.pageBackgroundStyle" aria-hidden="true" />
    <ParticleField v-if="!bare && theme.particle" />

    <header v-if="!bare" class="topbar">
      <button class="brand" type="button" @click="router.push('/home')">
        <span class="brand__lamp" aria-hidden="true">
          <i />
        </span>
        <span class="brand__text">
          <strong>小窝笔记</strong>
          <em>私人知识与创作空间</em>
        </span>
      </button>

      <nav class="nav">
        <RouterLink
          v-for="item in NAV"
          :key="item.path"
          :to="item.path"
          class="nav__item"
          :class="{ 'nav__item--active': route.path === item.path }"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="topbar__right">
        <button class="btn btn--ghost btn--icon" type="button" v-tooltip="'主题'" @click="drawerOpen = true">
          <span class="dot" :style="{ background: theme.brand }" />
          <span class="sr-only">主题</span>
        </button>
        <button
          class="btn btn--ghost btn--icon"
          type="button"
          v-tooltip="theme.mode === 'dark' ? '回到亮处' : '调暗灯光'"
          @click="theme.mode = theme.mode === 'dark' ? 'light' : 'dark'"
        >
          {{ theme.mode === 'dark' ? '☀' : '☾' }}
        </button>
        <button class="me" type="button" @click="router.push('/settings')">
          <img v-if="avatarUrl" :src="avatarUrl" :alt="user.displayName" />
          <span v-else class="me__fallback">{{ initials() }}</span>
          <span class="me__name">{{ user.displayName }}</span>
        </button>
        <button
          v-if="user.hasToken()"
          class="btn btn--ghost btn--sm"
          type="button"
          @click="user.logout(); router.push('/login')"
        >
          出门
        </button>
      </div>
    </header>

    <main class="main" :class="{ 'main--bare': bare }">
      <RouterView v-slot="{ Component }">
        <Transition name="route" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>

    <ThemeDrawer v-model:open="drawerOpen" />
  </div>
</template>

<style scoped>
.shell {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  isolation: isolate;
}

.shell__pattern {
  position: fixed;
  inset: 0;
  z-index: -2;
  pointer-events: none;
  opacity: 0.5;
  background-repeat: repeat;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: var(--sp-5);
  padding: var(--sp-3) var(--sp-6);
  background: color-mix(in srgb, var(--paper-page) 88%, transparent);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--paper-line-soft);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 0;
  background: none;
  cursor: pointer;
  padding: 0;
  text-align: left;
  color: inherit;
}

.brand__lamp {
  width: 26px;
  height: 26px;
  border-radius: 50% 50% 46% 46%;
  background: radial-gradient(circle at 35% 30%, color-mix(in srgb, var(--peach) 80%, #fff), var(--brand-base));
  box-shadow: 0 0 0 4px var(--brand-soft), 0 4px 10px color-mix(in srgb, var(--brand-base) 30%, transparent);
  animation: breathe 3.6s var(--ease) infinite;
  display: grid;
  place-items: center;
}

.brand__lamp i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: color-mix(in srgb, #fff 70%, var(--peach));
  opacity: 0.9;
}

.brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.brand__text strong {
  font-size: 15px;
  letter-spacing: 0.02em;
}

.brand__text em {
  font-style: normal;
  font-size: 11px;
  color: var(--ink-3);
}

.nav {
  display: flex;
  gap: var(--sp-1);
  margin-left: var(--sp-2);
}

.nav__item {
  padding: 6px 14px;
  border-radius: var(--r-pill);
  font-size: 13px;
  color: var(--ink-2);
  transition:
    background var(--dur-fast) var(--ease),
    color var(--dur-fast) var(--ease);
}

.nav__item:hover {
  background: var(--brand-soft);
  color: var(--brand-text);
}

.nav__item--active {
  background: var(--brand-soft-strong);
  color: var(--brand-text);
  font-weight: 600;
}

.topbar__right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.12);
}

.me {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 10px 0 4px;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-pill);
  background: var(--paper-surface);
  cursor: pointer;
  color: var(--ink);
  font-size: 13px;
}

.me:hover {
  border-color: var(--brand-line);
  background: var(--brand-soft);
}

.me img,
.me__fallback {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
}

.me__fallback {
  display: grid;
  place-items: center;
  background: var(--brand-soft-strong);
  color: var(--brand-text);
  font-size: 12px;
  font-weight: 700;
}

.main {
  flex: 1;
  position: relative;
  z-index: 1;
}

.main--bare {
  display: grid;
  place-items: stretch;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

@media (max-width: 760px) {
  .topbar {
    padding: var(--sp-3) var(--sp-4);
    gap: var(--sp-3);
  }

  .brand__text em,
  .me__name {
    display: none;
  }
}
</style>
