<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { aiToolApi } from '@/api'
import { getCode } from '@/api/http'
import type { AiTool } from '@/api/types'
import { useUserStore } from '@/stores/user'
import { NOTE_TYPES, parseAiFeatureMap, serializeAiFeatureMap, type NoteTypeFeatureFlags } from '@/stores/notes'

const userStore = useUserStore()

type FeedbackKind = 'ok' | 'err' | 'info'
const feedback = ref<{ kind: FeedbackKind; text: string } | null>(null)
let feedbackTimer: ReturnType<typeof setTimeout> | undefined

function say(kind: FeedbackKind, text: string, holdMs = 5000) {
  if (feedbackTimer) clearTimeout(feedbackTimer)
  feedback.value = { kind, text }
  feedbackTimer = setTimeout(() => {
    feedback.value = null
  }, holdMs)
}

onBeforeUnmount(() => {
  if (feedbackTimer) clearTimeout(feedbackTimer)
})

function explain(error: unknown): string {
  const code = getCode(error)
  const raw = error instanceof Error && error.message ? error.message : ''
  if (code === 1005) return raw || '填写的值超出了允许范围'
  if (code === 400) return raw || '参数校验没有通过'
  return raw || '保存没有成功'
}

const weatherCity = ref('')
const dailyGoal = ref<number | string>(2000)
const fontSize = ref(16)
const saving = ref(false)
const featureMap = ref<Record<number, NoteTypeFeatureFlags>>(parseAiFeatureMap(null))
const plugins = ref<AiTool[]>([])

function fillFromProfile() {
  const p = userStore.profile
  if (!p) return
  weatherCity.value = p.weatherCity ?? ''
  dailyGoal.value = typeof p.dailyGoal === 'number' ? p.dailyGoal : 2000
  fontSize.value = typeof p.fontSize === 'number' ? clampFont(p.fontSize) : 16
  featureMap.value = parseAiFeatureMap(p.aiFeatureMap)
}

function clampFont(v: number): number {
  if (!Number.isFinite(v)) return 16
  return Math.min(24, Math.max(12, Math.round(v)))
}

function setFeature(type: number, key: string, checked: boolean) {
  const flags = featureMap.value[type]
  if (flags) flags[key] = checked
}

async function loadPlugins() {
  try {
    plugins.value = await aiToolApi.list()
  } catch {
    plugins.value = []
  }
}

watch(() => userStore.profile, fillFromProfile)

onMounted(() => {
  fillFromProfile()
  void loadPlugins()
  if (!userStore.profile) {
    userStore.fetchProfile().catch((error: unknown) => say('err', '资料加载失败：' + explain(error)))
  }
})

/** 字号改动直接落到 CSS 变量，编辑器马上跟着变（保存前只影响本机） */
watch(
  fontSize,
  (v) => {
    document.documentElement.style.setProperty('--editor-font-size', `${v}px`)
  },
  { immediate: true },
)

