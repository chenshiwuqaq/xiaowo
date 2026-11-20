<script setup lang="ts">
/**
 * 登录 / 注册 / 找回密码三合一页。
 *
 * 气质定位：这是"回家开门"，不是 SaaS 登录框——左侧氛围区（窗、灯、星点全用 CSS 画，
 * 零图片依赖），右侧一张手写的表单卡片。颜色一律来自 tokens.css 的变量，
 * 所以 themeStore 一换 brand/mode，整页自动跟上。
 */
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi } from '@/api'
import { getCode } from '@/api/http'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'

type Mode = 'login' | 'register' | 'reset'
type AccountState = 'idle' | 'checking' | 'available' | 'taken'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const themeStore = useThemeStore()

/**
 * 1001/1002/1003 是 auth 域的业务码，没进 http.ts 的 CODE 常量——
 * 因为只有本页消费它们，下沉到请求层反而误导"全局都会遇到"。
 */
const AUTH_CODE = {
  WRONG_CREDENTIALS: 1001,
  ACCOUNT_EXISTS: 1002,
  ACCOUNT_EMAIL_MISMATCH: 1003,
} as const

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const mode = ref<Mode>('login')
const form = reactive({ account: '', username: '', password: '', confirm: '', email: '' })
type FormKey = keyof typeof form
const fieldErrors = reactive<Record<FormKey, string>>({
  account: '',
  username: '',
  password: '',
  confirm: '',
  email: '',
})

const accountState = ref<AccountState>('idle')
const accountNote = ref('')
// 并发校验时慢响应可能覆盖新输入的结果，用序号只认最后一次
const checkSeq = ref(0)

const banner = ref('')
const goodNews = ref('')
// resetPassword 不在 userStore 里（store 的 loading 只覆盖 login/register），单独记一个
const resetting = ref(false)
const reveal = ref(false)

const busy = computed(() => resetting.value || userStore.loading)
const isNight = computed(() => themeStore.mode === 'dark')

const COPY = {
  login: { title: '欢迎回家', go: '开门，回家', loading: '正在开门…' },
  register: { title: '安一个新窝', go: '落户小窝', loading: '正在铺窝…' },
  reset: { title: '重新配一把钥匙', go: '换上新钥匙', loading: '正在配钥匙…' },
} as const
const copy = computed(() => COPY[mode.value])

const greetLine = computed(() => {
  const h = new Date().getHours()
  if (h < 5) return '还没睡？夜里写字辛苦'
  if (h < 11) return '早上好，欢迎回家'
  if (h < 14) return '中午好，今天写了几句？'
  if (h < 18) return '下午好，进来坐坐'
  if (h < 22) return '晚上好，笔还温着'
  return '夜深了，睡前记几句？'
})

/**
 * 主题状态只有被 apply() 写进 <html> 的 CSS 变量后，页面才会真的变色。
 * 这里兜底监听：无论从哪个入口改了 brand/mode/radius，本页都跟得上。
 */
watch(
  () => [themeStore.brand, themeStore.mode, themeStore.radius],
  () => themeStore.apply(),
)

function resetErrors() {
  fieldErrors.account = ''
  fieldErrors.username = ''
  fieldErrors.password = ''
  fieldErrors.confirm = ''
  fieldErrors.email = ''
}

function switchMode(next: Mode) {
  mode.value = next
  banner.value = ''
  goodNews.value = ''
  resetErrors()
  // 账号/邮箱在三种模式间语义一致，留着少敲一遍；密码类永远清空
  form.password = ''
  form.confirm = ''
  accountState.value = 'idle'
  accountNote.value = ''
  reveal.value = false
}

function onInput(key: FormKey) {
  fieldErrors[key] = ''
  if (key === 'account' && mode.value === 'register') {
    accountState.value = 'idle'
    accountNote.value = ''
  }
}

function errMessage(error: unknown): string {
  return error instanceof Error && error.message ? error.message : '请求没成功，稍后再试'
}

/**
 * 注册页账号 blur 即查（而不是提交时才报）：
 * 不想让人填完一整张表才发现名字被占了。
 */
