<script setup lang="ts">
/**
 * AI 助手面板，绑定在笔记书页右侧。
 *
 * 能力分三类：
 * 1. 对话（SSE 流式）：自由问答，可延续会话、回看历史；
 * 2. 改写（预览 + 确认覆盖）：润色 / 扩写 / 缩写，选中文字后先出 diff，用户确认才覆盖；
 * 3. 场景化（无需 Key 也能用）：科研 → 参考文献推荐；生活 / 小说 → 背景知识浮现；
 *    另有续写、拟题、摘要、一致性检查等快捷动作，结果可直接插入正文。
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { aiApi, aiToolApi } from '@/api'
import { getCode, streamChat } from '@/api/http'
import type {
  AiMessage,
  AiSession,
  AiTool,
  AiToolRunResult,
  AiToolTextData,
  KnowledgeResult,
  ReferenceItem,
  ReferenceResult,
  RewritePreview,
  Suggestions,
} from '@/api/types'
import { ElMessage } from 'element-plus'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{
  noteId: number | null
  enabledTools: string[]
  selection: string
  configured: boolean
  model?: string | null
  providerName?: string | null
}>()

const emit = defineEmits<{
  insert: [text: string]
  rewrite: [preview: RewritePreview, action: 'polish' | 'expand' | 'shrink']
  'rewrite-start': []
  openSettings: []
}>()

interface ChatMsg {
  role: 'user' | 'assistant'
  content: string
}

// ---------- 对话 ----------
const messages = ref<ChatMsg[]>([])
const input = ref('')
const inputRef = ref<HTMLTextAreaElement | null>(null)
const streaming = ref(false)
const sessionId = ref<number | null>(null)
const sessions = ref<AiSession[]>([])

// ---------- 插件（动态工具） ----------
const tools = ref<AiTool[]>([])
const toolResults = ref<Record<string, AiToolRunResult>>({})
const busyTool = ref<string | null>(null)
const bindingRefId = ref<number | null>(null)

// ---------- 各类结果 ----------
const suggestions = ref<Suggestions | null>(null)
const actionText = ref<{ action: string; text: string; model?: string } | null>(null)

// ---------- 忙状态 ----------
const busyRewrite = ref(false)
const busyAction = ref<string | null>(null)

// ---------- 侧边栏导航 ----------
type TabKey = 'tools' | 'plugins' | 'chat'
const activeTab = ref<TabKey>('tools')

// 可见插件：按「书写习惯」里为该笔记类型开启的插件 id 过滤
const visibleTools = computed(() => tools.value.filter((t) => props.enabledTools.includes(t.id)))

function modelLabel(): string {
  if (props.model) return props.providerName ? `${props.providerName} · ${props.model}` : props.model
  return '未配置模型'
}

function explain(error: unknown): string {
  const code = getCode(error)
  const raw = error instanceof Error && error.message ? error.message : ''
  if (code === 2001) return '还没有配置 AI 模型，请到「个人设置 - AI 模型」里填写 API Key'
  if (code === 2002) return raw || 'AI 服务暂时不可用，稍后再试'
  return raw || '操作没有成功'
}

// ---------- 会话 ----------
async function loadSessions() {
  if (props.noteId == null) {
    sessions.value = []
    return
  }
  try {
    sessions.value = await aiApi.sessions(props.noteId)
  } catch {
    sessions.value = []
  }
}

async function pickSession(value: string) {
  if (!value) {
    newChat()
    return
  }
  const sid = Number(value)
  if (!Number.isFinite(sid)) return
  sessionId.value = sid
  try {
    const list = await aiApi.messages(sid)
    messages.value = list
      .filter((m: AiMessage) => m.role === 'user' || m.role === 'assistant')
      .map((m: AiMessage) => ({ role: m.role as 'user' | 'assistant', content: m.content }))
  } catch (error) {
    ElMessage.error(explain(error))
  }
}

function newChat() {
  sessionId.value = null
  messages.value = []
}

// ---------- 对话发送 ----------
async function send() {
  const question = input.value.trim()
  if (!question || streaming.value) return
  if (props.noteId == null) {
    ElMessage.warning('先打开一篇笔记，再来和助手聊')
    return
  }
  input.value = ''
  messages.value.push({ role: 'user', content: question })
  const assistant: ChatMsg = { role: 'assistant', content: '' }
  messages.value.push(assistant)
  streaming.value = true
  try {
    const result = await streamChat(
      { noteId: props.noteId, sessionId: sessionId.value, question },
      (delta: string) => {
        assistant.content += delta
      },
    )
    if (result.sessionId) sessionId.value = result.sessionId
    await loadSessions()
  } catch (error) {
    assistant.content = assistant.content || explain(error)
  } finally {
    streaming.value = false
  }
}

// ---------- 改写（润色/扩写/缩写） ----------
async function doRewrite(action: 'polish' | 'expand' | 'shrink') {
  if (props.noteId == null) {
    ElMessage.warning('先打开一篇笔记')
    return
  }
  if (!props.selection.trim()) {
    ElMessage.warning('先在编辑器里选中要修改的文字，再点润色 / 扩写 / 缩写')
    return
  }
  busyRewrite.value = true
  emit('rewrite-start')
  try {
    const preview = await aiApi.rewrite({ noteId: props.noteId, action, selection: props.selection })
    // 改写建议交给书写页在编辑器里内联展示，而不是堆在面板里
    emit('rewrite', preview, action)
  } catch (error) {
    ElMessage.error(explain(error))
  } finally {
    busyRewrite.value = false
  }
}

// ---------- 快捷动作 ----------
const ACTION_LABELS: Record<string, string> = {
  continue: '续写',
  title: '拟题',
  summary: '摘要',
  consistency: '一致性',
  name: '起名',
  ending: '写结尾',
}

async function doAction(action: string) {
  if (props.noteId == null) {
    ElMessage.warning('先打开一篇笔记')
    return
  }
  actionText.value = null
  busyAction.value = action
  try {
    const result = await aiApi.action({
      noteId: props.noteId,
      action,
      selection: props.selection || undefined,
      sessionId: sessionId.value ?? undefined,
    })
    actionText.value = { action, text: result.text, model: result.model }
  } catch (error) {
    ElMessage.error(explain(error))
  } finally {
    busyAction.value = null
  }
}

function insertAction() {
  if (!actionText.value) return
  emit('insert', actionText.value.text)
  actionText.value = null
}

// ---------- 插件（动态工具） ----------
async function loadTools() {
  try {
    tools.value = await aiToolApi.list()
  } catch {
    tools.value = []
  }
}

async function runTool(tool: AiTool) {
  if (props.noteId == null) {
    ElMessage.warning('先打开一篇笔记')
    return
  }
  busyTool.value = tool.id
  try {
    const result = await aiToolApi.run({
      id: tool.id,
      noteId: props.noteId,
      selection: props.selection || undefined,
    })
    toolResults.value = { ...toolResults.value, [tool.id]: result }
  } catch (error) {
    ElMessage.error(explain(error))
  } finally {
    busyTool.value = null
  }
}

function clearToolResult(id: string) {
  const next = { ...toolResults.value }
  delete next[id]
  toolResults.value = next
}

function textResult(tool: AiTool): AiToolTextData | null {
  const r = toolResults.value[tool.id]
  return r && r.renderType === 'text' ? (r.data as AiToolTextData) : null
}

function cardsResult(tool: AiTool): KnowledgeResult | null {
  const r = toolResults.value[tool.id]
  return r && r.renderType === 'cards' ? (r.data as KnowledgeResult) : null
}

function refsResult(tool: AiTool): ReferenceResult | null {
  const r = toolResults.value[tool.id]
  return r && r.renderType === 'references' ? (r.data as ReferenceResult) : null
}

function insertToolText(tool: AiTool) {
  const data = textResult(tool)
  if (data?.text) emit('insert', data.text)
}

async function adoptKnowledge(cardId: number, content: string) {
  try {
    await aiApi.adoptKnowledge(cardId)
  } catch {
    /* 采纳计数失败不阻断插入 */
  }
  emit('insert', content)
}

