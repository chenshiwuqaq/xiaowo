<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { aiToolApi } from '@/api'
import { getCode } from '@/api/http'
import type { AiToolDetail } from '@/api/types'

type FeedbackKind = 'ok' | 'err' | 'info'
const feedback = ref<{ kind: FeedbackKind; text: string } | null>(null)
let feedbackTimer: ReturnType<typeof setTimeout> | undefined

function say(kind: FeedbackKind, text: string, holdMs = 5200) {
  if (feedbackTimer) clearTimeout(feedbackTimer)
  feedback.value = { kind, text }
  feedbackTimer = setTimeout(() => {
    feedback.value = null
  }, holdMs)
}

onBeforeUnmount(() => {
  if (feedbackTimer) clearTimeout(feedbackTimer)
})

function explain(error: unknown): string {
  const code = getCode(error)
  const raw = error instanceof Error && error.message ? error.message : ''
  if (code === 1005) return raw || '填写的值超出了允许范围'
  if (code === 400) return raw || '参数校验没有通过，请检查表单'
  return raw || '操作没有成功，请稍后重试'
}

const tools = ref<AiToolDetail[]>([])
const loadingList = ref(false)

interface ToolForm {
  id?: number
  toolId: string
  name: string
  description: string
  systemPrompt: string
  userTemplate: string
  insertable: boolean
}

const emptyForm = (): ToolForm => ({
  toolId: '',
  name: '',
  description: '',
  systemPrompt: '',
  userTemplate: '{{note}}{{selection}}',
  insertable: true,
})

const form = reactive<ToolForm>(emptyForm())
const editing = ref(false)
const saving = ref(false)
const busyId = ref<number | null>(null)

const importOpen = ref(false)
const importJson = ref('')

/** 示例 manifest：第一次用可以直接点「填入示例」，照着改就行 */
const EXAMPLE_JSON = `{
  "toolId": "poetry-check",
  "name": "格律检查",
  "description": "检查选中诗句的平仄与押韵，指出问题并给出改法",
  "systemPrompt": "你是古典诗词格律专家。只检查平仄、押韵与对仗，不评价意境，不擅自改诗。发现不合律的地方，要指出具体字句并给出修改建议。",
  "userTemplate": "请检查下面这段文字的格律：{{selection}}",
  "insertable": true
}`

function fillExample() {
  importJson.value = EXAMPLE_JSON
}

async function loadTools() {
  loadingList.value = true
  try {
    tools.value = await aiToolApi.mine()
  } catch (error) {
    tools.value = []
    say('err', explain(error))
  } finally {
    loadingList.value = false
  }
}

onMounted(loadTools)

function openNew() {
  Object.assign(form, emptyForm())
  editing.value = true
  importOpen.value = false
}

function openEdit(tool: AiToolDetail) {
  Object.assign(form, {
    id: tool.id,
    toolId: tool.toolId,
    name: tool.name,
    description: tool.description ?? '',
    systemPrompt: tool.systemPrompt ?? '',
    userTemplate: tool.userTemplate ?? '{{note}}{{selection}}',
    insertable: tool.insertable ?? true,
  })
  editing.value = true
  importOpen.value = false
}

function closeForm() {
  editing.value = false
}

function validate(): string | null {
  if (!form.toolId.trim()) return '请填写插件标识（如 poetry-check）'
  if (!/^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$/.test(form.toolId.trim())) {
    return '插件标识只能由字母、数字、下划线、中划线组成（1~64 位，首字符为字母或数字）'
  }
  if (!form.name.trim()) return '请填写插件名称'
  if (!form.systemPrompt.trim()) return '请填写系统提示词'
  return null
}

async function saveTool() {
  const problem = validate()
  if (problem) {
    say('err', problem)
    return
  }
  saving.value = true
  const payload: AiToolDetail = {
    toolId: form.toolId.trim(),
    name: form.name.trim(),
    description: form.description.trim() || undefined,
    renderType: 'text',
    systemPrompt: form.systemPrompt,
    userTemplate: form.userTemplate || undefined,
    insertable: form.insertable,
  }
  try {
    if (form.id != null) {
      await aiToolApi.update(form.id, payload)
      say('ok', '插件已更新')
    } else {
      await aiToolApi.create(payload)
      say('ok', '插件已新增')
    }
    closeForm()
    await loadTools()
  } catch (error) {
    say('err', '保存失败：' + explain(error))
  } finally {
    saving.value = false
  }
}