async function checkAccountOnBlur() {
  if (mode.value !== 'register') return
  const account = form.account.trim()
  if (account.length < 3) {
    accountState.value = 'idle'
    accountNote.value = ''
    return
  }
  const seq = ++checkSeq.value
  accountState.value = 'checking'
  accountNote.value = ''
  try {
    const usable = await authApi.checkAccount(account)
    if (seq !== checkSeq.value || mode.value !== 'register') return
    if (usable) {
      accountState.value = 'available'
      accountNote.value = '这个名儿还空着，能用'
    } else {
      accountState.value = 'taken'
      accountNote.value = '账号已被占用，换一个试试'
    }
  } catch (error) {
    if (seq !== checkSeq.value || mode.value !== 'register') return
    if (getCode(error) === AUTH_CODE.ACCOUNT_EXISTS) {
      accountState.value = 'taken'
      accountNote.value = '账号已存在，换个名字吧'
    } else {
      // 查询失败不拦着人注册，只轻声说明
      accountState.value = 'idle'
      accountNote.value = errMessage(error)
    }
  }
}

function validate(): boolean {
  resetErrors()
  let ok = true
  const fail = (key: FormKey, msg: string) => {
    fieldErrors[key] = msg
    ok = false
  }
  const account = form.account.trim()
  if (!account) fail('account', '先告诉我账号')
  if (!form.password) fail('password', '别忘了密码')

  if (mode.value === 'register') {
    if (account && account.length < 3) fail('account', '账号至少 3 个字符')
    if (!form.username.trim()) fail('username', '想让我怎么称呼你？')
    if (form.password && form.password.length < 6) fail('password', '密码至少 6 位')
    if (form.confirm !== form.password || !form.confirm) fail('confirm', '两次密码不一致')
    if (!EMAIL_RE.test(form.email.trim())) fail('email', '邮箱格式不太对')
  } else if (mode.value === 'reset') {
    if (form.password && form.password.length < 6) fail('password', '新密码至少 6 位')
    if (form.confirm !== form.password || !form.confirm) fail('confirm', '两次密码不一致')
    if (!EMAIL_RE.test(form.email.trim())) fail('email', '邮箱格式不太对')
  }
  return ok
}

function describeError(error: unknown): string {
  const code = getCode(error)
  if (code === AUTH_CODE.WRONG_CREDENTIALS) {
    return '账号或密码不正确。想先看看？点下面的演示账号'
  }
  if (code === AUTH_CODE.ACCOUNT_EXISTS) {
    fieldErrors.account = '账号已存在，换一个吧'
    return '这个窝已经有人住了，换个账号再试'
  }
  if (code === AUTH_CODE.ACCOUNT_EMAIL_MISMATCH) {
    fieldErrors.email = '邮箱与账号不匹配'
    return '账号与邮箱不匹配，确认一下注册时用的邮箱'
  }
  return errMessage(error)
}

/**
 * ?redirect 来自地址栏，不校验就 push 会被拿来跳外站（开放重定向）；
 * 只认根目录开头的站内路径。vue-router 已替我们解码过 query。
 */
function goAfterAuth() {
  const raw = route.query.redirect
  const target = Array.isArray(raw) ? raw[0] : raw
  if (typeof target === 'string' && target.startsWith('/') && !target.startsWith('//')) {
    return router.push(target)
  }
  return router.push('/home')
}

async function onSubmit() {
  if (busy.value) return
  banner.value = ''
  goodNews.value = ''
  if (!validate()) return
  const account = form.account.trim()
  try {
    if (mode.value === 'login') {
      await userStore.login(account, form.password)
      await goAfterAuth()
    } else if (mode.value === 'register') {
      await userStore.register({
        account,
        username: form.username.trim(),
        password: form.password,
        email: form.email.trim(),
      })
      await goAfterAuth()
    } else {
      resetting.value = true
      await authApi.resetPassword({ account, email: form.email.trim(), newPassword: form.password })
      switchMode('login')
      goodNews.value = '钥匙重新配好了，用新密码开门吧'
    }
  } catch (error) {
    banner.value = describeError(error)
  } finally {
    resetting.value = false
  }
}

