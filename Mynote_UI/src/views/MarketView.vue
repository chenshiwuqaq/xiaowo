<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { marketApi } from '@/api'
import { ElMessage } from 'element-plus'
import type { MarketPlugin, MarketPluginDetail, MarketRankings } from '@/api/types'
import PluginCard from '@/components/market/PluginCard.vue'
import PluginDetailDialog from '@/components/market/PluginDetailDialog.vue'
import PublishDialog from '@/components/market/PublishDialog.vue'
import MyPluginsDialog from '@/components/market/MyPluginsDialog.vue'

const CATEGORIES = [
  { value: -1, label: '全部类型' },
  { value: 0, label: '通用' },
  { value: 1, label: '科研' },
  { value: 2, label: '生活' },
  { value: 3, label: '小说' },
  { value: 4, label: '随笔' },
]

const SORTS = [
  { value: 'random', label: '随机推荐' },
  { value: 'latest', label: '最新发布' },
  { value: 'like', label: '爱心最多' },
  { value: 'comment', label: '评论最多' },
  { value: 'use', label: '使用最多' },
]

const RANKS: { key: 'byLike' | 'byComment' | 'byUse'; label: string; count: (p: MarketPlugin) => number }[] = [
  { key: 'byLike', label: '爱心榜', count: (p) => p.likeCount },
  { key: 'byComment', label: '评论榜', count: (p) => p.commentCount },
  { key: 'byUse', label: '使用榜', count: (p) => p.useCount },
]

const keyword = ref('')
const category = ref(-1)
const sort = ref('random')
const pageNo = ref(1)
const pageSize = 12

const plugins = ref<MarketPlugin[]>([])
const total = ref(0)
const loadingList = ref(false)
const rankings = ref<MarketRankings | null>(null)
const loadingRankings = ref(false)
const busyId = ref<number | null>(null)

const detailId = ref<number | null>(null)
const publishOpen = ref(false)
const publishEditing = ref<MarketPluginDetail | null>(null)
const mineOpen = ref(false)

const isRandom = computed(() => sort.value === 'random')
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

onMounted(() => {
  void loadRankings()
  void refresh()
})

async function loadRankings() {
  loadingRankings.value = true
  try {
    rankings.value = await marketApi.rankings(10)
  } catch {
    rankings.value = null
  } finally {
    loadingRankings.value = false
  }
}

async function refresh() {
  loadingList.value = true
  try {
    if (isRandom.value) {
      plugins.value = await marketApi.random({
        keyword: keyword.value.trim() || undefined,
        category: category.value,
        size: pageSize,
      })
      total.value = plugins.value.length
    } else {
      const page = await marketApi.list({
        keyword: keyword.value.trim() || undefined,
        category: category.value,
        sort: sort.value,
        pageNo: pageNo.value,
        pageSize,
      })
      plugins.value = page.list
      total.value = page.total
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
    plugins.value = []
    total.value = 0
  } finally {
    loadingList.value = false
  }
}

function onSearch() {
  pageNo.value = 1
  void refresh()
}

function onSortChange() {
  pageNo.value = 1
  void refresh()
}

function onCategoryChange() {
  pageNo.value = 1
  void refresh()
}

function shuffle() {
  if (!isRandom.value) {
    sort.value = 'random'
    pageNo.value = 1
  }
  void refresh()
}

function goto(page: number) {
  if (page < 1 || page > totalPages.value || page === pageNo.value) return
  pageNo.value = page
  void refresh()
}

function rankList(key: 'byLike' | 'byComment' | 'byUse'): MarketPlugin[] {
  return rankings.value?.[key] ?? []
}

function openDetail(plugin: MarketPlugin) {
  detailId.value = plugin.id
}

async function toggleLike(plugin: MarketPlugin) {
  if (busyId.value != null) return
  busyId.value = plugin.id
  try {
    const state = await marketApi.like(plugin.id)
    plugin.liked = state.liked
    plugin.likeCount = state.likeCount
    void loadRankings()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    busyId.value = null
  }
}

async function usePlugin(plugin: MarketPlugin) {
  if (busyId.value != null) return
  busyId.value = plugin.id
  try {
    const state = await marketApi.use(plugin.id)
    plugin.used = true
    plugin.useCount = state.useCount
    ElMessage.success(state.newlyUsed ? `已装进「我的插件」（${state.toolId}）` : '你已装过这个插件')
    void loadRankings()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '使用失败')
  } finally {
    busyId.value = null
  }
}