async function toggleBind(item: ReferenceItem) {
  if (props.noteId == null || item.id == null) return
  bindingRefId.value = item.id
  try {
    if (item.bound) {
      await aiApi.unbindReference(props.noteId, item.id)
    } else {
      await aiApi.bindReference({ noteId: props.noteId, refId: item.id, source: item.source })
    }
    const refsTool = tools.value.find((t) => t.id === 'references')
    if (refsTool) await runTool(refsTool)
  } catch (error) {
    ElMessage.error(explain(error))
  } finally {
    bindingRefId.value = null
  }
}

// ---------- 飘窗建议 ----------
async function loadSuggestions(previous?: string[]) {
  if (props.noteId == null) {
    suggestions.value = null
    return
  }
  try {
    suggestions.value = await aiApi.suggest({
      noteId: props.noteId,
      selection: props.selection || undefined,
      previous,
    })
  } catch {
    suggestions.value = null
  }
}

/** 换一批建议：把上一轮的问题带给后端，避免重复 */
function reloadSuggestions() {
  const prev = suggestions.value?.suggestions ?? []
  void loadSuggestions(prev.length ? prev : undefined)
}

function useSuggestion(text: string) {
  fillQuestion(text)
}

/** 供书写页把"改写追问"一键填入对话框，让用户继续和助手对话 */
function setQuestion(text: string) {
  fillQuestion(text)
}