/** 演示账号：填完直接提交。新用户先"进屋看一眼"比读一段说明更有说服力。 */
function useDemo() {
  if (mode.value !== 'login') switchMode('login')
  form.account = 'chenjie'
  form.password = '123456'
  resetErrors()
  void onSubmit()
}

function setBrand(color: string) {
  themeStore.brand = color
  themeStore.apply()
}

function toggleLamp() {
  themeStore.mode = themeStore.mode === 'dark' ? 'light' : 'dark'
  themeStore.apply()
}
</script>

<template>
  <div class="login">
    <!-- 左：氛围区。窗/灯/星点全部 CSS 绘制，随主题变量变色 -->
    <section class="ambience">
      <div class="ambience-pattern" :class="themeStore.currentPatternClass"></div>

      <div class="window">
        <span v-if="!isNight" class="sun"></span>
        <template v-else>
          <span class="moon"></span>
          <i class="wstar"></i>
          <i class="wstar"></i>
          <i class="wstar"></i>
        </template>
      </div>

      <template v-if="themeStore.mascot === 'lantern'">
        <div class="lantern"><div class="lantern-body"></div></div>
      </template>
      <template v-else-if="themeStore.mascot === 'bird'">
        <div class="bird"></div>
      </template>
      <template v-else-if="themeStore.mascot === 'nest'">
        <div class="nest"><span class="egg"></span><span class="egg"></span></div>
      </template>

      <template v-if="isNight">
        <b class="star star--1"></b>
        <b class="star star--2"></b>
        <b class="star star--3"></b>
      </template>

      <div v-if="themeStore.particle" class="motes">
        <span v-for="i in 6" :key="i" class="mote"></span>
      </div>

      <header class="brand">
        <span class="logo">窝</span>
        <div>
          <h1>小窝笔记</h1>
          <p class="tag">一个懂你笔尖动静的小站</p>
        </div>
      </header>

      <div class="greeting anim-fade-up">
        <h2>{{ greetLine }}</h2>
        <p class="muted">推门进去，你的笔记还在小窝里等着你。写下的字，都不会丢。</p>
      </div>

      <footer class="looks">
        <button type="button" class="btn btn--sm" @click="toggleLamp">
          {{ isNight ? '把灯点亮' : '把灯调暗' }}
        </button>
        <div class="dots" aria-label="主色">
          <button
            v-for="p in themeStore.PRESETS"
            :key="p.name"
            type="button"
            class="dot"
            :class="{ 'is-on': p.color === themeStore.brand }"
            :style="{ background: p.color }"
            v-tooltip="p.name"
            :aria-label="`主色：${p.name}`"
            @click="setBrand(p.color)"
          ></button>
        </div>
      </footer>
    </section>

    <!-- 右：表单卡片 -->
    <section class="panel">
      <form class="card login-card anim-fade-up" @submit.prevent="onSubmit">
        <div class="seg">
          <button
            type="button"
            class="btn btn--ghost"
            :class="{ 'is-on': mode === 'login' }"
            @click="switchMode('login')"
          >登录</button>
          <button
            type="button"
            class="btn btn--ghost"
            :class="{ 'is-on': mode === 'register' }"
            @click="switchMode('register')"
          >注册</button>
          <button
            type="button"
            class="btn btn--ghost"
            :class="{ 'is-on': mode === 'reset' }"
            @click="switchMode('reset')"
          >忘记密码</button>
        </div>

        <h3 class="headline">{{ copy.title }}</h3>

        <p v-if="banner" class="strip strip--bad" role="alert">{{ banner }}</p>
        <p v-if="goodNews" class="strip strip--good" role="status">{{ goodNews }}</p>

        <div class="frow">
          <label class="lb" for="f-account">账号</label>
          <input
            id="f-account"
            v-model="form.account"
            class="field"
            :class="{ 'field--bad': fieldErrors.account }"
            autocomplete="username"
            placeholder="你的账号"
            @input="onInput('account')"
            @blur="checkAccountOnBlur"
          />
          <p v-if="fieldErrors.account" class="note note--bad">{{ fieldErrors.account }}</p>
          <p v-else-if="accountState === 'checking'" class="note note--muted">在问这个名字被占了没…</p>
          <p
            v-else-if="accountNote"
            class="note"
            :class="{
              'note--ok': accountState === 'available',
              'note--bad': accountState === 'taken',
              'note--muted': accountState === 'idle',
            }"
          >{{ accountNote }}</p>
        </div>

        <div v-if="mode === 'register'" class="frow">
          <label class="lb" for="f-username">昵称</label>
          <input
            id="f-username"
            v-model="form.username"
            class="field"
            :class="{ 'field--bad': fieldErrors.username }"
            placeholder="以后大家怎么称呼你"
            @input="onInput('username')"
          />
          <p v-if="fieldErrors.username" class="note note--bad">{{ fieldErrors.username }}</p>
        </div>

        <div class="frow">
          <label class="lb" for="f-password">{{ mode === 'reset' ? '新密码' : '密码' }}</label>
          <div class="pw">
            <input
              id="f-password"
              v-model="form.password"
              class="field"
              :class="{ 'field--bad': fieldErrors.password }"
              :type="reveal ? 'text' : 'password'"
              :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
              :placeholder="mode === 'reset' ? '至少 6 位' : '别告诉别人'"
              @input="onInput('password')"
            />
            <button type="button" class="reveal" @click="reveal = !reveal">
              {{ reveal ? '藏好' : '看一眼' }}
            </button>
          </div>
          <p v-if="fieldErrors.password" class="note note--bad">{{ fieldErrors.password }}</p>
        </div>

        <template v-if="mode !== 'login'">
          <div class="frow">
            <label class="lb" for="f-confirm">确认密码</label>
            <input
              id="f-confirm"
              v-model="form.confirm"
              class="field"
              :class="{ 'field--bad': fieldErrors.confirm }"
              type="password"
              autocomplete="new-password"
              placeholder="再敲一遍"
              @input="onInput('confirm')"
            />
            <p v-if="fieldErrors.confirm" class="note note--bad">{{ fieldErrors.confirm }}</p>
          </div>

          <div class="frow">
            <label class="lb" for="f-email">{{ mode === 'reset' ? '注册时用的邮箱' : '邮箱' }}</label>
            <input
              id="f-email"
              v-model="form.email"
              class="field"
              :class="{ 'field--bad': fieldErrors.email }"
              type="email"
              autocomplete="email"
              placeholder="you@example.com"
              @input="onInput('email')"
            />
            <p v-if="fieldErrors.email" class="note note--bad">{{ fieldErrors.email }}</p>
