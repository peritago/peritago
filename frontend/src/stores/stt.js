import { defineStore } from 'pinia'
import { computed, ref, shallowRef } from 'vue'
import { createDefaultSource } from '@/composables/sttSources'
import { useMicMeter } from '@/composables/useMicMeter'
import { usePersonaStore } from './persona'
import { useGlossaryStore } from './glossary'

/** UC-12 슬라이딩 윈도우 크기. 확정 시안이 6문장이라 6으로 둡니다. */
const WINDOW_SIZE = 6

/**
 * 녹음 상태(UC-10) · 실시간 STT(UC-11) · 슬라이딩 윈도우(UC-12) · 자동 감지(UC-13).
 *
 * 스토어에 둔 이유: 상단 고정 상태 패널, 접힌 레일, 콘솔 본문, 그리고 질의 입력창이
 * 모두 같은 녹음 상태와 같은 윈도우를 봐야 하기 때문입니다.
 */
export const useSttStore = defineStore('stt', () => {
  const persona = usePersonaStore()
  const glossary = useGlossaryStore()
  const meter = useMicMeter()

  /** idle · requesting · recording · stopping · error */
  const status = ref('idle')
  const errorMessage = ref('')

  /** 확정된 문장들 — 최근 WINDOW_SIZE개만 유지합니다. */
  const sentences = ref([])
  /** 인식 중인 문장. 확정되기 전까지 계속 덮어써집니다. */
  const interimText = ref('')

  const elapsedMs = ref(0)
  const consentGiven = ref(false) // UC-10 최초 1회 규칙
  const sessionNumber = ref(4)

  const source = shallowRef(null)
  const mediaStream = shallowRef(null)
  let timerId = null
  let startedAt = 0
  let seq = 0

  const isRecording = computed(() => status.value === 'recording')
  const isBusy = computed(() => status.value === 'requesting' || status.value === 'stopping')

  /** 화면에 항상 텍스트로 보여야 하는 상태 문구 (UC-10 중요 규칙). */
  const statusLabel = computed(
    () =>
      ({
        idle: '녹음 꺼짐',
        requesting: '마이크 권한 요청 중',
        recording: '녹음 중',
        stopping: '정리 중',
        error: '녹음 중단됨',
      })[status.value],
  )

  const micLabel = computed(
    () =>
      ({
        idle: '마이크 꺼짐',
        requesting: '권한 확인 중',
        recording: '마이크 사용 중',
        stopping: '마이크 해제 중',
        error: '마이크 사용 불가',
      })[status.value],
  )

  const timecode = computed(() => formatTimecode(elapsedMs.value))

  /** UC-04 대안 흐름: 수동 질의에 자동으로 실려가는 맥락. */
  const contextWindow = computed(() => sentences.value.map((s) => s.text).join(' '))

  /**
   * UC-13. 감지 기준은 "Glossary에 있는가"가 아니라 "내 페르소나 도메인 밖인가"입니다.
   * 후보를 띄우기만 하고 결과는 생성하지 않습니다 — 클릭이 있어야 UC-04가 돕니다.
   */
  const candidates = computed(() =>
    glossary.detect(sentences.value, persona.activeDomains).slice(0, 6),
  )

  const candidateTerms = computed(() => candidates.value.map((c) => c.term))

  async function start({ engine } = {}) {
    if (status.value === 'recording' || status.value === 'requesting') return
    status.value = 'requesting'
    errorMessage.value = ''

    try {
      mediaStream.value = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true },
      })
    } catch (err) {
      // 예외 흐름: 권한 거부·장치 미연결 시 대체 방법을 함께 안내합니다.
      status.value = 'error'
      errorMessage.value =
        err?.name === 'NotFoundError'
          ? '마이크를 찾지 못했습니다. 장치를 연결한 뒤 다시 시도하거나, 아래 입력창으로 직접 질의하세요.'
          : '마이크 권한이 거부되었습니다. 브라우저 설정에서 허용하거나, 아래 입력창으로 직접 질의하세요.'
      return
    }

    meter.attach(mediaStream.value)
    source.value = createDefaultSource({ engine })
    source.value.start({
      stream: mediaStream.value,
      meta: { sessionDomains: persona.activeDomains },
      handlers: {
        onOpen: () => {
          status.value = 'recording'
          startTimer()
        },
        onPartial: (text) => {
          interimText.value = text
        },
        onFinal: pushSentence,
        onError: (err) => {
          errorMessage.value = err.message
          // 스트림이 죽어도 수동 질의는 살아 있어야 하므로 화면 전체를 막지 않습니다.
          if (status.value === 'recording') stop({ keepError: true })
          status.value = 'error'
        },
      },
    })

    // Web Speech는 onOpen이 늦게 오는 경우가 있어 타임아웃으로 상태를 확정합니다.
    setTimeout(() => {
      if (status.value === 'requesting' && mediaStream.value) {
        status.value = 'recording'
        startTimer()
      }
    }, 800)
  }

  function stop({ keepError = false } = {}) {
    if (status.value === 'idle') return
    status.value = 'stopping'

    source.value?.stop()
    source.value = null

    mediaStream.value?.getTracks().forEach((track) => track.stop())
    mediaStream.value = null

    meter.detach()
    stopTimer()

    // 마지막 인식 중 문장도 버리지 않고 확정 처리합니다 (UC-10 "마지막 맥락 보존").
    if (interimText.value.trim()) pushSentence({ text: interimText.value })
    interimText.value = ''

    status.value = keepError ? 'error' : 'idle'
    if (!keepError) errorMessage.value = ''
  }

  function toggle() {
    return isRecording.value ? stop() : start()
  }

  function pushSentence({ text, at = null }) {
    const clean = String(text ?? '').trim()
    if (!clean) return
    seq += 1
    sentences.value.push({
      id: seq,
      text: clean,
      at: at ?? formatTimecode(Date.now() - startedAt),
    })
    // 윈도우를 벗어난 문장은 화면에서 제거합니다.
    if (sentences.value.length > WINDOW_SIZE)
      sentences.value.splice(0, sentences.value.length - WINDOW_SIZE)
    interimText.value = ''
  }

  function startTimer() {
    if (timerId) return
    startedAt = Date.now() - elapsedMs.value
    timerId = setInterval(() => {
      elapsedMs.value = Date.now() - startedAt
    }, 1000)
  }

  function stopTimer() {
    if (timerId) clearInterval(timerId)
    timerId = null
  }

  /** STT 중지 또는 새 세션이면 윈도우를 초기화합니다 (UC-12 예외). */
  function resetWindow() {
    sentences.value = []
    interimText.value = ''
    elapsedMs.value = 0
  }

  function grantConsent() {
    consentGiven.value = true
  }

  /** 데모용 — 백엔드 없이 스트리밍 렌더링을 확인할 때 씁니다. */
  function seed(list) {
    list.forEach((item) => {
      seq += 1
      sentences.value.push({ id: seq, ...item })
    })
  }

  return {
    status,
    statusLabel,
    micLabel,
    errorMessage,
    sentences,
    interimText,
    timecode,
    elapsedMs,
    isRecording,
    isBusy,
    consentGiven,
    sessionNumber,
    contextWindow,
    candidates,
    candidateTerms,
    waveformBars: meter.bars,
    micLevel: meter.level,
    windowSize: WINDOW_SIZE,
    start,
    stop,
    toggle,
    resetWindow,
    grantConsent,
    seed,
  }
})

function formatTimecode(ms) {
  const total = Math.max(0, Math.floor(ms / 1000))
  const h = String(Math.floor(total / 3600)).padStart(2, '0')
  const m = String(Math.floor((total % 3600) / 60)).padStart(2, '0')
  const s = String(total % 60).padStart(2, '0')
  return `${h}:${m}:${s}`
}
