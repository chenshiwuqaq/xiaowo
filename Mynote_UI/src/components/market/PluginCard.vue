<script setup lang="ts">
import { computed } from 'vue'
import type { MarketPlugin } from '@/api/types'

const props = defineProps<{ plugin: MarketPlugin; busy?: boolean }>()

const emit = defineEmits<{
  (e: 'open', plugin: MarketPlugin): void
  (e: 'like', plugin: MarketPlugin): void
  (e: 'use', plugin: MarketPlugin): void
}>()

const avatarUrl = computed(() => {
  const raw = props.plugin.authorAvatar
  if (!raw) return ''
  return raw.startsWith('http') || raw.startsWith('/upload') ? raw : `/upload/${raw}`
})

function initials() {
  return props.plugin.authorName ? props.plugin.authorName.slice(0, 1) : '友'
}
</script>

<template>
  <article class="pcard card card--hover" @click="emit('open', plugin)">
    <header class="pcard__head">
      <div class="pcard__name">
        <strong class="pcard__title">{{ plugin.name }}</strong>
        <span class="chip chip--brand">{{ plugin.categoryName }}</span>
      </div>
      <span class="chip pcard__id" :title="plugin.toolId">{{ plugin.toolId }}</span>
    </header>

    <p v-if="plugin.description" class="pcard__desc muted">{{ plugin.description }}</p>
    <p v-else class="pcard__desc muted">（作者没有留下说明）</p>

    <div v-if="plugin.tags?.length" class="pcard__tags">
      <span v-for="tag in plugin.tags" :key="tag" class="tag">#{{ tag }}</span>
    </div>

    <footer class="pcard__foot">
      <div class="pcard__author">
        <img v-if="avatarUrl" class="pcard__avatar" :src="avatarUrl" :alt="plugin.authorName" />
        <span v-else class="pcard__avatar pcard__avatar--fallback">{{ initials() }}</span>
        <span class="pcard__author-name muted">{{ plugin.authorName }}</span>
      </div>

      <div class="pcard__stats">
        <span class="stat" :title="`${plugin.likeCount} 个爱心`">
          <svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true" :class="{ 'stat__heart--on': plugin.liked }">
            <path
              d="M12 20.5c-5.2-3.5-8.2-6.4-8.2-10a4.7 4.7 0 0 1 8.2-2.9 4.7 4.7 0 0 1 8.2 2.9c0 3.6-3 6.5-8.2 10Z"
              :fill="plugin.liked ? 'currentColor' : 'none'"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linejoin="round"
            />
          </svg>
          {{ plugin.likeCount }}
        </span>
        <span class="stat" :title="`${plugin.commentCount} 条评论`">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M4.5 5.5h15v10h-8.5L6 19.5v-4H4.5v-10Z" />
          </svg>
          {{ plugin.commentCount }}
        </span>
        <span class="stat" :title="`${plugin.useCount} 人使用`">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M12 4v11M7 10l5 5 5-5" />
            <path d="M5 19h14" />
          </svg>
          {{ plugin.useCount }}
        </span>
      </div>
    </footer>

    <div class="pcard__ops">
      <button
        type="button"
        class="btn btn--sm"
        :class="{ 'like-on': plugin.liked }"
        :disabled="busy"
        @click.stop="emit('like', plugin)"
      >
        {{ plugin.liked ? '已点爱心' : '点爱心' }}
      </button>
      <button
        type="button"
        class="btn btn--sm"
        :class="{ 'btn--primary': !plugin.used }"
        :disabled="busy"
        @click.stop="emit('use', plugin)"
      >
        {{ plugin.used ? '已安装' : '使用' }}
      </button>
    </div>
  </article>
</template>

<style scoped>
.pcard {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  cursor: pointer;
}

.pcard__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-2);
}

.pcard__name {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  min-width: 0;
}

.pcard__title {
  font-size: 15px;
  font-weight: 700;
  line-height: 1.35;
}

.pcard__id {
  font-family: var(--font-mono);
  font-size: 11px;
  max-width: 130px;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: none;
}

.pcard__desc {
  font-size: 12.5px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.4em;
}

.pcard__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
}

.tag {
  font-size: 12px;
  color: var(--brand-text);
}

.pcard__foot {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  padding-top: var(--sp-2);
  border-top: 1px solid var(--paper-line-soft);
}

.pcard__author {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.pcard__avatar {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  object-fit: cover;
  flex: none;
}

.pcard__avatar--fallback {
  display: grid;
  place-items: center;
  background: var(--brand-soft-strong);
  color: var(--brand-text);
  font-size: 11px;
  font-weight: 700;
}

.pcard__author-name {
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pcard__stats {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex: none;
}

.stat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--ink-3);
  font-variant-numeric: tabular-nums;
}

.stat svg {
  color: var(--ink-4);
}

.stat .stat__heart--on {
  color: var(--danger);
}

.pcard__ops {
  display: flex;
  gap: 6px;
  margin-top: var(--sp-1);
}

.like-on {
  color: color-mix(in srgb, var(--danger) 78%, var(--ink));
  border-color: color-mix(in srgb, var(--danger) 40%, transparent);
  background: color-mix(in srgb, var(--danger) 9%, var(--paper-surface));
}
</style>
