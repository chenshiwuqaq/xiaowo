<script setup lang="ts">
/**
 * 书写页：整个产品的核心工作区。
 *
 * 三栏布局：
 *   左 —— 目录树 + 标签筛选 + 新建/导入/逻辑图库/回收站；
 *   中 —— 笔记列表（搜索、多选、批量操作）；
 *   右 —— 编辑器 + AI 助手面板（绑定在书页右侧）。
 *
 * 编辑器的"真相"在 notes store 的 current，本页维护标题/类型/标签/正文的本地镜像，
 * 配合 Vditor 的 v-model 做脏标记与防抖自动保存；Ctrl+S 手动保存。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { aiApi, diagramApi, folderApi, noteApi } from '@/api'
import { initMermaid, renderMermaid } from '@/utils/mermaid'
import type { Diagram, FolderNode, RewritePreview } from '@/api/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import FolderTree from '@/components/notes/FolderTree.vue'
import NoteEditor from '@/components/notes/NoteEditor.vue'
import AiPanel from '@/components/notes/AiPanel.vue'
import { parseAiFeatureMap, useNotesStore } from '@/stores/notes'
import { useThemeStore } from '@/stores/theme'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const notes = useNotesStore()
const theme = useThemeStore()
const userStore = useUserStore()

// ---------- 编辑器本地镜像 ----------
const editorContent = ref('')
const titleRef = ref('')
const noteTypeRef = ref<number>(4)
const tagNamesRef = ref<string[]>([])
const editorRef = ref<InstanceType<typeof NoteEditor> | null>(null)
const aiOpen = ref(false)
const tagInput = ref('')

const fontSize = computed(() => userStore.profile?.fontSize ?? 16)
const enabledTools = computed(() => {
  const flags = parseAiFeatureMap(userStore.profile?.aiFeatureMap)[notes.current?.noteType ?? 4] ?? {}
  return Object.entries(flags)
    .filter(([, enabled]) => enabled)
    .map(([id]) => id)
})
const selectedIds = ref<Set<number>>(new Set())
const showTrash = ref(false)
const batchCollapsed = ref(false)
const moveMenuOpen = ref(false)
const moveMenuExpanded = ref<Set<number>>(new Set())

// ---------- 对话框 ----------
const diagramDialog = ref(false)
const diagramLoading = ref(false)
const diagramType = ref('mindmap')
const diagramCode = ref('')
const diagramTitle = ref('')
const diagramSvg = ref('')

const libraryDialog = ref(false)
const libraryList = ref<Diagram[]>([])
const libraryLoading = ref(false)
const librarySvg = ref('')
const libraryCurrent = ref<Diagram | null>(null)

// ---------- 搜索 / 标签 ----------
let searchTimer: ReturnType<typeof setTimeout> | undefined
const searchInput = ref('')

const activeTag = computed(() => notes.filterTagId)
const allSelected = computed(() => notes.notes.length > 0 && notes.notes.every((n) => selectedIds.value.has(n.id)))
const selectedCount = computed(() => selectedIds.value.size)

// ---------- 初始化 ----------
onMounted(async () => {
  await Promise.all([notes.loadFolders(), notes.loadTags(), notes.refreshAiStatus()])
  await notes.loadNotes()
  // 从主页跳转过来的 ?open=id，直接打开对应笔记，并把目录切到该笔记所在位置
  const openId = Number(route.query.open)
  if (Number.isFinite(openId) && openId > 0) {
    await openNote(openId)
    if (notes.current) {
      notes.activeFolderId = notes.current.folderId
      await notes.loadNotes()
    }
    router.replace({ path: '/notes' })
  }
})

onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  // 离开页面（如点小窝切换路由）时若还有未保存的改动，立即落库，避免正文在防抖触发前被丢弃
  if (notes.dirty && notes.current?.id) {
    void saveCurrent().catch(() => undefined)
  }
})

// ---------- 笔记打开 / 新建 ----------
function syncFromCurrent() {
  const cur = notes.current
  programmaticSync = true
  editorContent.value = cur?.content ?? ''
  titleRef.value = cur?.title ?? ''
  noteTypeRef.value = cur?.noteType ?? 4
  tagNamesRef.value = (cur?.tags ?? []).map((t) => t.name)
  nextTick(() => {
    programmaticSync = false
  })
}

async function openNote(id: number) {
  if (notes.dirty && notes.current?.id) {
    await saveCurrent().catch(() => undefined)
  }
  showTrash.value = false
  await notes.openNote(id)
  syncFromCurrent()
}

async function createNote(folderId?: number, noteType?: number) {
  if (notes.dirty && notes.current?.id) {
    await saveCurrent().catch(() => undefined)
  }
  const created = await notes.createNote({ folderId, noteType })
  if (created) {
    syncFromCurrent()
    showTrash.value = false
  }
}

// ---------- 保存（防抖自动 + Ctrl+S 手动） ----------
let programmaticSync = false
let autoSaveTimer: ReturnType<typeof setTimeout> | undefined

watch(editorContent, () => {
  if (programmaticSync) return
  notes.markDirty()
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  autoSaveTimer = setTimeout(() => {
    void saveCurrent().catch(() => undefined)
  }, 1800)
})

async function saveCurrent() {
  if (!notes.current?.id) return
  try {
    await notes.saveCurrent({
      title: titleRef.value.trim() || undefined,
      content: editorContent.value,
      noteType: noteTypeRef.value,
      tagNames: tagNamesRef.value,
      snapshot: true,
    })
    ElMessage.success('已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  }
}

// ---------- 标签编辑 ----------
function addTag() {
  const name = tagInput.value.trim()
  if (!name) return
  if (tagNamesRef.value.includes(name)) {
    tagInput.value = ''
    return
  }
  tagNamesRef.value.push(name)
  tagInput.value = ''
  notes.markDirty()
}

function removeTag(name: string) {
  tagNamesRef.value = tagNamesRef.value.filter((t) => t !== name)
  notes.markDirty()
}

// ---------- 多选与批量 ----------
function toggleSelect(id: number) {
  const next = new Set(selectedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedIds.value = next
}

function toggleSelectAll() {
  selectedIds.value = allSelected.value ? new Set() : new Set(notes.notes.map((n) => n.id))
}

function clearSelection() {
  selectedIds.value = new Set()
}

async function batchTrash() {
  const ids = [...selectedIds.value]
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`把选中的 ${ids.length} 篇笔记收进回收站？`, '批量删除', {
      confirmButtonText: '收进回收站',
      cancelButtonText: '再想想',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await noteApi.batchTrash(ids)
    ElMessage.success('已收进回收站')
    clearSelection()
    if (notes.current && ids.includes(notes.current.id)) notes.closeNote()
    await Promise.all([notes.loadNotes(), notes.loadFolders()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量删除失败')
  }
}

async function batchMove(folderId: number) {
  const ids = [...selectedIds.value]
  if (!ids.length) return
  try {
    await noteApi.batchMove({ ids, folderId })
    ElMessage.success('已移动')
    clearSelection()
    await Promise.all([notes.loadNotes(), notes.loadFolders()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '移动失败')
  }
}

function moveTo(folderId: number) {
  moveMenuOpen.value = false
  void batchMove(folderId)
}

function toggleMoveFolder(id: number) {
  const next = new Set(moveMenuExpanded.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  moveMenuExpanded.value = next
}

// 移动下拉里的目录按"父→子"展开，只把展开的目录子树拍平供渲染
const moveMenuFolders = computed(() => {
  const out: { id: number; name: string; depth: number; hasChildren: boolean }[] = []
  const walk = (list: FolderNode[], depth: number) => {
    for (const node of list) {
      out.push({ id: node.id, name: node.name, depth, hasChildren: Boolean(node.children?.length) })
      if (node.children?.length && moveMenuExpanded.value.has(node.id)) {
        walk(node.children, depth + 1)
      }
    }
  }
  walk(notes.folders, 0)
  return out
})

// 清空选择时一并收起工具条、下拉与目录展开状态，避免下次选中时残留旧状态
watch(selectedCount, (n) => {
  if (n === 0) {
    batchCollapsed.value = false
    moveMenuOpen.value = false
    moveMenuExpanded.value = new Set()
  }
})

// ---------- 导入 / 导出 ----------
const importInput = ref<HTMLInputElement | null>(null)
const exportMenuOpen = ref(false)
const typeMenuOpen = ref(false)

const currentTypeLabel = computed(
  () => TYPE_OPTIONS.find((t) => t.value === noteTypeRef.value)?.label ?? TYPE_OPTIONS[3].label,
)

function pickType(value: number) {
  noteTypeRef.value = value
  typeMenuOpen.value = false
  notes.markDirty()
}

function pickImport() {
  importInput.value?.click()
}

async function onImport(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (!files.length) return
  try {
    const result = await noteApi.importFiles(files, notes.activeFolderId, noteTypeRef.value)
    ElMessage.success(`导入完成：成功 ${result.success} 篇，失败 ${result.failed} 篇`)
    await Promise.all([notes.loadNotes(), notes.loadFolders()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导入失败')
  }
}

function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

async function exportOne(format: 'md' | 'txt') {
  exportMenuOpen.value = false
  if (!notes.current?.id) return
  try {
    const { blob, filename } = await noteApi.exportOne(notes.current.id, format)
    saveBlob(blob, filename)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败')
  }
}

async function exportBatch(format: 'md' | 'txt') {
  exportMenuOpen.value = false
  const ids = [...selectedIds.value]
  if (!ids.length) {
    ElMessage.warning('先在列表里勾选要导出的笔记')
    return
  }
  try {
    const { blob, filename } = await noteApi.exportBatch(ids, format)
    saveBlob(blob, filename)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败')
  }
}

// ---------- 回收站 ----------
async function restoreNote(id: number) {
  try {
    await notes.restoreNote(id)
    ElMessage.success('已恢复')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '恢复失败')
  }
}

async function permanentRemove(id: number) {
  try {
    await ElMessageBox.confirm('彻底删除后无法恢复，确定吗？', '彻底删除', {
      confirmButtonText: '彻底删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await notes.permanentRemove(id)
    ElMessage.success('已彻底删除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

async function trashOne(id: number) {
  try {
    await notes.removeNote(id)
    ElMessage.success('已收进回收站')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

// ---------- 逻辑图生成 / 图库 ----------
async function generateDiagram() {
  const ids = [...selectedIds.value]
  if (ids.length < 2) {
    ElMessage.warning('先在列表里勾选至少两篇笔记，再生成逻辑图')
    return
  }
  diagramDialog.value = true
  diagramLoading.value = true
  diagramSvg.value = ''
  diagramCode.value = ''
  diagramTitle.value = ''
  try {
    const d = await aiApi.generateDiagram({ noteIds: ids, type: diagramType.value, useAi: false })
    diagramCode.value = d.code
    diagramTitle.value = d.title || '逻辑图'
    initMermaid(theme.brand, theme.mode === 'dark')
    try {
      diagramSvg.value = await renderMermaid(d.code)
    } catch {
      diagramSvg.value = ''
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '生成失败')
  } finally {
    diagramLoading.value = false
  }
}

async function saveDiagram() {
  if (!diagramCode.value) return
  try {
    await aiApi.saveDiagram({
      title: diagramTitle.value.trim() || '未命名逻辑图',
      diagramType: diagramType.value,
      noteIds: [...selectedIds.value].join(','),
      code: diagramCode.value,
    })
    ElMessage.success('逻辑图已保存')
    diagramDialog.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  }
}

async function openLibrary() {
  libraryDialog.value = true
  libraryLoading.value = true
  libraryCurrent.value = null
  librarySvg.value = ''
  try {
    libraryList.value = await diagramApi.list()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '图库加载失败')
  } finally {
    libraryLoading.value = false
  }
}

async function viewDiagram(d: Diagram) {
  libraryCurrent.value = d
  librarySvg.value = ''
  initMermaid(theme.brand, theme.mode === 'dark')
  try {
    librarySvg.value = await renderMermaid(d.code)
  } catch {
    librarySvg.value = ''
  }
}

async function removeDiagram(d: Diagram) {
  if (d.id == null) return
  try {
    await diagramApi.remove(d.id)
    ElMessage.success('已删除')
    if (libraryCurrent.value?.id === d.id) libraryCurrent.value = null
    libraryList.value = await diagramApi.list()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

// ---------- AI 面板 ----------
function onInsert(text: string) {
  editorRef.value?.insertText(text)
  notes.markDirty()
}

function onApplied(detail: import('@/api/types').NoteDetail) {
  notes.adoptDetail(detail)
  programmaticSync = true
  editorContent.value = detail.content
  titleRef.value = detail.title
  noteTypeRef.value = detail.noteType
  tagNamesRef.value = (detail.tags ?? []).map((t) => t.name)
  nextTick(() => {
    programmaticSync = false
  })
}

function gotoAiSettings() {
  router.push('/settings')
}

// ---------- AI 改写建议（编辑器内联展示） ----------
const rewritePreview = ref<RewritePreview | null>(null)
const rewriteAction = ref<'polish' | 'expand' | 'shrink'>('polish')

const REWRITE_LABELS: Record<'polish' | 'expand' | 'shrink', string> = {
  polish: '润色',
  expand: '扩写',
  shrink: '缩写',
}

// ---------- 改写卡片：拖动 / 折叠 ----------
const rewriteOffset = ref({ x: 0, y: 0 })
const rewriteCollapsed = ref(false)
const aiPanelRef = ref<InstanceType<typeof AiPanel> | null>(null)
let dragState: { startX: number; startY: number; baseX: number; baseY: number } | null = null

function onDragStart(e: PointerEvent) {
  // 只让标题栏空白处拖动，点折叠按钮/其它按钮不触发
  if ((e.target as HTMLElement).closest('button')) return
  dragState = { startX: e.clientX, startY: e.clientY, baseX: rewriteOffset.value.x, baseY: rewriteOffset.value.y }
  ;(e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId)
}

function onDragMove(e: PointerEvent) {
  if (!dragState) return
  rewriteOffset.value = {
    x: dragState.baseX + (e.clientX - dragState.startX),
    y: dragState.baseY + (e.clientY - dragState.startY),
  }
}

function onDragEnd(e: PointerEvent) {
  if (!dragState) return
  dragState = null
  ;(e.currentTarget as HTMLElement).releasePointerCapture?.(e.pointerId)
}

// ---------- 逐字 diff（增绿删红，LCS 回溯） ----------
type DiffSeg = { type: 'eq' | 'add' | 'del'; text: string }

function diffChars(original: string, revised: string): DiffSeg[] {
  const a = [...original]
  const b = [...revised]
  const n = a.length
  const m = b.length
  if (n === 0 && m === 0) return []
  if (n === 0) return [{ type: 'add', text: revised }]
  if (m === 0) return [{ type: 'del', text: original }]

  // 超长文本退化为整段替换，避免 O(n*m) 卡死编辑器
  if (n * m > 3_000_000) {
    const out: DiffSeg[] = []
    if (n) out.push({ type: 'del', text: original })
    if (m) out.push({ type: 'add', text: revised })
    return out
  }

  const w = m + 1
  const dp = new Uint16Array((n + 1) * w)
  for (let i = 1; i <= n; i++) {
    const ai = a[i - 1]!
    for (let j = 1; j <= m; j++) {
      if (ai === b[j - 1]) dp[i * w + j] = dp[(i - 1) * w + (j - 1)]! + 1
      else dp[i * w + j] = Math.max(dp[(i - 1) * w + j]!, dp[i * w + (j - 1)]!)
    }
  }

  // 从后往前回溯，push 时把同类相邻段合并
  const push = (segs: DiffSeg[], type: DiffSeg['type'], ch: string) => {
    if (segs.length && segs[0]!.type === type) segs[0]!.text = ch + segs[0]!.text
    else segs.unshift({ type, text: ch })
  }

  const segs: DiffSeg[] = []
  let i = n
  let j = m
  while (i > 0 && j > 0) {
    if (a[i - 1] === b[j - 1]) {
      push(segs, 'eq', a[i - 1]!)
      i--
      j--
    } else if (dp[(i - 1) * w + j]! >= dp[i * w + (j - 1)]!) {
      push(segs, 'del', a[i - 1]!)
      i--
    } else {
      push(segs, 'add', b[j - 1]!)
      j--
    }
  }
  while (i > 0) {
    push(segs, 'del', a[i - 1]!)
    i--
  }
  while (j > 0) {
    push(segs, 'add', b[j - 1]!)
    j--
  }
  return segs
}

const rewriteDiff = computed<DiffSeg[]>(() => {
  if (!rewritePreview.value) return []
  return diffChars(rewritePreview.value.original, rewritePreview.value.revised)
})

function onRewriteStart() {
  editorRef.value?.highlightRewriteSelection()
}

function onRewritePreview(preview: RewritePreview, action: 'polish' | 'expand' | 'shrink') {
  rewritePreview.value = preview
  rewriteAction.value = action
  rewriteOffset.value = { x: 0, y: 0 }
  rewriteCollapsed.value = false
}

function cancelRewrite() {
  rewritePreview.value = null
  editorRef.value?.clearRewriteHighlight()
}

async function applyRewrite() {
  if (!rewritePreview.value || !notes.current?.id) return
  const preview = rewritePreview.value
  try {
    // 先落盘当前内容：等待期间用户可能改过笔记其它地方，落盘后后端才能按"原文"精确定位替换
    if (notes.dirty) {
      await notes.saveCurrent({ content: editorContent.value })
    }
    const detail = await aiApi.applyRewrite({
      noteId: notes.current.id,
      original: preview.original,
      revised: preview.revised,
    })
    ElMessage.success('已覆盖正文')
    onApplied(detail)
    rewritePreview.value = null
    editorRef.value?.clearRewriteHighlight()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '覆盖失败')
  }
}

/** 把模型返回的"追问建议"按行/序号拆成多条，每条可单独点击 */
const followUpItems = computed<string[]>(() => {
  const raw = rewritePreview.value?.followUp
  if (!raw) return []
  return raw
    .split(/\r?\n/)
    .map((line) => line.replace(/^\s*\d+[.、)）:：]\s*/, '').trim())
    .filter(Boolean)
})