async function save() {
  const city = weatherCity.value.trim()
  const goal = Number(dailyGoal.value)
  if (!Number.isFinite(goal) || goal < 0 || goal > 20000) {
    say('err', '每日目标字数要在 0~20000 之间（0 表示不设目标）')
    return
  }
  if (city.length > 24) {
    say('err', '城市名不要太长（24 字以内）')
    return
  }
  const font = clampFont(Number(fontSize.value))
  saving.value = true
  try {
    await userStore.saveSettings({
      weatherCity: city,
      dailyGoal: Math.round(goal),
      fontSize: font,
      aiFeatureMap: serializeAiFeatureMap(featureMap.value),
    })
    say('ok', '书写习惯已保存')
  } catch (error) {
    const code = getCode(error)
    const reason = code === 1005 ? '有数值超出了允许范围' : code === 400 ? '参数校验没有通过' : explain(error)
    say('err', `${reason}——界面上的字号变化依然生效，但没有存进账号`, 6500)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="card sec">
    <h2 class="sec__title">书写习惯</h2>
    <p class="sec__desc muted">天气、每日目标、正文字号，以及各类型笔记要开启的 AI 助手场景。</p>

    <div v-if="feedback" class="fb" :class="`fb--${feedback.kind}`" role="status">{{ feedback.text }}</div>

    <div class="form-grid">
      <label class="field-row">
        <span class="field-row__label">天气城市</span>
        <input v-model="weatherCity" class="field" maxlength="24" placeholder="例如：杭州" />
        <span class="field-row__hint muted">主页天气用这座城市；留空则不显示天气。</span>
      </label>

      <label class="field-row">
        <span class="field-row__label">每日目标字数</span>
        <input v-model.number="dailyGoal" class="field" type="number" min="0" max="20000" step="100" placeholder="0 表示不设目标" />
        <span class="field-row__hint muted">0 ~ 20000，主页的进度环按这个数计算。</span>
      </label>

      <div class="field-row">
        <span class="field-row__label">编辑器字号</span>
        <div class="range-line">
          <input v-model.number="fontSize" type="range" min="12" max="24" step="1" aria-label="编辑器字号" />
          <span class="range-line__value metric">{{ fontSize }}px</span>
        </div>
        <div class="font-sample" :style="{ fontSize: `${fontSize}px` }">
          窗外的雨落在梧桐叶上，笔尖也跟着慢了下来。
        </div>
        <span class="field-row__hint muted">拖动即可预览；保存后换设备登录也保持这个字号。</span>
      </div>

      <div class="field-row">
        <span class="field-row__label">AI 助手插件</span>
        <div class="feature-grid">
          <div v-for="t in NOTE_TYPES" :key="t.value" class="feature-row">
            <span class="feature-type">{{ t.label }}</span>
            <div class="feature-checks">
              <label v-for="p in plugins" :key="p.id" class="feature-check">
                <input
                  type="checkbox"
                  :checked="featureMap[t.value]?.[p.id] ?? false"
                  @change="setFeature(t.value, p.id, ($event.target as HTMLInputElement).checked)"
                />
                {{ p.name }}
              </label>
              <span v-if="!plugins.length" class="muted tiny">还没有可用插件，去「AI 插件」里新增。</span>
            </div>
          </div>
        </div>
        <span class="field-row__hint muted">勾选后，该类型笔记的 AI 助手侧边栏会显示对应插件。用户插件在「AI 插件」里新增，然后在这里按类型开启。</span>
      </div>
    </div>

    <div class="sec__foot">
      <button type="button" class="btn btn--primary" :disabled="saving" @click="save">
        {{ saving ? '保存中…' : '保存设置' }}
      </button>
    </div>
  </section>
</template>

<style scoped>
.sec__title {
  font-size: 17px;
}

.sec__desc {
  margin-top: 2px;
  font-size: 13px;
}

.fb {
  margin-top: var(--sp-4);
  padding: 9px 12px;
  border-radius: var(--r-md);
  border: 1px solid transparent;
  font-size: 13px;
  line-height: 1.6;
}

.fb--ok {
  background: color-mix(in srgb, var(--ok) 12%, var(--paper-surface));
  border-color: color-mix(in srgb, var(--ok) 38%, transparent);
  color: color-mix(in srgb, var(--ok) 70%, var(--ink));
}

.fb--err {
  background: color-mix(in srgb, var(--danger) 10%, var(--paper-surface));
  border-color: color-mix(in srgb, var(--danger) 36%, transparent);
  color: color-mix(in srgb, var(--danger) 72%, var(--ink));
}

.fb--info {
  background: var(--brand-soft);
  border-color: var(--brand-line);
  color: var(--brand-text);
}

.form-grid {
  display: grid;
  gap: var(--sp-4);
  margin-top: var(--sp-4);
  max-width: 480px;
}

.field-row {
  display: grid;
  gap: 6px;
}

.field-row__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-2);
}

.field-row__hint {
  font-size: 12px;
  line-height: 1.6;
}

.range-line {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

input[type='range'] {
  flex: 1;
  accent-color: var(--brand-base);
}

.range-line__value {
  min-width: 46px;
  text-align: right;
  font-size: 13px;
  color: var(--ink-2);
}

.font-sample {
  border: 1px dashed var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-muted);
  color: var(--ink);
  padding: var(--sp-3);
  line-height: 1.8;
  font-family: var(--font-serif);
}

.feature-grid {
  display: grid;
  gap: var(--sp-2);
  padding: var(--sp-3);
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-surface);
}

.feature-row {
  display: grid;
  grid-template-columns: 64px 1fr;
  align-items: start;
  gap: var(--sp-2);
}

.feature-checks {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2) var(--sp-3);
}

.feature-type {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-2);
}

.feature-check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--ink-2);
  cursor: pointer;
}

.feature-check input {
  accent-color: var(--brand-base);
}

.sec__foot {
  margin-top: var(--sp-5);
  display: flex;
  justify-content: flex-end;
}
</style>
