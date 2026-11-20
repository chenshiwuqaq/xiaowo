<script setup lang="ts">
/**
 * 目录树。
 *
 * 只用原生 details/summary 之外的自定义展开状态（不用第三方树组件）：
 * 层级不深、节点量小，自己控制缩进和 hover 操作反而更贴合这套纸面视觉。
 */
import { computed, ref } from 'vue'
import { folderApi } from '@/api'
import type { FolderNode } from '@/api/types'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps<{
  folders: FolderNode[]
  activeId: number
  counts?: Record<number, number>
}>()

const emit = defineEmits<{
  select: [id: number]
  refresh: []
  create: [parentId: number]
}>()

/** 展开状态：默认全展开，目录层级不深，收起反而是负担 */
const expanded = ref<Set<number>>(new Set())

const allExpanded = computed(() => expanded.value.size === 0)

function isOpen(id: number) {
  // 未显式记录时默认展开，避免每次新建目录都要点一下
  return !expanded.value.has(id)
}

function toggle(id: number) {
  const next = new Set(expanded.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expanded.value = next
}

function flatten(list: FolderNode[], depth = 0): { node: FolderNode; depth: number }[] {
  const out: { node: FolderNode; depth: number }[] = []
  list.forEach((node) => {
    out.push({ node, depth })
    if (node.children?.length && isOpen(node.id)) out.push(...flatten(node.children, depth + 1))
  })
  return out
}

const visible = computed(() => flatten(props.folders))

async function rename(node: FolderNode) {
  try {
    const result = await ElMessageBox.prompt('目录名', '重命名', {
      inputValue: node.name,
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValidator: (value: string) => (value && value.trim().length <= 100 ? true : '名字要在 100 字以内'),
    })
    await folderApi.rename(node.id, { name: (result.value || '').trim(), icon: node.icon ?? undefined })
    ElMessage.success('已重命名')
    emit('refresh')
  } catch {
    /* 用户取消，不做任何事 */
  }
}

async function remove(node: FolderNode) {
  const count = node.noteCount ?? props.counts?.[node.id] ?? 0
  try {
    await ElMessageBox.confirm(
      `删除「${node.name}」后，里面 ${count} 篇笔记会退回未分类（笔记不会被删掉）。`,
      '删除目录',
      { confirmButtonText: '删除目录', cancelButtonText: '再想想', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await folderApi.remove(node.id)
    if (props.activeId === node.id) emit('select', 0)
    ElMessage.success('目录已删除')
    emit('refresh')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

/**
 * 移动目录：点 ⇄ 弹出小窗，在小窗里按层级选择目标父目录。
 * 源目录自身及其子孙不可作为目标（会成环），从候选里排除。
 */
const moveDialog = ref(false)
const movingId = ref<number | null>(null)
const movingName = ref('')
const moveExpanded = ref<Set<number>>(new Set())

function startMove(node: FolderNode) {
  movingId.value = node.id
  movingName.value = node.name
  moveExpanded.value = new Set()
  moveDialog.value = true
}

function closeMove() {
  moveDialog.value = false
  movingId.value = null
  movingName.value = ''
}

function toggleMoveFolder(id: number) {
  const next = new Set(moveExpanded.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  moveExpanded.value = next
}

/** 源目录自身及其子孙 id 集合：不可作为移动目标 */
const excludedIds = computed<Set<number>>(() => {
  const ids = new Set<number>()
  if (movingId.value == null) return ids
  const find = (list: FolderNode[]): boolean => {
    for (const node of list) {
      if (node.id === movingId.value) {
        collectSubtree(node, ids)
        return true
      }
      if (node.children?.length && find(node.children)) return true
    }
    return false
  }
  find(props.folders)
  return ids
})

function collectSubtree(node: FolderNode, ids: Set<number>) {
  ids.add(node.id)
  node.children?.forEach((c) => collectSubtree(c, ids))
}

/** 移动弹窗里的目录候选：按"父→子"展开拍平，排除源目录子树 */
const moveFolders = computed(() => {
  const out: { id: number; name: string; depth: number; hasChildren: boolean }[] = []
  const walk = (list: FolderNode[], depth: number) => {
    for (const node of list) {
      if (excludedIds.value.has(node.id)) continue
      out.push({ id: node.id, name: node.name, depth, hasChildren: Boolean(node.children?.length) })
      if (node.children?.length && moveExpanded.value.has(node.id)) {
        walk(node.children, depth + 1)
      }
    }
  }
  walk(props.folders, 0)
  return out
})

async function confirmMove(parentId: number) {
  if (movingId.value == null) return
  try {
    await folderApi.move({ id: movingId.value, parentId })
    ElMessage.success(`已把「${movingName.value}」移到${parentId === 0 ? '未分类' : '目标目录'}下`)
    closeMove()
    emit('refresh')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '移动失败')
  }
}
</script>

<template>
  <div class="tree">
    <div class="tree__head">
      <span class="tree__title">目录</span>
      <div class="tree__tools">
        <button class="mini" type="button" v-tooltip="allExpanded ? '全部收起' : '全部展开'" @click="
          expanded = allExpanded
            ? new Set(folders.map((f) => f.id).concat(folders.flatMap((f) => (f.children || []).map((c) => c.id))))
            : new Set()
          ">
          {{ allExpanded ? '收起' : '展开' }}
        </button>
        <button class="mini" type="button" v-tooltip="'在顶层新建目录'" @click="emit('create', 0)">+ 目录</button>
      </div>
    </div>

    <button class="tree__row tree__root" :class="{ 'is-active': activeId === 0 }" type="button"
      @click="emit('select', 0)">
      <span class="tree__icon">◌</span>
      <span class="tree__name">未分类</span>
    </button>

    <div v-if="!visible.length" class="tree__empty muted">还没有目录，点「+ 目录」建一个。</div>

    <div v-for="item in visible" :key="item.node.id" class="tree__row" :class="{ 'is-active': activeId === item.node.id }"
      :style="{ paddingLeft: `${8 + item.depth * 14}px` }" role="button" tabindex="0"
      @click="emit('select', item.node.id)" @keydown.enter="emit('select', item.node.id)">
      <button v-if="item.node.children?.length" class="tree__twist" type="button" @click.stop="toggle(item.node.id)">
        {{ isOpen(item.node.id) ? '▾' : '▸' }}
      </button>
      <span v-else class="tree__twist tree__twist--leaf">·</span>
      <span class="tree__name" v-tooltip="item.node.name">{{ item.node.name }}</span>
      <span class="tree__count metric">{{ item.node.noteCount ?? counts?.[item.node.id] ?? 0 }}</span>
      <span class="tree__ops">
        <button class="mini" type="button" v-tooltip="'新建子目录'" @click.stop="emit('create', item.node.id)">+</button>
        <button class="mini" type="button" v-tooltip="'重命名'" @click.stop="rename(item.node)">✎</button>
        <button class="mini" type="button" v-tooltip="'移动'" @click.stop="startMove(item.node)">⇄</button>
        <button class="mini mini--danger" type="button" v-tooltip="'删除目录'" @click.stop="remove(item.node)">✕</button>
      </span>
    </div>
  </div>

  <!-- 移动目录弹窗：按层级选择目标父目录 -->
  <el-dialog v-model="moveDialog" :title="`移动目录「${movingName}」`" width="min(400px, 92vw)">
    <div class="move-folder">
      <div class="move-folder__row">
        <span class="move-folder__twist move-folder__twist--leaf"></span>
        <button class="move-folder__name" type="button" @click="confirmMove(0)">未分类（顶层）</button>
      </div>
      <div
        v-for="f in moveFolders"
        :key="f.id"
        class="move-folder__row"
        :style="{ paddingLeft: `${f.depth * 14}px` }"
      >
        <button
          v-if="f.hasChildren"
          type="button"
          class="move-folder__twist"
          @click.stop="toggleMoveFolder(f.id)"
        >
          {{ moveExpanded.has(f.id) ? '▾' : '▸' }}
        </button>
        <span v-else class="move-folder__twist move-folder__twist--leaf"></span>
        <button class="move-folder__name" type="button" @click="confirmMove(f.id)">
          {{ f.name }}
        </button>
      </div>
      <p v-if="!moveFolders.length" class="move-folder__empty muted">没有可移入的目录。</p>
    </div>
    <template #footer>
      <button class="btn btn--ghost" type="button" @click="closeMove">取消</button>
    </template>
  </el-dialog>
</template>

<style scoped>
.tree {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.tree__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px var(--sp-2);
}

.tree__title {
  font-size: 12px;
  letter-spacing: 0.08em;
  color: var(--ink-3);
}

.tree__tools {
  display: flex;
  gap: 4px;
}

.mini {
  border: 1px solid transparent;
  background: transparent;
  color: var(--ink-3);
  font-size: 11px;
  height: 20px;
  min-width: 20px;
  padding: 0 5px;
  border-radius: var(--r-sm);
  cursor: pointer;
}

.mini:hover {
  background: var(--brand-soft);
  color: var(--brand-text);
  border-color: var(--brand-line);
}

.mini--danger:hover {
  background: color-mix(in srgb, var(--danger) 14%, transparent);
  color: var(--danger);
  border-color: color-mix(in srgb, var(--danger) 30%, transparent);
}

.tree__row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
  border-radius: var(--r-md);
  cursor: pointer;
  color: var(--ink-2);
  font-size: 13px;
  border: 0;
  background: none;
  text-align: left;
  width: 100%;
  transition: background var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}

.tree__row:hover {
  background: var(--brand-soft);
  color: var(--brand-text);
}

.tree__row.is-active {
  background: var(--brand-soft-strong);
  color: var(--brand-text);
  font-weight: 600;
}

.tree__row:hover .tree__ops,
.tree__row.is-active .tree__ops {
  display: flex;
}

.tree__root {
  margin-bottom: 2px;
}

.tree__icon {
  width: 14px;
  text-align: center;
  opacity: 0.7;
}

.tree__twist {
  width: 14px;
  border: 0;
  background: none;
  color: var(--ink-4);
  cursor: pointer;
  font-size: 10px;
  padding: 0;
}

.tree__twist--leaf {
  cursor: default;
}

.tree__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree__count {
  font-size: 11px;
  color: var(--ink-4);
}

.tree__ops {
  display: none;
  gap: 2px;
}

.move-folder {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 60vh;
  overflow-y: auto;
}

.move-folder__row {
  display: flex;
  align-items: center;
  gap: 2px;
}

.move-folder__twist {
  flex: none;
  width: 18px;
  height: 28px;
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

.move-folder__twist--leaf {
  cursor: default;
}

.move-folder__name {
  flex: 1;
  min-width: 0;
  padding: 7px 8px;
  border: 0;
  border-radius: var(--r-sm);
  background: none;
  color: var(--ink-2);
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.move-folder__name:hover {
  background: var(--brand-soft);
  color: var(--brand-text);
}

.move-folder__empty {
  padding: var(--sp-3) 4px;
  font-size: 12px;
}

.tree__empty {
  padding: var(--sp-3) 8px;
  font-size: 12px;
}
</style>
