import { api } from './http'

/**
 * UC-08. POST /api/translate (실제 구현, 2026-09-03부로 backend/origin-youngmin 병합 예정).
 *
 * 서버는 SSE가 아니라 완성된 JSON을 한 번에 돌려줍니다. 점진 렌더링은
 * 그 텍스트를 클라이언트에서 어절 단위로 재생해 흉내 낸 것이라, 실제 스트리밍이
 * 없어도 같은 화면 효과를 유지할 수 있습니다.
 */
const SOURCE_TYPE_TO_EVIDENCE = {
  GLOSSARY: 'glossary',
  WIKI: 'wiki',
  GENERAL: 'general_knowledge',
}

export async function streamTranslation({ sessionId, term }, handlers, { signal } = {}) {
  const response = await api.post('/api/translate', { sessionId, term }, { signal })

  handlers.onEvidence?.({
    evidenceType: SOURCE_TYPE_TO_EVIDENCE[response.sourceType] ?? 'general_knowledge',
    sourceRef: response.sourceRef,
    outsideCompanyStandard: response.outsideCompanyStandard,
  })

  await emit(response.officialDefinition, handlers.onOfficial, signal)
  await emit(response.personalizedExplanation, handlers.onPersonalized, signal)

  handlers.onDone?.({ queryId: response.queryId })
}

async function emit(text, fn, signal) {
  if (!fn || !text) return
  // 어절 단위로 흘려보내야 렌더링이 자연스럽습니다.
  for (const token of text.match(/\S+\s*/g) ?? []) {
    await wait(26, signal)
    fn({ delta: token })
  }
}

function wait(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(new DOMException('Aborted', 'AbortError'))
    const id = setTimeout(resolve, ms)
    signal?.addEventListener('abort', () => {
      clearTimeout(id)
      reject(new DOMException('Aborted', 'AbortError'))
    })
  })
}

export { SOURCE_TYPE_TO_EVIDENCE }