function onDetailChanged() {
  void refresh()
  void loadRankings()
}

function onSaved() {
  void refresh()
  void loadRankings()
}

async function openEdit(pluginId: number) {
  mineOpen.value = false
  try {
    publishEditing.value = await marketApi.detail(pluginId)
    publishOpen.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载发布详情失败')
  }
}
</script>

<template>
  <div class="market">
    <header class="market__head anim-fade-up">
      <div>
        <h1>插件市场</h1>
        <p class="muted">逛逛大家做的小工具，点个爱心、装一个回去用。</p>
      </div>
      <div class="market__head-actions">
        <button type="button" class="btn" @click="mineOpen = true">我的发布</button>
        <button type="button" class="btn btn--primary" @click="publishEditing = null; publishOpen = true">发布插件</button>
      </div>
    </header>

    <!-- 排行榜 -->
    <section class="card ranks anim-fade-up">
      <template v-if="loadingRankings">
        <div v-for="n in 3" :key="n" class="skeleton" style="height: 160px"></div>
      </template>
      <template v-else-if="rankings">
        <div v-for="rank in RANKS" :key="rank.key" class="rank">
          <h2 class="rank__title">{{ rank.label }}</h2>
          <ol v-if="rankList(rank.key).length" class="rank__list">
            <li v-for="(p, i) in rankList(rank.key).slice(0, 5)" :key="p.id">
              <button type="button" class="rank__item" @click="openDetail(p)">
                <span class="rank__no" :class="{ 'rank__no--top': i < 3 }">{{ i + 1 }}</span>
                <span class="rank__name">{{ p.name }}</span>
                <span class="rank__count metric">{{ rank.count(p) }}</span>
              </button>
            </li>
          </ol>
          <p v-else class="muted rank__empty">还没有上榜插件</p>
        </div>
      </template>
    </section>

    <!-- 工具栏 -->
    <div class="toolbar anim-fade-up">
      <div class="toolbar__search">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true">
          <circle cx="11" cy="11" r="6.5" />
          <path d="M20 20l-4.2-4.2" />
        </svg>
        <input
          v-model="keyword"
          class="toolbar__input"
          type="text"
          placeholder="按名称 / ID / 标签 / 作者搜索"
          @keyup.enter="onSearch"
        />
        <button type="button" class="btn btn--sm" @click="onSearch">搜索</button>
      </div>
      <select v-model.number="category" class="field toolbar__select" aria-label="类型" @change="onCategoryChange">
        <option v-for="c in CATEGORIES" :key="c.value" :value="c.value">{{ c.label }}</option>
      </select>
      <select v-model="sort" class="field toolbar__select" aria-label="排序" @change="onSortChange">
        <option v-for="s in SORTS" :key="s.value" :value="s.value">{{ s.label }}</option>
      </select>
      <button v-if="isRandom" type="button" class="btn toolbar__shuffle" @click="shuffle">
        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M4 7h3.5c3 0 5 10 8 10H20M20 7h-4.5c-1.3 0-2.4.5-3.2 1.4M17 4l3 3-3 3M17 14l3 3-3 3" />
        </svg>
        换一批
      </button>
    </div>

    <!-- 卡片网格 -->
    <p v-if="loadingList" class="muted market__hint">加载插件…</p>
    <template v-else>
      <div v-if="plugins.length" class="grid">
        <PluginCard
          v-for="p in plugins"
          :key="p.id"
          :plugin="p"
          :busy="busyId === p.id"
          @open="openDetail"
          @like="toggleLike"
          @use="usePlugin"
        />
      </div>
      <div v-else class="empty card">
        <p class="empty__title">这里还空着</p>
        <p class="muted">没有找到匹配的插件，换个关键词或分类试试，或者发布第一个插件。</p>
      </div>
    </template>

    <!-- 分页 / 换一批 -->
    <div v-if="!loadingList && plugins.length" class="pager">
      <template v-if="isRandom">
        <button type="button" class="btn" @click="shuffle">再换一批</button>
      </template>
      <template v-else>
        <button type="button" class="btn btn--sm" :disabled="pageNo <= 1" @click="goto(pageNo - 1)">上一页</button>
        <span class="muted pager__info">第 {{ pageNo }} / {{ totalPages }} 页 · 共 {{ total }} 个</span>
        <button type="button" class="btn btn--sm" :disabled="pageNo >= totalPages" @click="goto(pageNo + 1)">下一页</button>
      </template>
    </div>

    <PluginDetailDialog :plugin-id="detailId" @close="detailId = null" @changed="onDetailChanged" />
    <PublishDialog :open="publishOpen" :editing="publishEditing" @close="publishOpen = false" @saved="onSaved" />
    <MyPluginsDialog :open="mineOpen" @close="mineOpen = false" @changed="onSaved" @view="detailId = $event" @edit="openEdit" />
  </div>
