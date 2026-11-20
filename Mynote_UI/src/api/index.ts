import { del, download, get, post, put } from './http'
import type {
  AiConfig,
  AiMessage,
  AiSession,
  AiStatus,
  AiTool,
  AiToolDetail,
  AiToolRunResult,
  Diagram,
  FolderNode,
  Greeting,
  HomeOverview,
  ImportResult,
  KnowledgeResult,
  DailyWord,
  LoginResult,
  MarketComment,
  MarketLikeState,
  MarketPlugin,
  MarketPluginDetail,
  MarketPluginSave,
  MarketRankings,
  MarketUseState,
  NoteDetail,
  NoteHistoryItem,
  NoteItem,
  PageResult,
  ProviderPreset,
  ReferenceResult,
  RewritePreview,
  Suggestions,
  Tag,
  UserProfile,
  UploadResult,
  Weather,
} from './types'

/** 账号相关接口 */
export const authApi = {
  login: (data: { account: string; password: string }) => post<LoginResult>('/auth/login', data),
  register: (data: { account: string; username: string; password: string; email: string }) =>
    post<LoginResult>('/auth/register', data),
  resetPassword: (data: { account: string; email: string; newPassword: string }) =>
    post<void>('/auth/reset-password', data),
  checkAccount: (account: string) => get<boolean>('/auth/check-account', { account }),
  providers: () => get<ProviderPreset[]>('/auth/providers'),
}

/** 资料与设置接口 */
export const userApi = {
  profile: () => get<UserProfile>('/user/profile'),
  /**
   * 更新资料。
   *
   * avatar 传 /api/file/upload 返回的相对路径；留空表示保持原头像（后端按"不改"处理）。
   */
  updateProfile: (data: { username: string; email: string; intro?: string; avatar?: string }) =>
    put<UserProfile>('/user/profile', data),
  updateSettings: (data: {
    themeColor?: string
    themeMode?: string
    noteBg?: string
    weatherCity?: string
    dailyGoal?: number
    fontSize?: number
    aiFeatureMap?: string
  }) => put<UserProfile>('/user/settings', data),
  changePassword: (data: { oldPassword: string; newPassword: string }) =>
    put<void>('/user/password', data),
}

/** 文件上传 */
export const fileApi = {
  /**
   * 上传图片/头像。
   *
   * @param file  文件对象
   * @param type  avatar 或 image，决定落盘子目录
   */
  upload: (file: File, type: 'avatar' | 'image' = 'image') => {
    const form = new FormData()
    form.append('file', file)
    form.append('type', type)
    return post<UploadResult>('/file/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000,
    })
  },
}

/** 目录接口 */
export const folderApi = {
  tree: () => get<FolderNode[]>('/folder/tree'),
  create: (data: { parentId: number; name: string; icon?: string }) => post<FolderNode>('/folder', data),
  rename: (id: number, data: { name: string; icon?: string; sortOrder?: number }) =>
    put<FolderNode>(`/folder/${id}`, data),
  move: (data: { id: number; parentId: number }) => put<void>('/folder/move', data),
  remove: (id: number) => del<void>(`/folder/${id}`),
}