async function toggleTool(tool: AiToolDetail) {
  if (tool.id == null) return
  busyId.value = tool.id
  try {
    await aiToolApi.toggle(tool.id)
    await loadTools()
  } catch (error) {
    say('err', explain(error))
  } finally {
    busyId.value = null
  }
}

async function removeTool(tool: AiToolDetail) {
  if (tool.id == null) return
  if (!window.confirm(`确定删除「${tool.name}」？删除后助手面板里不再显示它。`)) return
  busyId.value = tool.id
  try {
    await aiToolApi.remove(tool.id)
    if (editing.value && form.id === tool.id) closeForm()
    say('ok', `已删除「${tool.name}」`)
    await loadTools()
  } catch (error) {
    say('err', '删除失败：' + explain(error))
  } finally {
    busyId.value = null
  }
}

/** 从 JSON manifest 导入：解析后填进表单，不直接落库，给用户改完再保存 */
function applyImport() {
  const raw = importJson.value.trim()
  if (!raw) {
    say('err', '先粘贴插件 JSON')
    return
  }
  try {
    const parsed = JSON.parse(raw) as Partial<ToolForm> & { id?: number }
    Object.assign(form, emptyForm(), {
      toolId: String(parsed.toolId ?? ''),
      name: String(parsed.name ?? ''),
      description: String(parsed.description ?? ''),
      systemPrompt: String(parsed.systemPrompt ?? ''),
      userTemplate: String(parsed.userTemplate ?? '{{note}}{{selection}}'),
      insertable: parsed.insertable !== false,
    })
    delete (form as { id?: number }).id
    editing.value = true
    importOpen.value = false
    importJson.value = ''
    say('info', '已解析 manifest，请核对后保存')
  } catch {
    say('err', 'JSON 格式不对，请检查引号与逗号')
  }
}
</script>