/** 把助手征询口吻（"需不需要我帮你…"）转成用户可直接发问的口吻（"帮我…"） */
function toAskableQuestion(text: string): string {
  return text
    .replace(/(需不需要|要不要)我(?:再)?(?:帮你|帮我)?/g, '帮我')
    .replace(/(需不需要|要不要)/g, '帮我')
    .trim()
}

/** 把某条追问填入 AI 助手对话框，让用户确认或修改后直接发送 */
async function sendFollowUp(suggestion: string) {
  const question = toAskableQuestion(suggestion)
  if (!question) return
  aiOpen.value = true
  await nextTick()
  aiPanelRef.value?.setQuestion(question)
}

// ---------- 搜索防抖 ----------
watch(searchInput, () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    showTrash.value = false
    notes.keyword = searchInput.value
    void notes.loadNotes()
  }, 350)
})

function selectTag(tagId: number | undefined) {
  showTrash.value = false
  notes.filterTagId = tagId
  void notes.loadNotes()
}

function selectFolder(id: number) {
  showTrash.value = false
  notes.activeFolderId = id
  notes.filterTagId = undefined
  notes.keyword = ''
  searchInput.value = ''
  void notes.loadNotes()
}

async function createFolder(parentId: number) {
  try {
    const result = await ElMessageBox.prompt('目录名', '新建目录', {
      inputValue: '',
      confirmButtonText: '创建',
      cancelButtonText: '取消',
      inputValidator: (value: string) => (value && value.trim().length <= 100 ? true : '名字要在 100 字以内'),
    })
    await folderApi.create({ parentId, name: (result.value || '').trim(), icon: 'folder' })
    ElMessage.success('目录已创建')
    await Promise.all([notes.loadFolders(), notes.loadNotes()])
  } catch {
    /* 用户取消 */
  }
}