/** 笔记接口 */
export const noteApi = {
  list: (params?: { folderId?: number; keyword?: string; tagId?: number }) => get<NoteItem[]>('/note/list', params),
  trash: () => get<NoteItem[]>('/note/trash'),
  detail: (id: number) => get<NoteDetail>(`/note/${id}`),
  create: (data: {
    title?: string
    content?: string
    folderId?: number
    noteType?: number
    tagNames?: string[]
  }) => post<NoteDetail>('/note', data),
  update: (
    id: number,
    data: {
      title?: string
      content?: string
      folderId?: number
      noteType?: number
      tagNames?: string[]
      snapshot?: boolean
      snapshotSource?: string
    },
  ) => put<NoteDetail>(`/note/${id}`, data),
  trashOne: (id: number) => del<void>(`/note/${id}`),
  restore: (id: number) => put<void>(`/note/${id}/restore`),
  permanent: (id: number) => del<void>(`/note/${id}/permanent`),
  setTop: (data: { id: number; top: boolean }) => put<void>('/note/top', data),
  batchMove: (data: { ids: number[]; folderId: number }) => put<void>('/note/batch-move', data),
  batchTrash: (ids: number[]) => del<void>('/note/batch', { ids }),
  history: (id: number) => get<NoteHistoryItem[]>(`/note/${id}/history`),
  restoreHistory: (historyId: number) => put<NoteDetail>(`/note/history/${historyId}/restore`),
  /** 导入 md/txt，可多文件 */
  importFiles: (files: File[], folderId: number, noteType: number) => {
    const form = new FormData()
    files.forEach((file) => form.append('files', file))
    form.append('folderId', String(folderId))
    form.append('noteType', String(noteType))
    return post<ImportResult>('/note/import', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000,
    })
  },
  exportOne: (id: number, format: 'md' | 'txt') => download({ url: `/note/${id}/export`, params: { format } }),
  exportBatch: (ids: number[], format: 'md' | 'txt') =>
    download({ url: '/note/export', method: 'POST', data: { ids, format } }),
}

/** 标签接口 */
export const tagApi = {
  list: () => get<Tag[]>('/tag/list'),
  create: (data: { name: string; color?: string }) => post<Tag>('/tag', data),
  update: (id: number, data: { name: string; color: string }) => put<Tag>(`/tag/${id}`, data),
  remove: (id: number) => del<void>(`/tag/${id}`),
}

/** 主页与统计接口 */
export const homeApi = {
  overview: () => get<HomeOverview>('/home/overview'),
  calendar: (days = 120) => get<{ days: DailyWord[] }>('/home/writing-calendar', { days }),
  trend: (days = 30) => get<{ days: DailyWord[]; total: number; max: number }>('/home/trend', { days }),
  /** 天气失败是"可忽略"的，调用方需捕获 ApiError 并静默降级 */
  weather: () => get<Weather>('/home/weather'),
  greeting: () => get<Greeting>('/home/greeting'),
}

/** AI 助手接口 */
export const aiApi = {
  status: () => get<AiStatus>('/ai/status'),
  // 同步调用模型的接口耗时可能超过默认 30s（后端读超时 120s），这里单独放宽到 130s
  suggest: (data: { noteId: number; selection?: string; previous?: string[] }) =>
    post<Suggestions>('/ai/suggest', data, { timeout: 130000 }),
  action: (data: { noteId: number; action: string; selection?: string; instruction?: string; sessionId?: number }) =>
    post<{ action: string; text: string; model: string }>('/ai/action', data, { timeout: 130000 }),
  rewrite: (data: { noteId: number; action: string; selection: string; instruction?: string }) =>
    post<RewritePreview>('/ai/rewrite', data, { timeout: 130000 }),
  applyRewrite: (data: { noteId: number; original: string; revised: string }) =>
    post<NoteDetail>('/ai/apply-rewrite', data),
  knowledge: (noteId: number, selection?: string) =>
    get<KnowledgeResult>('/ai/knowledge', { noteId, selection: selection || undefined }),
  adoptKnowledge: (cardId: number) => post<void>('/ai/knowledge/adopt', undefined, { params: { cardId } }),
  references: (noteId: number) =>
    // 文献推荐会并发拉取 OpenAlex 并可能让模型做二次排序，后端有兜底超时，这里放宽到 60s 避免误报超时
    get<ReferenceResult>('/ai/references', { noteId }, { timeout: 60000 }),
  bindReference: (data: { noteId: number; refId: number; source?: string }) => post<void>('/ai/references/bind', data),
  unbindReference: (noteId: number, refId: number) => del<void>('/ai/references/bind', undefined, { noteId, refId }),
  sessions: (noteId?: number) => get<AiSession[]>('/ai/sessions', noteId ? { noteId } : undefined),
  messages: (sessionId: number) => get<AiMessage[]>(`/ai/session/${sessionId}/messages`),
  removeSession: (sessionId: number) => del<void>(`/ai/session/${sessionId}`),
  generateDiagram: (data: { noteIds: number[]; type?: string; useAi?: boolean }) =>
    post<Diagram>('/ai/diagram', data, { timeout: 130000 }),
  saveDiagram: (data: { title: string; diagramType: string; noteIds: string; code: string }) =>
    post<Diagram>('/ai/diagram/save', data),
}

