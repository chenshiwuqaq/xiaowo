/**
 * 接口返回的领域类型定义。
 *
 * 与后端 docs/API.md 一一对应，字段命名保持驼峰（后端 VO 即驼峰）。
 * 集中定义是为了让视图层拿到的数据是"有类型的"，改接口时 TS 会直接报出来。
 */

/** 后端统一响应信封 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface UserProfile {
  id: number
  account: string
  username: string
  email?: string | null
  avatar?: string | null
  intro?: string | null
  themeColor?: string | null
  themeMode?: 'light' | 'dark' | null
  noteBg?: string | null
  weatherCity?: string | null
  dailyGoal?: number | null
  fontSize?: number | null
  aiFeatureMap?: string | null
  createTime?: string | null
}

export interface LoginResult {
  token: string
  user: UserProfile
}

/** 目录树节点 */
export interface FolderNode {
  id: number
  parentId: number
  name: string
  icon?: string | null
  sortOrder?: number
  noteCount?: number
  children?: FolderNode[]
}

export interface Tag {
  id: number
  name: string
  color: string
  noteCount?: number | null
}

/** 列表项（不含正文） */
export interface NoteItem {
  id: number
  title: string
  summary?: string | null
  folderId: number
  noteType: number
  noteTypeName?: string
  wordCount: number
  top?: boolean
  tags?: Tag[]
  createTime?: string
  updateTime?: string
}

/** 详情（含正文） */
export interface NoteDetail extends NoteItem {
  content: string
}

export interface NoteHistoryItem {
  id: number
  noteId?: number
  title?: string
  wordCount?: number
  source?: string
  createTime?: string
}

export interface ImportResult {
  success: number
  failed: number
  titles: string[]
}

export interface DailyWord {
  date: string
  words: number
}

export interface TypeCount {
  noteType: number
  name: string
  count: number
}

export interface WeatherForecast {
  date: string
  text: string
  icon: string
  min: number
  max: number
}

export interface Weather {
  city: string
  country?: string
  temperature: number
  feelsLike?: number
  humidity?: number
  windSpeed?: number
  weatherCode?: number
  weatherText: string
  icon: string
  updatedAt: string
  forecast?: WeatherForecast[]
}

export interface HomeOverview {
  username: string
  avatar?: string | null
  themeColor?: string
  noteCount: number
  totalWords: number
  writtenWords: number
  todayWords: number
  dailyGoal: number
  goalReached: boolean
  streakDays: number
  lastMilestone?: string | null
  typeDistribution: TypeCount[]
  recentNotes: NoteItem[]
  topNotes: NoteItem[]
  wordCalendar: DailyWord[]
  trend: DailyWord[]
  weather?: Weather | null
}

export interface Greeting {
  greeting: string
  subtitle: string
  tips: string[]
}

/** AI 助手 */
export interface AiStatus {
  configured: boolean
  model?: string | null
  providerName?: string | null
  offlineScenes: string[]
  onlineScenes: string[]
}

export interface AiConfig {
  id?: number
  providerCode: string
  providerName?: string
  baseUrl: string
  modelName: string
  apiKeyMask?: string | null
  hasKey?: boolean
  apiKey?: string
  temperature?: number
  maxTokens?: number
  defaultConfig?: boolean
  enabled?: boolean
}

/** AI 工具插件清单项（助手面板） */
export interface AiTool {
  id: string
  origin: 'builtin' | 'user'
  name: string
  description?: string
  renderType: 'text' | 'cards' | 'references' | string
  requiresModel: boolean
  insertable?: boolean
}

/** text 渲染类型的结果数据体 */
export interface AiToolTextData {
  text: string
  model?: string
  insertable?: boolean
}

/** 通用执行结果 */
export interface AiToolRunResult {
  toolId: string
  renderType: 'text' | 'cards' | 'references' | string
  data: unknown
}

/** 用户插件管理项 */
export interface AiToolDetail {
  id?: number
  toolId: string
  name: string
  description?: string
  renderType: string
  systemPrompt: string
  userTemplate?: string
  insertable?: boolean
  enabled?: boolean
  sortOrder?: number
  createTime?: string
  updateTime?: string
}

export interface ProviderPreset {
  code: string
  name: string
  baseUrl: string
  models: string[]
}

export interface KnowledgeCard {
  id: number
  category: string
  era?: string | null
  region?: string | null
  title: string
  content: string
  source?: string | null
  hitWords: string[]
  score: number
}

export interface KnowledgeResult {
  mode: string
  modeName: string
  cards: KnowledgeCard[]
  triggerWords: string[]
  reason: string
}

export interface ReferenceItem {
  id?: number | null
  title: string
  authors?: string | null
  journal?: string | null
  pubYear?: number | null
  doi?: string | null
  url?: string | null
  field?: string | null
  abstract?: string | null
  citedCount?: number | null
  matched: string[]
  reason?: string | null
  score?: number
  bound?: boolean
  source: 'local' | 'online' | string
}

export interface ReferenceResult {
  mode: string
  keywords: string[]
  items: ReferenceItem[]
  online: boolean
  reason: string
  onlineError?: string | null
}

export interface RewritePreview {
  original: string
  revised: string
  explanation?: string
  /** 改写后主动抛出的"下一轮方向"建议，前端可一键据此再改 */
  followUp?: string
  model?: string
}

export interface Suggestions {
  suggestions: string[]
  mode: string
  source: 'ai' | 'local' | string
}

export interface AiSession {
  id: number
  noteId: number
  title?: string
  messageCount: number
  updateTime?: string
}

export interface AiMessage {
  id: number
  role: 'user' | 'assistant' | 'system'
  content: string
  scene?: string
  createTime?: string
}

export interface Diagram {
  id?: number
  title: string
  diagramType: string
  noteIds?: string
  code: string
  generator?: string
  createTime?: string
}

export interface UploadResult {
  url: string
  name: string
  size: number
}

/** 通用分页结果（与后端 PageResult 对齐） */
export interface PageResult<T> {
  list: T[]
  total: number
  pageNo: number
  pageSize: number
}

/** 插件市场条目（卡片） */
export interface MarketPlugin {
  id: number
  authorId: number
  authorName: string
  authorAvatar?: string | null
  toolId: string
  name: string
  description?: string | null
  renderType: string
  insertable?: boolean
  category: number
  categoryName: string
  tags: string[]
  likeCount: number
  commentCount: number
  useCount: number
  status?: number
  liked: boolean
  used: boolean
  createTime?: string | null
  updateTime?: string | null
}

/** 市场评论 */
export interface MarketComment {
  id: number
  pluginId: number
  userId: number
  userName: string
  userAvatar?: string | null
  content: string
  createTime?: string | null
}

/** 市场条目详情（含 manifest 全文与评论） */
export interface MarketPluginDetail extends MarketPlugin {
  systemPrompt: string
  userTemplate?: string | null
  comments: MarketComment[]
}

/** 市场排行榜：三张榜单 */
export interface MarketRankings {
  byLike: MarketPlugin[]
  byComment: MarketPlugin[]
  byUse: MarketPlugin[]
}

/** 点赞 / 取消点赞后的状态 */
export interface MarketLikeState {
  liked: boolean
  likeCount: number
}

/** 下载使用后的状态 */
export interface MarketUseState {
  installed: boolean
  newlyUsed: boolean
  toolId: string
  useCount: number
}

/** 市场发布 / 更新入参 */
export interface MarketPluginSave {
  sourceToolId?: number
  toolId?: string
  name?: string
  description?: string
  renderType?: string
  systemPrompt?: string
  userTemplate?: string
  insertable?: boolean
  category: number
  tags?: string
}