/** 把问题填进输入框并切到问答页：放大输入框、聚焦并把光标放到末尾，方便直接改 */
function fillQuestion(text: string) {
  input.value = text
  activeTab.value = 'chat'
  nextTick(() => {
    resizeInput()
    const el = inputRef.value
    if (el) {
      el.focus()
      el.setSelectionRange(el.value.length, el.value.length)
    }
  })
}

/** 输入框随内容自动增高（最多 220px），保证长问题导入后也能看清 */
function resizeInput() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(Math.max(el.scrollHeight, 36), 220)}px`
}

onMounted(async () => {
  await Promise.all([loadSessions(), loadSuggestions(), loadTools()])
})

// 切换笔记时重置面板状态并刷新建议
watch(
  () => props.noteId,
  () => {
    newChat()
    toolResults.value = {}
    actionText.value = null
    void Promise.all([loadSessions(), loadSuggestions()])
  },
)

watch(input, () => nextTick(resizeInput))

defineExpose({ setQuestion })
</script>

<template>
  <div class="ai">
    <header class="ai__head">
      <div class="ai__title">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M12 4.5l1.7 4.3 4.3 1.7-4.3 1.7L12 16.5l-1.7-4.3-4.3-1.7 4.3-1.7L12 4.5Z" />
          <path d="M18.5 16.5l.7 1.8 1.8.7-1.8.7-.7 1.8-.7-1.8-1.8-.7 1.8-.7.7-1.8Z" />
        </svg>
        <strong>AI 助手</strong>
        <span class="ai__model muted">{{ modelLabel() }}</span>
      </div>
      <div class="ai__head-actions">
        <button class="btn btn--ghost btn--icon" type="button" v-tooltip="'配置模型'" @click="emit('openSettings')">⚙</button>
      </div>
    </header>

    <!-- 未配置提示 -->
    <div v-if="!props.configured" class="ai__banner">
      <p>还没配置模型，对话与改写不可用；文献推荐与背景知识仍能离线使用。</p>
      <button type="button" class="btn btn--sm" @click="emit('openSettings')">去配置模型</button>
    </div>

    <div class="ai__layout">
      <!-- 侧边栏导航：切换主区域内容 -->
      <nav class="ai__nav">
        <button type="button" class="ai__nav-item" :class="{ 'is-active': activeTab === 'tools' }" @click="activeTab = 'tools'">
          <span class="ai__nav-label">工具</span>
        </button>
        <button type="button" class="ai__nav-item" :class="{ 'is-active': activeTab === 'plugins' }" @click="activeTab = 'plugins'">
          <span class="ai__nav-label">插件</span>
        </button>
        <button type="button" class="ai__nav-item" :class="{ 'is-active': activeTab === 'chat' }" @click="activeTab = 'chat'">
          <span class="ai__nav-label">问答</span>
        </button>
      </nav>

      <!-- 主内容区 -->
      <div class="ai__main">
        <!-- 工具 -->
        <div v-if="activeTab === 'tools'" class="ai__tab">
          <div v-if="suggestions && suggestions.suggestions.length" class="ai__section">
            <div class="ai__section-head">
              <p class="ai__label muted">也许你想问</p>
              <button type="button" class="mini ai__reload" v-tooltip="'换一批问题'" aria-label="换一批问题" @click="reloadSuggestions">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 12a8 8 0 1 1-2.34-5.66" /><path d="M20 4v4h-4" /></svg>
              </button>
            </div>
            <div class="ai__suggestions">
              <button
                v-for="(s, i) in suggestions.suggestions"
                :key="i"
                type="button"
                class="chip chip--click"
                @click="useSuggestion(s)"
              >
                {{ s }}
              </button>
            </div>
          </div>

          <div class="ai__section">
            <p class="ai__label muted">改写（先选中文字）</p>
            <div class="ai__actions">
              <button type="button" class="btn btn--sm" :disabled="busyRewrite" @click="doRewrite('polish')">润色</button>
              <button type="button" class="btn btn--sm" :disabled="busyRewrite" @click="doRewrite('expand')">扩写</button>
              <button type="button" class="btn btn--sm" :disabled="busyRewrite" @click="doRewrite('shrink')">缩写</button>
            </div>
          </div>

          <div class="ai__section">
            <p class="ai__label muted">快捷动作</p>
            <div class="ai__actions">
              <button
                v-for="(label, action) in ACTION_LABELS"
                :key="action"
                type="button"
                class="btn btn--sm"
                :disabled="busyAction === action"
                @click="doAction(action)"
              >
                {{ busyAction === action ? '…' : label }}
              </button>
            </div>
          </div>

          <div v-if="busyRewrite || busyAction" class="ai__thinking">
            <span class="ai__thinking-dots" aria-hidden="true"><i></i><i></i><i></i></span>
            <span class="muted">正在思考…</span>
          </div>

          <div v-if="actionText" class="ai__result">
            <div class="ai__result-head">
              <strong>{{ ACTION_LABELS[actionText.action] ?? actionText.action }}结果</strong>
              <button type="button" class="mini" @click="actionText = null">✕</button>
            </div>
            <div class="ai__text" v-html="renderMarkdown(actionText.text)"></div>
            <div class="ai__result-foot">
              <button type="button" class="btn btn--sm btn--primary" @click="insertAction">插入正文</button>
            </div>
          </div>
        </div>

        <!-- 插件（动态工具） -->
        <div v-else-if="activeTab === 'plugins'" class="ai__tab">
          <p v-if="!visibleTools.length" class="muted">
            当前笔记类型没有可用的插件。可以到「个人设置 - AI 插件」里添加你自己的工具。
          </p>
          <div v-for="tool in visibleTools" :key="tool.id" class="ai__section">
            <div class="ai__section-head">
              <div class="ai__tool-title">
                <strong>{{ tool.name }}</strong>
                <span class="chip chip--dim">{{ tool.origin === 'builtin' ? '内置' : '我的' }}</span>
              </div>
              <button type="button" class="btn btn--sm btn--scene" :disabled="busyTool === tool.id" @click="runTool(tool)">
                {{ busyTool === tool.id ? '运行中…' : '运行' }}
              </button>
            </div>
            <p v-if="tool.description" class="ai__reason muted">{{ tool.description }}</p>

            <!-- 文本结果 -->
            <div v-if="textResult(tool)" class="ai__result">
              <div class="ai__result-head">
                <strong>{{ tool.name }}结果</strong>
                <button type="button" class="mini" @click="clearToolResult(tool.id)">✕</button>
              </div>
              <div class="ai__text" v-html="renderMarkdown(textResult(tool)!.text)"></div>
              <div v-if="textResult(tool)!.insertable" class="ai__result-foot">
                <button type="button" class="btn btn--sm btn--primary" @click="insertToolText(tool)">插入正文</button>
              </div>
            </div>

            <!-- 卡片结果 -->
            <div v-else-if="cardsResult(tool)" class="ai__result">
              <div class="ai__result-head">
                <strong>{{ tool.name }}</strong>
                <button type="button" class="mini" @click="clearToolResult(tool.id)">✕</button>
              </div>
              <p class="ai__reason muted">{{ cardsResult(tool)!.reason }}</p>
              <p v-if="!cardsResult(tool)!.cards.length" class="muted">没有命中相关卡片。</p>
              <div v-for="card in cardsResult(tool)!.cards" :key="card.id" class="kcard">
                <div class="kcard__head">
                  <strong>{{ card.title }}</strong>
                  <span class="chip">{{ card.category }}</span>
                </div>
                <p class="kcard__content">{{ card.content }}</p>
                <div class="kcard__foot">
                  <span v-if="card.source" class="muted">出处：{{ card.source }}</span>
                  <button type="button" class="btn btn--sm" @click="adoptKnowledge(card.id, card.content)">采纳进正文</button>
                </div>
              </div>
            </div>

            <!-- 文献结果 -->
            <div v-else-if="refsResult(tool)" class="ai__result">
              <div class="ai__result-head">
                <strong>{{ tool.name }}</strong>
                <button type="button" class="mini" @click="clearToolResult(tool.id)">✕</button>
              </div>
              <p class="ai__reason muted">{{ refsResult(tool)!.reason }}</p>
              <p v-if="!refsResult(tool)!.online && refsResult(tool)!.onlineError" class="ai__reason muted ai__reason--warn">
                联网原因：{{ refsResult(tool)!.onlineError }}
              </p>
              <p v-if="!refsResult(tool)!.items.length" class="muted">没有找到相关文献。</p>
              <div v-for="item in refsResult(tool)!.items" :key="item.id ?? item.title" class="ref">
                <div class="ref__head">
                  <strong>{{ item.title }}</strong>
                  <span v-if="item.pubYear" class="chip">{{ item.pubYear }}</span>
                  <span v-if="item.source === 'online'" class="chip">联网</span>
                </div>
                <p v-if="item.authors || item.journal" class="ref__meta muted">{{ [item.authors, item.journal].filter(Boolean).join(' · ') }}</p>
                <p v-if="item.reason" class="ref__why muted">{{ item.reason }}</p>
                <div class="ref__foot">
                  <a v-if="item.url || item.doi" class="lnk" :href="item.url || `https://doi.org/${item.doi}`" target="_blank" rel="noopener">打开原文</a>
                  <button
                    v-if="item.id != null"
                    type="button"
                    class="btn btn--sm"
                    :disabled="bindingRefId === item.id"
                    @click="toggleBind(item)"
                  >
                    {{ item.bound ? '解除绑定' : '绑定到笔记' }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 问答 -->
        <div v-else class="ai__tab ai__tab--chat">
          <div class="ai__chat-head">
            <select class="ai__sessions" :value="sessionId ?? ''" @change="pickSession(($event.target as HTMLSelectElement).value)">
              <option value="">＋ 新对话</option>
              <option v-for="s in sessions" :key="s.id" :value="s.id">{{ s.title || `会话 ${s.id}` }}</option>
            </select>
            <button v-if="sessionId" type="button" class="mini" @click="newChat">重置</button>
          </div>

          <div class="ai__messages">
            <div v-if="!messages.length" class="ai__empty">
              <span class="ai__empty-icon" aria-hidden="true">✨</span>
              <strong>开启你的第一次对话吧</strong>
              <p class="muted">在下方输入问题，或切到右侧「工具」用改写 / 快捷动作帮你想。</p>
            </div>
            <div v-for="(m, i) in messages" :key="i" class="msg" :class="`msg--${m.role}`">
              <div class="msg__text" v-html="m.content ? renderMarkdown(m.content) : (streaming && i === messages.length - 1 ? '正在想…' : '')"></div>
            </div>
          </div>

          <div class="ai__input">
            <textarea
              ref="inputRef"
              v-model="input"
              class="field ai__textarea"
              rows="1"
              placeholder="问点什么，或让助手起个头"
              @keydown.enter.exact.prevent="send"
            ></textarea>
            <button type="button" class="btn btn--primary btn--sm" :disabled="streaming || !input.trim()" @click="send">
              {{ streaming ? '…' : '发送' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  position: relative;
  background: var(--paper-surface);
  border-left: 1px solid var(--paper-line);
}

.ai__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  padding: var(--sp-3) var(--sp-3);
  border-bottom: 1px solid var(--paper-line-soft);
}

.ai__title {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.ai__title strong {
  font-size: 14px;
  white-space: nowrap;
  flex-shrink: 0;
}

.ai__model {
  font-size: 11px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai__head-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 侧边栏 + 主内容区布局 */
.ai__layout {
  flex: 1;
  min-height: 0;
  display: flex;
}

.ai__nav {
  flex: none;
  width: 64px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--sp-2) var(--sp-1);
  border-left: 1px solid var(--paper-line);
  background: var(--paper-muted);
  order: 2;
}

.ai__nav-item {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 9px 4px;
  border: 0;
  border-radius: var(--r-sm);
  background: none;
  color: var(--ink-2);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}

.ai__nav-item:hover {
  background: var(--brand-soft);
  color: var(--brand-text);
}

.ai__nav-item.is-active {
  background: var(--brand-soft-strong);
  color: var(--brand-text);
  font-weight: 600;
}

.ai__nav-label {
  line-height: 1.2;
  text-align: center;
}

.ai__main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  order: 1;
}

.ai__tab {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--sp-3);
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.ai__tab--chat {
  overflow: hidden;
  gap: var(--sp-2);
}

.ai__banner {
  margin: var(--sp-3) var(--sp-3) 0;
  padding: 10px 12px;
  border-radius: var(--r-md);
  border: 1px solid var(--warn);
  background: color-mix(in srgb, var(--warn) 12%, var(--paper-surface));
  font-size: 12px;
  line-height: 1.6;
  display: grid;
  gap: 8px;
  justify-items: start;
}

.ai__section {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.ai__label {
  font-size: 11px;
  letter-spacing: 0.05em;
}

.ai__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
}

.ai__tool-title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 1;
}

