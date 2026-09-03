import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { streamTranslation, SOURCE_TYPE_TO_EVIDENCE } from '@/api/translate'
import { api, ApiError } from '@/api/http'
import { usePersonaStore } from './persona'
import { useSttStore } from './stt'

/**
 * UC-16(새 채팅) · UC-04(수동 질의) · UC-08(결과 카드) · UC-03(이력).
 * 질의 스택은 최신이 위로 오고, 최신 한 건만 펼쳐집니다.
 *
 * 채팅 = 서버의 세션(POST/GET /api/sessions). 세션 하나 안의 질의 목록은
 * GET /api/translate/history를 세션별로 묶어 복원합니다 — 세션 단위 조회
 * 엔드포인트가 따로 없어, 전체 이력을 한 번 받아 클라이언트에서 그룹핑합니다.
 */
export const useChatStore = defineStore('chat', () => {
  const persona = usePersonaStore()
  const stt = useSttStore()

  const chats = ref([])
  const sessionsLoaded = ref(false)
  const historyBySession = ref(new Map())

  const currentChatId = ref(null)
  const queries = ref([])
  const expandedId = ref(null)
  const submitError = ref('')

  let controller = null
  let seq = 0

  const currentChat = computed(() => chats.value.find((c) => c.id === currentChatId.value))
  const queryCount = computed(() => queries.value.length)
  /** 최신이 위로 (질의 스택 역순). */
  const stack = computed(() => [...queries.value].reverse())
  const isStreaming = computed(() => queries.value.some((q) => q.status === 'streaming'))

  /** 홈 진입 시 1회 — 세션 목록과 이력을 불러오고, 없으면 새 채팅을 만듭니다. */
  async function init() {
    if (sessionsLoaded.value) return

    const history = await api.get('/api/translate/history?page=0&size=200').catch(() => null)
    historyBySession.value = groupBySession(history?.content ?? [])

    const sessions = await api.get('/api/sessions').catch(() => [])
    chats.value = (sessions ?? []).map((session, index) => toChatSummary(session, index))
    sessionsLoaded.value = true

    if (chats.value.length) selectChat(chats.value[0].id)
    else await createChat()
  }

  function toChatSummary(session, index) {
    return {
      id: session.id,
      no: String(index + 1).padStart(2, '0'),
      when: formatWhen(session.updatedAt ?? session.createdAt),
      title: session.title ?? '새 채팅',
      count: historyBySession.value.get(session.id)?.length ?? 0,
      recording: false,
    }
  }

  /** UC-01 → UC-16: 로그인하면 사용자 개입 없이 새 채팅이 생깁니다. */
  async function createChat() {
    const { session } = await api.post('/api/sessions')
    chats.value.unshift({
      id: session.id,
      no: String(chats.value.length + 1).padStart(2, '0'),
      when: formatWhen(session.createdAt),
      title: session.title ?? '새 채팅',
      count: 0,
      recording: false,
    })
    currentChatId.value = session.id
    queries.value = []
    expandedId.value = null
    persona.clearSessionPersona()
    stt.setSessionId(session.id)
    stt.resetWindow()
    return session.id
  }

  /** 세션 전환 — 그 세션의 과거 질의를 이력에서 복원합니다. */
  function selectChat(id) {
    currentChatId.value = id
    queries.value = (historyBySession.value.get(id) ?? []).map(fromHistoryItem)
    expandedId.value = queries.value.at(-1)?.id ?? null
    stt.setSessionId(id)
    stt.resetWindow()
  }

  /**
   * UC-04. 진입점은 두 가지 — 직접 입력, 또는 자동 감지 후보 클릭.
   * 어느 쪽이든 이 지점부터 흐름은 완전히 같습니다.
   */
  async function submitQuery(rawTerm, { origin = 'manual' } = {}) {
    const term = String(rawTerm ?? '').trim()
    submitError.value = ''

    if (!term) {
      submitError.value = '용어를 입력해 주세요.'
      return null
    }
    if (term.length > 100) {
      submitError.value = '100자 이내로 입력해 주세요.'
      return null
    }
    if (!currentChatId.value) {
      submitError.value = '채팅을 먼저 시작해 주세요.'
      return null
    }

    controller?.abort()
    controller = new AbortController()

    seq -= 1
    queries.value.push({
      id: seq, // 서버 queryId를 받기 전까지 쓰는 임시 id (음수라 절대 충돌하지 않음)
      term,
      origin,
      evidenceType: null,
      outsideCompanyStandard: false,
      official: '',
      personalized: '',
      at: nowLabel(),
      status: 'streaming',
      error: '',
    })

    /*
     * 반드시 배열에서 다시 꺼내 씁니다.
     * push한 원본 객체를 그대로 들고 있으면 프록시를 우회해서 델타를 써도
     * 화면이 갱신되지 않습니다 — 스트리밍이 통째로 안 보이는 원인이 됩니다.
     */
    const query = queries.value[queries.value.length - 1]
    expandedId.value = query.id
    const isFirstInChat = queries.value.length === 1
    if (currentChat.value) currentChat.value.count = queries.value.length

    try {
      await streamTranslation(
        { sessionId: currentChatId.value, term },
        {
          onEvidence: (data) => Object.assign(query, pickEvidence(data)),
          onOfficial: ({ delta }) => {
            query.official += delta
          },
          onPersonalized: ({ delta }) => {
            query.personalized += delta
          },
          onDone: ({ queryId }) => {
            query.status = 'done'
            if (expandedId.value === query.id) expandedId.value = queryId
            query.id = queryId
          },
        },
        { signal: controller.signal },
      )
      // 서버가 세션의 첫 질의 용어로 제목을 자동 채우므로 사이드바도 같이 맞춥니다.
      if (isFirstInChat && currentChat.value) currentChat.value.title = term
    } catch (err) {
      if (err.name === 'AbortError') return query
      query.status = 'error'
      query.error =
        err instanceof ApiError
          ? err.message
          : '생성이 중단되었습니다. 다시 설명하기를 눌러 재시도할 수 있습니다.'
    }

    return query
  }

  /** '다시 설명하기' — 같은 용어를 같은 흐름으로 다시 태웁니다. */
  function regenerate(id) {
    const query = queries.value.find((q) => q.id === id)
    if (!query) return
    queries.value = queries.value.filter((q) => q.id !== id)
    return submitQuery(query.term, { origin: query.origin })
  }

  function toggleExpanded(id) {
    expandedId.value = expandedId.value === id ? null : id
  }

  function cancel() {
    controller?.abort()
    controller = null
  }

  return {
    chats,
    currentChatId,
    currentChat,
    queries,
    stack,
    queryCount,
    expandedId,
    submitError,
    isStreaming,
    init,
    createChat,
    selectChat,
    submitQuery,
    regenerate,
    toggleExpanded,
    cancel,
  }
})