</template>

<style scoped>
.market {
  max-width: 1180px;
  margin: 0 auto;
  padding: var(--sp-6) var(--sp-5) var(--sp-8);
  display: flex;
  flex-direction: column;
  gap: var(--sp-5);
}

.market__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--sp-4);
  flex-wrap: wrap;
}

.market__head h1 {
  font-size: 24px;
  letter-spacing: -0.02em;
}

.market__head p {
  margin-top: var(--sp-1);
  font-size: 13px;
}

.market__head-actions {
  display: flex;
  gap: var(--sp-2);
}

.ranks {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--sp-5);
}

.rank__title {
  font-size: 14px;
  margin-bottom: var(--sp-3);
}

.rank__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 6px;
}

.rank__item {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  width: 100%;
  padding: 6px 8px;
  border: 0;
  border-radius: var(--r-md);
  background: transparent;
  color: var(--ink);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.rank__item:hover {
  background: var(--brand-soft);
}

.rank__no {
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: var(--paper-muted);
  color: var(--ink-3);
  font-size: 12px;
  font-weight: 700;
  flex: none;
}

.rank__no--top {
  background: var(--brand-soft-strong);
  color: var(--brand-text);
}

.rank__name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank__count {
  font-size: 12px;
  color: var(--ink-3);
}

.rank__empty {
  font-size: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.toolbar__search {
  flex: 1;
  min-width: 220px;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 var(--sp-3);
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-surface);
  color: var(--ink-3);
}

.toolbar__search:focus-within {
  border-color: var(--brand-base);
  box-shadow: 0 0 0 3px var(--brand-soft);
}

.toolbar__input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  font: inherit;
  font-size: 13px;
}

.toolbar__input:focus {
  outline: none;
}

/* 与全局 select.field 的纸面下拉样式保持一致（36px 高、14px 字号、定制箭头），
   这里只约束宽度，不让 flex 布局把它拉伸或压扁 */
.toolbar__select {
  flex: none;
  width: 136px;
}

.toolbar__shuffle {
  height: 36px;
}

.market__hint {
  font-size: 13px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--sp-4);
}

.empty {
  text-align: center;
  padding: var(--sp-7) var(--sp-5);
}

.empty__title {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: var(--sp-1);
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
}

.pager__info {
  font-size: 12px;
}

@media (max-width: 860px) {
  .ranks {
    grid-template-columns: 1fr;
  }
}
</style>
