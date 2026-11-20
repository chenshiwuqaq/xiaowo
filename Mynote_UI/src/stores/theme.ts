import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

/** 可选装饰图案，与 styles/base.css 里的 .pattern-* 一一对应 */
export type PatternKey = 'none' | 'dot' | 'grid' | 'star' | 'cloud' | 'ruled'

/** 吉祥物：小窝的陪伴角色，空状态与 AI 思考时出现 */
export type MascotKey = 'lantern' | 'bird' | 'nest' | 'none'

/**
 * 主题状态。
 *
 * 设计原则：只有 brand 一个"真颜色"由用户决定，其余深浅色全部用 color-mix 从它派生
 * （见 tokens.css），所以换主色不会造成某个角落色号对不上的问题。
 * 本地 localStorage 保证首屏不闪，登录后再与后端 userSettings 双向同步。
 */
export const useThemeStore = defineStore('theme', () => {
  const STORAGE_KEY = 'mynote_theme'

  /** 预设主色，命名沿用文档里的暖色系意象 */
  const PRESETS: { name: string; color: string }[] = [
    { name: '暖陶', color: '#C06A46' },
    { name: '蜜桃', color: '#E3A878' },
    { name: '橄榄', color: '#8FA98A' },
    { name: '雾蓝', color: '#7C9CB8' },
    { name: '莓果', color: '#B5718A' },
    { name: '焦糖', color: '#A9743F' },
    { name: '石板', color: '#8A8D93' },
    { name: '紫罗兰', color: '#9A8BB5' },
  ]

  /** 书页背景：预设图案 + 用户自定义图片路径 */
  const PAGE_BGS: { key: string; name: string; pattern: PatternKey; tint: string }[] = [
    { key: 'paper', name: '米纸', pattern: 'none', tint: 'var(--paper-surface)' },
    { key: 'grid', name: '方格', pattern: 'grid', tint: 'var(--paper-surface)' },
    { key: 'dot', name: '点阵', pattern: 'dot', tint: 'var(--paper-surface)' },
    { key: 'ruled', name: '横线稿', pattern: 'ruled', tint: 'var(--paper-surface)' },
    { key: 'star', name: '星点', pattern: 'star', tint: 'var(--brand-soft)' },
    { key: 'cloud', name: '云朵', pattern: 'cloud', tint: 'var(--brand-soft)' },
  ]

  const brand = ref('#C06A46')
  const mode = ref<'light' | 'dark'>('light')
  const pattern = ref<PatternKey>('none')
  const pageBg = ref<string>('paper')
  const customBgUrl = ref<string>('')
  const mascot = ref<MascotKey>('lantern')
  const radius = ref<number>(16)
  const particle = ref<boolean>(true)

  function persist() {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        brand: brand.value,
        mode: mode.value,
        pattern: pattern.value,
        pageBg: pageBg.value,
        customBgUrl: customBgUrl.value,
        mascot: mascot.value,
        radius: radius.value,
        particle: particle.value,
      }),
    )
  }

  function restore() {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return
    try {
      const saved = JSON.parse(raw) as Partial<{
        brand: string
        mode: 'light' | 'dark'
        pattern: PatternKey
        pageBg: string
        customBgUrl: string
        mascot: MascotKey
        radius: number
        particle: boolean
      }>
      if (saved.brand) brand.value = saved.brand
      if (saved.mode) mode.value = saved.mode
      if (saved.pattern) pattern.value = saved.pattern
      if (saved.pageBg) pageBg.value = saved.pageBg
      if (typeof saved.customBgUrl === 'string') customBgUrl.value = saved.customBgUrl
      if (saved.mascot) mascot.value = saved.mascot
      if (typeof saved.radius === 'number') radius.value = saved.radius
      if (typeof saved.particle === 'boolean') particle.value = saved.particle
    } catch {
      localStorage.removeItem(STORAGE_KEY)
    }
  }

  /** 把状态写成 CSS 变量与属性，页面实时生效 */
  function apply() {
    const root = document.documentElement
    root.style.setProperty('--brand-base', brand.value)
    root.style.setProperty('--r-lg', `${radius.value}px`)
    root.dataset.theme = mode.value
    persist()
  }

  /** 后端设置 → 本地主题（登录时调用；后端为空字段时不覆盖本地选择） */
  function syncFromServer(settings: {
    themeColor?: string | null
    themeMode?: string | null
    noteBg?: string | null
    fontSize?: number | null
  }) {
    if (settings.themeColor) brand.value = settings.themeColor
    if (settings.themeMode === 'light' || settings.themeMode === 'dark') mode.value = settings.themeMode
    if (settings.noteBg) {
      // noteBg 既可能是图案 key，也可能是自定义图片路径
      if (settings.noteBg.startsWith('/upload/') || /^https?:/.test(settings.noteBg)) {
        customBgUrl.value = settings.noteBg
        pageBg.value = 'custom'
      } else if (PAGE_BGS.some((item) => item.key === settings.noteBg)) {
        pageBg.value = settings.noteBg
        pattern.value = PAGE_BGS.find((item) => item.key === settings.noteBg)!.pattern
      }
    }
    if (typeof settings.fontSize === 'number') {
      document.documentElement.style.setProperty('--editor-font-size', `${settings.fontSize}px`)
    }
    apply()
  }

  /** 本地主题 → 后端设置载荷（供保存按钮使用） */
  function toServerPayload() {
    return {
      themeColor: brand.value,
      themeMode: mode.value,
      noteBg: pageBg.value === 'custom' && customBgUrl.value ? customBgUrl.value : pageBg.value,
    }
  }

  const currentPatternClass = computed(() =>
    pageBg.value === 'custom' && customBgUrl.value ? 'pattern-none' : `pattern-${pattern.value}`,
  )

  const pageBackgroundStyle = computed(() =>
    pageBg.value === 'custom' && customBgUrl.value
      ? { backgroundImage: `url(${customBgUrl.value})`, backgroundSize: 'cover', backgroundAttachment: 'fixed' }
      : {},
  )

  watch([brand, mode, pattern, pageBg, customBgUrl, mascot, radius, particle], persist, { deep: true })

  restore()
  apply()

  return {
    PRESETS,
    PAGE_BGS,
    brand,
    mode,
    pattern,
    pageBg,
    customBgUrl,
    mascot,
    radius,
    particle,
    apply,
    restore,
    syncFromServer,
    toServerPayload,
    currentPatternClass,
    pageBackgroundStyle,
  }
})