async function toggleTrashMode() {
  showTrash.value = !showTrash.value
  if (showTrash.value) {
    await notes.loadTrash()
  }
}

const DIAGRAM_TYPES = [
  { value: 'mindmap', label: '思维导图' },
  { value: 'flowchart', label: '流程图' },
  { value: 'graph', label: '关系图' },
  { value: 'timeline', label: '时间线' },
] as const

const TYPE_OPTIONS = notes.NOTE_TYPES
</script>

<template>
  <div class="notes">
    <!-- 左栏：目录 + 标签 + 动作 -->
    <aside class="notes__side">
      <div class="side__scroll">
        <FolderTree
          :folders="notes.folders"
          :active-id="notes.activeFolderId"
          @select="selectFolder"
          @refresh="notes.loadFolders"
          @create="(pid: number) => createFolder(pid)"
        />

        <div class="side__block">
          <p class="side__label muted">标签</p>
          <button type="button" class="tag-item" :class="{ 'is-active': activeTag === undefined }" @click="selectTag(undefined)">全部</button>
          <button
            v-for="t in notes.tags"
            :key="t.id"
            type="button"
            class="tag-item"
            :class="{ 'is-active': activeTag === t.id }"
            @click="selectTag(t.id)"
          >
            <i class="tag-item__dot" :style="{ background: t.color }"></i>
            <span class="tag-item__name">{{ t.name }}</span>
            <span class="tag-item__count muted">{{ t.noteCount ?? '' }}</span>
          </button>
          <p v-if="!notes.tags.length" class="muted side__empty">还没有标签。</p>
        </div>
      </div>

      <div class="side__actions">
        <button type="button" class="btn btn--primary" @click="createNote(notes.activeFolderId, 4)">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M12 5.5v13M5.5 12h13" /></svg>
          新建笔记
        </button>
        <button type="button" class="btn btn--sm" @click="pickImport">导入 md/txt</button>
        <button type="button" class="btn btn--sm" @click="openLibrary">逻辑图库</button>
        <button type="button" class="btn btn--sm" :class="{ 'is-on': showTrash }" @click="toggleTrashMode">回收站</button>
      </div>
      <input ref="importInput" type="file" accept=".md,.markdown,.txt,text/markdown,text/plain" multiple hidden @change="onImport" />
    </aside>

    <!-- 中栏：列表 -->
    <section class="notes__list">
      <div class="list__head">
        <input v-model="searchInput" class="field list__search" type="search" placeholder="搜索标题或内容…" />
      </div>

      <!-- 多选操作条（展开） -->
      <div v-if="selectedCount && !showTrash && !batchCollapsed" class="list__batch">
        <div class="batch__row batch__row--head">
          <label class="batch__check">
            <input type="checkbox" :checked="allSelected" @change="toggleSelectAll" />
            <span>已选 {{ selectedCount }} 篇</span>
          </label>
          <button type="button" class="mini batch__collapse" v-tooltip="'收起工具条'" aria-label="收起工具条" @click="batchCollapsed = true">−</button>
        </div>

        <div class="batch__row">
          <div class="batch__ops">
            <button type="button" class="btn btn--sm" @click="batchTrash">删除</button>
            <button type="button" class="btn btn--sm" @click="exportBatch('md')">导出</button>
            <button type="button" class="btn btn--sm btn--brand" @click="generateDiagram">生成逻辑图</button>
          </div>
        </div>

        <div class="batch__row batch__row--foot">
          <div class="batch__move">
            <span class="batch__move-label muted">移到目录</span>
            <div class="batch__dropdown" :class="{ 'is-open': moveMenuOpen }">
              <button type="button" class="btn btn--sm" @click="moveMenuOpen = !moveMenuOpen">选择目录 ▾</button>
              <div v-if="moveMenuOpen" class="batch__dropdown-backdrop" @click="moveMenuOpen = false"></div>
              <div v-if="moveMenuOpen" class="batch__dropdown-menu">
                <div class="batch__dropdown-row">
                  <span class="batch__dropdown-twist batch__dropdown-twist--leaf"></span>
                  <button type="button" class="batch__dropdown-item" @click="moveTo(0)">未分类</button>
                </div>
                <div
                  v-for="f in moveMenuFolders"
                  :key="f.id"
                  class="batch__dropdown-row"
                  :style="{ paddingLeft: `${f.depth * 14}px` }"
                >
                  <button
                    v-if="f.hasChildren"
                    type="button"
                    class="batch__dropdown-twist"
                    @click.stop="toggleMoveFolder(f.id)"
                  >
                    {{ moveMenuExpanded.has(f.id) ? '▾' : '▸' }}
                  </button>
                  <span v-else class="batch__dropdown-twist batch__dropdown-twist--leaf"></span>
                  <button type="button" class="batch__dropdown-item" @click="moveTo(f.id)">
                    {{ f.name }}
                  </button>
                </div>
              </div>
            </div>
          </div>
          <button type="button" class="mini batch__cancel" @click="clearSelection">取消选择</button>
        </div>
      </div>

      <!-- 多选操作条（收起） -->
      <div v-else-if="selectedCount && !showTrash" class="list__batch list__batch--collapsed">
        <button type="button" class="batch__collapsed-btn" @click="batchCollapsed = false">
          <span class="batch__collapsed-icon">＋</span>
          <span>已选 {{ selectedCount }} 篇</span>
        </button>
      </div>

      <!-- 回收站列表 -->
      <div v-if="showTrash" class="list__body">
        <p v-if="!notes.trash.length" class="muted list__empty">回收站空空的。</p>
        <div v-for="item in notes.trash" :key="item.id" class="row">
          <div class="row__main">
            <strong class="row__title">{{ item.title }}</strong>
            <span class="row__meta muted">{{ item.noteTypeName }} · {{ item.wordCount }} 字 · {{ item.updateTime }}</span>
          </div>
          <div class="row__ops">
            <button type="button" class="btn btn--sm" @click="restoreNote(item.id)">恢复</button>
            <button type="button" class="btn btn--sm btn--danger" @click="permanentRemove(item.id)">彻底删除</button>
          </div>
        </div>
      </div>

      <!-- 正常列表 -->
      <div v-else class="list__body">
        <p v-if="notes.loadingList" class="muted list__empty">加载中…</p>
        <p v-else-if="!notes.notes.length" class="muted list__empty">
          {{ notes.keyword || notes.filterTagId ? '没有匹配的笔记。' : '这里还没有笔记，点左侧「新建笔记」开始。' }}
        </p>
        <div
          v-for="item in notes.notes"
          :key="item.id"
          class="row"
          :class="{ 'row--active': notes.current?.id === item.id }"
        >
          <label class="row__check" @click.stop>
            <input type="checkbox" :checked="selectedIds.has(item.id)" @change="toggleSelect(item.id)" />
          </label>
          <button type="button" class="row__main" @click="openNote(item.id)">
            <span class="row__title-line">
              <strong class="row__title" v-tooltip="item.title || '（无标题）'">{{ item.title || '（无标题）' }}</strong>
              <span class="chip">{{ item.noteTypeName }}</span>
            </span>
            <span class="row__meta muted">
              {{ item.wordCount }} 字 · {{ item.updateTime }}
            </span>
          </button>
          <div class="row__ops" @click.stop>
            <button type="button" class="mini mini--danger" v-tooltip="'收进回收站'" @click="trashOne(item.id)">✕</button>
          </div>
        </div>
      </div>
    </section>

    <!-- 右栏：编辑器 + AI -->
    <main class="notes__work">
      <template v-if="notes.current">
        <div class="work__toolbar">
          <input v-model="titleRef" class="field work__title" type="text" placeholder="标题（可留空，用正文首行）" @input="notes.markDirty" />
          <div class="type-picker" :class="{ 'is-open': typeMenuOpen }">
            <button type="button" class="type-picker__trigger" v-tooltip="'笔记类型'" @click="typeMenuOpen = !typeMenuOpen">
              <span class="type-picker__label">{{ currentTypeLabel }}</span>
              <svg class="type-picker__caret" viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 6l4 4 4-4" /></svg>
            </button>
            <div v-if="typeMenuOpen" class="type-picker__backdrop" @click="typeMenuOpen = false"></div>
            <div v-if="typeMenuOpen" class="type-picker__menu">
              <button
                v-for="t in TYPE_OPTIONS"
                :key="t.value"
                type="button"
                class="type-picker__item"
                :class="{ 'is-active': noteTypeRef === t.value }"
                @click="pickType(t.value)"
              >
                <span class="type-picker__item-label">{{ t.label }}</span>
                <span class="type-picker__item-hint">{{ t.hint }}</span>
              </button>
            </div>
          </div>
          <div class="work__save">
            <span v-if="notes.saving" class="muted">保存中…</span>
            <span v-else-if="notes.dirty" class="muted">有改动</span>
            <span v-else class="muted ok">{{ notes.lastSavedAt ? `已保存 ${notes.lastSavedAt}` : '' }}</span>
            <button type="button" class="btn btn--sm btn--primary" :disabled="notes.saving" @click="saveCurrent">保存</button>
          </div>
          <div class="work__export">
            <button type="button" class="btn btn--sm" @click="exportMenuOpen = !exportMenuOpen">导出 ▾</button>
            <div v-if="exportMenuOpen" class="export-menu">
              <button type="button" class="export-menu__item" @click="exportOne('md')">Markdown (.md)</button>
              <button type="button" class="export-menu__item" @click="exportOne('txt')">纯文本 (.txt)</button>
            </div>
          </div>
          <button type="button" class="btn btn--sm btn--ai" :class="{ 'is-on': aiOpen }" @click="aiOpen = !aiOpen">✨ AI 助手</button>
        </div>

        <div class="work__tags" v-if="tagNamesRef.length || true">
          <span v-for="name in tagNamesRef" :key="name" class="chip chip--brand">
            {{ name }}
            <button type="button" class="chip__x" @click="removeTag(name)">×</button>
          </span>
          <input
            v-model="tagInput"
            class="work__taginput"
            type="text"
            placeholder="添加标签后回车"
            @keydown.enter.prevent="addTag"
          />
        </div>

        <div class="work__body" :class="{ 'work__body--ai': aiOpen }">
          <div class="work__editor">
            <NoteEditor
              ref="editorRef"
              v-model="editorContent"
              :font-size="fontSize"
              @selection="notes.setSelection"
              @save="saveCurrent"
            />
            <!-- 改写建议内联浮层：直接作用于笔记原文，可拖动 / 折叠，逐字 diff 增绿删红 -->
            <div
              v-if="rewritePreview"
              class="rewrite-inline"
              :class="{ 'rewrite-inline--collapsed': rewriteCollapsed }"
              :style="{ transform: `translate(${rewriteOffset.x}px, ${rewriteOffset.y}px)` }"
            >
              <div class="rewrite-inline__head" @pointerdown="onDragStart" @pointermove="onDragMove" @pointerup="onDragEnd">
                <strong>AI 改写 · {{ REWRITE_LABELS[rewriteAction] }}</strong>
                <div class="rewrite-inline__head-right">
                  <span v-if="rewritePreview.model" class="muted">{{ rewritePreview.model }}</span>
                  <button
                    type="button"
                    class="rewrite-inline__collapse"
                    v-tooltip="rewriteCollapsed ? '展开' : '折叠'"
                    aria-label="折叠/展开"
                    @click="rewriteCollapsed = !rewriteCollapsed"
                  >
                    {{ rewriteCollapsed ? '＋' : '−' }}
                  </button>
                </div>
              </div>

              <template v-if="!rewriteCollapsed">
                <div class="rewrite-inline__diff">
                  <div class="rewrite-inline__legend">
                    <span class="rewrite-inline__legend-item"><i class="diff-swatch diff-swatch--del"></i>删除</span>
                    <span class="rewrite-inline__legend-item"><i class="diff-swatch diff-swatch--add"></i>新增</span>
                  </div>
                  <p class="rewrite-inline__text">
                    <template v-for="(seg, idx) in rewriteDiff" :key="idx">
                      <span v-if="seg.type === 'add'" class="diff-add">{{ seg.text }}</span>
                      <span v-else-if="seg.type === 'del'" class="diff-del">{{ seg.text }}</span>
                      <template v-else>{{ seg.text }}</template>
                    </template>
                  </p>
                </div>
                <div v-if="rewritePreview.explanation" class="rewrite-inline__explain">
                  <span class="rewrite-inline__explain-label">改动说明</span>
                  <p class="rewrite-inline__explain-text">{{ rewritePreview.explanation }}</p>
                </div>
                <div v-if="followUpItems.length" class="rewrite-inline__follows">
                  <button
                    v-for="(item, i) in followUpItems"
                    :key="i"
                    type="button"
                    class="rewrite-inline__follow"
                    v-tooltip="'点击把这条问题填入 AI 助手对话框'"
                    @click="sendFollowUp(item)"
                  >
                    <span class="rewrite-inline__follow-text">{{ item }}</span>
                    <span class="rewrite-inline__follow-hint">↗ 问助手</span>
                  </button>
                </div>
                <div class="rewrite-inline__foot">
                  <button type="button" class="btn btn--sm btn--ghost" @click="cancelRewrite">取消</button>
                  <button type="button" class="btn btn--sm btn--primary" @click="applyRewrite">确认覆盖</button>
                </div>
              </template>
            </div>
          </div>
          <AiPanel
            v-if="aiOpen"
            ref="aiPanelRef"
            class="work__ai"
            :note-id="notes.current.id"
            :enabled-tools="enabledTools"
            :selection="notes.selection"
            :configured="notes.aiConfigured"
            :model="notes.aiStatus?.model ?? null"
            :provider-name="notes.aiStatus?.providerName ?? null"
            @insert="onInsert"
            @rewrite="onRewritePreview"
            @rewrite-start="onRewriteStart"
            @open-settings="gotoAiSettings"
          />
        </div>
      </template>

      <div v-else class="work__empty">
        <div class="work__empty-card card anim-pop">
          <p class="work__empty-emoji">✎</p>
          <h2>挑一篇笔记，或写点新的</h2>
          <p class="muted">左边选一篇继续写，或点「新建笔记」开启一页空白。</p>
          <button type="button" class="btn btn--primary" @click="createNote(notes.activeFolderId, 4)">新建笔记</button>
        </div>
      </div>
    </main>

    <!-- 生成逻辑图对话框 -->
    <el-dialog v-model="diagramDialog" title="生成逻辑图" width="min(760px, 92vw)">
      <div class="diagram__form">
        <label class="diagram__field">
          <span>图类型</span>
          <select v-model="diagramType" class="field">
            <option v-for="t in DIAGRAM_TYPES" :key="t.value" :value="t.value">{{ t.label }}</option>
          </select>
        </label>
        <label class="diagram__field">
          <span>标题</span>
          <input v-model="diagramTitle" class="field" type="text" placeholder="给这张图起个名字" />
        </label>
      </div>
      <div class="diagram__preview">
        <p v-if="diagramLoading" class="muted">正在生成…</p>
        <div v-else-if="diagramSvg" class="diagram__svg" v-html="diagramSvg"></div>
        <p v-else class="muted">生成失败或代码为空，可重试。</p>
      </div>
      <template #footer>
        <button type="button" class="btn btn--ghost" @click="diagramDialog = false">关闭</button>
        <button type="button" class="btn btn--primary" :disabled="!diagramCode" @click="saveDiagram">保存到图库</button>
      </template>
    </el-dialog>

    <!-- 逻辑图库 -->
    <el-dialog v-model="libraryDialog" title="逻辑图库" width="min(760px, 92vw)">
      <div v-if="libraryLoading" class="muted">加载中…</div>
      <div v-else-if="!libraryList.length" class="muted">还没有保存过逻辑图。</div>
      <div v-else class="library">
        <ul class="library__list">
          <li v-for="d in libraryList" :key="d.id" class="library__item" :class="{ 'is-active': libraryCurrent?.id === d.id }">
            <button type="button" class="library__btn" @click="viewDiagram(d)">
              <strong>{{ d.title }}</strong>
              <span class="muted">{{ d.diagramType }} · {{ d.createTime }}</span>
            </button>
            <button v-if="d.id != null" type="button" class="mini mini--danger" @click="removeDiagram(d)">删除</button>
          </li>
        </ul>
        <div class="library__preview">
          <div v-if="librarySvg" class="diagram__svg" v-html="librarySvg"></div>
          <p v-else class="muted">选一张图查看。</p>
        </div>
      </div>
      <template #footer>
        <button type="button" class="btn" @click="libraryDialog = false">关闭</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.notes {
  height: calc(100vh - 61px);
  display: grid;
  grid-template-columns: 240px 200px minmax(0, 1fr);
  min-height: 0;
}