<!--            <p v-else-if="mode === 'reset'" class="note note&#45;&#45;muted">后端会核对账号与邮箱是不是同一人</p>-->
          </div>
        </template>

        <button class="btn btn--primary go" type="submit" :disabled="busy">
          {{ busy ? copy.loading : copy.go }}
        </button>

        <div class="foot">
          <template v-if="mode === 'login'">
            <button type="button" class="lnk" @click="switchMode('register')">还没窝？注册一个</button>
            <span class="dotsep">·</span>
            <button type="button" class="lnk" @click="switchMode('reset')">忘了钥匙</button>
          </template>
          <template v-else>
            <button type="button" class="lnk" @click="switchMode('login')">想起钥匙了，回去登录</button>
          </template>
        </div>

<!--        <div v-if="mode === 'login'" class="demo">-->
<!--          <button type="button" class="btn btn&#45;&#45;sm" @click="useDemo">用演示账号直接进屋</button>-->
<!--          <p class="muted">演示账号 chenjie / 123456 —— 点一下自动登录，先看看屋里的样子</p>-->
<!--        </div>-->
      </form>
    </section>
  </div>
</template>

<style scoped>
/* ---------- 布局：左氛围右表单，<=860px 上下堆叠 ---------- */
.login {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) clamp(400px, 40vw, 540px);
  background: var(--paper-page);
}

