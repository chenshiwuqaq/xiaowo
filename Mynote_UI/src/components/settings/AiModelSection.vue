<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { aiConfigApi, authApi } from '@/api'
import { getCode } from '@/api/http'
import type { AiConfig, ProviderPreset } from '@/api/types'

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

/** 2001 = 后端没有可用的模型配置；其余优先展示后端中文 message */
function explain(error: unknown): string {
  const code = getCode(error)
  const raw = error instanceof Error && error.message ? error.message : ''
  if (code === 2001) return raw || '还没有可用的模型配置，请先保存一份填好密钥的配置'
  if (code === 1005) return raw || '填写的值超出了允许范围'
  if (code === 400) return raw || '参数校验没有通过，请检查表单'
  return raw || '操作没有成功，请稍后重试'
}

const providers = ref<ProviderPreset[]>([])
const configs = ref<AiConfig[]>([])
const loadingList = ref(false)

interface ModelForm {
  id?: number
  providerCode: string
  providerName: string
  baseUrl: string
  modelName: string
  apiKey: string
  temperature: number
  maxTokens: number | string
  enabled: boolean
}

const form = reactive<ModelForm>({
  providerCode: '',
  providerName: '',
  baseUrl: '',
  modelName: '',
  apiKey: '',
  temperature: 0.7,
  maxTokens: 2048,
  enabled: true,
})

const editing = ref(false)
const editingMask = ref('')
const saving = ref(false)
const testing = ref(false)
const testingRowId = ref<number | null>(null)
const busyRowId = ref<number | null>(null)

const selectedProvider = computed(() => providers.value.find((p) => p.code === form.providerCode) ?? null)

/** 下拉的"值"要顺带带出服务商名、默认地址与模型候选，所以做成带副作用的 computed */
const providerCodeModel = computed({
  get: () => form.providerCode,
  set: (code: string) => applyProvider(code),
})

function applyProvider(code: string) {
  const preset = providers.value.find((p) => p.code === code) ?? null
  form.providerCode = code
  if (!preset) return
  form.providerName = preset.name
  form.baseUrl = preset.baseUrl
  if (!form.modelName) form.modelName = preset.models[0] ?? ''
}

function openNew() {
  const first = providers.value[0] ?? null
  editingMask.value = ''
  form.id = undefined
  form.providerCode = first?.code ?? ''
  form.providerName = first?.name ?? ''
  form.baseUrl = first?.baseUrl ?? ''
  form.modelName = first?.models?.[0] ?? ''
  form.apiKey = ''
  form.temperature = 0.7
  form.maxTokens = 2048
  form.enabled = true
  testResult.value = null
  editing.value = true
}

function openEdit(cfg: AiConfig) {
  form.id = cfg.id
  form.providerCode = cfg.providerCode
  form.providerName = cfg.providerName ?? selectedProviderOf(cfg.providerCode)?.name ?? ''
  form.baseUrl = cfg.baseUrl
  form.modelName = cfg.modelName
  form.apiKey = ''
  form.temperature = cfg.temperature ?? 0.7
  form.maxTokens = cfg.maxTokens ?? 2048
  form.enabled = cfg.enabled ?? true
  editingMask.value = cfg.apiKeyMask ?? (cfg.hasKey ? '已保存' : '')
  testResult.value = null
  editing.value = true
}

function selectedProviderOf(code: string): ProviderPreset | null {
  return providers.value.find((p) => p.code === code) ?? null
}

function closeForm() {
  editing.value = false
  testResult.value = null
}

async function loadProviders() {
  try {
    providers.value = await authApi.providers()
  } catch (error) {
    say('err', '服务商列表加载失败：' + explain(error))
  }
}

async function loadConfigs() {
  loadingList.value = true
  try {
    configs.value = await aiConfigApi.list()
  } catch (error) {
    configs.value = []
    say('err', explain(error))
  } finally {
    loadingList.value = false
  }
}

onMounted(() => {
  void loadProviders()
  void loadConfigs()
})

const apiKeyPlaceholder = computed(() => {
  if (form.id == null) return '填入服务商给的密钥，如 sk-xxxx'
  return editingMask.value
    ? `留空表示不修改（当前：${editingMask.value}）`
    : '当前未填密钥；留空表示不修改'
})