/* ---------- 左栏 ---------- */
.notes__side {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--paper-line);
  background: var(--paper-surface);
  min-height: 0;
}

.side__scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--sp-3);
}

.side__block {
  margin-top: var(--sp-4);
}

.side__label {
  font-size: 11px;
  letter-spacing: 0.08em;
  margin-bottom: var(--sp-2);
}

.tag-item {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 100%;
  padding: 5px 8px;
  border: 0;
  border-radius: var(--r-md);
  background: none;
  color: var(--ink-2);
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.tag-item:hover {
  background: var(--brand-soft);
  color: var(--brand-text);
}

.tag-item.is-active {
  background: var(--brand-soft-strong);
  color: var(--brand-text);
  font-weight: 600;
}

.tag-item__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}

.tag-item__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tag-item__count {
  font-size: 11px;
}

.side__empty {
  font-size: 12px;
}

.side__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: var(--sp-3);
  border-top: 1px solid var(--paper-line);
}

.side__actions .btn--primary {
  width: 100%;
}

/* ---------- 中栏 ---------- */
.notes__list {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--paper-line);
  background: var(--paper-page);
  min-height: 0;
}

.list__head {
  padding: var(--sp-3);
  border-bottom: 1px solid var(--paper-line);
}

.list__search {
  height: 34px;
}

