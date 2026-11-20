<script setup lang="ts">
import { ref, watch } from 'vue'
import { marketApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { MarketComment, MarketPluginDetail } from '@/api/types'
import { useUserStore } from '@/stores/user'

const props = defineProps<{ pluginId: number | null }>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'changed'): void
}>()

const userStore = useUserStore()

/** 默认用户模板的占位展示（含模板变量，放到脚本里避免被模板解析器误判为插值） */
const DEFAULT_TEMPLATE = '{{note}}{{selection}}'

const detail = ref<MarketPluginDetail | null>(null)
const loading = ref(false)
const commentText = ref('')
const sending = ref(false)
const likeBusy = ref(false)
const useBusy = ref(false)

watch(
  () => props.pluginId,
  async (id) => {
    if (id == null) {
      detail.value = null
      return
    }
    await load(id)
  },
)

async function load(id: number) {
  loading.value = true
  try {
    detail.value = await marketApi.detail(id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '详情加载失败')
    detail.value = null
  } finally {
    loading.value = false
  }
}

function avatarUrl(raw?: string | null): string {
  if (!raw) return ''
  return raw.startsWith('http') || raw.startsWith('/upload') ? raw : `/upload/${raw}`
}

function initials(name?: string | null): string {
  return name ? name.slice(0, 1) : '友'
}

/** 当前用户是否可删除某条评论（评论作者或插件作者） */
function canDeleteComment(c: MarketComment): boolean {
  const uid = userStore.profile?.id
  if (uid == null) return false
  return c.userId === uid || detail.value?.authorId === uid
}

async function toggleLike() {
  if (!detail.value || likeBusy.value) return
  likeBusy.value = true
  try {
    const state = await marketApi.like(detail.value.id)
    detail.value.liked = state.liked
    detail.value.likeCount = state.likeCount
    emit('changed')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    likeBusy.value = false
  }
}

async function usePlugin() {
  if (!detail.value || useBusy.value) return
  useBusy.value = true
  try {
    const state = await marketApi.use(detail.value.id)
    detail.value.used = true
    detail.value.useCount = state.useCount
    emit('changed')
    ElMessage.success(
      state.newlyUsed ? `已装进「我的插件」（${state.toolId}）` : '你已装过这个插件，使用数已累计',
    )
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '使用失败')
  } finally {
    useBusy.value = false
  }
}

function buildManifest(): string {
  const d = detail.value
  if (!d) return ''
  return JSON.stringify(
    {
      toolId: d.toolId,
      name: d.name,
      description: d.description ?? '',
      systemPrompt: d.systemPrompt,
      userTemplate: d.userTemplate ?? '{{note}}{{selection}}',
      insertable: d.insertable !== false,
    },
    null,
    2,
  )
}

async function copyJson() {
  try {
    await navigator.clipboard.writeText(buildManifest())
    ElMessage.success('manifest JSON 已复制，去「设置 → AI 插件 → 导入 JSON」粘贴即可')
  } catch {
    ElMessage.warning('复制失败，请手动选择 JSON 文本复制')
  }
}

async function submitComment() {
  if (!detail.value) return
  const content = commentText.value.trim()
  if (!content) {
    ElMessage.warning('先写点评论内容')
    return
  }
  sending.value = true
  try {
    const created = await marketApi.comment(detail.value.id, content)
    detail.value.comments.unshift(created)
    detail.value.commentCount += 1
    commentText.value = ''
    emit('changed')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '评论失败')
  } finally {
    sending.value = false
  }
}