.ai__tool-title strong {
  font-size: 13px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip--dim {
  background: var(--paper-muted);
  color: var(--ink-3);
}

.ai__reload {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.ai__reload:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.ai__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.btn--scene {
  flex: none;
  border-color: var(--brand-line);
  background: var(--brand-soft);
  color: var(--brand-text);
}

.btn--scene:hover {
  background: var(--brand-soft-strong);
}

.ai__suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chip--click {
  cursor: pointer;
  background: var(--paper-raised);
}

.chip--click:hover {
  border-color: var(--brand-line);
  color: var(--brand-text);
}

.ai__thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: var(--sp-3);
  border: 1px solid var(--brand-line);
  border-radius: var(--r-md);
  background: var(--paper-raised);
}

.ai__thinking-dots {
  display: inline-flex;
  gap: 4px;
}

.ai__thinking-dots i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand-base);
  animation: ai-thinking-blink 1.2s infinite ease-in-out;
}

.ai__thinking-dots i:nth-child(2) {
  animation-delay: 0.2s;
}

.ai__thinking-dots i:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes ai-thinking-blink {
  0%,
  80%,
  100% {
    opacity: 0.25;
    transform: scale(0.8);
  }
  40% {
    opacity: 1;
    transform: scale(1);
  }
}

.ai__result {
  border: 1px solid var(--brand-line);
  background: var(--paper-raised);
  border-radius: var(--r-md);
  padding: var(--sp-3);
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.ai__result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
}

