<script setup>
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { usePersonaStore } from '@/stores/persona'
import { useChatStore } from '@/stores/chat'

import AppHeader from '@/components/AppHeader.vue'
import ResultCard from '@/components/ResultCard.vue'
import ResultRow from '@/components/ResultRow.vue'

/**
 * S-06. 나의 용어집 — 전용 백엔드 엔터티 없이, 내가 지금까지 물어본 용어를
 * chat 스토어의 glossaryTerms(질의 이력을 용어 기준으로 묶은 것)로 그대로 보여줍니다.
 * 관리자용 공식 Glossary(/glossary, /api/glossary/admin)와는 다른 화면입니다 —
 * 저긴 회사 전체가 공유하는 사전, 여긴 "내가 몰라서 물어본 것"의 개인 기록입니다.
 */

const router = useRouter()
const auth = useAuthStore()
const persona = usePersonaStore()
const chat = useChatStore()

const { user, initial, isAdmin } = storeToRefs(auth)
const { activeLabel, activePersona } = storeToRefs(persona)
const { glossaryTerms } = storeToRefs(chat)

const loading = ref(true)
const query = ref('')
const expandedId = ref(null)

const personaLabel = computed(() => activePersona.value.domains.slice(0, 2).join(' / '))

const filtered = computed(() => {
  const q = query.value.trim()
  if (!q) return glossaryTerms.value
  return glossaryTerms.value.filter((t) => t.term.includes(q))
})

onMounted(async () => {
  await chat.init()
  loading.value = false
})

/** 카드의 '다시 설명하기' — 지금 채팅에 같은 용어로 새 질의를 태우고 그 채팅으로 돌아갑니다. */
async function askAgain(term) {
  await chat.submitQuery(term, { origin: 'manual' })
  const id = chat.currentChatId
  router.push(id ? { name: 'chat', params: { id } } : '/')
}
</script>

<template>
  <div class="myg">
    <AppHeader
      :user-name="user?.name"
      :initial="initial"
      :lens-label="activeLabel"
      :lens-scope="activePersona.scope"
      :is-admin="isAdmin"
      @open-glossary="router.push('/glossary')"
    />

    <main class="myg__body">
      <div class="myg__inner">
        <header class="myg__head">
          <div class="myg__title">
            <h1>나의 용어집</h1>
            <span class="u-meta">{{ glossaryTerms.length }}개 용어</span>
          </div>
          <input
            v-model="query"
            type="search"
            class="input myg__search"
            placeholder="용어로 찾기"
            aria-label="용어로 찾기"
          />
        </header>

        <p class="myg__lead">
          지금까지 물어본 용어를 모아뒀습니다. 같은 용어를 여러 번 물어봤다면 가장 최근 설명만
          남겨둡니다.
        </p>

        <p v-if="loading" class="myg__empty">불러오는 중…</p>
        <p v-else-if="!glossaryTerms.length" class="myg__empty">
          아직 물어본 용어가 없습니다. 홈에서 궁금한 용어를 질의하면 여기 쌓입니다.
        </p>
        <p v-else-if="!filtered.length" class="myg__empty">
          "{{ query }}"와(과) 일치하는 용어가 없습니다.
        </p>

        <div v-else class="myg__list">
          <template v-for="term in filtered" :key="term.id">
            <ResultCard
              v-if="term.id === expandedId"
              :query="term"
              :persona-label="personaLabel"
              :show-less-analogy="false"
              :saved-label="`${term.count}번 질의함`"
              @collapse="expandedId = null"
              @regenerate="askAgain(term.term)"
            />
            <ResultRow v-else :query="term" @expand="expandedId = term.id" />
          </template>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.myg {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.myg__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.myg__inner {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 760px;
  margin: 0 auto;
  padding: var(--s-6) var(--s-7);
}

.myg__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.myg__title {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.myg__title h1 {
  margin: 0;
  font-weight: 800;
  font-size: 22px;
  letter-spacing: -0.02em;
}

.myg__search {
  width: 220px;
}

.myg__lead {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--c-text-muted);
}

.myg__empty {
  margin: 0;
  padding: var(--s-6);
  background: var(--c-surface);
  border: 1px dashed var(--c-border);
  border-radius: var(--r-card);
  font-size: 14px;
  line-height: 1.7;
  color: var(--c-text-muted);
}

.myg__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: var(--s-5);
}

@media (max-width: 860px) {
  .myg__inner {
    padding: var(--s-5);
  }

  .myg__search {
    width: 100%;
  }
}
</style>