<template>
  <section class="card sec">
    <h2 class="sec__title">AI 插件</h2>
    <p class="sec__desc muted">给自己写几个趁手的 AI 工具：填一段系统提示词，它就会在助手面板里按你的规则处理当前笔记。</p>

    <div v-if="feedback" class="fb" :class="`fb--${feedback.kind}`" role="status">{{ feedback.text }}</div>

    <div class="privacy">
      <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M12 3.5l7 3v5c0 4.6-3 8-7 9.5-4-1.5-7-4.9-7-9.5v-5l7-3Z" />
        <path d="M9.5 12l1.8 1.8 3.4-3.6" />
      </svg>
      <div>
        <p class="privacy__main">声明式插件会调用你配置的默认模型。</p>
        <p v-pre class="muted tiny">系统提示词决定插件的角色与行为；模板里的 {{note}}、{{selection}}、{{siblings}}、{{title}} 会被替换成当前笔记、选区、邻域摘要与标题。</p>
      </div>
    </div>

    <div class="head-line">
      <h3 class="group__title">我的插件</h3>
      <div class="head-actions">
        <button type="button" class="btn btn--sm" @click="importOpen = !importOpen">导入 JSON</button>
        <button type="button" class="btn btn--sm btn--primary" @click="openNew">
          <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M12 5.5v13M5.5 12h13" /></svg>
          新增插件
        </button>
      </div>
    </div>

    <!-- 导入 JSON -->
    <div v-if="importOpen" class="import-box">
      <div class="import-box__head">
        <p class="import-box__title">粘贴插件 JSON（manifest）</p>
        <button type="button" class="btn btn--sm" @click="fillExample">填入示例</button>
      </div>
      <textarea v-model="importJson" class="field import-area" spellcheck="false" placeholder="把插件描述粘贴到这里，或点右上角「填入示例」先看一个完整例子"></textarea>
      <div class="import-actions">
        <button type="button" class="btn btn--ghost" @click="importOpen = false">取消</button>
        <button type="button" class="btn" @click="applyImport">解析并填入表单</button>
      </div>

      <div v-pre class="import-guide">
        <p class="import-guide__title">每个字段是干嘛的（小白照着改就行）</p>
        <ul class="import-guide__list">
          <li><code>toolId</code> —— 插件唯一标识，只写英文，可用字母/数字/下划线/中划线，如 <code>poetry-check</code>；保存后不能改。</li>
          <li><code>name</code> —— 显示在助手面板上的名字，起个中文名，如「格律检查」。</li>
          <li><code>description</code> —— 一句话说明它是干嘛的，可不填。</li>
          <li><code>systemPrompt</code> —— 给 AI 定人设和规矩（最关键）：写清「你是谁、只做什么、不做什么」。</li>
          <li><code>userTemplate</code> —— 发给 AI 的具体任务，可塞变量：<code>{{note}}</code> 整篇笔记、<code>{{selection}}</code> 选中的文字、<code>{{title}}</code> 标题、<code>{{siblings}}</code> 同目录摘要。</li>
          <li><code>insertable</code> —— 结果要不要带「插入正文」按钮，填 <code>true</code> 或 <code>false</code>。</li>
        </ul>
        <p class="import-guide__tip muted">提示：粘贴 JSON 点「解析并填入表单」后，内容会自动填进上面的表单，核对后再点「保存」。保存后还要到「书写习惯」里按笔记类型开启这个插件，它才会出现在 AI 助手面板。</p>
      </div>
    </div>

    <p v-if="loadingList" class="muted tiny">加载插件列表…</p>
    <template v-else>
      <div v-if="!tools.length" class="empty muted">
        还没有自己的插件。内置的「背景知识」「文献推荐」已在助手面板里；点「新增插件」写一个只属于你的工具，或「导入 JSON」批量带入。
      </div>
      <ul v-else class="cfg-list">
        <li v-for="tool in tools" :key="tool.id ?? tool.toolId" class="cfg">
          <div class="cfg__main">
            <div class="cfg__name">
              <strong>{{ tool.name }}</strong>
              <span class="chip">{{ tool.toolId }}</span>
              <span v-if="tool.enabled === false" class="chip chip--off">已停用</span>
            </div>
            <div v-if="tool.description" class="cfg__meta muted">{{ tool.description }}</div>
          </div>
          <div class="cfg__ops">
            <button type="button" class="btn btn--sm" @click="openEdit(tool)">编辑</button>
            <button type="button" class="btn btn--sm" :disabled="busyId === tool.id" @click="toggleTool(tool)">
              {{ tool.enabled === false ? '启用' : '停用' }}
            </button>
            <button type="button" class="btn btn--sm btn--danger" :disabled="busyId === tool.id" @click="removeTool(tool)">删除</button>
          </div>
        </li>
      </ul>
    </template>

    <!-- 新增 / 编辑表单 -->
    <form v-if="editing" class="model-form" @submit.prevent="saveTool">
      <hr class="divider" />
      <h3 class="group__title">{{ form.id != null ? '编辑插件' : '新增插件' }}</h3>

      <div class="form-grid">
        <label class="field-row">
          <span class="field-row__label">插件标识</span>
          <input v-model="form.toolId" class="field" type="text" spellcheck="false" placeholder="如 poetry-check" :disabled="form.id != null" />
        </label>

        <label class="field-row">
          <span class="field-row__label">名称</span>
          <input v-model="form.name" class="field" type="text" maxlength="100" placeholder="如 格律检查" />
        </label>

        <span class="field-row__hint field-row--full muted">插件标识账号内唯一，保存后不可改；系统内完整 id 为 user:插件标识。</span>

        <label class="field-row field-row--full">
          <span class="field-row__label">说明（可选）</span>
          <input v-model="form.description" class="field" type="text" maxlength="500" placeholder="一句话说明这个插件做什么" />
        </label>

        <label class="field-row field-row--full">
          <span class="field-row__label">系统提示词</span>
          <textarea v-model="form.systemPrompt" class="field prompt-area" spellcheck="false" placeholder="定义这个插件的角色与行为，例如：你是古典诗词格律专家，只检查平仄与押韵，不评价意境……"></textarea>
        </label>

        <label class="field-row field-row--full">
          <span class="field-row__label">用户模板</span>
          <textarea v-model="form.userTemplate" class="field prompt-area" spellcheck="false" placeholder="{{note}}{{selection}}"></textarea>
          <span v-pre class="field-row__hint muted">可用变量：{{note}}（当前笔记）{{selection}}（选区）{{siblings}}（同目录摘要）{{title}}（标题）。</span>
        </label>

        <label class="toggle-line">
          <input v-model="form.insertable" type="checkbox" />
          <span>结果可一键插入正文</span>
        </label>
      </div>

      <div class="sec__foot">
        <button type="button" class="btn btn--ghost" @click="closeForm">取消</button>
        <button type="submit" class="btn btn--primary" :disabled="saving">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.sec__title {
  font-size: 17px;
}