.list__batch {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  padding: var(--sp-2) var(--sp-3);
  border-bottom: 1px solid var(--brand-line);
  background: var(--brand-soft);
}

.batch__row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.batch__row--head,
.batch__row--foot {
  justify-content: space-between;
}

.batch__check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--brand-text);
  cursor: pointer;
}

.batch__check input {
  accent-color: var(--brand-base);
}

.batch__ops {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.batch__move {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.batch__move-label {
  font-size: 12px;
  color: var(--ink-3);
}

.batch__collapse {
  font-size: 15px;
  line-height: 1;
  font-weight: 700;
}

.batch__dropdown {
  position: relative;
}

.batch__dropdown-backdrop {
  position: fixed;
  inset: 0;
  z-index: 30;
}

.batch__dropdown-menu {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  z-index: 31;
  min-width: 160px;
  max-height: 280px;
  overflow-y: auto;
  padding: 4px;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-raised);
  box-shadow: var(--sh-3);
}

.batch__dropdown-row {
  display: flex;
  align-items: center;
  gap: 2px;
}

.batch__dropdown-twist {
  flex: none;
  width: 18px;
  height: 22px;
  border: 0;
  background: none;
  color: var(--ink-4);
  font-size: 10px;
  cursor: pointer;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.batch__dropdown-twist--leaf {
  cursor: default;
}

.batch__dropdown-item {
  flex: 1;
  min-width: 0;
  padding: 6px 8px;
  border: 0;
  border-radius: var(--r-sm);
  background: none;
  color: var(--ink-2);
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.batch__dropdown-item:hover {
  background: var(--brand-soft);
  color: var(--brand-text);
}

.list__batch--collapsed {
  justify-content: flex-start;
}

.batch__collapsed-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 10px;
  border: 1px solid var(--brand-line);
  border-radius: var(--r-pill);
  background: var(--paper-surface);
  color: var(--brand-text);
  font-size: 12px;
  cursor: pointer;
}

.batch__collapsed-btn:hover {
  background: var(--brand-soft-strong);
}

.batch__collapsed-icon {
  font-size: 13px;
  line-height: 1;
}

.btn--brand {
  border-color: var(--brand-base);
  background: var(--brand-base);
  color: #fff8f0;
}

.btn--brand:hover {
  background: var(--brand-hover);
  color: #fff;
}

.list__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--sp-2);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.list__empty {
  font-size: 13px;
  padding: var(--sp-4);
  text-align: center;
}

.row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 8px 10px;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-surface);
  transition: border-color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease);
}

