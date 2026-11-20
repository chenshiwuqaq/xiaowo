import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import type { ApiResponse } from './types'

/** 业务状态码，与后端 ResultCode 对齐 */
export const CODE = {
  SUCCESS: 200,
  PARAM_ERROR: 400,
  UNAUTHORIZED: 401,
  NOT_FOUND: 404,
  AI_NOT_CONFIGURED: 2001,
  AI_CALL_FAILED: 2002,
  /** 可忽略的失败：卡片切成占位即可，不该弹提示打扰书写 */
  WEATHER_UNAVAILABLE: 3001,
} as const

/**
 * 携带业务码的错误对象。
 *
 * 视图层据此区分"要提示用户"与"静默降级"两类失败：
 * 2001 引导去设置页，3001 直接忽略。
 */
export class ApiError extends Error {
  code: number

  constructor(code: number, message: string) {
    super(message)
    this.code = code
    this.name = 'ApiError'
  }
}

const TOKEN_KEY = 'mynote_token'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 跳转到登录页。
 *
 * 这里不 import router（会形成 http ↔ router 的循环依赖），直接用 location 兜底，
 * 代价是多一次整页刷新，但登录态过期本就是全量重置。
 */
function redirectToLogin() {
  clearToken()
  if (!location.pathname.startsWith('/login')) {
    const from = encodeURIComponent(location.pathname + location.search)
    location.href = `/login?redirect=${from}`
  }
}

/** 从未知错误里取业务码，视图据此决定提示还是静默 */
export function getCode(error: unknown): number {
  return error instanceof ApiError ? error.code : -1
}

/** 该错误是否属于"可以静默降级、不该弹提示"的一类（例如天气取不到） */
export function isIgnorable(error: unknown): boolean {
  return getCode(error) === CODE.WEATHER_UNAVAILABLE
}

/** 发起请求并拆掉 Result 信封；业务失败一律抛 ApiError */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  let body: ApiResponse<T>
  try {
    const response = await http.request<ApiResponse<T>>(config)
    body = response.data
  } catch (error) {
    const message =
      axios.isAxiosError(error) && error.code === 'ECONNABORTED'
        ? '请求超时，后端是不是没启动？'
        : '连不上后端服务（默认 http://127.0.0.1:8080）'
    throw new ApiError(CODE.PARAM_ERROR, message)
  }

  if (!body || typeof body.code !== 'number') {
    throw new ApiError(CODE.PARAM_ERROR, '返回内容不是约定的格式')
  }
  if (body.code === CODE.SUCCESS) {
    return body.data
  }
  if (body.code === CODE.UNAUTHORIZED) {
    redirectToLogin()
  }
  throw new ApiError(body.code, body.message || '操作失败')
}

/** GET 快捷方法 */
export function get<T>(url: string, params?: Record<string, unknown>, extra?: AxiosRequestConfig) {
  return request<T>({ url, method: 'GET', params, ...extra })
}

/** POST 快捷方法；传 FormData 时用 extra 指定 multipart 头与更长超时 */
export function post<T>(url: string, data?: unknown, extra?: AxiosRequestConfig) {
  return request<T>({ url, method: 'POST', data, ...extra })
}

/** PUT 快捷方法 */
export function put<T>(url: string, data?: unknown, extra?: AxiosRequestConfig) {
  return request<T>({ url, method: 'PUT', data, ...extra })
}

/** DELETE 快捷方法；带 body 的删除用 data 传，带查询参数的用 params 传 */
export function del<T>(url: string, data?: unknown, params?: Record<string, unknown>) {
  return request<T>({ url, method: 'DELETE', data, params })
}

/**
 * 流式对话（SSE）。
 *
 * EventSource 不能带 Authorization 头，所以这里用 fetch + ReadableStream 自己解析帧，
 * 每收到一帧把增量文本交给 onDelta；上层负责渲染打字机效果。
 *
 * @param payload 请求体
 * @param onDelta 增量回调
 * @returns 完整回答与会话 id
 */
export async function streamChat(
  payload: { noteId?: number; sessionId?: number | null; question: string },
  onDelta: (chunk: string) => void,
  signal?: AbortSignal,
): Promise<{ text: string; sessionId?: number }> {
  const response = await fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: `Bearer ${getToken()}`,
    },
    body: JSON.stringify(payload),
    signal,
  })
  if (!response.ok || !response.body) {
    throw new ApiError(CODE.AI_CALL_FAILED, `助手连接失败（${response.status}）`)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let full = ''
  let sessionId: number | undefined

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    // SSE 以空行分帧；逐帧解析而不是一次 split，避免半截 JSON
    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      const frame = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      const dataLine = frame.split('\n').find((line) => line.startsWith('data:'))
      if (dataLine) {
        try {
          const parsed = JSON.parse(dataLine.slice(5).trim()) as {
            delta?: string
            done?: boolean
            error?: boolean
            message?: string
            code?: number
            sessionId?: number
          }
          if (parsed.error) {
            throw new ApiError(parsed.code ?? CODE.AI_CALL_FAILED, parsed.message ?? '助手暂时不可用')
          }
          if (parsed.delta) {
            full += parsed.delta
            onDelta(parsed.delta)
          }
          if (parsed.done) {
            sessionId = parsed.sessionId
          }
        } catch (error) {
          if (error instanceof ApiError) throw error
          // 非 JSON 的心跳帧直接忽略
        }
      }
      boundary = buffer.indexOf('\n\n')
    }
  }
  // sessionId 回传给上层，让后续追问延续同一会话
  return { text: full, sessionId }
}


/**
 * 下载文件（导出笔记）。
 *
 * 后端用 Content-Disposition 给出文件名，这里必须走 blob 而不是直接开链接，
 * 因为下载请求要带 Authorization 头。
 */
export async function download(config: AxiosRequestConfig): Promise<{ blob: Blob; filename: string }> {
  const response = await http.request<Blob>({ ...config, responseType: 'blob' })
  const disposition = String(response.headers['content-disposition'] || '')
  let filename = 'download'
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  if (utf8?.[1]) {
    filename = decodeURIComponent(utf8[1])
  } else if (plain?.[1]) {
    filename = plain[1]
  }
  return { blob: response.data, filename }
}

export { http }
export default http
