<script setup lang="ts">
/**
 * Vditor 编辑器封装。
 *
 * 三个设计决定：
 * 1. cdn 指向本地 public/vditor，Vditor 的 lute.min.js / 图标 / 多语言默认从 unpkg 取，
 *    断网或内网环境会直接白屏，本地化之后离线可用；
 * 2. 不用 Vditor 的 localStorage cache（enable:false）——笔记的真相在服务端，
 *    本地缓存会造成"刷新后看到旧内容"的错觉，自动保存已经承担了防丢职责；
 * 3. ready 之前收到的 setValue 调用要排队，否则异步加载完成时会把用户已经输入的内容覆盖掉。
 */
import Vditor from 'vditor'
// Vditor v4 的 UMD 包不会自动注入样式，必须手动引入，否则工具栏/下拉菜单会无样式堆叠
import 'vditor/dist/index.css'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fileApi } from '@/api'
import { useThemeStore } from '@/stores/theme'

type EditorMode = 'ir' | 'wysiwyg' | 'sv'

const props = withDefaults(
  defineProps<{
    modelValue: string
    mode?: EditorMode
    placeholder?: string
    fontSize?: number
    disabled?: boolean
  }>(),
  {
    mode: 'ir',
    placeholder: '从这里开始写。支持 Markdown，也可以直接切换所见即所得。',
    fontSize: 16,
    disabled: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  selection: [text: string]
  ready: []
  /** Ctrl+S 交给父级处理保存，编辑器自己不发请求 */
  save: []
  blur: [value: string]
}>()

const theme = useThemeStore()
const holder = ref<HTMLDivElement | null>(null)
const editorId = 'mynote-vditor-holder'
let instance: Vditor | null = null
/** ready 之前的赋值先缓存，ready 后一次性补上 */
let pendingValue: string | null = null
let syncing = false

/** 工具栏：常用 Markdown 能力 + 内置模式切换，精简到单行可容纳，避免折行吞掉正文 */
const TOOLBAR: (string | any)[] = [
  'headings',
  'bold',
  'italic',
  'strike',
  'link',
  '|',
  'list',
  'ordered-list',
  'check',
  'quote',
  'line',
  'code',
  'inline-code',
  'table',
  '|',
  'undo',
  'redo',
  '|',
  'edit-mode',
  'both',
  '|',
  'upload',
  'fullscreen',
]

function buildOptions(): any {
  return {
    cdn: '/vditor',
    lang: 'zh_CN',
    mode: props.mode,
    height: '100%',
    minHeight: 240,
    placeholder: props.placeholder,
    theme: theme.mode === 'dark' ? 'dark' : 'classic',
    icon: 'ant',
    iconTheme: 'outlined',
    toolbar: TOOLBAR,
    toolbarConfig: { pin: true },
    preview: {
      hljs: { style: theme.mode === 'dark' ? 'native' : 'github', lineNumber: false },
      theme: { current: theme.mode === 'dark' ? 'dark' : 'light' },
      markdown: {
        // 允许脚注与 GFM 任务列表，科研笔记常用到脚注
        footnotes: true,
        autoSpace: true,
        toc: true,
        // linkPrefix 是"链接强制前缀"，必须为字符串；传空对象会被当成 "[object Object]" 加到图片 URL 前
        linkPrefix: '',
        mark: true,
      },
      actions: ['desktop', 'tablet', 'mobile'],
    },
    counter: { enable: true, type: 'markdown' },
    cache: { enable: false },
    typewriterMode: false,
    undoDelay: 800,
    // 图片粘贴/拖拽/上传都走后端 /api/file/upload，落本机磁盘并以相对路径引用
    upload: {
      multiple: true,
      accept: 'image/*',
      handler: async (files: File[]): Promise<string | null> => {
        if (!files?.length) return null
        try {
          const urls: string[] = []
          for (const file of files) {
            const result = await fileApi.upload(file, 'image')
            urls.push(`![${result.name}](${result.url})`)
          }
          instance?.insertValue(urls.join('\n'))
          return null
        } catch (error) {
          instance?.tip(error instanceof Error ? error.message : '图片上传失败')
          return error instanceof Error ? error.message : '图片上传失败'
        }
      },
    },
    after: () => {
      const editor = vditorRef
      instance = editor
      if (pendingValue !== null && editor) {
        editor.setValue(pendingValue)
        pendingValue = null
      }
      if (props.disabled && editor) editor.disabled()
      applyFontScale()
      emit('ready')
    },
    input: (value: string) => {
      emitSelection()
      if (syncing) return
      emit('update:modelValue', value)
    },
    blur: (value: string) => {
      emit('blur', value)
      emitSelection()
    },
    keydown: (event: KeyboardEvent) => {
      const saveCombo = (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's'
      if (saveCombo) {
        event.preventDefault()
        emit('save')
      }
    },
  }
}

let vditorRef: Vditor | null = null

onMounted(() => {
  if (!holder.value) return
  holder.value.id = editorId
  vditorRef = new Vditor(editorId, buildOptions())
})

/** 正文字号：Vditor 内部类名较多，直接改它的编辑区 font-size */
function applyFontScale() {
  const root = document.getElementById(editorId)
  if (!root) return
  root.querySelectorAll('.vditor-ir pre.vditor-reset, .vditor-wysiwyg pre.vditor-reset, .vditor-sv textarea').forEach(
    (el) => {
      ;(el as HTMLElement).style.fontSize = `${props.fontSize}px`
      ;(el as HTMLElement).style.lineHeight = '1.85'
    },
  )
}

let selectionRange: Range | null = null

function emitSelection() {
  const text = instance?.getSelection?.() ?? ''
  emit('selection', text)
  // 记录当前浏览器选区，供改写时高亮"正在处理的段落"
  const sel = window.getSelection()
  if (sel && sel.rangeCount > 0 && sel.toString().trim()) {
    selectionRange = sel.getRangeAt(0).cloneRange()
  }
}

/** 高亮当前选中段落（用 CSS Custom Highlight，不侵入编辑器 DOM） */
function highlightRewriteSelection(): boolean {
  if (!selectionRange) return false
  try {
    const HighlightCtor = (window as unknown as { Highlight?: new (...r: Range[]) => unknown }).Highlight
    const highlights = (CSS as unknown as { highlights?: { set: (k: string, v: unknown) => void } }).highlights
    if (!HighlightCtor || !highlights) return false
    highlights.set('mynote-rewrite', new HighlightCtor(selectionRange))
    return true
  } catch {
    return false
  }
}

function clearRewriteHighlight() {
  try {
    const highlights = (CSS as unknown as { highlights?: { delete: (k: string) => void } }).highlights
    highlights?.delete('mynote-rewrite')
  } catch {
    /* 忽略 */
  }
}

/** 外部改值时同步进编辑器（例如 AI 改写覆盖后回填） */
watch(
  () => props.modelValue,
  (value) => {
    if (!instance) {
      pendingValue = value
      return
    }
    if (instance.getValue() === value) return
    syncing = true
    instance.setValue(value ?? '')
    syncing = false
    applyFontScale()
  },
)

watch(() => props.fontSize, applyFontScale)

watch(
  () => theme.mode,
  () => {
    instance?.setTheme(theme.mode === 'dark' ? 'dark' : 'classic')
  },
)

watch(
  () => props.disabled,
  (disabled) => {
    if (!instance) return
    if (disabled) instance.disabled()
    else instance.enable()
  },
)

/** 供父组件直接调用 */
defineExpose({
  focus: () => instance?.focus(),
  getValue: () => instance?.getValue() ?? props.modelValue,
  getSelection: () => instance?.getSelection?.() ?? '',
  insertText: (text: string) => instance?.insertValue(text),
  isReady: () => Boolean(instance),
  highlightRewriteSelection,
  clearRewriteHighlight,
})

onBeforeUnmount(() => {
  // Vditor 内部有监听与定时器，不 destroy 会在路由来回切换后残留
  try {
    instance?.destroy()
  } catch {
    /* 未 ready 就卸载时 destroy 会抛错，忽略 */
  }
  instance = null
  vditorRef = null
})
</script>

<template>
  <div class="editor-shell">
    <div ref="holder" class="editor-host" />
  </div>
</template>

<style scoped>
.editor-shell {
  height: 100%;
  min-height: 0;
  position: relative;
}

.editor-host {
  height: 100%;
}

/* 让编辑器融进纸面，而不是顶着一块白色控件 */
.editor-host :deep(.vditor) {
  border: 0;
  border-radius: 0 var(--r-md) var(--r-md) 0;
  background: transparent;
}

.editor-host :deep(.vditor-toolbar) {
  background: color-mix(in srgb, var(--paper-surface) 80%, transparent);
  border-bottom: 1px solid var(--paper-line-soft);
  padding: 4px var(--sp-3) !important;
}

.editor-host :deep(.vditor-content) {
  background: transparent;
}

/* Vditor 会按 preview.maxWidth 给 pre.vditor-reset 动态加两侧 padding 做居中，这里用 !important 覆盖让正文铺满宽度 */
.editor-host :deep(.vditor-ir pre.vditor-reset),
.editor-host :deep(.vditor-wysiwyg pre.vditor-reset) {
  padding: var(--sp-4) var(--sp-3) !important;
  color: var(--ink);
  font-family: var(--font-sans);
}

.editor-host :deep(.vditor-sv) {
  padding: var(--sp-4) var(--sp-3);
  color: var(--ink);
  font-family: var(--font-sans);
}

.editor-host :deep(.vditor-sv .vditor-outline),
.editor-host :deep(.vditor-content) {
  background: transparent;
}

.editor-host :deep(.vditor-counter) {
  color: var(--ink-3);
  background: var(--brand-soft);
}

.editor-host :deep(.vditor-hint) {
  background: var(--paper-raised);
  border-color: var(--paper-line);
  color: var(--ink);
}

html[data-theme='dark'] .editor-host :deep(.vditor-reset),
html[data-theme='dark'] .editor-host :deep(textarea) {
  color: var(--ink);
}
</style>
