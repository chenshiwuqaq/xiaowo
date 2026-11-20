<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { aiToolApi, marketApi } from '@/api'
import { ElMessage } from 'element-plus'
import type { AiToolDetail, MarketPluginDetail } from '@/api/types'

const props = defineProps<{ open: boolean; editing: MarketPluginDetail | null }>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'saved'): void
}>()

const CATEGORIES = [
  { value: 0, label: '通用' },
  { value: 1, label: '科研' },
  { value: 2, label: '生活' },
  { value: 3, label: '小说' },
  { value: 4, label: '随笔' },
]

const mode = ref<'pick' | 'manual'>('pick')
const myTools = ref<AiToolDetail[]>([])
const loadingTools = ref(false)
const selectedToolId = ref<number | null>(null)
const saving = ref(false)

const form = reactive({
  toolId: '',
  name: '',
  description: '',
  systemPrompt: '',
  userTemplate: '{{note}}{{selection}}',
  insertable: true,
  category: 0,
  tags: '',
})

watch(
  () => props.open,
  (open) => {
    if (!open) return
    Object.assign(form, {
      toolId: '',
      name: '',
      description: '',
      systemPrompt: '',
      userTemplate: '{{note}}{{selection}}',
      insertable: true,
      category: 0,
      tags: '',
    })
    selectedToolId.value = null
    if (props.editing) {
      mode.value = 'manual'
      Object.assign(form, {
        toolId: props.editing.toolId,
        name: props.editing.name,
        description: props.editing.description ?? '',
        systemPrompt: props.editing.systemPrompt,
        userTemplate: props.editing.userTemplate ?? '{{note}}{{selection}}',
        insertable: props.editing.insertable !== false,
        category: props.editing.category,
        tags: (props.editing.tags ?? []).join(','),
      })
    } else {
      mode.value = 'pick'
      void loadTools()
    }
  },
)

async function loadTools() {
  loadingTools.value = true
  try {
    myTools.value = await aiToolApi.mine()
    if (myTools.value.length === 1) selectedToolId.value = myTools.value[0]?.id ?? null
  } catch {
    myTools.value = []
  } finally {
    loadingTools.value = false
  }
}

function validateManual(): string | null {
  if (!form.toolId.trim()) return '请填写插件标识（如 poetry-check）'
  if (!/^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$/.test(form.toolId.trim())) {
    return '插件标识只能由字母、数字、下划线、中划线组成（1~64 位，首字符为字母或数字）'
  }
  if (!form.name.trim()) return '请填写插件名称'
  if (!form.systemPrompt.trim()) return '请填写系统提示词'
  return null
}

