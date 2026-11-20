<script setup lang="ts">
import { ref, watch } from 'vue'
import { marketApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { MarketPlugin } from '@/api/types'

const props = defineProps<{ open: boolean }>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'changed'): void
  (e: 'view', id: number): void
  (e: 'edit', pluginId: number): void
}>()

const list = ref<MarketPlugin[]>([])
const loading = ref(false)
const busyId = ref<number | null>(null)

watch(
  () => props.open,
  (open) => {
    if (open) void load()
  },
)

async function load() {
  loading.value = true
  try {
    list.value = await marketApi.mine()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

async function toggleStatus(plugin: MarketPlugin) {
  if (plugin.id == null) return
  busyId.value = plugin.id
  try {
    await marketApi.setStatus(plugin.id, plugin.status !== 1)
    ElMessage.success(plugin.status !== 1 ? '已上架' : '已下架')
    await load()
    emit('changed')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    busyId.value = null
  }
}

async function editPlugin(plugin: MarketPlugin) {
  emit('edit', plugin.id)
}

async function removePlugin(plugin: MarketPlugin) {
  try {
    await ElMessageBox.confirm(`确定删除「${plugin.name}」？点赞、评论与使用记录会一并清除。`, '删除发布', {
      type: 'warning',
    })
  } catch {
    return
  }
  busyId.value = plugin.id
  try {
    await marketApi.remove(plugin.id)
    ElMessage.success('已删除')
    await load()
    emit('changed')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  } finally {
    busyId.value = null
  }
}
</script>

<template>
  <div v-if="open" class="overlay" @click.self="emit('close')">
    <div class="dialog card anim-pop" role="dialog" aria-modal="true">
      <button class="dialog__close" type="button" aria-label="关闭" @click="emit('close')">×</button>
      <h2 class="dialog__title">我的发布</h2>
      <p class="muted dialog__sub">管理你发到市场的插件：查看、编辑、上架/下架或删除。</p>

      <p v-if="loading" class="muted tiny">加载中…</p>
      <template v-else>
        <ul v-if="list.length" class="list">
          <li v-for="p in list" :key="p.id" class="item">
            <div class="item__main">
              <div class="item__name">
                <strong>{{ p.name }}</strong>
                <span class="chip">{{ p.categoryName }}</span>
                <span v-if="p.status !== 1" class="chip chip--off">已下架</span>
              </div>
              <div class="item__meta muted tiny">
                ❤ {{ p.likeCount }} · 💬 {{ p.commentCount }} · ⬇ {{ p.useCount }}
              </div>
            </div>
            <div class="item__ops">
              <button type="button" class="btn btn--sm" @click="emit('view', p.id)">查看</button>
              <button type="button" class="btn btn--sm" @click="editPlugin(p)">编辑</button>
              <button type="button" class="btn btn--sm" :disabled="busyId === p.id" @click="toggleStatus(p)">
                {{ p.status !== 1 ? '上架' : '下架' }}
              </button>
              <button type="button" class="btn btn--sm btn--danger" :disabled="busyId === p.id" @click="removePlugin(p)">
                删除
              </button>
            </div>
          </li>
        </ul>
        <p v-else class="muted empty">你还没有发布过插件。点市场页右上角「发布插件」把好用的插件分享出来。</p>
      </template>
    </div>
  </div>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  padding: var(--sp-4);
  background: color-mix(in srgb, #2b1a12 34%, transparent);
  backdrop-filter: blur(2px);
}

.dialog {
  position: relative;
  width: min(620px, 100%);
  max-height: 86vh;
  overflow-y: auto;
  padding: var(--sp-5);
}

.dialog__close {
  position: absolute;
  top: var(--sp-3);
  right: var(--sp-3);
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: var(--r-md);
  background: transparent;
  color: var(--ink-3);
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}

.dialog__close:hover {
  background: var(--brand-soft);
  color: var(--brand-text);
}

.dialog__title {
  font-size: 18px;
}

.dialog__sub {
  margin-top: var(--sp-1);
  font-size: 12.5px;
}

.list {
  list-style: none;
  margin: var(--sp-4) 0 0;
  padding: 0;
  display: grid;
  gap: var(--sp-2);
}

.item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  flex-wrap: wrap;
  padding: var(--sp-3);
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-surface);
}

.item__main {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.item__name {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 14px;
}

.item__meta {
  font-size: 12px;
}

.item__ops {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  flex: none;
}

.chip--off {
  color: var(--ink-4);
}

.btn--danger {
  color: color-mix(in srgb, var(--danger) 80%, var(--ink));
}

.btn--danger:hover {
  border-color: color-mix(in srgb, var(--danger) 50%, transparent);
  background: color-mix(in srgb, var(--danger) 10%, var(--paper-surface));
  color: color-mix(in srgb, var(--danger) 78%, var(--ink));
}

.empty {
  margin-top: var(--sp-4);
  font-size: 13px;
  line-height: 1.7;
}

.tiny {
  font-size: 12px;
}
</style>