.ai__result-head strong {
  font-size: 13px;
}

.ai__result-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.mini {
  border: 0;
  background: none;
  color: var(--ink-3);
  font-size: 11px;
  cursor: pointer;
  padding: 2px 4px;
}

.mini:hover {
  color: var(--brand-text);
}

.ai__explain,
.ai__reason,
.ai__hint {
  font-size: 12px;
  line-height: 1.6;
}

.ai__reason--warn {
  color: color-mix(in srgb, var(--warn) 80%, var(--ink));
}

.ai__text {
  font-size: 13px;
  line-height: 1.7;
  word-break: break-word;
}

.ai__result-foot {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 2px;
}

.diff {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-2);
}

.diff__col {
  border: 1px solid var(--paper-line);
  border-radius: var(--r-sm);
  padding: 8px 10px;
  background: var(--paper-muted);
  min-width: 0;
}

.diff__col--new {
  border-color: color-mix(in srgb, var(--ok) 40%, var(--paper-line));
  background: color-mix(in srgb, var(--ok) 8%, var(--paper-surface));
}

.diff__tag {
  font-size: 11px;
  margin-bottom: 4px;
}

.diff__text {
  font-size: 12.5px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.kcard,
.ref {
  border: 1px solid var(--paper-line);
  border-radius: var(--r-sm);
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 5px;
  background: var(--paper-surface);
}

.kcard__head,
.ref__head {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.kcard__head strong,
.ref__head strong {
  font-size: 13px;
}

.kcard__content {
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink-2);
}

.kcard__foot,
.ref__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  font-size: 11px;
}