.ambience {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: var(--sp-7) var(--sp-8);
  background: linear-gradient(155deg, var(--brand-soft-strong) 0%, var(--paper-page) 58%);
}

.ambience-pattern {
  position: absolute;
  inset: 0;
  opacity: 0.35;
  pointer-events: none;
}

.panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-6) var(--sp-5);
}

.login-card {
  width: min(430px, 100%);
  padding: var(--sp-5) var(--sp-6) var(--sp-6);
  border-radius: var(--r-xl);
  box-shadow: var(--sh-2);
}

/* ---------- 品牌与问候 ---------- */
.brand {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.logo {
  width: 44px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-md);
  background: var(--brand-base);
  color: var(--ink-inverse);
  font-family: var(--font-round);
  font-size: 22px;
  font-weight: 700;
  box-shadow: var(--sh-brand);
}

.brand h1 {
  font-family: var(--font-round);
  font-size: 20px;
  letter-spacing: 0.12em;
  color: var(--brand-text);
}

.tag {
  font-size: 12px;
  color: var(--ink-3);
}

.greeting {
  position: relative;
  z-index: 2;
  margin-top: var(--sp-7);
  max-width: 46ch;
}

.greeting h2 {
  font-family: var(--font-round);
  font-size: clamp(26px, 3.4vw, 38px);
  color: var(--ink);
}

.greeting .muted {
  margin-top: var(--sp-2);
  font-size: 14px;
}

.looks {
  position: relative;
  z-index: 2;
  margin-top: auto;
  padding-top: var(--sp-6);
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  flex-wrap: wrap;
}

.dots {
  display: flex;
  gap: var(--sp-2);
}

.dot {
  width: 18px;
  height: 18px;
  padding: 0;
  border-radius: var(--r-pill);
  border: 2px solid transparent;
  cursor: pointer;
  opacity: 0.8;
  transition:
    transform var(--dur-fast) var(--ease),
    border-color var(--dur-fast) var(--ease),
    opacity var(--dur-fast) var(--ease);
}

.dot:hover {
  transform: scale(1.15);
  opacity: 1;
}

.dot.is-on {
  border-color: var(--ink);
  opacity: 1;
  transform: scale(1.1);
}

/* ---------- CSS 装饰：窗 ---------- */
.window {
  position: absolute;
  z-index: 1;
  top: 76px;
  right: clamp(28px, 9vw, 120px);
  width: 150px;
  height: 196px;
  border: 3px solid var(--brand-line);
  border-radius: 75px 75px var(--r-md) var(--r-md);
  background: linear-gradient(180deg, var(--brand-soft-strong), var(--paper-raised));
  box-shadow:
    var(--sh-2),
    inset 0 0 30px color-mix(in srgb, var(--peach) 16%, transparent);
}

.window::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 6%;
  bottom: 6%;
  width: 3px;
  margin-left: -1.5px;
  background: var(--brand-line);
}

.window::after {
  content: '';
  position: absolute;
  top: 52%;
  left: 6%;
  right: 6%;
  height: 3px;
  background: var(--brand-line);
}

.sun {
  position: absolute;
  left: 50%;
  top: 62%;
  width: 26px;
  height: 26px;
  margin-left: -13px;
  border-radius: var(--r-pill);
  background: var(--peach);
  box-shadow: 0 0 22px color-mix(in srgb, var(--peach) 55%, transparent);
  animation: breathe 5s var(--ease) infinite;
}

.moon {
  position: absolute;
  left: 50%;
  top: 20%;
  width: 24px;
  height: 24px;
  border-radius: var(--r-pill);
  background: transparent;
  box-shadow: inset 7px 4px 0 0 var(--paper-muted);
  transform: rotate(14deg);
}

.wstar {
  position: absolute;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--paper-muted);
  animation: twinkle 2.6s var(--ease) infinite;
}

.wstar:nth-of-type(1) { left: 24%; top: 26%; }
.wstar:nth-of-type(2) { left: 66%; top: 38%; }
.wstar:nth-of-type(3) { left: 30%; top: 46%; animation-delay: 0.9s; }