.row:hover {
  border-color: var(--brand-line);
}

.row--active {
  border-color: var(--brand-base);
  background: var(--brand-soft-strong);
}

.row__check {
  flex: none;
  cursor: pointer;
}

.row__check input {
  accent-color: var(--brand-base);
}

.row__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
  border: 0;
  background: none;
  padding: 0;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.row__title-line {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.row__title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row__meta {
  font-size: 11px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.row__ops {
  display: flex;
  gap: 2px;
  flex: none;
}

.mini {
  border: 0;
  background: none;
  color: var(--ink-3);
  font-size: 13px;
  cursor: pointer;
  padding: 2px 5px;
  border-radius: var(--r-sm);
}

.mini:hover {
  background: var(--brand-soft);
  color: var(--brand-text);
}

.mini--danger:hover {
  background: color-mix(in srgb, var(--danger) 12%, transparent);
  color: var(--danger);
}

.btn--danger {
  color: color-mix(in srgb, var(--danger) 80%, var(--ink));
}

/* ---------- 右栏 ---------- */
.notes__work {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: var(--paper-page);
}

.work__toolbar {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-2) var(--sp-3);
  border-bottom: 1px solid var(--paper-line);
  background: var(--paper-surface);
  flex-wrap: wrap;
}

.work__title {
  flex: 1;
  min-width: 100px;
  height: 32px;
}

.type-picker {
  position: relative;
  flex: none;
}

.type-picker__trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  min-width: 88px;
  padding: 0 8px 0 12px;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-surface);
  color: var(--ink);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease), box-shadow var(--dur-fast) var(--ease);
}

