import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { streamTranslation } from '@/api/translate'
import { usePersonaStore } from './persona'
import { useSttStore } from './stt'
import { useGlossaryStore } from './glossary'

/**
 * UC-16(새 채팅) · UC-04(수동 질의) · UC-08(결과 카드) · UC-03(이력).
 * 질의 스택은 최신이 위로 오고, 최신 한 건만 펼쳐집니다.
 */
export const useChatStore = defineStore('chat', () => {
  const persona = usePersonaStore()
  const stt = useSttStore()
  const glossary = useGlossaryStore()

  const chats = ref([
    {
      id: 4,
      no: '04',
      when: '오늘 14:22',
      title: '사내 IT 인프라 교육',
      count: 0,
      recording: false,
    },
    { id: 3, no: '03', when: '어제 10:05', title: '주간 운영 리뷰', count: 1, recording: false },
    { id: 2, no: '02', when: '08/28 16:40', title: '스프린트 플래닝', count: 2, recording: false },
  ])

  const currentChatId = ref(4)
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

  /** UC-01 → UC-16: 로그인하면 사용자 개입 없이 새 채팅이 생깁니다. */
  function createChat({ title = '새 채팅' } = {}) {
    const id = Math.max(0, ...chats.value.map((c) => c.id)) + 1
    chats.value.unshift({
      id,
      no: String(id).padStart(2, '0'),
      when: '지금',
      title,
      count: 0,
      recording: false,
    })
    currentChatId.value = id
    queries.value = []
    expandedId.value = null
    persona.clearSessionPersona()
    stt.resetWindow()
    return id
  }

  function selectChat(id) {
    currentChatId.value = id
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

    controller?.abort()
    controller = new AbortController()

    seq += 1
    queries.value.push({
      id: seq,
      term,
      origin,
      domain: '',
      evidenceType: null,
      source: null,
      cached: false,
      official: '',
      personalized: '',
      contextInterpretation: '',
      contextSource: stt.isRecording ? `발화 기준 ${stt.timecode}` : null,
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
    if (currentChat.value) currentChat.value.count = queries.value.length

    try {
      await streamTranslation(
        {
          term,
          context: stt.contextWindow,
          persona: persona.activePersona,
          chatId: currentChatId.value,
        },
        {
          onEvidence: (data) => Object.assign(query, pickEvidence(data)),
          onOfficial: ({ delta }) => {
            query.official += delta
          },
          onPersonalized: ({ delta }) => {
            query.personalized += delta
          },
          onContext: ({ delta }) => {
            query.contextInterpretation += delta
          },
          onDone: () => {
            query.status = 'done'
          },
          onError: ({ message }) => {
            query.status = 'error'
            query.error = message
          },
        },
        { signal: controller.signal, lookup: glossary.match },
      )
      if (query.status === 'streaming') query.status = 'done'
    } catch (err) {
      if (err.name === 'AbortError') return query
      query.status = 'error'
      // 예외 흐름: 확보된 근거만 우선 노출하고 재생성 버튼을 제공합니다.
      query.error = '생성이 중단되었습니다. 다시 설명하기를 눌러 재시도할 수 있습니다.'
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
    domain: data.domain ?? '',
    source: data.source ?? null,
    cached: Boolean(data.cached),
  }
}

function nowLabel() {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