/* ---------- CSS 装饰：挂灯 / 小鸟 / 巢（跟随 themeStore.mascot） ---------- */
.lantern {
  position: absolute;
  z-index: 1;
  top: 0;
  right: clamp(150px, 18vw, 250px);
  animation: sway 6s ease-in-out infinite;
  transform-origin: 50% 0;
}

.lantern::before {
  content: '';
  display: block;
  width: 2px;
  height: 56px;
  margin: 0 auto;
  background: var(--brand-line);
}

.lantern-body {
  position: relative;
  width: 34px;
  height: 40px;
  margin: 0 auto;
  border-radius: 46% / 50%;
  background: radial-gradient(
    circle at 50% 30%,
    color-mix(in srgb, var(--peach) 85%, transparent),
    var(--brand-base)
  );
  box-shadow:
    0 0 26px color-mix(in srgb, var(--peach) 35%, transparent),
    inset 0 -6px 10px color-mix(in srgb, var(--brand-deep) 45%, transparent);
  animation: breathe 4.5s var(--ease) infinite;
}

.lantern-body::before,
.lantern-body::after {
  content: '';
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  width: 12px;
  height: 5px;
  border-radius: var(--r-sm);
  background: var(--brand-deep);
}

.lantern-body::before { top: -5px; }
.lantern-body::after { bottom: -5px; }

.bird {
  position: absolute;
  z-index: 1;
  top: 240px;
  right: clamp(40px, 12vw, 160px);
  width: 46px;
  height: 34px;
  border-radius: 50% 50% 45% 45%;
  background: var(--brand-soft-strong);
  border: 2px solid var(--brand-line);
  animation: bob 3s ease-in-out infinite;
}

.bird::before {
  content: '';
  position: absolute;
  right: -10px;
  top: 12px;
  width: 0;
  height: 0;
  border-left: 9px solid var(--peach);
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
}

.bird::after {
  content: '';
  position: absolute;
  right: 10px;
  top: 9px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--ink-2);
}

.nest {
  position: absolute;
  z-index: 1;
  top: 250px;
  right: clamp(40px, 12vw, 160px);
  width: 72px;
  height: 32px;
  border: 3px solid var(--brand-line);
  border-top: none;
  border-radius: 6px 6px 36px 36px;
  background: linear-gradient(180deg, var(--brand-soft), var(--paper-raised));
}

.egg {
  position: absolute;
  top: -8px;
  width: 16px;
  height: 20px;
  border-radius: 50% 50% 46% 46%;
  background: var(--paper-raised);
  border: 2px solid var(--paper-line);
}

.egg:nth-child(1) { left: 14px; transform: rotate(-8deg); }
.egg:nth-child(2) { left: 36px; transform: rotate(6deg); }

/* ---------- CSS 装饰：星点与漂浮微粒 ---------- */
.star {
  position: absolute;
  z-index: 1;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--peach);
  animation: twinkle 3.2s var(--ease) infinite;
}

.star--1 { top: 14%; left: 18%; }
.star--2 { top: 30%; left: 42%; background: var(--brand-line); animation-delay: 1.1s; }
.star--3 { top: 8%; left: 60%; animation-delay: 2s; }

.motes {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.mote {
  position: absolute;
  bottom: -14px;
  left: 8%;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--peach) 70%, transparent);
  opacity: 0;
  animation: float-up 16s linear infinite;
}

.mote:nth-child(2) { left: 22%; width: 5px; height: 5px; animation-duration: 20s; animation-delay: 3s; }
.mote:nth-child(3) { left: 40%; animation-duration: 18s; animation-delay: 6s; }
.mote:nth-child(4) { left: 58%; width: 4px; height: 4px; animation-delay: 2s; }
.mote:nth-child(5) { left: 74%; animation-duration: 22s; animation-delay: 8s; }
.mote:nth-child(6) { left: 88%; width: 5px; height: 5px; animation-delay: 5s; }