function pickEvidence(data) {
  return {
    evidenceType: data.evidenceType,
    outsideCompanyStandard: Boolean(data.outsideCompanyStandard),
  }
}

function fromHistoryItem(item) {
  return {
    id: item.queryId,
    term: item.term,
    origin: 'manual',
    evidenceType: SOURCE_TYPE_TO_EVIDENCE[item.sourceType] ?? 'general_knowledge',
    outsideCompanyStandard: Boolean(item.outsideCompanyStandard),
    official: item.officialDefinition ?? '',
    personalized: item.personalizedExplanation ?? '',
    at: formatAt(item.createdAt),
    status: 'done',
    error: '',
  }
}

/** GET /api/translate/history는 세션별이 아니라 전체 목록이라 sessionId로 직접 묶습니다. */
function groupBySession(list) {
  const map = new Map()
  for (const item of list) {
    if (!map.has(item.sessionId)) map.set(item.sessionId, [])
    map.get(item.sessionId).push(item)
  }
  for (const items of map.values()) items.sort((a, b) => a.queryId - b.queryId)
  return map
}

function nowLabel() {
  return formatAt(new Date().toISOString())
}

function formatAt(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function formatWhen(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  const now = new Date()
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  const time = formatAt(iso)

  if (d.toDateString() === now.toDateString()) return `오늘 ${time}`
  if (d.toDateString() === yesterday.toDateString()) return `어제 ${time}`
  return `${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getDate()).padStart(2, '0')} ${time}`
}
