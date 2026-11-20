<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
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

const form = reactive({ oldPwd: '', newPwd: '', confirmPwd: '' })
const showPwd = ref(false)
const submitting = ref(false)

/** 简单强度提示：够长 + 至少两类字符 */
const strength = ref<'weak' | 'ok' | 'strong' | 'none'>('none')

function onNewInput() {
  const v = form.newPwd
  if (!v) {
    strength.value = 'none'
    return
  }
  const kinds =
    Number(/[a-zA-Z]/.test(v)) + Number(/\d/.test(v)) + Number(/[^a-zA-Z0-9]/.test(v))
  if (v.length >= 10 && kinds >= 2) strength.value = 'strong'
  else if (v.length >= 6 && kinds >= 2) strength.value = 'ok'
  else strength.value = 'weak'
}

async function submit() {
  const oldP = form.oldPwd
  const newP = form.newPwd
  if (!oldP) {
    say('err', '请先输入旧密码')
    return
  }
  if (newP.length < 6 || newP.length > 32) {
    say('err', '新密码长度需在 6~32 个字符之间')
    return
  }
  if (newP !== form.confirmPwd) {
    say('err', '两次输入的新密码不一致')
    return
  }
  if (newP === oldP) {
    say('err', '新密码和旧密码相同，换一个吧')
    return
  }
  submitting.value = true
  try {
    await userStore.changePassword(oldP, newP)
    form.oldPwd = ''
    form.newPwd = ''
    form.confirmPwd = ''
    strength.value = 'none'
    say('ok', '密码已修改，之后请用新密码登录', 6500)
  } catch (error) {
    const code = getCode(error)
    if (code === 1004) {
      say('err', '旧密码不正确，请检查后重新输入', 6500)
    } else if (code === 1005) {
      say('err', '新密码超出允许范围（6~32 个字符）')
    } else {
      const raw = error instanceof Error && error.message ? error.message : ''
      say('err', raw || '修改失败，请稍后重试')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="card sec">
    <h2 class="sec__title">安全</h2>
    <p class="sec__desc muted">修改登录密码。改完后当前登录仍然有效，下次登录起使用新密码。</p>

    <div v-if="feedback" class="fb" :class="`fb--${feedback.kind}`" role="status">{{ feedback.text }}</div>

    <div class="form-grid">
      <label class="field-row">
        <span class="field-row__label">旧密码</span>
        <input v-model="form.oldPwd" class="field" :type="showPwd ? 'text' : 'password'" autocomplete="current-password" placeholder="当前使用的密码" />
      </label>

      <label class="field-row">
        <span class="field-row__label">新密码</span>
        <input v-model="form.newPwd" class="field" :type="showPwd ? 'text' : 'password'" autocomplete="new-password" placeholder="6~32 个字符" @input="onNewInput" />
        <span class="field-row__hint muted">
          <template v-if="strength === 'weak'">再长一点、字母数字混用会更安全</template>
          <template v-else-if="strength === 'ok'">强度尚可，混用符号会更稳妥</template>
          <template v-else-if="strength === 'strong'">这个密码挺结实</template>
          <template v-else>建议字母与数字混用，避免使用生日等易猜内容</template>
        </span>
      </label>

      <label class="field-row">
        <span class="field-row__label">确认新密码</span>
        <input v-model="form.confirmPwd" class="field" :type="showPwd ? 'text' : 'password'" autocomplete="new-password" placeholder="再输一遍新密码" />
        <span v-if="form.newPwd && form.confirmPwd && form.newPwd !== form.confirmPwd" class="field-row__hint mismatch">两次输入不一致</span>
      </label>
    </div>

    <div class="sec__foot">
      <label class="toggle-inline">
        <input v-model="showPwd" type="checkbox" />
        显示输入内容
      </label>
      <button type="button" class="btn btn--primary" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '确认修改' }}
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

.form-grid {
  display: grid;
  gap: var(--sp-4);
  margin-top: var(--sp-4);
  max-width: 420px;
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

.mismatch {
  color: color-mix(in srgb, var(--danger) 78%, var(--ink));
}

.sec__foot {
  margin-top: var(--sp-5);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
}

.toggle-inline {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ink-3);
  cursor: pointer;
}

.toggle-inline input {
  accent-color: var(--brand-base);
}
</style>