.ref__meta,
.ref__why {
  font-size: 12px;
  line-height: 1.6;
}

.lnk {
  color: var(--brand-text);
  font-size: 12px;
}

.ai__chat-head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.ai__sessions {
  flex: 1;
  height: 28px;
  appearance: none;
  -webkit-appearance: none;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background-color: var(--paper-surface);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 16 16'%3E%3Cpath d='M4 6l4 4 4-4' fill='none' stroke='%238b7a6d' stroke-width='1.6' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 8px center;
  background-size: 14px 14px;
  color: var(--ink-2);
  font: inherit;
  font-size: 12px;
  padding: 0 24px 0 8px;
  max-width: 100%;
  cursor: pointer;
}

.ai__messages {
  flex: 1;
  min-height: 80px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.ai__empty {
  margin: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-2);
  text-align: center;
  padding: var(--sp-5) var(--sp-3);
}

.ai__empty-icon {
  font-size: 26px;
  line-height: 1;
  opacity: 0.55;
}

.ai__empty strong {
  font-size: 14px;
  color: var(--ink-2);
}

.ai__empty p {
  font-size: 12px;
  max-width: 220px;
  line-height: 1.7;
}

.msg {
  display: flex;
}

.msg--user {
  justify-content: flex-end;
}

.msg--user .msg__text {
  background: var(--brand-soft-strong);
  color: var(--brand-text);
}