/* ---------- 表单卡片内部 ---------- */
.seg {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
  padding: 4px;
  margin-bottom: var(--sp-5);
  background: var(--paper-muted);
  border-radius: var(--r-pill);
}

.seg .btn {
  height: 30px;
  padding: 0;
  border: none;
  border-radius: var(--r-pill);
  background: transparent;
  box-shadow: none;
  font-size: 13px;
  color: var(--ink-2);
}

.seg .btn:hover {
  background: color-mix(in srgb, var(--paper-surface) 55%, transparent);
  color: var(--brand-text);
}

.seg .is-on {
  background: var(--paper-surface);
  color: var(--brand-text);
  box-shadow: var(--sh-1);
}

.headline {
  margin-bottom: var(--sp-4);
  font-family: var(--font-round);
  font-size: 18px;
  color: var(--ink);
}

.strip {
  padding: var(--sp-2) var(--sp-3);
  margin-bottom: var(--sp-4);
  border-radius: var(--r-md);
  font-size: 13px;
  line-height: 1.6;
}

.strip--bad {
  background: color-mix(in srgb, var(--danger) 12%, var(--paper-surface));
  border: 1px solid color-mix(in srgb, var(--danger) 40%, var(--paper-line));
  color: var(--danger);
}

.strip--good {
  background: color-mix(in srgb, var(--ok) 14%, var(--paper-surface));
  border: 1px solid color-mix(in srgb, var(--ok) 40%, var(--paper-line));
  color: var(--ok);
}

.frow {
  margin-bottom: var(--sp-4);
}

.lb {
  display: block;
  margin-bottom: var(--sp-1);
  font-size: 12px;
  letter-spacing: 0.05em;
  color: var(--ink-3);
}

.field--bad {
  border-color: color-mix(in srgb, var(--danger) 55%, var(--paper-line));
}

.pw {
  position: relative;
}

.pw .field {
  padding-right: 58px;
}

.reveal {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  padding: 4px 8px;
  border: none;
  background: transparent;
  color: var(--ink-3);
  font-size: 12px;
  cursor: pointer;
}

.reveal:hover {
  color: var(--brand-text);
}

.note {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
}

.note--bad { color: var(--danger); }
.note--ok { color: var(--ok); }
.note--muted { color: var(--ink-3); }

.go {
  width: 100%;
  height: 42px;
  margin-top: var(--sp-2);
  font-size: 14px;
}

.foot {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-2);
  margin-top: var(--sp-4);
  font-size: 12px;
}

.dotsep {
  color: var(--ink-4);
}

.lnk {
  padding: 0;
  border: none;
  background: transparent;
  font-size: 12px;
  color: var(--brand-text);
  cursor: pointer;
}

.lnk:hover {
  text-decoration: underline;
}

.demo {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  margin-top: var(--sp-4);
  padding-top: var(--sp-4);
  border-top: 1px dashed var(--paper-line);
}

.demo .muted {
  font-size: 12px;
}

/* ---------- 本地动效（float-up / breathe 来自 base.css） ---------- */
@keyframes sway {
  0%, 100% { transform: rotate(-3deg); }
  50% { transform: rotate(3deg); }
}

@keyframes bob {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-5px); }
}

@keyframes twinkle {
  0%, 100% { opacity: 0.15; transform: scale(0.8); }
  50% { opacity: 0.95; transform: scale(1.2); }
}

/* ---------- 窄屏：上下堆叠 ---------- */
@media (max-width: 860px) {
  .login {
    grid-template-columns: 1fr;
    grid-template-rows: auto 1fr;
  }

  .ambience {
    min-height: 44vh;
    padding: var(--sp-5) var(--sp-5) var(--sp-4);
  }

  .window {
    top: 20px;
    transform: scale(0.78);
    transform-origin: top right;
  }

  .lantern { right: clamp(120px, 30vw, 190px); }

  .bird,
  .nest { top: auto; bottom: 96px; }

  .greeting {
    margin-top: var(--sp-5);
  }

  .looks {
    padding-top: var(--sp-5);
  }

  .panel {
    padding: var(--sp-4) var(--sp-3) var(--sp-6);
  }
}
</style>
