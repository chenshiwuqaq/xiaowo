import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { folderApi, noteApi, tagApi, aiApi } from '@/api'
import type { AiStatus, FolderNode, NoteDetail, NoteItem, Tag } from '@/api/types'

/** 笔记类型：与后端 Constants 对齐，1 科研 2 生活 3 小说 4 随笔 */
export const NOTE_TYPES = [
  { value: 1, label: '科研', hint: '助手会检索相关文献' },
  { value: 2, label: '生活', hint: '助手会浮现时代与生活常识' },
  { value: 3, label: '小说', hint: '助手会给出背景知识与一致性检查' },
  { value: 4, label: '随笔', hint: '通用对话与改写' },
] as const

/** 单个笔记类型可开关的插件：key 为插件有效 id（knowledge / references / user:xxx），value 是否启用 */
export type NoteTypeFeatureFlags = Record<string, boolean>

/** 默认开关：科研→文献；生活/小说→背景知识；随笔→无（内置插件的初始值） */
export const AI_FEATURE_DEFAULTS: Record<number, NoteTypeFeatureFlags> = {
  1: { knowledge: false, references: true },
  2: { knowledge: true, references: false },
  3: { knowledge: true, references: false },
  4: { knowledge: false, references: false },
}

/** 解析后端返回的 aiFeatureMap JSON（缺失/损坏时回落默认），保证四个类型都有值，用户插件开关原样保留 */
export function parseAiFeatureMap(json?: string | null): Record<number, NoteTypeFeatureFlags> {
  const map: Record<number, NoteTypeFeatureFlags> = {}
  for (const [key, value] of Object.entries(AI_FEATURE_DEFAULTS)) {
    map[Number(key)] = { ...value }
  }
  if (!json) return map
  try {
    const parsed = JSON.parse(json) as Record<string, Record<string, boolean> | undefined>
    if (parsed && typeof parsed === 'object') {
      for (const key of Object.keys(AI_FEATURE_DEFAULTS)) {
        const item = parsed[key]
        const entry = map[Number(key)]
        if (entry && item && typeof item === 'object') {
          for (const [pluginKey, enabled] of Object.entries(item)) {
            if (typeof enabled === 'boolean') entry[pluginKey] = enabled
          }
        }
      }
    }
  } catch {
    /* 忽略损坏配置，使用默认 */
  }
  return map
}

/** 序列化开关配置为后端存储用的 JSON 字符串 */
export function serializeAiFeatureMap(map: Record<number, NoteTypeFeatureFlags>): string {
  return JSON.stringify(map)
}

/**
 * 笔记工作区状态。
 *
 * 这里是"编辑器页的数据中枢"：目录树、笔记列表、当前笔记、脏标记、自动保存时间
 * 都在一处，视图组件只读状态 + 派发方法，避免三个面板各自请求导致数据打架。
 *
 * 自动保存策略：脏标记 + 由调用方（编辑器）决定何时触发 save，store 不自己起定时器，
 * 这样"用户切换笔记 / 关闭页面前"这些边界都由视图显式处理，不会出现保存与卸载竞态。
 */
