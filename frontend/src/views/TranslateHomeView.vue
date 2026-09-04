<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { usePersonaStore } from '@/stores/persona'
import { useChatStore } from '@/stores/chat'
import { useSttStore } from '@/stores/stt'

import AppHeader from '@/components/AppHeader.vue'
import ChatSidebar from '@/components/ChatSidebar.vue'
import RecordingStatusPanel from '@/components/RecordingStatusPanel.vue'
import SttConsole from '@/components/SttConsole.vue'
import SttRail from '@/components/SttRail.vue'
import QueryComposer from '@/components/QueryComposer.vue'
import ResultCard from '@/components/ResultCard.vue'
import ResultRow from '@/components/ResultRow.vue'
import SessionLensModal from '@/components/SessionLensModal.vue'
import FirstRunConsentModal from '@/components/FirstRunConsentModal.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const persona = usePersonaStore()
const chat = useChatStore()
const stt = useSttStore()

const { user, initial, isAdmin } = storeToRefs(auth)
const { activeLabel, activePersona } = storeToRefs(persona)
const {
  chats,
  sessionsLoaded,
  currentChatId,
  stack,
  expandedId,
  queryCount,
  submitError,
  isStreaming,
} = storeToRefs(chat)
const { candidates, isRecording, consentGiven, sessionNumber } = storeToRefs(stt)

/** STT 콘솔 접기 — 접혀도 레일에 녹음 상태가 남습니다. */
const consoleOpen = ref(true)

/**
 * 상단 고정 상태 바를 전폭으로 띄울지 여부.
 * 콘솔이 접혀 있는데 녹음 중이면, 녹음 사실이 화면에서 사라지지 않도록 자동으로 띄웁니다.
 */
const showGlobalBar = ref(false)
const globalBarVisible = computed(
  () => showGlobalBar.value || (isRecording.value && !consoleOpen.value),
)

const modal = ref(null) // 'lens' | 'first-run'
const composer = ref(null)

const personaLabel = computed(() => activePersona.value.domains.slice(0, 2).join(' / '))

/**
 * 세션 라우팅 — 채팅을 바꿀 때마다(사이드바 선택·새 채팅) URL을 /chat/:id로 맞춰둡니다.
 * 그래야 새로고침·뒤로가기·링크 공유로 같은 채팅을 다시 열 수 있습니다. history를 채팅
 * 전환마다 쌓지 않으려고 push 대신 replace를 씁니다.
 *
 * onMounted에서도 한 번 직접 불러줘야 합니다 — 로그인 직후엔 LoginView가 이미
 * chat.createChat()으로 currentChatId를 정해둔 채로 이 컴포넌트가 마운트되는데, 그러면
 * chat.init()이 같은 값을 다시 선택해도 ref 값이 안 바뀌어 watch 콜백이 아예 안 돕니다.
 */
function syncRouteToChatId(id) {
  if (id == null) return
  if (Number(route.params.id) !== id) router.replace({ name: 'chat', params: { id } })
}

watch(currentChatId, syncRouteToChatId)

onMounted(async () => {
  const initialId = route.params.id ? Number(route.params.id) : null
  await chat.init(initialId)
  syncRouteToChatId(currentChatId.value)
  persona.loadDomainTags()
})

/** URL이 바뀌었는데(뒤로가기 등) 아직 그 채팅이 아니면 맞춰서 선택합니다. */
watch(
  () => route.params.id,
  (id) => {
    if (!sessionsLoaded.value) return // 초기 선택은 onMounted → chat.init()이 담당
    const chatId = id ? Number(id) : null
    if (chatId && chatId !== currentChatId.value && chats.value.some((c) => c.id === chatId)) {
      chat.selectChat(chatId)
    }
  },
)

async function onLogout() {
  if (stt.isRecording) stt.stop()
  chat.clearCurrentContext()
  await auth.logout()
  persona.reset()
  router.push('/login')
}

/**
 * UC-10: 자동 시작하지 않습니다. 항상 명시적 토글 → 모달 → 시작 순서입니다.
 * 단, 이 채팅에서 세션 렌즈를 이미 확인했다면(persona.sessionPersona) 다시 묻지 않고
 * 그 상태를 그대로 재사용합니다 — 확인은 채팅 진입 시(첫 녹음 시작 시) 1회면 충분합니다.
 */
function requestStart() {
  if (!consentGiven.value) {
    modal.value = 'first-run'
    return
  }
  modal.value = 'lens'
}

function onConsent() {
  stt.grantConsent()
  modal.value = 'lens'
}

function onLensConfirm() {
  modal.value = null
  if (!isRecording.value) stt.start()
}

/** 자동 감지 후보 클릭 — 여기서부터는 수동 질의와 완전히 같은 흐름입니다. */
function pickCandidate(candidate) {
  chat.submitQuery(candidate.term, { origin: 'auto-detect' })
}

