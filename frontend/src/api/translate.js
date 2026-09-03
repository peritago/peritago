import { authHeaders } from './http'

/**
 * UC-08. 결과 카드는 SSE로 점진 렌더링합니다.
 *
 * ⚠ 백엔드에 아직 translate 도메인이 없습니다. (GlossaryMatcher 인터페이스만 준비돼 있음)
 *    VITE_TRANSLATE_STREAM이 비어 있으면 목 스트림으로 동작하고,
 *    엔드포인트가 생기면 .env에 경로만 넣으면 바로 붙습니다.
 *
 * 서버 이벤트 계약:
 *   event: evidence      { evidenceType, term, domain, source, cached }
 *   event: official      { delta }
 *   event: personalized  { delta }
 *   event: context       { delta }
 *   event: done          { queryId }
 *   event: error         { message }
 *
 * evidence를 먼저 보내는 이유: 공식 정의와 개인화 설명을 시각적으로 분리해 그리려면
 * 카드가 어떤 근거 유형인지 먼저 알아야 하기 때문입니다.
 */

const ENDPOINT = import.meta.env?.VITE_TRANSLATE_STREAM ?? ''

export async function streamTranslation(payload, handlers, { signal, lookup } = {}) {
  if (!ENDPOINT) return mockStream(payload, handlers, { signal, lookup })

  const response = await fetch(ENDPOINT, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json', Accept: 'text/event-stream' }),
    body: JSON.stringify(payload),
    signal,
  })

  if (!response.ok || !response.body) {
    throw new Error('설명을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    const chunks = buffer.split('\n\n')
    buffer = chunks.pop() ?? ''
    for (const chunk of chunks) dispatch(chunk, handlers)
  }
}

function dispatch(chunk, handlers) {
  let event = 'message'
  const dataLines = []

  for (const line of chunk.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  }
  if (!dataLines.length) return

  let data
  try {
    data = JSON.parse(dataLines.join('\n'))
  } catch {
    return
  }

  const map = {
    evidence: handlers.onEvidence,
    official: handlers.onOfficial,
    personalized: handlers.onPersonalized,
    context: handlers.onContext,
    done: handlers.onDone,
    error: handlers.onError,
  }
  map[event]?.(data)
}

/* ------------------------------------------------------------------ *
 * 목 스트림 — translate 엔드포인트가 생기기 전까지 UI를 그대로 확인합니다.
 * ------------------------------------------------------------------ */
async function mockStream({ term, context, persona }, handlers, { signal, lookup } = {}) {
  const entry = lookup?.(term) ?? null
  const evidenceType = entry ? (entry.evidenceType ?? 'glossary') : 'general_knowledge'
  const myDomain = persona?.domains?.[0] ?? '내 도메인'

  await wait(320, signal)
  handlers.onEvidence?.({
    evidenceType,
    term: entry?.term ?? term,
    domain: entry ? [entry.domains?.[0], entry.subdomain].filter(Boolean).join(' · ') : '분류 없음',
    source: entry?.source ?? null,
    cached: false,
  })

  // 공식 정의는 원문 그대로 — 생성 모델이 재작성하지 않습니다 (UC-05 중요 규칙).
  await emit(
    entry?.definition ??
      `${term}에 대한 사내 공식 정의를 찾지 못했습니다. 아래 설명은 일반 지식에 기반합니다.`,
    handlers.onOfficial,
    signal,
  )

  await emit(
    entry
      ? `${myDomain} 일로 바꿔 보면, §여러 갈래로 흩어진 작업을 한 묶음으로 봉인해 두는 것§에 가깝습니다. 묶어 두었기 때문에 어디에 옮겨 놓아도 §같은 결과가 나온다§는 점이 핵심입니다.`
      : `${term}은(는) ${myDomain} 관점에서 보면 여러 단계를 하나로 묶어 관리하는 개념에 가깝습니다.`,
    handlers.onPersonalized,
    signal,
  )

  if (context) {
    await emit(
      `방금 발화의 “${truncate(context, 36)}”는 그 묶음을 여러 곳에 한꺼번에 적용한다는 뜻입니다.`,
      handlers.onContext,
      signal,
    )
  }

  handlers.onDone?.({ queryId: `mock_${Date.now()}` })
}

async function emit(text, fn, signal) {
  if (!fn) return
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

function truncate(value, max) {
  return value.length > max ? `${value.slice(0, max)}…` : value
}
