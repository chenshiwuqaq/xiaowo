<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { fileApi } from '@/api'
import { getCode } from '@/api/http'
import { useThemeStore, type MascotKey, type PatternKey } from '@/stores/theme'
import { useUserStore } from '@/stores/user'

const theme = useThemeStore()
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
  if (code === 1005) return raw || '设置值超出了允许范围'
  if (code === 400) return raw || '参数校验没有通过'
  return raw || '保存没有成功'
}

/** 主题 store 的 watch 只负责落盘；这里补上"改值即生效"的一环 */
watch(
  () => [theme.brand, theme.mode, theme.pattern, theme.pageBg, theme.customBgUrl, theme.mascot, theme.radius, theme.particle],
  () => theme.apply(),
)

const fallbackBrand = theme.PRESETS[0]?.color ?? '#c06a46'

/** <input type=color> 只认 #rrggbb，非规范值先兜底再交给取色器 */
const colorHex = computed({
  get: () => (/^#[0-9a-fA-F]{6}$/.test(theme.brand) ? theme.brand : fallbackBrand),
  set: (value: string) => {
    theme.brand = value
  },
})

const brandPresetName = computed(
  () => theme.PRESETS.find((p) => p.color.toLowerCase() === theme.brand.toLowerCase())?.name ?? '自定义',
)

function pickPreset(color: string) {
  theme.brand = color
}

type PageBgItem = { key: string; name: string; pattern: PatternKey; tint: string }

function pickPageBg(bg: PageBgItem) {
  theme.pageBg = bg.key
  theme.pattern = bg.pattern
}

function isBgActive(bg: PageBgItem) {
  return theme.pageBg === bg.key
}

const bgInput = ref<HTMLInputElement | null>(null)
const bgUploading = ref(false)

function pickCustomBg() {
  bgInput.value?.click()
}

async function onBgPicked(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (file.size > 3 * 1024 * 1024) {
    say('err', '背景图超过 3MB，换张小一点的吧')
    return
  }
  bgUploading.value = true
  try {
    const res = await fileApi.upload(file, 'image')
    if (!res.url) {
      say('err', '上传接口没有返回图片地址')
      return
    }
    theme.customBgUrl = res.url
    theme.pageBg = 'custom'
    say('ok', '自定义背景已应用，别忘了点下方「保存到账号」')
  } catch (error) {
    say('err', '背景上传失败：' + explain(error))
  } finally {
    bgUploading.value = false
  }
}

function clearCustomBg() {
  const back = theme.PAGE_BGS[0]
  theme.pageBg = back?.key ?? 'paper'
  theme.pattern = back?.pattern ?? 'none'
}

const mascots: { key: MascotKey; name: string; desc: string }[] = [
  { key: 'lantern', name: '小灯笼', desc: '夜里给你留一盏' },
  { key: 'bird', name: '圆尾鸟', desc: '停在笔尖打盹' },
  { key: 'nest', name: '暖窝', desc: '句子们住的地方' },
  { key: 'none', name: '不需要', desc: '安安静静写字' },
]

function pickMascot(key: MascotKey) {
  theme.mascot = key
}

const saving = ref(false)

async function saveToAccount() {
  saving.value = true
  try {
    await userStore.saveSettings(theme.toServerPayload())
    say('ok', '外观已保存到账号，换设备登录后也会是这样')
  } catch (error) {
    const code = getCode(error)
    const reason = code === 1005 ? '有设置超出了允许范围' : code === 400 ? '参数校验没有通过' : explain(error)
    say('err', `${reason}——界面上的改动依然生效，但没有存进账号`, 6500)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="card sec">
    <h2 class="sec__title">外观主题</h2>
    <p class="sec__desc muted">下面的改动即刻生效；想留在账号里，记得点底部的「保存到账号」。</p>

    <div v-if="feedback" class="fb" :class="`fb--${feedback.kind}`" role="status">{{ feedback.text }}</div>

    <!-- 主色 -->
    <div class="group">
      <h3 class="group__title">主色 · {{ brandPresetName }}</h3>
      <div class="swatches">
        <button
          v-for="preset in theme.PRESETS"
          :key="preset.color"
          type="button"
          class="swatch"
          :class="{ 'swatch--active': theme.brand.toLowerCase() === preset.color.toLowerCase() }"
          :style="{ background: preset.color }"
          v-tooltip="preset.name"
          :aria-label="`主色：${preset.name}`"
          @click="pickPreset(preset.color)"
        ></button>
        <label class="swatch swatch--custom" v-tooltip="'自由取色'">
          <input v-model="colorHex" type="color" class="swatch__color-input" aria-label="自定义主色" />
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true">
            <path d="M12 3.5c3.8 3.9 6.2 6.9 6.2 9.8a6.2 6.2 0 1 1-12.4 0c0-2.9 2.4-5.9 6.2-9.8Z" />
          </svg>
        </label>
      </div>
    </div>

    <hr class="divider" />

    <!-- 深浅色 -->
    <div class="group group--line">
      <div>
        <h3 class="group__title">日间 / 夜间</h3>
        <p class="muted tiny">夜间只是把纸换成暖褐，不做纯黑，深夜写字不刺眼。</p>
      </div>
      <div class="segment" role="group" aria-label="深浅色">
        <button type="button" class="segment__btn" :class="{ 'segment__btn--active': theme.mode === 'light' }" @click="theme.mode = 'light'">
          <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true">
            <circle cx="12" cy="12" r="4" />
            <path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M18.4 5.6L17 7M7 17l-1.4 1.4" />
          </svg>
          日间
        </button>
        <button type="button" class="segment__btn" :class="{ 'segment__btn--active': theme.mode === 'dark' }" @click="theme.mode = 'dark'">
          <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M19.5 14.5A8 8 0 1 1 9.5 4.5a6.6 6.6 0 0 0 10 10Z" />
          </svg>
          夜间
        </button>
      </div>
    </div>

    <hr class="divider" />

    <!-- 书页背景 -->
    <div class="group">
      <h3 class="group__title">书页背景</h3>
      <div class="bg-grid">
        <button
          v-for="bg in theme.PAGE_BGS"
          :key="bg.key"
          type="button"
          class="bg-item"
          :class="{ 'bg-item--active': isBgActive(bg) }"
          @click="pickPageBg(bg)"
        >
          <span class="bg-item__swatch" :class="`pattern-${bg.pattern}`" :style="{ backgroundColor: bg.tint }"></span>
          <span class="bg-item__name">{{ bg.name }}</span>
        </button>
        <button type="button" class="bg-item" :disabled="bgUploading" @click="pickCustomBg">
          <span class="bg-item__swatch bg-item__swatch--upload">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true">
              <path d="M12 16.5V7.5M8.5 11L12 7.5l3.5 3.5" />
              <path d="M4.5 16.5v2a1.5 1.5 0 0 0 1.5 1.5h12a1.5 1.5 0 0 0 1.5-1.5v-2" />
            </svg>
            <small>{{ bgUploading ? '上传中' : '上传图片' }}</small>
          </span>
          <span class="bg-item__name">自定义</span>
        </button>
      </div>
      <input ref="bgInput" type="file" accept="image/png,image/jpeg,image/webp" hidden @change="onBgPicked" />

      <div v-if="theme.pageBg === 'custom' && theme.customBgUrl" class="custom-row">
        <span class="chip chip--brand">当前使用自定义图片</span>
        <code class="custom-row__url muted">{{ theme.customBgUrl }}</code>
        <button type="button" class="btn btn--sm btn--ghost" @click="clearCustomBg">换回预设</button>
      </div>

      <div class="bg-preview" :class="theme.currentPatternClass" :style="theme.pageBackgroundStyle" aria-hidden="true">
        <span class="bg-preview__line"></span>
        <span class="bg-preview__line bg-preview__line--short"></span>
      </div>
    </div>

    <hr class="divider" />

    <!-- 圆角 -->
    <div class="group group--line">
      <div>
        <h3 class="group__title">圆角半径</h3>
        <p class="muted tiny">卡片与输入框的圆润程度，6 更利落，26 更柔软。</p>
      </div>
      <div class="range-line">
        <input v-model.number="theme.radius" type="range" min="6" max="26" step="1" aria-label="圆角半径" />
        <span class="range-line__value metric">{{ theme.radius }}px</span>
      </div>
    </div>

    <hr class="divider" />

    <!-- 吉祥物 -->
    <div class="group">
      <h3 class="group__title">陪伴吉祥物</h3>
      <div class="mascot-grid">
        <button
          v-for="m in mascots"
          :key="m.key"
          type="button"
          class="mascot"
          :class="{ 'mascot--active': theme.mascot === m.key }"
          @click="pickMascot(m.key)"
        >
          <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <template v-if="m.key === 'lantern'">
              <path d="M12 2.6v1.8" />
              <path d="M9.4 5.2h5.2" />
              <ellipse cx="12" cy="11.6" rx="5.4" ry="6.2" />
              <path d="M8.4 8.8v5.6M15.6 8.8v5.6" />
              <path d="M12 17.8v2.8" />
            </template>
            <template v-else-if="m.key === 'bird'">
              <circle cx="11" cy="11.6" r="6.2" />
              <path d="M16.9 10.1l3.4 1.5-3.4 1.5" />
              <circle cx="9" cy="10.2" r="0.55" fill="currentColor" stroke="none" />
              <path d="M6.6 12.8c1.5.9 3.5.9 5 0" />
              <path d="M9.6 17.9l-.9 2.4M12.7 17.9l.9 2.4" />
            </template>
            <template v-else-if="m.key === 'nest'">
              <path d="M4.2 13.4c1.6 4.3 4.6 6.4 7.8 6.4s6.2-2.1 7.8-6.4" />
              <path d="M4.2 13.4c2.4-1.4 13.2-1.4 15.6 0" />
              <circle cx="9.4" cy="12.4" r="1.7" />
              <circle cx="12.4" cy="11.2" r="1.7" />
              <circle cx="15.3" cy="12.4" r="1.7" />
            </template>
            <template v-else>
              <circle cx="12" cy="12" r="8" />
              <path d="M6.6 17.4l10.8-10.8" />
            </template>
          </svg>
          <strong>{{ m.name }}</strong>
          <small class="muted">{{ m.desc }}</small>
        </button>
      </div>
    </div>

    <hr class="divider" />

    <!-- 粒子 -->
    <div class="group group--line">
      <div>
        <h3 class="group__title">漂浮粒子</h3>
        <p class="muted tiny">页面上缓缓上浮的细小暖点，不喜欢可以关掉。</p>
      </div>
      <label class="switch">
        <input v-model="theme.particle" type="checkbox" />
        <span class="switch__track"><span class="switch__thumb"></span></span>
      </label>
    </div>

    <div class="sec__foot">
      <button type="button" class="btn btn--primary" :disabled="saving" @click="saveToAccount">
        {{ saving ? '保存中…' : '保存到账号' }}
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

.group {
  padding: var(--sp-2) 0;
}

.group--line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
}

.group--line > div:first-child {
  min-width: 0;
}

.group__title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: var(--sp-2);
}

.group--line .group__title {
  margin-bottom: 2px;
}

.tiny {
  font-size: 12px;
  line-height: 1.6;
}

/* 主色 */
.swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.swatch {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 2px solid transparent;
  outline: 1px solid var(--paper-line);
  outline-offset: 1px;
  cursor: pointer;
  padding: 0;
  transition:
    transform var(--dur-fast) var(--ease),
    outline-color var(--dur-fast) var(--ease);
}

.swatch:hover {
  transform: scale(1.1);
}

.swatch--active {
  border-color: var(--paper-surface);
  outline: 2px solid var(--brand-text);
  outline-offset: 0;
}

.swatch--custom {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background:
    conic-gradient(from 210deg, #d98c6b, #caa07a, #8fa98a, #7c9cb8, #b5718a, #d98c6b) border-box;
  color: var(--ink);
  cursor: pointer;
}

.swatch__color-input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
  border: 0;
  padding: 0;
}

/* 深浅色 */
.segment {
  display: inline-flex;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-pill);
  padding: 3px;
  background: var(--paper-muted);
  gap: 3px;
}