function onSubmit(term) {
  chat.submitQuery(term, { origin: 'manual' })
}

/**
 * '비유 더 적게' — 개인화 설명 분량을 한 단계 줄이고 같은 용어를 다시 태웁니다.
 * 백엔드 ExplanationLength에는 '비유 비율' 필드가 없어 분량으로 대신합니다.
 */
const SHORTER = { DETAILED: 'MEDIUM', MEDIUM: 'SHORT', SHORT: 'SHORT' }

async function lessAnalogy(id) {
  const current = persona.userPersona
  await persona
    .save({ ...current, personalizedExpLength: SHORTER[current.personalizedExpLength] })
    .catch(() => {})
  chat.regenerate(id)
}

async function onNewChat() {
  await chat.createChat()
  composer.value?.focus()
}

/** 실패하면 chat.renameChat이 이미 제목을 되돌려 두므로, 여기선 콘솔에만 남깁니다. */
function onRenameChat(id, title) {
  chat.renameChat(id, title).catch((err) => console.warn('채팅 제목 변경 실패', err))
}
</script>

<template>
  <div class="home">
    <AppHeader
      :user-name="user?.name"
      :initial="initial"
      :lens-label="activeLabel"
      :lens-scope="activePersona.scope"
      :is-admin="isAdmin"
      @open-lens="modal = 'lens'"
      @open-my-glossary="router.push('/my-glossary')"
      @open-glossary="router.push('/glossary')"
      @logout="onLogout"
    />

    <!-- 헤더 아래 전폭 고정 바. 콘솔을 접어도 녹음 상태가 계속 보입니다. -->
    <RecordingStatusPanel
      v-if="globalBarVisible"
      placement="global"
      @request-start="requestStart"
    />

    <div class="home__body">
      <ChatSidebar
        :chats="chats"
        :current-id="currentChatId"
        :recording-chat-id="isRecording ? currentChatId : null"
        @create="onNewChat"
        @select="chat.selectChat"
        @rename="onRenameChat"
      />

      <main class="desk">
        <div class="desk__scroll">
          <div class="desk__head">
            <div class="desk__title">
              <h1>이 채팅의 질의</h1>
              <span class="u-meta">{{ queryCount }}건</span>
            </div>
            <router-link to="/my-glossary" class="desk__all">나의 용어집</router-link>
          </div>

          <div class="desk__stack">
            <!-- 최신 한 건만 펼쳐지고, 나머지는 접힌 행으로 쌓입니다. -->
            <template v-for="query in stack" :key="query.id">
              <ResultCard
                v-if="query.id === expandedId"
                :query="query"
                :persona-label="personaLabel"
                @collapse="chat.toggleExpanded(query.id)"
                @regenerate="chat.regenerate(query.id)"
                @less-analogy="lessAnalogy(query.id)"
              />
              <ResultRow v-else :query="query" @expand="chat.toggleExpanded(query.id)" />
            </template>

            <p v-if="!stack.length" class="desk__empty">
              용어를 입력하면 여기에 결과가 쌓입니다. 녹음을 켜면 내 도메인 밖 용어가 후보로
              떠오르고, 후보를 눌러도 같은 결과가 만들어집니다.
            </p>
          </div>
        </div>

        <QueryComposer
          ref="composer"
          :candidates="candidates"
          :error="submitError"
          :busy="isStreaming"
          @submit="onSubmit"
          @pick="pickCandidate"
        />
      </main>

      <SttRail v-if="!consoleOpen" @expand="consoleOpen = true" />
      <SttConsole
        v-else
        :session-label="`세션 ${String(sessionNumber).padStart(2, '0')}`"
        @collapse="consoleOpen = false"
        @request-start="requestStart"
      />
    </div>

    <SessionLensModal
      v-if="modal === 'lens'"
      :session-no="String(sessionNumber).padStart(2, '0')"
      @close="modal = null"
      @confirm="onLensConfirm"
    />
    <FirstRunConsentModal v-if="modal === 'first-run'" @close="modal = null" @confirm="onConsent" />
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.home__body {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: stretch;
}

.desk {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.desk__scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: var(--s-6) var(--s-7) 0;
}

.desk__head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.desk__title {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.desk__title h1 {
  margin: 0;
  font-weight: 800;
  font-size: 17px;
}

.desk__all {
  font-weight: 500;
  font-size: 14px;
  color: var(--c-text-muted);
  text-decoration: none;
}

.desk__all:hover {
  color: var(--c-text);
  text-decoration: underline;
}

.desk__stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: var(--s-5);
}

.desk__empty {
  margin: 0;
  padding: var(--s-6);
  background: var(--c-surface);
  border: 1px dashed var(--c-border);
  border-radius: var(--r-card);
  font-size: 14px;
  line-height: 1.7;
  color: var(--c-text-muted);
}

@media (max-width: 1240px) {
  .desk__scroll {
    padding-inline: var(--s-5);
  }
}
</style>
