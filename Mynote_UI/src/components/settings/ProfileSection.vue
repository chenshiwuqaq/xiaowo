<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { fileApi } from '@/api'
import { getCode } from '@/api/http'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

type FeedbackKind = 'ok' | 'err' | 'info'
const feedback = ref<{ kind: FeedbackKind; text: string } | null>(null)
let feedbackTimer: ReturnType<typeof setTimeout> | undefined

function say(kind: FeedbackKind, text: string, holdMs = 5000) {
  if (feedbackTimer) clearTimeout(feedbackTimer)
  feedback.value = { kind, text }
  feedbackTimer = setTimeout(() => {
    feedback.value = null
  }, holdMs)
}

onBeforeUnmount(() => {
  if (feedbackTimer) clearTimeout(feedbackTimer)
})

/** 业务码翻译：后端 message 已是中文，这里只对越界/校验码兜底 */
function explain(error: unknown): string {
  const code = getCode(error)
  const raw = error instanceof Error && error.message ? error.message : ''
  if (code === 1005) return raw || '填写的内容超出允许范围'
  if (code === 400) return raw || '参数校验没有通过，请检查填写内容'
  return raw || '操作没有成功，请稍后重试'
}

const form = reactive({ username: '', email: '', intro: '' })
const saving = ref(false)
const uploading = ref(false)

/** 上传成功但尚未（或未能）写入服务器的头像地址，与服务端头像分开管理 */
const avatarTemp = ref('')
const avatarPending = ref('')

const fileInput = ref<HTMLInputElement | null>(null)

const account = computed(() => userStore.profile?.account ?? '')
const displayAvatar = computed(() => avatarTemp.value || avatarPending.value || userStore.profile?.avatar || '')

function fillFromProfile() {
  const p = userStore.profile
  if (!p) return
  form.username = p.username ?? ''
  form.email = p.email ?? ''
  form.intro = p.intro ?? ''
}

watch(() => userStore.profile, fillFromProfile)

onMounted(() => {
  fillFromProfile()
  if (!userStore.profile) {
    userStore.fetchProfile().catch((error: unknown) => say('err', '资料加载失败：' + explain(error)))
  }
})

/** 校验并收集要提交的资料；不合法时给出可见提示并返回 null */
function collectValid(): { username: string; email: string; intro: string } | null {
  const username = form.username.trim()
  const email = form.email.trim()
  const intro = form.intro.trim()
  if (!username) {
    say('err', '请填写昵称')
    return null
  }
  if (username.length > 32) {
    say('err', '昵称最多 32 个字')
    return null
  }
  if (!email) {
    say('err', '请填写邮箱')
    return null
  }
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
    say('err', '邮箱格式不太对')
    return null
  }
  if (intro.length > 500) {
    say('err', '简介最多 500 字')
    return null
  }
  return { username, email, intro }
}

function pickAvatar() {
  fileInput.value?.click()
}

async function onAvatarPicked(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    say('err', '图片超过 2MB，换张小一点的吧')
    return
  }
  uploading.value = true
  try {
    const res = await fileApi.upload(file, 'avatar')
    if (!res.url) {
      say('err', '上传接口没有返回图片地址')
      return
    }
    avatarTemp.value = res.url
  } catch (error) {
    say('err', '头像上传失败：' + explain(error))
    return
  } finally {
    uploading.value = false
  }

  // 头像走 PUT /user/profile 持久化：需要随资料一并提交，先本地校验再保存
  const data = collectValid()
  if (!data) {
    avatarPending.value = avatarTemp.value
    say('info', '头像已选好，补全昵称与邮箱后点「保存资料」即可让它生效')
    return
  }
  try {
    await userStore.updateProfile({ ...data, avatar: avatarTemp.value })
    avatarTemp.value = ''
    avatarPending.value = ''
    say('ok', '头像已更新')
  } catch (error) {
    avatarPending.value = avatarTemp.value
    say('err', '头像上传成功了，但保存失败：' + explain(error) + '。可点「保存资料」重试')
  }
}

async function save() {
  const data = collectValid()
  if (!data) return
  saving.value = true
  try {
    await userStore.updateProfile({
      ...data,
      // 有等待保存的新头像就带上，否则不传（后端按"保持原头像"处理）
      avatar: avatarPending.value || undefined,
    })
    avatarTemp.value = ''
    avatarPending.value = ''
    say('ok', '资料已保存')
  } catch (error) {
    say('err', '保存失败：' + explain(error))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="card sec">
    <h2 class="sec__title">基础信息</h2>
    <p class="sec__desc muted">昵称与邮箱会展示在小窝的各个角落。</p>

    <div v-if="feedback" class="fb" :class="`fb--${feedback.kind}`" role="status">{{ feedback.text }}</div>

    <div class="avatar-row">
      <div class="avatar">
        <img v-if="displayAvatar" :src="displayAvatar" alt="当前头像" />
        <svg v-else viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" stroke-width="1.4" aria-hidden="true">
          <path d="M12 11.5a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4Z" />
          <path d="M5.5 19.5c1.2-3 3.6-4.5 6.5-4.5s5.3 1.5 6.5 4.5" />
        </svg>
      </div>
      <div class="avatar-row__side">
        <button type="button" class="btn btn--sm" :disabled="uploading" @click="pickAvatar">
          {{ uploading ? '上传中…' : '上传头像' }}
        </button>
        <input ref="fileInput" type="file" accept="image/png,image/jpeg,image/webp,image/gif" hidden @change="onAvatarPicked" />
        <p class="muted tiny">建议正方形、2MB 以内；选定后会自动保存进资料。</p>
      </div>
    </div>

    <hr class="divider" />

    <div class="form-grid">
      <label class="field-row">
        <span class="field-row__label">账号</span>
        <input class="field field--disabled" :value="account" disabled title="账号注册后不可修改" />
        <span class="field-row__hint muted">账号注册后不可修改，请牢记它。</span>
      </label>

      <label class="field-row">
        <span class="field-row__label">昵称</span>
        <input v-model="form.username" class="field" maxlength="32" placeholder="想让大家怎么称呼你" />
        <span class="field-row__hint muted">{{ form.username.trim().length }}/32</span>
      </label>

      <label class="field-row">
        <span class="field-row__label">邮箱</span>
        <input v-model="form.email" class="field" maxlength="64" placeholder="用于找回密码，不会公开展示" />
      </label>

      <label class="field-row">
        <span class="field-row__label">简介</span>
        <textarea v-model="form.intro" class="field" rows="3" maxlength="500" placeholder="一两句话，说说你在写什么"></textarea>
        <span class="field-row__hint muted">{{ form.intro.trim().length }}/500</span>
      </label>
    </div>

    <div class="sec__foot">
      <button type="button" class="btn btn--primary" :disabled="saving" @click="save">
        {{ saving ? '保存中…' : '保存资料' }}
      </button>
    </div>
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

.avatar-row {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  margin-top: var(--sp-4);
}

.avatar {
  width: 68px;
  height: 68px;
  flex: none;
  border-radius: 50%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--paper-muted);
  border: 1px solid var(--paper-line);
  color: var(--ink-4);
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-row__side {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}

.tiny {
  font-size: 12px;
  line-height: 1.6;
}

.form-grid {
  display: grid;
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
}

.field--disabled {
  background: var(--paper-muted);
  color: var(--ink-3);
  cursor: not-allowed;
}

.sec__foot {
  margin-top: var(--sp-5);
  display: flex;
  justify-content: flex-end;
}
</style>