async function submit() {
  if (props.editing == null && mode.value === 'pick' && selectedToolId.value == null) {
    ElMessage.warning('先选一个要发布的插件，或切到「手动填写」')
    return
  }
  if (mode.value === 'manual') {
    const problem = validateManual()
    if (problem) {
      ElMessage.warning(problem)
      return
    }
  }
  saving.value = true
  try {
    if (props.editing) {
      await marketApi.update(props.editing.id, {
        toolId: form.toolId.trim(),
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        systemPrompt: form.systemPrompt,
        userTemplate: form.userTemplate || undefined,
        insertable: form.insertable,
        category: form.category,
        tags: form.tags.trim() || undefined,
      })
      ElMessage.success('发布已更新')
    } else if (mode.value === 'pick') {
      await marketApi.publish({
        sourceToolId: selectedToolId.value ?? undefined,
        category: form.category,
        tags: form.tags.trim() || undefined,
      })
      ElMessage.success('已发布到市场')
    } else {
      await marketApi.publish({
        toolId: form.toolId.trim(),
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        systemPrompt: form.systemPrompt,
        userTemplate: form.userTemplate || undefined,
        insertable: form.insertable,
        category: form.category,
        tags: form.tags.trim() || undefined,
      })
      ElMessage.success('已发布到市场')
    }
    emit('saved')
    emit('close')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发布失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div v-if="open" class="overlay" @click.self="emit('close')">
    <div class="dialog card anim-pop" role="dialog" aria-modal="true">
      <button class="dialog__close" type="button" aria-label="关闭" @click="emit('close')">×</button>
      <h2 class="dialog__title">{{ editing ? '编辑发布' : '发布插件到市场' }}</h2>
      <p class="muted dialog__sub">把插件的 manifest 快照公开到市场，别人可以点爱心、评论、下载使用。发布后改自己账号里的原插件不会影响市场这份。</p>

      <!-- 来源选择（仅新建时） -->
      <div v-if="!editing" class="seg">
        <button type="button" class="seg__item" :class="{ 'seg__item--on': mode === 'pick' }" @click="mode = 'pick'">
          从我的插件选
        </button>
        <button type="button" class="seg__item" :class="{ 'seg__item--on': mode === 'manual' }" @click="mode = 'manual'">
          手动填写
        </button>
      </div>

      <!-- 从我的插件选 -->
      <div v-if="!editing && mode === 'pick'" class="pick">
        <p v-if="loadingTools" class="muted tiny">加载我的插件…</p>
        <template v-else-if="myTools.length">
          <button
            v-for="tool in myTools"
            :key="tool.id"
            type="button"
            class="pick__item"
            :class="{ 'pick__item--on': selectedToolId === tool.id }"
            @click="selectedToolId = tool.id ?? null"
          >
            <strong>{{ tool.name }}</strong>
            <span class="chip">{{ tool.toolId }}</span>
            <span v-if="tool.description" class="muted tiny pick__desc">{{ tool.description }}</span>
          </button>
        </template>
        <p v-else class="muted tiny">你还没有自己的插件，去「设置 → AI 插件」里先新建一个，或切到「手动填写」。</p>
      </div>

      <!-- 手动填写 manifest -->
      <div v-if="mode === 'manual'" class="form">
        <label class="field-row">
          <span class="field-row__label">插件标识</span>
          <input v-model="form.toolId" class="field" type="text" spellcheck="false" placeholder="如 poetry-check" />
        </label>
        <label class="field-row">
          <span class="field-row__label">名称</span>
          <input v-model="form.name" class="field" type="text" maxlength="100" placeholder="如 格律检查" />
        </label>
        <label class="field-row">
          <span class="field-row__label">说明（可选）</span>
          <input v-model="form.description" class="field" type="text" maxlength="500" placeholder="一句话说明这个插件做什么" />
        </label>
        <label class="field-row">
          <span class="field-row__label">系统提示词</span>
          <textarea v-model="form.systemPrompt" class="field code" spellcheck="false" placeholder="定义这个插件的角色与行为……"></textarea>
        </label>
        <label class="field-row">
          <span class="field-row__label">用户模板</span>
          <textarea v-model="form.userTemplate" class="field code" spellcheck="false" placeholder="{{note}}{{selection}}"></textarea>
          <span v-pre class="field-row__hint muted">可用变量：{{note}} {{selection}} {{siblings}} {{title}}。</span>
        </label>
        <label class="toggle-line">
          <input v-model="form.insertable" type="checkbox" />
          <span>结果可一键插入正文</span>
        </label>
      </div>

      <!-- 分类与标签（两种模式都需要） -->
      <div class="form form--meta">
        <label class="field-row">
          <span class="field-row__label">分类</span>
          <select v-model.number="form.category" class="field">
            <option v-for="c in CATEGORIES" :key="c.value" :value="c.value">{{ c.label }}</option>
          </select>
          <span class="field-row__hint muted">按笔记类型归类，方便别人在书写习惯里按类型开启。</span>
        </label>
        <label class="field-row">
          <span class="field-row__label">标签（可选，逗号分隔）</span>
          <input v-model="form.tags" class="field" type="text" maxlength="200" placeholder="如 诗歌, 写作, 校对" />
        </label>
      </div>

      <div class="dialog__foot">
        <button type="button" class="btn btn--ghost" @click="emit('close')">取消</button>
        <button type="button" class="btn btn--primary" :disabled="saving" @click="submit">
          {{ saving ? '发布中…' : editing ? '保存修改' : '发布' }}
        </button>
      </div>
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
  width: min(560px, 100%);
  max-height: 88vh;
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
  line-height: 1.7;
}

.seg {
  display: flex;
  gap: var(--sp-1);
  margin-top: var(--sp-4);
  padding: 3px;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-muted);
}

.seg__item {
  flex: 1;
  height: 32px;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-2);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}

.seg__item--on {
  background: var(--paper-surface);
  color: var(--ink);
  font-weight: 600;
  box-shadow: var(--sh-1);
}

.pick {
  margin-top: var(--sp-3);
  display: grid;
  gap: var(--sp-2);
}

.pick__item {
  display: grid;
  gap: 2px;
  text-align: left;
  padding: var(--sp-2) var(--sp-3);
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-surface);
  color: var(--ink);
  font: inherit;
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease);
}

.pick__item:hover {
  border-color: var(--brand-line);
}

.pick__item--on {
  border-color: var(--brand-base);
  background: var(--brand-soft);
}

.pick__item strong {
  font-size: 13px;
}

.pick__desc {
  font-size: 12px;
}

.form {
  margin-top: var(--sp-4);
  display: grid;
  gap: var(--sp-3);
}

.form--meta {
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--paper-line);
}

.field-row {
  display: grid;
  gap: 6px;
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

.code {
  min-height: 90px;
  font-family: var(--font-mono);
  font-size: 12.5px;
  line-height: 1.7;
}

.toggle-line {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--ink-2);
  cursor: pointer;
}

.toggle-line input {
  accent-color: var(--brand-base);
}

.tiny {
  font-size: 12px;
}

.dialog__foot {
  margin-top: var(--sp-4);
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
}
</style>