.segment__btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  background: transparent;
  color: var(--ink-3);
  font: inherit;
  font-size: 13px;
  padding: 5px 14px;
  border-radius: var(--r-pill);
  cursor: pointer;
  transition:
    background var(--dur-fast) var(--ease),
    color var(--dur-fast) var(--ease);
}

.segment__btn--active {
  background: var(--paper-surface);
  color: var(--brand-text);
  box-shadow: var(--sh-1);
}

/* 书页背景 */
.bg-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(92px, 1fr));
  gap: 10px;
  margin-top: var(--sp-2);
}

.bg-item {
  display: grid;
  gap: 6px;
  justify-items: center;
  padding: 8px 6px 9px;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-surface);
  color: var(--ink-2);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  transition:
    border-color var(--dur-fast) var(--ease),
    box-shadow var(--dur-fast) var(--ease);
}

.bg-item:hover {
  border-color: var(--brand-line);
}

.bg-item--active {
  border-color: var(--brand-base);
  box-shadow: 0 0 0 2px var(--brand-soft-strong);
  color: var(--brand-text);
}

.bg-item__swatch {
  width: 100%;
  height: 46px;
  border-radius: var(--r-sm);
  border: 1px solid var(--paper-line-soft);
  background-size: 22px 22px;
}

.bg-item__swatch--upload {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  background: var(--paper-muted);
  color: var(--ink-3);
}