.type-picker__trigger:hover,
.type-picker.is-open .type-picker__trigger {
  border-color: var(--brand-base);
}

.type-picker.is-open .type-picker__trigger {
  box-shadow: 0 0 0 3px var(--brand-soft);
}

.type-picker__label {
  flex: 1;
  text-align: left;
}

.type-picker__caret {
  color: var(--ink-3);
  flex: none;
  transition: transform var(--dur-fast) var(--ease);
}

.type-picker.is-open .type-picker__caret {
  transform: rotate(180deg);
}

.type-picker__backdrop {
  position: fixed;
  inset: 0;
  z-index: 30;
}

.type-picker__menu {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  z-index: 31;
  min-width: 220px;
  padding: 4px;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-raised);
  box-shadow: var(--sh-3);
}

.type-picker__item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 8px 10px;
  border: 0;
  border-radius: var(--r-sm);
  background: none;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.type-picker__item:hover {
  background: var(--brand-soft);
}

.type-picker__item.is-active {
  background: var(--brand-soft-strong);
}

.type-picker__item-label {
  font-size: 13px;
  color: var(--ink);
}

.type-picker__item:hover .type-picker__item-label,
.type-picker__item.is-active .type-picker__item-label {
  color: var(--brand-text);
}

.type-picker__item-hint {
  font-size: 11px;
  color: var(--ink-3);
}

.work__save {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: 12px;
}

.work__save .ok {
  color: var(--ok);
}

.work__export {
  position: relative;
}

.export-menu {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  z-index: 30;
  padding: 4px;
  display: flex;
  flex-direction: column;
  min-width: 150px;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-raised);
  box-shadow: var(--sh-3);
}

.export-menu__item {
  border: 0;
  background: none;
  text-align: left;
  padding: 8px 10px;
  border-radius: var(--r-sm);
  font: inherit;
  font-size: 13px;
  color: var(--ink-2);
  cursor: pointer;
}

