import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi, userApi } from '@/api'
import { ApiError, clearToken, getCode, getToken, setToken } from '@/api/http'
import type { LoginResult, UserProfile } from '@/api/types'
import { useThemeStore } from './theme'

/**
 * 登录态与个人设置。
 *
 * 令牌只放 localStorage（单机自托管场景，无跨标签同步需求），
 * 用户信息在刷新页面后按需重新拉取，避免"本地缓存与服务端不一致"这类难查的问题。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken())
  const profile = ref<UserProfile | null>(null)
  const loading = ref(false)

  const logged = computed(() => Boolean(token.value))
  const displayName = computed(() => profile.value?.username || profile.value?.account || '朋友')

  function adopt(result: LoginResult) {
    token.value = result.token
    profile.value = result.user
    setToken(result.token)
  }

  async function login(account: string, password: string) {
    loading.value = true
    try {
      const result = await authApi.login({ account, password })
      adopt(result)
      await pullSettingsIntoTheme()
      return result
    } finally {
      loading.value = false
    }
  }

  async function register(payload: { account: string; username: string; password: string; email: string }) {
    loading.value = true
    try {
      const result = await authApi.register(payload)
      adopt(result)
      return result
    } finally {
      loading.value = false
    }
  }

  async function fetchProfile(force = false) {
    if (profile.value && !force) return profile.value
    if (!token.value) return null
    try {
      profile.value = await userApi.profile()
      await pullSettingsIntoTheme()
      return profile.value
    } catch (error) {
      // 令牌过期由 http 层统一跳转，这里不把错误继续抛给视图
      if (error instanceof ApiError && error.code === 401) return null
      throw error
    }
  }

  /** 把服务端的主题相关设置灌进 theme store，保证换设备后观感一致 */
  async function pullSettingsIntoTheme() {
    if (!profile.value) return
    const theme = useThemeStore()
    theme.syncFromServer({
      themeColor: profile.value.themeColor,
      themeMode: profile.value.themeMode,
      noteBg: profile.value.noteBg,
      fontSize: profile.value.fontSize,
    })
  }

  async function updateProfile(payload: { username: string; email: string; intro?: string; avatar?: string }) {
    profile.value = await userApi.updateProfile(payload)
    return profile.value
  }

  /**
   * 保存外观与习惯设置。
   *
   * 主题 store 是"即时生效"的（改了立刻变），后端只负责持久化，
   * 所以这里失败时要提示，但不能回滚界面——用户已经看到效果了。
   */
  async function saveSettings(payload: {
    themeColor?: string
    themeMode?: string
    noteBg?: string
    weatherCity?: string
    dailyGoal?: number
    fontSize?: number
    aiFeatureMap?: string
  }) {
    // 后端 /user/settings 是全量更新，六个字段都必须齐发；各设置分区只传自己负责的字段，
    // 这里用当前 profile 补齐其余字段，避免只传主题三件套时被参数校验拦截
    const current = profile.value
    const merged = {
      themeColor: payload.themeColor ?? current?.themeColor ?? '#C06A46',
      themeMode: payload.themeMode ?? current?.themeMode ?? 'light',
      noteBg: payload.noteBg ?? current?.noteBg ?? 'paper',
      weatherCity: payload.weatherCity ?? current?.weatherCity ?? '杭州',
      dailyGoal: payload.dailyGoal ?? current?.dailyGoal ?? 1000,
      fontSize: payload.fontSize ?? current?.fontSize ?? 16,
      aiFeatureMap: payload.aiFeatureMap ?? current?.aiFeatureMap ?? '',
    }
    profile.value = await userApi.updateSettings(merged)
    return profile.value
  }

  async function changePassword(oldPassword: string, newPassword: string) {
    await userApi.changePassword({ oldPassword, newPassword })
  }

  function logout() {
    token.value = ''
    profile.value = null
    clearToken()
  }

  return {
    token,
    profile,
    loading,
    logged,
    displayName,
    login,
    register,
    fetchProfile,
    updateProfile,
    saveSettings,
    changePassword,
    logout,
    pullSettingsIntoTheme,
    /** 供路由守卫判断 */
    hasToken: () => Boolean(token.value || getToken()),
    /** 错误码提取，视图里区分"该提示"与"该静默" */
    errorCodeOf: (error: unknown) => getCode(error),
  }
})