export const useNotesStore = defineStore('notes', () => {
  const folders = ref<FolderNode[]>([])
  const notes = ref<NoteItem[]>([])
  const trash = ref<NoteItem[]>([])
  const tags = ref<Tag[]>([])
  const current = ref<NoteDetail | null>(null)
  const aiStatus = ref<AiStatus | null>(null)

  const activeFolderId = ref<number>(0)
  const keyword = ref('')
  const filterTagId = ref<number | undefined>(undefined)

  const loadingList = ref(false)
  const loadingDetail = ref(false)
  const saving = ref(false)
  const dirty = ref(false)
  const lastSavedAt = ref<string>('')
  const saveError = ref<string>('')

  /** 编辑器里选中的文字，AI 面板的润色/扩写以它为准 */
  const selection = ref('')

  const flatFolders = computed(() => {
    const out: { id: number; name: string; depth: number }[] = []
    const walk = (list: FolderNode[], depth: number) => {
      list.forEach((node) => {
        out.push({ id: node.id, name: node.name, depth })
        if (node.children?.length) walk(node.children, depth + 1)
      })
    }
    walk(folders.value, 0)
    return out
  })

  const currentType = computed(() => current.value?.noteType ?? 4)
  const aiConfigured = computed(() => Boolean(aiStatus.value?.configured))

  async function loadFolders() {
    folders.value = await folderApi.tree()
  }

  async function loadTags() {
    tags.value = await tagApi.list()
  }

  async function loadNotes(options?: { silent?: boolean }) {
    loadingList.value = true
    try {
      notes.value = await noteApi.list({
        folderId: keyword.value.trim() || filterTagId.value ? undefined : activeFolderId.value,
        keyword: keyword.value.trim() || undefined,
        tagId: filterTagId.value,
      })
    } finally {
      loadingList.value = false
      void options
    }
  }

  async function loadTrash() {
    trash.value = await noteApi.trash()
  }

  async function openNote(id: number): Promise<NoteDetail | null> {
    // 切换笔记前若有未保存内容，交给视图决定要不要先保存；这里只做提醒性状态
    loadingDetail.value = true
    try {
      current.value = await noteApi.detail(id)
      dirty.value = false
      saveError.value = ''
      return current.value
    } finally {
      loadingDetail.value = false
    }
  }

  async function createNote(payload: { folderId?: number; noteType?: number; title?: string; content?: string }) {
    const created = await noteApi.create({
      folderId: payload.folderId ?? activeFolderId.value,
      noteType: payload.noteType ?? 4,
      title: payload.title ?? '',
      content: payload.content ?? '',
    })
    current.value = created
    dirty.value = false
    await Promise.all([loadNotes(), loadFolders()])
    return created
  }

  /** 保存当前笔记。snapshot 让后端在覆盖前留一份历史版本 */
  async function saveCurrent(payload: { title?: string; content: string; noteType?: number; tagNames?: string[]; snapshot?: boolean }) {
    if (!current.value?.id) return null
    saving.value = true
    saveError.value = ''
    try {
      const updated = await noteApi.update(current.value.id, payload)
      current.value = updated
      dirty.value = false
      lastSavedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
      await loadNotes({ silent: true })
      return updated
    } catch (error) {
      saveError.value = error instanceof Error ? error.message : '保存失败'
      throw error
    } finally {
      saving.value = false
    }
  }

  /** AI 覆盖正文后，用后端返回的详情刷新编辑器 */
  function adoptDetail(detail: NoteDetail) {
    current.value = detail
    dirty.value = false
    lastSavedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  }

  async function removeNote(id: number) {
    await noteApi.trashOne(id)
    if (current.value?.id === id) current.value = null
    await Promise.all([loadNotes(), loadFolders()])
  }

  async function restoreNote(id: number) {
    await noteApi.restore(id)
    await Promise.all([loadTrash(), loadNotes(), loadFolders()])
  }

  async function permanentRemove(id: number) {
    await noteApi.permanent(id)
    await loadTrash()
  }

  async function refreshAiStatus() {
    try {
      aiStatus.value = await aiApi.status()
    } catch {
      aiStatus.value = null
    }
  }

  function markDirty() {
    dirty.value = true
  }

  function setSelection(text: string) {
    selection.value = text
  }

  function closeNote() {
    current.value = null
    selection.value = ''
    dirty.value = false
  }

  return {
    NOTE_TYPES,
    folders,
    notes,
    trash,
    tags,
    current,
    aiStatus,
    activeFolderId,
    keyword,
    filterTagId,
    loadingList,
    loadingDetail,
    saving,
    dirty,
    lastSavedAt,
    saveError,
    selection,
    flatFolders,
    currentType,
    aiConfigured,
    loadFolders,
    loadTags,
    loadNotes,
    loadTrash,
    openNote,
    createNote,
    saveCurrent,
    adoptDetail,
    removeNote,
    restoreNote,
    permanentRemove,
    refreshAiStatus,
    markDirty,
    setSelection,
    closeNote,
  }
})