/** 逻辑图接口 */
export const diagramApi = {
  list: () => get<Diagram[]>('/diagram/list'),
  detail: (id: number) => get<Diagram>(`/diagram/${id}`),
  remove: (id: number) => del<void>(`/diagram/${id}`),
}

/** AI 模型配置接口 */
export const aiConfigApi = {
  list: () => get<AiConfig[]>('/ai/config/list'),
  save: (data: AiConfig) => post<AiConfig>('/ai/config/save', data),
  remove: (id: number) => del<void>(`/ai/config/${id}`),
  setDefault: (id: number) => put<AiConfig>(`/ai/config/${id}/default`),
  test: (data: { id?: number; baseUrl?: string; modelName?: string; apiKey?: string }) =>
    post<{ ok: boolean; message: string; sample?: string; model?: string }>('/ai/config/test', data),
}

/** AI 工具插件接口（可插拔工具 + 用户自定义插件管理） */
export const aiToolApi = {
  /** 当前可用插件清单（内置 + 已启用的用户插件） */
  list: () => get<AiTool[]>('/ai/tools'),
  /** 通用执行 */
  run: (data: {
    id: string
    noteId?: number
    selection?: string
    sessionId?: number
    params?: Record<string, unknown>
  }) => post<AiToolRunResult>('/ai/tools/run', data, { timeout: 130000 }),
  /** 我的插件（管理页，含停用） */
  mine: () => get<AiToolDetail[]>('/ai/tools/mine'),
  create: (data: AiToolDetail) => post<AiToolDetail>('/ai/tools/mine', data),
  update: (id: number, data: AiToolDetail) => put<AiToolDetail>(`/ai/tools/mine/${id}`, data),
  remove: (id: number) => del<void>(`/ai/tools/mine/${id}`),
  toggle: (id: number) => put<AiToolDetail>(`/ai/tools/mine/${id}/toggle`),
}

/** 插件市场接口（发布 / 浏览 / 点赞 / 评论 / 下载使用） */
export const marketApi = {
  /** 上架条目检索（关键词 + 分类 + 排序 + 分页） */
  list: (params?: { keyword?: string; category?: number; sort?: string; pageNo?: number; pageSize?: number }) =>
    get<PageResult<MarketPlugin>>('/market/list', params),
  /** 三张排行榜：爱心最多 / 评论最多 / 使用最多 */
  rankings: (size = 10) => get<MarketRankings>('/market/rankings', { size }),
  /** 随机换一批（受关键词与分类约束） */
  random: (params?: { keyword?: string; category?: number; size?: number }) =>
    get<MarketPlugin[]>('/market/random', params),
  /** 我的发布（含下架） */
  mine: () => get<MarketPlugin[]>('/market/mine'),
  /** 条目详情（含 manifest 与评论） */
  detail: (id: number) => get<MarketPluginDetail>(`/market/${id}`),
  /** 发布 */
  publish: (data: MarketPluginSave) => post<MarketPlugin>('/market', data),
  /** 更新自己的发布 */
  update: (id: number, data: MarketPluginSave) => put<MarketPlugin>(`/market/${id}`, data),
  /** 删除自己的发布 */
  remove: (id: number) => del<void>(`/market/${id}`),
  /** 上架 / 下架 */
  setStatus: (id: number, published: boolean) =>
    put<MarketPlugin>(`/market/${id}/status`, undefined, { params: { published } }),
  /** 点赞 / 取消点赞 */
  like: (id: number) => post<MarketLikeState>(`/market/${id}/like`),
  /** 下载使用（装进我的插件） */
  use: (id: number) => post<MarketUseState>(`/market/${id}/use`),
  /** 发表评论 */
  comment: (id: number, content: string) => post<MarketComment>(`/market/${id}/comments`, { content }),
  /** 删除评论 */
  deleteComment: (commentId: number) => del<void>(`/market/comments/${commentId}`),
}