.bg-item__swatch--upload small {
  font-size: 11px;
}

.custom-row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
  min-width: 0;
}

.custom-row__url {
  font-size: 12px;
  font-family: var(--font-mono);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  flex: 1;
}

.bg-preview {
  margin-top: var(--sp-4);
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background-color: var(--paper-surface);
  padding: var(--sp-4);
  display: grid;
  gap: 10px;
}

.bg-preview__line {
  height: 8px;
  border-radius: var(--r-pill);
  background: color-mix(in srgb, var(--ink-4) 35%, transparent);
}

.bg-preview__line--short {
  width: 60%;
}

/* 圆角 */
.range-line {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex: none;
  min-width: 200px;
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

/* 吉祥物 */
.mascot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(128px, 1fr));
  gap: 10px;
  margin-top: var(--sp-2);
}

.mascot {
  display: grid;
  justify-items: center;
  gap: 3px;
  padding: 12px 8px;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-surface);
  color: var(--ink-2);
  font: inherit;
  cursor: pointer;
  transition:
    border-color var(--dur-fast) var(--ease),
    box-shadow var(--dur-fast) var(--ease),
    color var(--dur-fast) var(--ease);
}

.mascot:hover {
  border-color: var(--brand-line);
}

.mascot--active {
  border-color: var(--brand-base);
  box-shadow: 0 0 0 2px var(--brand-soft-strong);
  color: var(--brand-text);
}

.mascot strong {
  font-size: 13px;
  font-weight: 600;
}

.mascot small {
  font-size: 11.5px;
}

/* 开关 */
.switch {
  position: relative;
  display: inline-flex;
  align-items: center;
  flex: none;
  cursor: pointer;
}

.switch input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.switch__track {
  width: 42px;
  height: 24px;
  border-radius: var(--r-pill);
  background: var(--paper-muted);
  border: 1px solid var(--paper-line);
  position: relative;
  transition:
    background var(--dur-fast) var(--ease),
    border-color var(--dur-fast) var(--ease);
}

.switch__thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--paper-surface);
  box-shadow: var(--sh-1);
  transition: left var(--dur-fast) var(--ease);
}

.switch input:checked + .switch__track {
  background: var(--brand-base);
  border-color: var(--brand-base);
}

.switch input:checked + .switch__track .switch__thumb {
  left: 20px;
}

.switch input:focus-visible + .switch__track {
  outline: 2px solid var(--brand-text);
  outline-offset: 2px;
}

.sec__foot {
  margin-top: var(--sp-5);
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 640px) {
  .group--line {
    flex-direction: column;
    align-items: flex-start;
  }

  .range-line {
    width: 100%;
  }
}
</style>