.export-menu__item:hover {
  background: var(--brand-soft);
  color: var(--brand-text);
}

.btn--ai {
  border-color: var(--brand-line);
  background: var(--brand-soft);
  color: var(--brand-text);
  margin-left: auto;
}

.btn--ai.is-on {
  background: var(--brand-base);
  border-color: var(--brand-base);
  color: #fff8f0;
}

.work__tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding: var(--sp-2) var(--sp-3);
  border-bottom: 1px solid var(--paper-line-soft);
  background: var(--paper-surface);
}

.chip__x {
  border: 0;
  background: none;
  color: inherit;
  cursor: pointer;
  padding: 0 0 0 2px;
  font-size: 13px;
}

.work__taginput {
  border: 0;
  background: none;
  font: inherit;
  font-size: 13px;
  color: var(--ink);
  outline: none;
  min-width: 120px;
  height: 24px;
}

.work__taginput::placeholder {
  color: var(--ink-4);
}

.work__body {
  flex: 1;
  min-height: 0;
  display: flex;
}

.work__editor {
  flex: 1;
  min-width: 0;
  min-height: 0;
  position: relative;
}

.rewrite-inline {
  position: absolute;
  right: var(--sp-4);
  top: var(--sp-4);
  width: 320px;
  max-width: calc(100% - var(--sp-8));
  max-height: 62%;
  overflow-y: auto;
  z-index: 20;
  padding: var(--sp-3);
  border: 1px solid var(--brand-line);
  border-radius: var(--r-md);
  background: var(--paper-raised);
  box-shadow: var(--sh-3);
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  will-change: transform;
}

.rewrite-inline__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  cursor: move;
  user-select: none;
  touch-action: none;
}

.rewrite-inline__head strong {
  font-size: 13px;
}

.rewrite-inline__head .muted {
  font-size: 11px;
}

.rewrite-inline__head-right {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex: none;
}

.rewrite-inline__collapse {
  flex: none;
  width: 20px;
  height: 20px;
  border: 0;
  border-radius: var(--r-sm);
  background: var(--brand-soft);
  color: var(--brand-text);
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.rewrite-inline__collapse:hover {
  background: var(--brand-soft-strong);
}

.rewrite-inline__diff {
  padding: 8px 10px;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-sm);
  background: var(--paper-muted);
}

.rewrite-inline__legend {
  display: flex;
  gap: 10px;
  margin-bottom: 6px;
  font-size: 11px;
}

.rewrite-inline__legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.diff-swatch {
  width: 9px;
  height: 9px;
  border-radius: 2px;
  display: inline-block;
  flex: none;
}

.diff-swatch--del {
  background: color-mix(in srgb, var(--danger) 45%, transparent);
}

.diff-swatch--add {
  background: color-mix(in srgb, var(--ok) 45%, transparent);
}

.rewrite-inline__text {
  font-size: 12.5px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
}

.diff-add {
  background: color-mix(in srgb, var(--ok) 22%, transparent);
  color: color-mix(in srgb, var(--ok) 78%, var(--ink));
  border-radius: 2px;
}

.diff-del {
  background: color-mix(in srgb, var(--danger) 20%, transparent);
  color: color-mix(in srgb, var(--danger) 78%, var(--ink));
  text-decoration: line-through;
  border-radius: 2px;
}

.rewrite-inline__explain {
  padding: 6px 8px;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-sm);
  background: var(--paper-muted);
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.rewrite-inline__explain-label {
  font-size: 11px;
  color: var(--ink-3);
  letter-spacing: 0.04em;
}

.rewrite-inline__explain-text {
  font-size: 12px;
  line-height: 1.6;
  margin: 0;
  white-space: pre-line;
  word-break: break-word;
}

.rewrite-inline__follows {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.rewrite-inline__follow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  padding: 6px 8px;
  border: 1px dashed var(--brand-line);
  border-radius: var(--r-sm);
  background: color-mix(in srgb, var(--brand-soft) 60%, transparent);
  font: inherit;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease);
}

.rewrite-inline__follow:hover {
  border-color: var(--brand-base);
  background: var(--brand-soft-strong);
}

.rewrite-inline__follow-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  min-width: 0;
  white-space: pre-line;
  word-break: break-word;
}

.rewrite-inline__follow-hint {
  flex: none;
  font-size: 11px;
  color: var(--brand-text);
  white-space: nowrap;
}

.rewrite-inline__foot {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 2px;
}

.work__ai {
  width: 420px;
  flex: none;
  min-height: 0;
}

.work__empty {
  flex: 1;
  display: grid;
  place-items: center;
  padding: var(--sp-6);
}

.work__empty-card {
  max-width: 380px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-7) var(--sp-6);
}

.work__empty-emoji {
  font-size: 40px;
  margin: 0;
  color: var(--brand-base);
  opacity: 0.55;
}

/* ---------- 逻辑图对话框 ---------- */
.diagram__form {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: var(--sp-3);
  margin-bottom: var(--sp-3);
}

.diagram__field {
  display: grid;
  gap: 6px;
  font-size: 13px;
  color: var(--ink-2);
}

.diagram__preview {
  min-height: 220px;
  max-height: 56vh;
  overflow: auto;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-surface);
  padding: var(--sp-3);
}

.diagram__svg {
  display: flex;
  justify-content: center;
}

.diagram__svg :deep(svg) {
  max-width: 100%;
  height: auto;
}

.library {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: var(--sp-3);
  min-height: 260px;
}

.library__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.library__item {
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  padding: 6px 8px;
}

.library__item.is-active {
  border-color: var(--brand-base);
}

.library__btn {
  flex: 1;
  min-width: 0;
  border: 0;
  background: none;
  text-align: left;
  font: inherit;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: var(--ink);
}

.library__btn strong {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.library__btn span {
  font-size: 11px;
}

.library__preview {
  min-height: 260px;
  max-height: 56vh;
  overflow: auto;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-surface);
  padding: var(--sp-3);
}

@media (max-width: 1080px) {
  .notes {
    grid-template-columns: 220px 180px minmax(0, 1fr);
  }

  .work__ai {
    width: 360px;
  }
}

@media (max-width: 820px) {
  .notes {
    grid-template-columns: 1fr;
    height: auto;
  }

  .notes__side,
  .notes__list {
    border-right: 0;
    border-bottom: 1px solid var(--paper-line);
  }

  .work__ai {
    width: 100%;
  }
}
</style>
