<script setup lang="ts">
/**
 * 主页看板。
 *
 * 一次 overview 请求把"推开房门看到的全部信息"带回：时钟、今日字数、两个字数口径、
 * 天气、热力图、趋势、最近在写与置顶笔记。开场白单独取，因为它会随时间变化，
 * 回到主页时只刷新这一小块即可。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { homeApi } from '@/api'
import { isIgnorable } from '@/api/http'
import type { Greeting, HomeOverview, NoteItem } from '@/api/types'
import ClockCard from '@/components/home/ClockCard.vue'
import WordStats from '@/components/home/WordStats.vue'
import WeatherCard from '@/components/home/WeatherCard.vue'
import HeatCalendar from '@/components/home/HeatCalendar.vue'
import TrendSpark from '@/components/home/TrendSpark.vue'
import Mascot from '@/components/Mascot.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const overview = ref<HomeOverview | null>(null)
const greeting = ref<Greeting | null>(null)
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  // 先保证 profile 已就绪（主题色、昵称），再拉看板数据
  if (userStore.hasToken() && !userStore.profile) {
    await userStore.fetchProfile().catch(() => undefined)
  }
  await Promise.all([loadOverview(), loadGreeting()])
})

async function loadOverview() {
  loading.value = true
  error.value = ''
  try {
    overview.value = await homeApi.overview()
  } catch (e) {
    // 天气失败是"可忽略"的，但 overview 内部已做兜底，这里只把整页失败暴露出来
    if (!isIgnorable(e)) error.value = e instanceof Error ? e.message : '看板加载失败'
  } finally {
    loading.value = false
  }
}

async function loadGreeting() {
  try {
    greeting.value = await homeApi.greeting()
  } catch {
    // 开场白失败不影响看板主体，静默降级
    greeting.value = null
  }
}

const hello = computed(() => greeting.value?.greeting ?? `欢迎回家，${userStore.displayName}`)
const subtitle = computed(() => greeting.value?.subtitle ?? '')
const tips = computed(() => greeting.value?.tips ?? [])

function openNote(id: number) {
  router.push({ path: '/notes', query: { open: String(id) } })
}

function noteTime(item: NoteItem): string {
  return item.updateTime ?? item.createTime ?? ''
}

function noteSummary(item: NoteItem): string {
  return item.summary?.trim() || '（这篇还没有摘要）'
}

const NOTE_TYPE_COLOR: Record<number, string> = { 1: '科研', 2: '生活', 3: '小说', 4: '随笔' }
</script>

<template>
  <div class="home">
    <header class="home__hello anim-fade-up">
      <div class="hello__text">
        <h1 class="hello__title">{{ hello }}</h1>
        <p v-if="subtitle" class="muted hello__sub">{{ subtitle }}</p>
        <div v-if="tips.length" class="hello__tips">
          <span v-for="(tip, i) in tips" :key="i" class="chip">{{ tip }}</span>
        </div>
      </div>
    </header>

    <p v-if="error" class="page-notice" role="alert">{{ error }}</p>

    <!-- 顶部三卡：时钟 / 字数 / 天气 -->
    <div v-if="loading" class="home__grid">
      <div v-for="n in 3" :key="n" class="card skeleton" style="min-height: 200px"></div>
    </div>
    <div v-else-if="overview" class="home__grid">
      <ClockCard :username="overview.username" class="g-clock" />
      <WordStats
        class="g-stats"
        :today-words="overview.todayWords"
        :daily-goal="overview.dailyGoal"
        :goal-reached="overview.goalReached"
        :streak-days="overview.streakDays"
        :note-count="overview.noteCount"
        :total-words="overview.totalWords"
        :written-words="overview.writtenWords"
        :last-milestone="overview.lastMilestone"
        :type-distribution="overview.typeDistribution"
      />
      <WeatherCard class="g-weather" :weather="overview.weather ?? null" />
    </div>

    <!-- 中部两卡：热力图 / 趋势 -->
    <div v-if="overview" class="home__grid home__grid--charts">
      <HeatCalendar class="g-heat" :days="overview.wordCalendar" />
      <TrendSpark class="g-trend" :days="overview.trend" label="近 30 天" />
    </div>

    <!-- 底部：最近在写 / 置顶 -->
    <div v-if="overview" class="home__grid home__grid--notes">
      <section class="card note-block">
        <div class="note-block__head">
          <h2 class="note-block__title">最近在写</h2>
          <button class="lnk" type="button" @click="router.push('/notes')">去书写页 →</button>
        </div>
        <p v-if="!overview.recentNotes.length" class="muted note-block__empty">还没有动笔，去写第一篇吧。</p>
        <ul v-else class="note-list">
          <li v-for="item in overview.recentNotes" :key="item.id">
            <button class="note" type="button" @click="openNote(item.id)">
              <span class="note__main">
                <strong class="note__title">{{ item.title }}</strong>
                <span class="note__summary muted">{{ noteSummary(item) }}</span>
              </span>
              <span class="note__meta">
                <span class="chip">{{ NOTE_TYPE_COLOR[item.noteType] ?? item.noteTypeName ?? '笔记' }}</span>
                <span class="muted note__time">{{ noteTime(item) }}</span>
              </span>
            </button>
          </li>
        </ul>
      </section>

      <section class="card note-block">
        <div class="note-block__head">
          <h2 class="note-block__title">置顶</h2>
        </div>
        <p v-if="!overview.topNotes.length" class="muted note-block__empty">没有置顶笔记。在书写页给重要的笔记点「置顶」。</p>
        <ul v-else class="note-list">
          <li v-for="item in overview.topNotes" :key="item.id">
            <button class="note" type="button" @click="openNote(item.id)">
              <span class="note__main">
                <strong class="note__title">{{ item.title }}</strong>
                <span class="note__summary muted">{{ noteSummary(item) }}</span>
              </span>
              <span class="note__meta">
                <span class="chip">{{ NOTE_TYPE_COLOR[item.noteType] ?? item.noteTypeName ?? '笔记' }}</span>
                <span class="muted note__time">{{ noteTime(item) }}</span>
              </span>
            </button>
          </li>
        </ul>
      </section>
    </div>

    <Mascot side="right" />
  </div>
</template>

<style scoped>
.home {
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--sp-6) var(--sp-5) var(--sp-8);
  display: flex;
  flex-direction: column;
  gap: var(--sp-5);
}

.home__hello {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--sp-4);
}

.hello__title {
  font-family: var(--font-round);
  font-size: clamp(24px, 3vw, 32px);
  letter-spacing: 0.01em;
}

.hello__sub {
  margin-top: var(--sp-1);
  font-size: 13px;
}

.hello__tips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
}

.page-notice {
  margin: 0;
  padding: 10px 14px;
  border-radius: var(--r-md);
  border: 1px solid color-mix(in srgb, var(--danger) 35%, transparent);
  background: color-mix(in srgb, var(--danger) 10%, var(--paper-surface));
  color: color-mix(in srgb, var(--danger) 78%, var(--ink));
  font-size: 13px;
}

.home__grid {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: var(--sp-4);
}

.g-clock {
  grid-column: span 4;
}

.g-stats {
  grid-column: span 4;
}

.g-weather {
  grid-column: span 4;
}

.home__grid--charts {
  grid-template-columns: repeat(12, 1fr);
}

.g-heat {
  grid-column: span 7;
}

.g-trend {
  grid-column: span 5;
}

.home__grid--notes {
  grid-template-columns: repeat(2, 1fr);
}

.note-block {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.note-block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
}

.note-block__title {
  font-size: 15px;
}

.note-block__empty {
  font-size: 13px;
  padding: var(--sp-3) 0;
}

.lnk {
  border: 0;
  background: none;
  padding: 0;
  color: var(--brand-text);
  font-size: 12px;
  cursor: pointer;
}

.lnk:hover {
  text-decoration: underline;
}

.note-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.note {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  background: var(--paper-raised);
  color: var(--ink);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease), transform var(--dur-fast) var(--ease);
}

.note:hover {
  border-color: var(--brand-line);
  background: var(--brand-soft);
  transform: translateY(-1px);
}

.note__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.note__title {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note__summary {
  font-size: 12px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.note__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex: none;
}

.note__time {
  font-size: 11px;
}

@media (max-width: 980px) {
  .g-clock,
  .g-stats,
  .g-weather {
    grid-column: span 12;
  }

  .g-heat,
  .g-trend {
    grid-column: span 12;
  }

  .home__grid--notes {
    grid-template-columns: 1fr;
  }
}
</style>