function validate(): string | null {
  if (!form.providerCode) return '请选择模型服务商'
  const baseUrl = form.baseUrl.trim()
  if (!baseUrl) return '请填写 API 地址（Base URL）'
  if (!/^https?:\/\//i.test(baseUrl)) return 'API 地址需要以 http:// 或 https:// 开头'
  if (!form.modelName.trim()) return '请填写或选择一个模型名'
  if (!Number.isFinite(form.temperature) || form.temperature < 0 || form.temperature > 2) {
    return '随机温度（temperature）要在 0 ~ 2 之间'
  }
  const maxTokens = Number(form.maxTokens)
  if (!Number.isFinite(maxTokens) || maxTokens <= 0 || maxTokens > 32768) {
    return '最大输出 token 要在 1 ~ 32768 之间'
  }
  return null
}

async function saveConfig() {
  const problem = validate()
  if (problem) {
    say('err', problem)
    return
  }
  const maxTokens = Math.round(Number(form.maxTokens))
  const payload: AiConfig = {
    id: form.id,
    providerCode: form.providerCode,
    providerName: form.providerName || selectedProvider.value?.name,
    baseUrl: form.baseUrl.trim(),
    modelName: form.modelName.trim(),
    // 后端契约：空串或含 **** 视为保持原密钥
    apiKey: form.apiKey.trim(),
    temperature: Number(form.temperature),
    maxTokens,
    enabled: form.enabled,
  }
  saving.value = true
  try {
    await aiConfigApi.save(payload)
    say('ok', form.id != null ? '模型配置已更新' : '模型配置已新增')
    closeForm()
    await loadConfigs()
  } catch (error) {
    say('err', '保存失败：' + explain(error))
  } finally {
    saving.value = false
  }
}

interface TestOutcome {
  ok: boolean
  message: string
  sample?: string
  model?: string
  label?: string
}

const testResult = ref<TestOutcome | null>(null)

async function testForm() {
  const baseUrl = form.baseUrl.trim()
  const modelName = form.modelName.trim()
  if (!baseUrl || !modelName) {
    say('err', '请先填好 API 地址与模型名，再测连通')
    return
  }
  testing.value = true
  testResult.value = null
  const payload: { id?: number; baseUrl?: string; modelName?: string; apiKey?: string } = { baseUrl, modelName }
  if (form.id != null) payload.id = form.id
  const key = form.apiKey.trim()
  if (key) payload.apiKey = key
  try {
    const result = await aiConfigApi.test(payload)
    testResult.value = { ...result, label: modelName }
    say(result.ok ? 'ok' : 'err', result.ok ? '连通测试成功' : `连通测试没通过：${result.message || '见下方详情'}`)
  } catch (error) {
    testResult.value = { ok: false, message: explain(error), label: modelName }
    say('err', explain(error))
  } finally {
    testing.value = false
  }
}

async function testRow(cfg: AiConfig) {
  if (cfg.id == null) {
    say('err', '这条配置缺少 ID，无法单独测试')
    return
  }
  testingRowId.value = cfg.id
  testResult.value = null
  const label = `${cfg.providerName || cfg.providerCode} · ${cfg.modelName}`
  try {
    const result = await aiConfigApi.test({ id: cfg.id })
    testResult.value = { ...result, label }
    say(result.ok ? 'ok' : 'err', result.ok ? `「${cfg.modelName}」连通成功` : `「${cfg.modelName}」连通失败：${result.message || ''}`)
  } catch (error) {
    testResult.value = { ok: false, message: explain(error), label }
    say('err', explain(error))
  } finally {
    testingRowId.value = null
  }
}

async function setDefault(cfg: AiConfig) {
  if (cfg.id == null) {
    say('err', '这条配置缺少 ID，无法设为默认')
    return
  }
  busyRowId.value = cfg.id
  try {
    await aiConfigApi.setDefault(cfg.id)
    say('ok', `已把「${cfg.providerName || cfg.providerCode} · ${cfg.modelName}」设为默认模型`)
    await loadConfigs()
  } catch (error) {
    say('err', '设为默认失败：' + explain(error))
  } finally {
    busyRowId.value = null
  }
}

async function removeRow(cfg: AiConfig) {
  if (cfg.id == null) {
    say('err', '这条配置缺少 ID，无法删除')
    return
  }
  const label = `${cfg.providerName || cfg.providerCode} · ${cfg.modelName}`
  if (!window.confirm(`确定删除「${label}」？删除后 AI 功能会退回本地规则。`)) return
  busyRowId.value = cfg.id
  try {
    await aiConfigApi.remove(cfg.id)
    if (editing.value && form.id === cfg.id) closeForm()
    say('ok', `已删除「${label}」`)
    await loadConfigs()
  } catch (error) {
    say('err', '删除失败：' + explain(error))
  } finally {
    busyRowId.value = null
  }
}

function keyText(cfg: AiConfig): string {
  return cfg.apiKeyMask || (cfg.hasKey ? '已配置密钥' : '未填密钥')
}
</script>

<template>
  <section class="card sec">
    <h2 class="sec__title">AI 模型</h2>
    <p class="sec__desc muted">配置一个 OpenAI 兼容的服务商，润色、提纲、问答都会用它。</p>

    <div v-if="feedback" class="fb" :class="`fb--${feedback.kind}`" role="status">{{ feedback.text }}</div>

    <div class="privacy">
      <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="8.2" />
        <path d="M12 8.2v.1M12 11.4v4.4" />
      </svg>
      <div>
        <p class="privacy__main">笔记内容与上下文会发送给你所选的模型服务商。</p>
        <p class="muted tiny">密钥保存在你自己的后端，仅用于转发请求；发送出去的文本是否被服务商留存，取决于对方条款。</p>
      </div>
    </div>

    <!-- 已配置列表 -->
    <div class="head-line">
      <h3 class="group__title">已配置模型</h3>
      <button type="button" class="btn btn--sm btn--primary" @click="openNew">
        <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
          <path d="M12 5.5v13M5.5 12h13" />
        </svg>
        新增配置
      </button>
    </div>

    <p v-if="loadingList" class="muted tiny">加载配置列表…</p>
    <template v-else>
      <div v-if="!configs.length" class="empty muted">
        还没有配置任何模型。未配置时，AI 相关功能会退回本地规则（润色词表、提纲模板等），也能用，只是没那么聪明。
      </div>
      <ul v-else class="cfg-list">
        <li v-for="(cfg, idx) in configs" :key="cfg.id ?? `${cfg.providerCode}-${cfg.modelName}-${idx}`" class="cfg">
          <div class="cfg__main">
            <div class="cfg__name">
              <strong>{{ cfg.providerName || cfg.providerCode }}</strong>
              <span class="cfg__model chip">{{ cfg.modelName }}</span>
              <span v-if="cfg.defaultConfig" class="chip chip--brand">默认</span>
              <span v-if="cfg.enabled === false" class="chip chip--off">已停用</span>
            </div>
            <div class="cfg__meta muted">{{ cfg.baseUrl }}</div>
            <div class="cfg__meta muted">密钥 {{ keyText(cfg) }}</div>
          </div>
          <div class="cfg__ops">
            <button type="button" class="btn btn--sm" :disabled="testingRowId === cfg.id" @click="testRow(cfg)">
              {{ testingRowId === cfg.id ? '测试中…' : '测试连通' }}
            </button>
            <button
              type="button"
              class="btn btn--sm"
              :disabled="busyRowId === cfg.id || cfg.defaultConfig || cfg.id == null"
              @click="setDefault(cfg)"
            >
              设为默认
            </button>
            <button type="button" class="btn btn--sm" @click="openEdit(cfg)">编辑</button>
            <button type="button" class="btn btn--sm btn--danger" :disabled="busyRowId === cfg.id" @click="removeRow(cfg)">删除</button>
          </div>
        </li>
      </ul>
    </template>

    <!-- 测试结果 -->
    <div v-if="testResult" class="test-result" :class="testResult.ok ? 'test-result--ok' : 'test-result--err'" role="status">
      <div class="test-result__head">
        <strong>{{ testResult.ok ? '连通成功' : '连通失败' }}</strong>
        <span v-if="testResult.label" class="muted tiny">{{ testResult.label }}</span>
      </div>
      <p class="test-result__msg">{{ testResult.message }}</p>
      <p v-if="testResult.sample" class="test-result__sample muted">模型示例回答：{{ testResult.sample }}</p>
    </div>

    <!-- 新增 / 编辑表单 -->
    <form v-if="editing" class="model-form" @submit.prevent="saveConfig">
      <hr class="divider" />
      <h3 class="group__title">{{ form.id != null ? '编辑配置' : '新增配置' }}</h3>

      <div class="form-grid">
        <label class="field-row">
          <span class="field-row__label">服务商</span>
          <select v-model="providerCodeModel" class="field">
            <option v-if="!providers.length" value="" disabled>服务商列表加载失败，可先手动填写</option>
            <option v-for="p in providers" :key="p.code" :value="p.code">{{ p.name }}</option>
          </select>
        </label>

        <label class="field-row">
          <span class="field-row__label">API 地址（Base URL）</span>
          <input
            v-model="form.baseUrl"
            class="field"
            type="text"
            spellcheck="false"
            :placeholder="selectedProvider?.baseUrl || 'https://api.example.com/v1'"
          />
          <span class="field-row__hint muted">切换服务商会自动填入它的默认地址，代理或自建网关可以手动改。</span>
        </label>

        <label class="field-row">
          <span class="field-row__label">模型名</span>
          <input v-model="form.modelName" class="field" list="ai-model-options" spellcheck="false" placeholder="从候选里选，或直接填自定义模型名" />
          <datalist id="ai-model-options">
            <option v-for="m in selectedProvider?.models ?? []" :key="m" :value="m"></option>
          </datalist>
        </label>

        <label class="field-row">
          <span class="field-row__label">API Key</span>
          <input v-model="form.apiKey" class="field" type="password" autocomplete="new-password" spellcheck="false" :placeholder="apiKeyPlaceholder" />
          <span class="field-row__hint muted">编辑时留空（或保留含 **** 的掩码）表示保持原密钥不变；要换就完整重填。</span>
        </label>

        <div class="field-row">
          <span class="field-row__label">随机温度（temperature）</span>
          <div class="range-line">
            <input v-model.number="form.temperature" type="range" min="0" max="2" step="0.1" aria-label="随机温度" />
            <span class="range-line__value metric">{{ Number(form.temperature).toFixed(1) }}</span>
          </div>
          <span class="field-row__hint muted">越低越稳（润色、摘要 0.3~0.7），越高越发散（头脑风暴 1.0+）。0 ~ 2。</span>
        </div>

        <label class="field-row">
          <span class="field-row__label">最大输出 token</span>
          <input v-model.number="form.maxTokens" class="field" type="number" min="1" max="32768" step="256" />
        </label>

        <label class="toggle-line">
          <input v-model="form.enabled" type="checkbox" />
          <span>启用这条配置</span>
        </label>
      </div>

      <div class="sec__foot">
        <button type="button" class="btn btn--ghost" @click="closeForm">取消</button>
        <button type="button" class="btn" :disabled="testing" @click="testForm">
          {{ testing ? '测试中…' : '测试连通' }}
        </button>
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

.group__title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: var(--sp-3);
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

.btn--danger {
  color: color-mix(in srgb, var(--danger) 80%, var(--ink));
}

.btn--danger:hover {
  border-color: color-mix(in srgb, var(--danger) 50%, transparent);
  background: color-mix(in srgb, var(--danger) 10%, var(--paper-surface));
  color: color-mix(in srgb, var(--danger) 78%, var(--ink));
}

.chip--off {
  color: var(--ink-4);
}

.test-result {
  margin-top: var(--sp-4);
  padding: 12px var(--sp-4);
  border-radius: var(--r-md);
  border: 1px solid transparent;
  font-size: 13px;
  line-height: 1.7;
}

.test-result__head {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
}

.test-result__msg {
  overflow-wrap: anywhere;
}

.test-result__sample {
  margin-top: 6px;
  padding: 8px 10px;
  border-radius: var(--r-sm);
  background: var(--paper-muted);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 140px;
  overflow: auto;
  font-size: 12.5px;
}

.test-result--ok {
  border-color: color-mix(in srgb, var(--ok) 38%, transparent);
  background: color-mix(in srgb, var(--ok) 9%, var(--paper-surface));
}

.test-result--err {
  border-color: color-mix(in srgb, var(--danger) 36%, transparent);
  background: color-mix(in srgb, var(--danger) 8%, var(--paper-surface));
}

.model-form {
  margin-top: var(--sp-2);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--sp-4);
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

.range-line {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

input[type='range'] {
  flex: 1;
  accent-color: var(--brand-base);
}

.range-line__value {
  min-width: 42px;
  text-align: right;
  font-size: 13px;
  color: var(--ink-2);
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