async function removeComment(c: MarketComment) {
  try {
    await ElMessageBox.confirm('确定删除这条评论？', '删除评论', { type: 'warning' })
  } catch {
    return
  }
  try {
    await marketApi.deleteComment(c.id)
    if (detail.value) {
      detail.value.comments = detail.value.comments.filter((x) => x.id !== c.id)
      detail.value.commentCount = Math.max(0, detail.value.commentCount - 1)
    }
    emit('changed')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

function fmtTime(raw?: string | null): string {
  if (!raw) return ''
  return raw.replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <div v-if="pluginId != null" class="overlay" @click.self="emit('close')">
    <div class="dialog card anim-pop" role="dialog" aria-modal="true">
      <button class="dialog__close" type="button" aria-label="关闭" @click="emit('close')">×</button>

      <template v-if="loading">
        <div class="skeleton" style="height: 240px"></div>
      </template>
      <template v-else-if="detail">
        <header class="d-head">
          <div class="d-head__title">
            <h2>{{ detail.name }}</h2>
            <div class="d-head__chips">
              <span class="chip chip--brand">{{ detail.categoryName }}</span>
              <span class="chip">{{ detail.toolId }}</span>
              <span v-if="detail.insertable !== false" class="chip">可插入正文</span>
            </div>
          </div>
          <div class="d-head__author">
            <img v-if="avatarUrl(detail.authorAvatar)" class="avatar" :src="avatarUrl(detail.authorAvatar)" :alt="detail.authorName" />
            <span v-else class="avatar avatar--fallback">{{ initials(detail.authorName) }}</span>
            <span class="muted">{{ detail.authorName }}</span>
          </div>
        </header>

        <p v-if="detail.description" class="d-desc muted">{{ detail.description }}</p>
        <div v-if="detail.tags?.length" class="d-tags">
          <span v-for="tag in detail.tags" :key="tag" class="tag">#{{ tag }}</span>
        </div>

        <div class="d-stats">
          <span class="stat">❤ {{ detail.likeCount }}</span>
          <span class="stat">💬 {{ detail.commentCount }}</span>
          <span class="stat">⬇ {{ detail.useCount }} 人使用</span>
        </div>

        <div class="d-ops">
          <button type="button" class="btn" :class="{ 'like-on': detail.liked }" :disabled="likeBusy" @click="toggleLike">
            {{ detail.liked ? '取消爱心' : '点爱心' }}
          </button>
          <button type="button" class="btn" :disabled="useBusy" @click="usePlugin">
            {{ detail.used ? '已安装' : '安装使用' }}
          </button>
          <button type="button" class="btn btn--primary" @click="copyJson">复制 JSON</button>
        </div>

        <section class="d-sec">
          <h3 class="d-sec__title">插件设定（manifest）</h3>
          <div class="d-field">
            <span class="d-field__label">系统提示词</span>
            <pre class="d-code">{{ detail.systemPrompt || '（空）' }}</pre>
          </div>
          <div class="d-field">
            <span class="d-field__label">用户模板</span>
            <pre class="d-code">{{ detail.userTemplate || DEFAULT_TEMPLATE }}</pre>
          </div>
        </section>

        <section class="d-sec">
          <h3 class="d-sec__title">评论 · {{ detail.comments.length }}</h3>
          <div class="d-compose">
            <textarea v-model="commentText" class="field" rows="2" maxlength="1000" placeholder="说点什么……"></textarea>
            <button type="button" class="btn btn--primary btn--sm" :disabled="sending" @click="submitComment">
              {{ sending ? '发送中…' : '发表评论' }}
            </button>
          </div>
          <ul v-if="detail.comments.length" class="comments">
            <li v-for="c in detail.comments" :key="c.id" class="comment">
              <img v-if="avatarUrl(c.userAvatar)" class="avatar avatar--sm" :src="avatarUrl(c.userAvatar)" :alt="c.userName" />
              <span v-else class="avatar avatar--sm avatar--fallback">{{ initials(c.userName) }}</span>
              <div class="comment__body">
                <div class="comment__meta">
                  <strong>{{ c.userName }}</strong>
                  <span class="muted">{{ fmtTime(c.createTime) }}</span>
                </div>
                <p class="comment__content">{{ c.content }}</p>
              </div>
              <button v-if="canDeleteComment(c)" class="comment__del" type="button" title="删除" @click="removeComment(c)">×</button>
            </li>
          </ul>
          <p v-else class="muted d-empty">还没有评论，来抢沙发。</p>
        </section>
      </template>
      <p v-else class="muted">没有找到这个插件。</p>
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
  width: min(640px, 100%);
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

.d-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-3);
}

.d-head__title h2 {
  font-size: 19px;
}

.d-head__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: var(--sp-2);
}

.d-head__author {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: none;
  font-size: 12px;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
  flex: none;
}

.avatar--sm {
  width: 24px;
  height: 24px;
}

.avatar--fallback {
  display: grid;
  place-items: center;
  background: var(--brand-soft-strong);
  color: var(--brand-text);
  font-size: 13px;
  font-weight: 700;
}

.d-desc {
  margin-top: var(--sp-3);
  font-size: 13px;
  line-height: 1.7;
}

.d-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-top: var(--sp-2);
}

.tag {
  font-size: 12px;
  color: var(--brand-text);
}

.d-stats {
  display: flex;
  gap: var(--sp-4);
  margin-top: var(--sp-3);
  font-size: 13px;
  color: var(--ink-2);
  font-variant-numeric: tabular-nums;
}

.d-ops {
  display: flex;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
  flex-wrap: wrap;
}

.like-on {
  color: color-mix(in srgb, var(--danger) 78%, var(--ink));
  border-color: color-mix(in srgb, var(--danger) 40%, transparent);
  background: color-mix(in srgb, var(--danger) 9%, var(--paper-surface));
}

.d-sec {
  margin-top: var(--sp-5);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--paper-line);
}

.d-sec__title {
  font-size: 14px;
  margin-bottom: var(--sp-3);
}

.d-field {
  display: grid;
  gap: 6px;
  margin-bottom: var(--sp-3);
}

.d-field__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-3);
}

.d-code {
  margin: 0;
  padding: var(--sp-3);
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-muted);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.d-compose {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--sp-2);
}

.d-compose textarea {
  min-height: 60px;
}

.comments {
  list-style: none;
  margin: var(--sp-3) 0 0;
  padding: 0;
  display: grid;
  gap: var(--sp-2);
}

.comment {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-2);
  padding: var(--sp-2) var(--sp-3);
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-raised);
}

.comment__body {
  flex: 1;
  min-width: 0;
}

.comment__meta {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
  font-size: 12px;
}

.comment__content {
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.comment__del {
  border: 0;
  background: none;
  color: var(--ink-4);
  font-size: 16px;
  cursor: pointer;
  flex: none;
  line-height: 1;
}

.comment__del:hover {
  color: var(--danger);
}

.d-empty {
  margin-top: var(--sp-2);
  font-size: 12px;
}
</style>
