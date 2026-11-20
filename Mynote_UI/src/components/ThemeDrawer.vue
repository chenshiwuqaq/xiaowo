<script setup lang="ts">
/**
 * 主题速调抽屉。
 *
 * 顶栏的主题按钮点开后从右侧滑出：主色、深浅色、书页背景、陪伴小兽、粒子与圆角。
 * 所有改动即时生效（theme store 会落 localStorage），"保存到账号"则把关键项写回后端。
 * 完整的外观设置仍在「个人设置 - 外观主题」里，这里只放最常用的几项，减少抽屉里的认知负担。
 */
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fileApi } from '@/api'
import { getCode } from '@/api/http'
import { useThemeStore, type MascotKey } from '@/stores/theme'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const theme = useThemeStore()
const userStore = useUserStore()
const router = useRouter()

/** 改动即时生效：theme store 的 watch 只负责落盘，这里补上"改值即写 CSS 变量" */
watch(
  () => [theme.brand, theme.mode, theme.pattern, theme.pageBg, theme.customBgUrl, theme.mascot, theme.radius, theme.particle],
  () => theme.apply(),
)

const fallbackBrand = theme.PRESETS[0]?.color ?? '#c06a46'

const colorHex = computed({
  get: () => (/^#[0-9a-fA-F]{6}$/.test(theme.brand) ? theme.brand : fallbackBrand),
  set: (value: string) => {
    theme.brand = value
  },
})

const brandPresetName = computed(
  () => theme.PRESETS.find((p) => p.color.toLowerCase() === theme.brand.toLowerCase())?.name ?? '自定义',
)

const mascots: { key: MascotKey; name: string }[] = [
  { key: 'lantern', name: '小灯笼' },
  { key: 'bird', name: '圆尾鸟' },
  { key: 'nest', name: '暖窝' },
  { key: 'none', name: '不需要' },
]

const bgInput = ref<HTMLInputElement | null>(null)
const bgUploading = ref(false)
const saving = ref(false)

function pickCustomBg() {
  bgInput.value?.click()
}

async function onBgPicked(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (file.size > 3 * 1024 * 1024) {
    ElMessage.warning('背景图超过 3MB，换张小一点的吧')
    return
  }
  bgUploading.value = true
  try {
    const res = await fileApi.upload(file, 'image')
    if (!res.url) {
      ElMessage.error('上传接口没有返回图片地址')
      return
    }
    theme.customBgUrl = res.url
    theme.pageBg = 'custom'
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '背景上传失败')
  } finally {
    bgUploading.value = false
  }
}

async function saveToAccount() {
  saving.value = true
  try {
    await userStore.saveSettings(theme.toServerPayload())
    ElMessage.success('外观已保存到账号')
    emit('update:open', false)
  } catch (error) {
    const code = getCode(error)
    ElMessage.warning(code === 1005 || code === 400 ? '有设置超出范围，界面改动已生效但未存进账号' : (error instanceof Error ? error.message : '保存失败'))
  } finally {
    saving.value = false
  }
}

function gotoFullSettings() {
  emit('update:open', false)
  router.push('/settings')
}
</script>

<template>
  <Transition name="drawer">
    <div v-if="props.open" class="drawer-root" @click.self="emit('update:open', false)">
      <aside class="drawer card" role="dialog" aria-label="主题设置">
        <header class="drawer__head">
          <div>
            <h2 class="drawer__title">把灯调到舒服的亮度</h2>
            <p class="muted">下面的改动立刻生效，想留在账号里点底部保存。</p>
          </div>
          <button class="btn btn--ghost btn--icon" type="button" aria-label="关闭" @click="emit('update:open', false)">✕</button>
        </header>

        <div class="drawer__body">
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
                @click="theme.brand = preset.color"
              ></button>
              <label class="swatch swatch--custom" v-tooltip="'自由取色'">
                <input v-model="colorHex" type="color" class="swatch__input" aria-label="自定义主色" />
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true">
                  <path d="M12 3.5c3.8 3.9 6.2 6.9 6.2 9.8a6.2 6.2 0 1 1-12.4 0c0-2.9 2.4-5.9 6.2-9.8Z" />
                </svg>
              </label>
            </div>
          </div>

          <!-- 深浅色 -->
          <div class="group group--line">
            <div>
              <h3 class="group__title">日间 / 夜间</h3>
              <p class="muted tiny">夜间是暖褐纸，不是纯黑。</p>
            </div>
            <div class="segment" role="group" aria-label="深浅色">
              <button type="button" class="segment__btn" :class="{ 'segment__btn--active': theme.mode === 'light' }" @click="theme.mode = 'light'">☀ 日间</button>
              <button type="button" class="segment__btn" :class="{ 'segment__btn--active': theme.mode === 'dark' }" @click="theme.mode = 'dark'">☾ 夜间</button>
            </div>
          </div>

          <!-- 书页背景 -->
          <div class="group">
            <h3 class="group__title">书页背景</h3>
            <div class="bg-grid">
              <button
                v-for="bg in theme.PAGE_BGS"
                :key="bg.key"
                type="button"
                class="bg-item"
                :class="{ 'bg-item--active': theme.pageBg === bg.key }"
                @click="theme.pageBg = bg.key; theme.pattern = bg.pattern"
              >
                <span class="bg-item__swatch" :class="`pattern-${bg.pattern}`" :style="{ backgroundColor: bg.tint }"></span>
                <span class="bg-item__name">{{ bg.name }}</span>
              </button>
              <button type="button" class="bg-item" :disabled="bgUploading" @click="pickCustomBg">
                <span class="bg-item__swatch bg-item__swatch--upload">{{ bgUploading ? '上传中' : '＋' }}</span>
                <span class="bg-item__name">自定义</span>
              </button>
            </div>
            <input ref="bgInput" type="file" accept="image/png,image/jpeg,image/webp" hidden @change="onBgPicked" />
          </div>

          <!-- 吉祥物 -->
          <div class="group">
            <h3 class="group__title">陪伴小兽</h3>
            <div class="mascot-grid">
              <button
                v-for="m in mascots"
                :key="m.key"
                type="button"
                class="mascot"
                :class="{ 'mascot--active': theme.mascot === m.key }"
                @click="theme.mascot = m.key"
              >
                {{ m.name }}
              </button>
            </div>
          </div>

          <!-- 圆角 -->
          <div class="group group--line">
            <div>
              <h3 class="group__title">圆角半径</h3>
              <p class="muted tiny">6 更利落，26 更柔软。</p>
            </div>
            <div class="range-line">
              <input v-model.number="theme.radius" type="range" min="6" max="26" step="1" aria-label="圆角半径" />
              <span class="range-line__value metric">{{ theme.radius }}px</span>
            </div>
          </div>

          <!-- 粒子 -->
          <div class="group group--line">
            <div>
              <h3 class="group__title">漂浮粒子</h3>
              <p class="muted tiny">页面上缓缓上浮的暖点。</p>
            </div>
            <label class="switch">
              <input v-model="theme.particle" type="checkbox" />
              <span class="switch__track"><span class="switch__thumb"></span></span>
            </label>
          </div>
        </div>

        <footer class="drawer__foot">
          <button type="button" class="btn btn--ghost" @click="gotoFullSettings">完整设置</button>
          <button type="button" class="btn btn--primary" :disabled="saving" @click="saveToAccount">
            {{ saving ? '保存中…' : '保存到账号' }}
          </button>
        </footer>
      </aside>
    </div>
  </Transition>
