<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import ProfileSection from '@/components/settings/ProfileSection.vue'
import PasswordSection from '@/components/settings/PasswordSection.vue'
import AppearanceSection from '@/components/settings/AppearanceSection.vue'
import WritingSection from '@/components/settings/WritingSection.vue'
import AiModelSection from '@/components/settings/AiModelSection.vue'
import AiToolSection from '@/components/settings/AiToolSection.vue'
import Mascot from '@/components/Mascot.vue'

type SectionKey = 'profile' | 'password' | 'appearance' | 'writing' | 'ai' | 'plugins'

const userStore = useUserStore()

/** 左侧分组导航的定义；图标在每个分组里用 v-if 内联绘制 */
const sections: { key: SectionKey; title: string; desc: string }[] = [
  { key: 'profile', title: '基础信息', desc: '头像、昵称与联系方式' },
  { key: 'password', title: '安全', desc: '修改登录密码' },
  { key: 'appearance', title: '外观主题', desc: '主色、书页与陪伴小兽' },
  { key: 'writing', title: '书写习惯', desc: '天气城市、字数目标与正文字号' },
  { key: 'ai', title: 'AI 模型', desc: '服务商、密钥与连通测试' },
  { key: 'plugins', title: 'AI 插件', desc: '自己写声明式 AI 工具' },
]

const active = ref<SectionKey>('profile')
const loadError = ref('')

onMounted(() => {
  // 各分组自己也会兜底拉取；这里先拉一次，失败就在页顶给出可见提示
  userStore.fetchProfile().catch((error: unknown) => {
    loadError.value = error instanceof Error ? error.message : '资料加载失败，稍后重试'
  })
})
</script>

<template>
  <div class="settings">
    <header class="settings__head anim-fade-up">
      <h1>个人设置</h1>
      <p class="muted">{{ userStore.displayName }}，把小窝调成舒服的样子。</p>
    </header>

    <p v-if="loadError" class="page-notice" role="alert">{{ loadError }}</p>

    <div class="settings__body">
      <nav class="card settings__nav" aria-label="设置分组">
        <button
          v-for="item in sections"
          :key="item.key"
          type="button"
          class="nav-item"
          :class="{ 'nav-item--active': active === item.key }"
          :aria-current="active === item.key ? 'page' : undefined"
          @click="active = item.key"
        >
          <svg
            class="nav-item__icon"
            viewBox="0 0 24 24"
            width="18"
            height="18"
            fill="none"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <template v-if="item.key === 'profile'">
              <path d="M12 12a3.6 3.6 0 1 0 0-7.2 3.6 3.6 0 0 0 0 7.2Z" />
              <path d="M5 19.5c1.3-3.2 3.9-4.8 7-4.8s5.7 1.6 7 4.8" />
            </template>
            <template v-else-if="item.key === 'password'">
              <rect x="6.5" y="10.5" width="11" height="8" rx="2.2" />
              <path d="M9.3 10.5V8a2.7 2.7 0 0 1 5.4 0v2.5" />
              <path d="M12 13.6v2.2" />
            </template>
            <template v-else-if="item.key === 'appearance'">
              <path d="M12 3.5c3.8 3.9 6.2 6.9 6.2 9.8a6.2 6.2 0 1 1-12.4 0c0-2.9 2.4-5.9 6.2-9.8Z" />
              <circle cx="12" cy="14.6" r="2.1" />
            </template>
            <template v-else-if="item.key === 'writing'">
              <path d="M4.5 19.5l1-4L16 5a2.2 2.2 0 0 1 3.1 3.1L8.5 18.5l-4 1Z" />
              <path d="M14.5 6.5l3 3" />
            </template>
            <template v-else-if="item.key === 'plugins'">
              <rect x="5" y="5" width="6" height="6" rx="1.4" />
              <rect x="13" y="5" width="6" height="6" rx="1.4" />
              <rect x="5" y="13" width="6" height="6" rx="1.4" />
              <rect x="13" y="13" width="6" height="6" rx="1.4" />
            </template>
            <template v-else>
              <path d="M12 4.5l1.7 4.3 4.3 1.7-4.3 1.7L12 16.5l-1.7-4.3-4.3-1.7 4.3-1.7L12 4.5Z" />
              <path d="M18.5 16.5l.7 1.8 1.8.7-1.8.7-.7 1.8-.7-1.8-1.8-.7 1.8-.7.7-1.8Z" />
            </template>
          </svg>
          <span class="nav-item__text">
            <strong>{{ item.title }}</strong>
            <small class="muted">{{ item.desc }}</small>
          </span>
        </button>
      </nav>

      <main class="settings__main">
        <div :key="active" class="anim-fade-up">
          <ProfileSection v-if="active === 'profile'" />
          <PasswordSection v-else-if="active === 'password'" />
          <AppearanceSection v-else-if="active === 'appearance'" />
          <WritingSection v-else-if="active === 'writing'" />
          <AiModelSection v-else-if="active === 'ai'" />
          <AiToolSection v-else />
        </div>
      </main>
    </div>

    <Mascot side="right" />
  </div>
</template>

<style scoped>
.settings {
  max-width: 1060px;
  margin: 0 auto;
  padding: var(--sp-6) var(--sp-5) var(--sp-8);
}

.settings__head h1 {
  font-size: 22px;
  letter-spacing: -0.02em;
}

.settings__head p {
  margin-top: var(--sp-1);
  font-size: 13px;
}

.page-notice {
  margin: var(--sp-4) 0 0;
  padding: 10px 14px;
  border-radius: var(--r-md);
  border: 1px solid color-mix(in srgb, var(--danger) 35%, transparent);
  background: color-mix(in srgb, var(--danger) 10%, var(--paper-surface));
  color: color-mix(in srgb, var(--danger) 78%, var(--ink));
  font-size: 13px;
}

.settings__body {
  display: grid;
  grid-template-columns: 236px minmax(0, 1fr);
  gap: var(--sp-5);
  margin-top: var(--sp-5);
  align-items: start;
}

.settings__nav {
  padding: var(--sp-2) var(--sp-2);
  display: flex;
  flex-direction: column;
  gap: 2px;
  position: sticky;
  top: var(--sp-5);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  width: 100%;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: var(--r-md);
  background: transparent;
  color: var(--ink-2);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    background var(--dur-fast) var(--ease),
    border-color var(--dur-fast) var(--ease),
    color var(--dur-fast) var(--ease);
}

.nav-item:hover {
  background: var(--brand-soft);
  color: var(--brand-text);
}

.nav-item--active {
  background: var(--brand-soft-strong);
  border-color: var(--brand-line);
  color: var(--brand-text);
}

.nav-item__icon {
  flex: none;
  opacity: 0.9;
}

.nav-item__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.nav-item__text strong {
  font-size: 14px;
  font-weight: 600;
}

.nav-item__text small {
  font-size: 12px;
  line-height: 1.5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.settings__main {
  min-width: 0;
}

@media (max-width: 880px) {
  .settings__body {
    grid-template-columns: 1fr;
  }

  .settings__nav {
    position: static;
    flex-direction: row;
    overflow-x: auto;
  }

  .nav-item {
    flex: none;
  }

  .nav-item__text small {
    display: none;
  }
}
</style>