.sec__desc {
  margin-top: 2px;
  font-size: 13px;
}

.fb {
  margin-top: var(--sp-4);
  padding: 9px 12px;
  border-radius: var(--r-md);
  border: 1px solid transparent;
  font-size: 13px;
  line-height: 1.6;
}

.fb--ok {
  background: color-mix(in srgb, var(--ok) 12%, var(--paper-surface));
  border-color: color-mix(in srgb, var(--ok) 38%, transparent);
  color: color-mix(in srgb, var(--ok) 70%, var(--ink));
}

.fb--err {
  background: color-mix(in srgb, var(--danger) 10%, var(--paper-surface));
  border-color: color-mix(in srgb, var(--danger) 36%, transparent);
  color: color-mix(in srgb, var(--danger) 72%, var(--ink));
}

.fb--info {
  background: var(--brand-soft);
  border-color: var(--brand-line);
  color: var(--brand-text);
}

.tiny {
  font-size: 12px;
  line-height: 1.6;
}

.privacy {
  display: flex;
  gap: var(--sp-3);
  align-items: flex-start;
  margin-top: var(--sp-4);
  padding: 12px var(--sp-4);
  border-radius: var(--r-md);
  border: 1px solid var(--brand-line);
  background: var(--brand-soft);
  color: var(--brand-text);
}

.privacy svg {
  flex: none;
  margin-top: 4px;
}

.privacy__main {
  font-size: 13px;
  font-weight: 600;
}

.head-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-top: var(--sp-5);
}

.head-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.group__title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: var(--sp-3);
}

.import-box {
  margin-top: var(--sp-2);
  display: grid;
  gap: var(--sp-2);
  padding: var(--sp-3);
  border: 1px dashed var(--brand-line);
  border-radius: var(--r-md);
  background: var(--paper-muted);
}

.import-area {
  min-height: 120px;
  font-family: var(--font-mono);
  font-size: 12px;
}

.import-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
}

.import-box__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
}

.import-box__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-2);
}

.import-guide {
  margin-top: var(--sp-2);
  padding: var(--sp-3);
  border-radius: var(--r-md);
  border: 1px solid var(--paper-line);
  background: var(--paper-surface);
}

.import-guide__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-2);
  margin-bottom: var(--sp-2);
}

.import-guide__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 6px;
}

.import-guide__list li {
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink-2);
}

.import-guide__list code {
  font-family: var(--font-mono);
  font-size: 0.92em;
  padding: 1px 5px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--brand-base) 10%, transparent);
  color: var(--brand-text);
}

.import-guide__tip {
  margin-top: var(--sp-2);
  font-size: 12px;
  line-height: 1.6;
}

.empty {
  border: 1px dashed var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-muted);
  padding: var(--sp-4);
  font-size: 13px;
  line-height: 1.7;
}

.cfg-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--sp-2);
}

.cfg {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  flex-wrap: wrap;
  padding: 12px var(--sp-4);
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-surface);
}

.cfg__main {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.cfg__name {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 14px;
}

.cfg__meta {
  font-size: 12px;
  overflow-wrap: anywhere;
  line-height: 1.6;
}

.cfg__ops {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  flex: none;
}

.chip--dim {
  background: var(--paper-muted);
  color: var(--ink-3);
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

.model-form {
  margin-top: var(--sp-2);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-4);
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}

.field-row {
  display: grid;
  gap: 6px;
}

.field-row--full {
  grid-column: 1 / -1;
}

.field-row__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-2);
}

.field-row__hint {
  font-size: 12px;
  line-height: 1.6;
}

.prompt-area {
  min-height: 120px;
  resize: vertical;
  font-family: var(--font-mono);
  font-size: 12.5px;
  line-height: 1.7;
}

.check-row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-wrap: wrap;
}

.check-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: var(--ink-2);
  cursor: pointer;
}

.check-item input {
  accent-color: var(--brand-base);
}

.toggle-line {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--ink-2);
  cursor: pointer;
  align-self: end;
  padding-bottom: 6px;
  grid-column: 1 / -1;
}

.toggle-line input {
  accent-color: var(--brand-base);
}

.sec__foot {
  margin-top: var(--sp-5);
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
}
</style>