</template>

<style scoped>
.drawer-root {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: color-mix(in srgb, #1c120c 32%, transparent);
  display: flex;
  justify-content: flex-end;
}

.drawer {
  width: min(360px, 100vw);
  height: 100%;
  border-radius: var(--r-lg) 0 0 var(--r-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.drawer__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-4) var(--sp-4) var(--sp-3);
  border-bottom: 1px solid var(--paper-line);
}

.drawer__title {
  font-size: 16px;
}

.drawer__head .muted {
  font-size: 12px;
  margin-top: 2px;
}

.drawer__body {
  flex: 1;
  overflow-y: auto;
  padding: var(--sp-4);
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.drawer__foot {
  display: flex;
  justify-content: space-between;
  gap: var(--sp-2);
  padding: var(--sp-3) var(--sp-4);
  border-top: 1px solid var(--paper-line);
}

.group {
  padding: var(--sp-1) 0;
}

.group--line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
}

.group--line > div:first-child {
  min-width: 0;
}

.group__title {
  font-size: 13px;
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

.swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.swatch {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 2px solid transparent;
  outline: 1px solid var(--paper-line);
  outline-offset: 1px;
  cursor: pointer;
  padding: 0;
}

.swatch--active {
  outline: 2px solid var(--brand-text);
  outline-offset: 0;
}

.swatch--custom {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: conic-gradient(from 210deg, #d98c6b, #caa07a, #8fa98a, #7c9cb8, #b5718a, #d98c6b);
  color: var(--ink);
}

.swatch__input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

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
  gap: 5px;
  border: 0;
  background: transparent;
  color: var(--ink-3);
  font: inherit;
  font-size: 12px;
  padding: 4px 12px;
  border-radius: var(--r-pill);
  cursor: pointer;
}

.segment__btn--active {
  background: var(--paper-surface);
  color: var(--brand-text);
  box-shadow: var(--sh-1);
}

.bg-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(78px, 1fr));
  gap: 8px;
  margin-top: var(--sp-1);
}

.bg-item {
  display: grid;
  gap: 5px;
  justify-items: center;
  padding: 6px 4px 8px;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-surface);
  color: var(--ink-2);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}

.bg-item--active {
  border-color: var(--brand-base);
  box-shadow: 0 0 0 2px var(--brand-soft-strong);
  color: var(--brand-text);
}

.bg-item__swatch {
  width: 100%;
  height: 34px;
  border-radius: var(--r-sm);
  border: 1px solid var(--paper-line-soft);
  background-size: 22px 22px;
}

.bg-item__swatch--upload {
  display: grid;
  place-items: center;
  background: var(--paper-muted);
  color: var(--ink-3);
  font-size: 14px;
}

.mascot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(88px, 1fr));
  gap: 8px;
  margin-top: var(--sp-1);
}

.mascot {
  padding: 8px;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-surface);
  color: var(--ink-2);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}

.mascot--active {
  border-color: var(--brand-base);
  box-shadow: 0 0 0 2px var(--brand-soft-strong);
  color: var(--brand-text);
}

.range-line {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex: none;
  min-width: 140px;
}

input[type='range'] {
  flex: 1;
  accent-color: var(--brand-base);
}

.range-line__value {
  min-width: 42px;
  text-align: right;
  font-size: 13px;
  color: var(--ink-2);
}

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
  width: 40px;
  height: 22px;
  border-radius: var(--r-pill);
  background: var(--paper-muted);
  border: 1px solid var(--paper-line);
  position: relative;
  transition: background var(--dur-fast) var(--ease), border-color var(--dur-fast) var(--ease);
}

.switch__thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
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

/* 抽屉动画 */
.drawer-enter-active,
.drawer-leave-active {
  transition: opacity var(--dur-base) var(--ease);
}

.drawer-enter-active .drawer,
.drawer-leave-active .drawer {
  transition: transform var(--dur-base) var(--ease);
}

.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}

.drawer-enter-from .drawer,
.drawer-leave-to .drawer {
  transform: translateX(100%);
}
</style>