.msg--assistant .msg__text {
  background: var(--paper-muted);
  color: var(--ink);
}

.msg__text {
  max-width: 86%;
  padding: 7px 10px;
  border-radius: var(--r-md);
  font-size: 12.5px;
  line-height: 1.7;
  word-break: break-word;
}

/* AI 返回的 Markdown 样式：v-html 生成的标签不受 scoped 约束，需用 :deep 穿透 */
.msg__text :deep(p),
.ai__text :deep(p) {
  margin: 0;
}

.msg__text :deep(p + p),
.ai__text :deep(p + p) {
  margin-top: 6px;
}

.msg__text :deep(h1),
.msg__text :deep(h2),
.msg__text :deep(h3),
.msg__text :deep(h4),
.msg__text :deep(h5),
.msg__text :deep(h6),
.ai__text :deep(h1),
.ai__text :deep(h2),
.ai__text :deep(h3),
.ai__text :deep(h4),
.ai__text :deep(h5),
.ai__text :deep(h6) {
  margin: 8px 0 4px;
  font-weight: 700;
  line-height: 1.4;
}

.msg__text :deep(h1),
.ai__text :deep(h1) { font-size: 1.4em; }
.msg__text :deep(h2),
.ai__text :deep(h2) { font-size: 1.3em; }
.msg__text :deep(h3),
.ai__text :deep(h3) { font-size: 1.2em; }
.msg__text :deep(h4),
.ai__text :deep(h4) { font-size: 1.1em; }
.msg__text :deep(h5),
.ai__text :deep(h5) { font-size: 1.05em; }
.msg__text :deep(h6),
.ai__text :deep(h6) { font-size: 1em; }

.msg__text :deep(ul),
.msg__text :deep(ol),
.ai__text :deep(ul),
.ai__text :deep(ol) {
  margin: 6px 0;
  padding-left: 20px;
}

.msg__text :deep(li),
.ai__text :deep(li) {
  margin: 2px 0;
}

.msg__text :deep(code),
.ai__text :deep(code) {
  font-family: var(--font-mono);
  font-size: 0.92em;
  padding: 1px 5px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--brand-base) 10%, transparent);
}

.msg__text :deep(pre),
.ai__text :deep(pre) {
  margin: 8px 0;
  padding: 8px 10px;
  border-radius: var(--r-sm);
  background: color-mix(in srgb, var(--paper-muted) 70%, transparent);
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

.msg__text :deep(pre code),
.ai__text :deep(pre code) {
  padding: 0;
  background: none;
}

.msg__text :deep(blockquote),
.ai__text :deep(blockquote) {
  margin: 6px 0;
  padding: 2px 10px;
  border-left: 3px solid var(--brand-line);
  color: var(--ink-2);
}

.msg__text :deep(hr),
.ai__text :deep(hr) {
  margin: 8px 0;
  border: 0;
  border-top: 1px solid var(--paper-line);
}

.msg__text :deep(a),
.ai__text :deep(a) {
  color: var(--brand-text);
  text-decoration: underline;
}

.ai__input {
  display: flex;
  gap: var(--sp-2);
  align-items: flex-end;
}

.ai__textarea {
  flex: 1;
  min-height: 36px;
  max-height: 220px;
  resize: none;
  overflow-y: auto;
}
</style>
