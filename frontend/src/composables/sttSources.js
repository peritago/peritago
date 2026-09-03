/**
 * STT 스트림 소스 어댑터.
 *
 * 두 구현이 같은 인터페이스를 따르므로 useSttStream은 어느 쪽이든 동일하게 씁니다.
 *
 *   const source = createSocketSource({ url })
 *   source.start({ stream, handlers })   // handlers: { onPartial, onFinal, onError, onOpen, onClose }
 *   source.stop()
 *
 * onPartial(text)           인식 중인 문장 — 계속 덮어써집니다
 * onFinal({ text, at })     확정된 문장 — 슬라이딩 윈도우에 push 됩니다
 *
 * UC-11 리스크 항목대로, 어느 소스가 죽어도 수동 질의(UC-04)는 독립 동작해야 하므로
 * 실패는 onError로 올려보내기만 하고 여기서 화면을 건드리지 않습니다.
 */

/* ------------------------------------------------------------------ *
 * 1) 서버 스트리밍 — MediaRecorder 청크를 WebSocket으로 계속 밀어넣습니다.
 *    (Whisper streaming / Google STT / Clova 등 서버측 엔진용 기본 경로)
 * ------------------------------------------------------------------ */
export function createSocketSource({
  url = defaultSocketUrl(),
  timesliceMs = 250,
  mimeType = 'audio/webm;codecs=opus',
} = {}) {
  let socket = null
  let recorder = null
  let handlers = {}
  let closedByUs = false

  function start({ stream, handlers: h = {}, meta = {} }) {
    handlers = h
    closedByUs = false

    socket = new WebSocket(url)
    socket.binaryType = 'arraybuffer'

    socket.addEventListener('open', () => {
      // 첫 프레임으로 세션 메타(언어, 세션 페르소나 등)를 보냅니다.
      socket.send(JSON.stringify({ type: 'start', lang: 'ko-KR', ...meta }))

      const type = MediaRecorder.isTypeSupported(mimeType) ? mimeType : ''
      recorder = new MediaRecorder(stream, type ? { mimeType: type } : undefined)
      recorder.addEventListener('dataavailable', (e) => {
        if (e.data.size > 0 && socket?.readyState === WebSocket.OPEN) socket.send(e.data)
      })
      recorder.start(timesliceMs)
      handlers.onOpen?.()
    })

    socket.addEventListener('message', (event) => {
      let msg
      try {
        msg = JSON.parse(event.data)
      } catch {
        return
      }
      if (msg.type === 'partial') handlers.onPartial?.(msg.text ?? '')
      else if (msg.type === 'final')
        handlers.onFinal?.({ text: msg.text ?? '', at: msg.at ?? null })
      else if (msg.type === 'error') handlers.onError?.(new Error(msg.message ?? 'STT 오류'))
    })

    socket.addEventListener('error', () => {
      handlers.onError?.(new Error('STT 서버에 연결하지 못했습니다.'))
    })

    socket.addEventListener('close', () => {
      if (!closedByUs) handlers.onError?.(new Error('STT 연결이 끊겼습니다.'))
      handlers.onClose?.()
    })
  }

  function stop() {
    closedByUs = true
    try {
      if (recorder && recorder.state !== 'inactive') recorder.stop()
    } catch {
      /* 무시 */
    }
    if (socket?.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: 'stop' }))
      socket.close()
    }
    recorder = null
    socket = null
  }

  return { name: 'socket', start, stop }
}

function defaultSocketUrl() {
  if (typeof window === 'undefined') return 'ws://localhost:8080/ws/stt'
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${window.location.host}/ws/stt`
}

/* ------------------------------------------------------------------ *
 * 2) Web Speech API — 명세서상 보조 액터. 서버 경로가 없을 때의 폴백입니다.
 *    interimResults로 partial을 그대로 흘려보내므로 스트리밍 동작은 동일합니다.
 * ------------------------------------------------------------------ */
export function createWebSpeechSource({ lang = 'ko-KR' } = {}) {
  let recognition = null
  let handlers = {}
  let stopping = false

  function start({ handlers: h = {} }) {
    handlers = h
    const Ctor = window.SpeechRecognition || window.webkitSpeechRecognition
    if (!Ctor) {
      handlers.onError?.(new Error('이 브라우저는 음성 인식을 지원하지 않습니다.'))
      return
    }

    stopping = false
    recognition = new Ctor()
    recognition.lang = lang
    recognition.continuous = true
    recognition.interimResults = true

    recognition.addEventListener('start', () => handlers.onOpen?.())

    recognition.addEventListener('result', (event) => {
      let interim = ''
      for (let i = event.resultIndex; i < event.results.length; i += 1) {
        const result = event.results[i]
        const text = result[0].transcript.trim()
        if (!text) continue
        if (result.isFinal) handlers.onFinal?.({ text, at: null })
        else interim += result[0].transcript
      }
      handlers.onPartial?.(interim.trim())
    })

    recognition.addEventListener('error', (event) => {
      if (event.error === 'no-speech' || event.error === 'aborted') return
      handlers.onError?.(new Error(webSpeechErrorText(event.error)))
    })

    // continuous라도 브라우저가 임의로 끊는 일이 잦아 재시작으로 스트림을 유지합니다.
    recognition.addEventListener('end', () => {
      if (stopping) {
        handlers.onClose?.()
        return
      }
      try {
        recognition.start()
      } catch {
        handlers.onClose?.()
      }
    })

    recognition.start()
  }

  function stop() {
    stopping = true
    try {
      recognition?.stop()
    } catch {
      /* 무시 */
    }
    recognition = null
  }

  return { name: 'webspeech', start, stop }
}

function webSpeechErrorText(code) {
  const map = {
    'not-allowed':
      '마이크 권한이 거부되었습니다. 브라우저 주소창의 자물쇠 아이콘에서 허용해 주세요.',
    'service-not-allowed': '브라우저가 음성 인식 서비스를 차단했습니다.',
    'audio-capture': '마이크를 찾지 못했습니다. 장치 연결을 확인해 주세요.',
    network: '네트워크 문제로 음성 인식이 중단되었습니다.',
  }
  return map[code] ?? '음성 인식 중 오류가 발생했습니다.'
}

/** '/ws/stt' 같은 상대 경로도 받을 수 있게 절대 ws URL로 바꿉니다. */
function resolveSocketUrl(value) {
  if (!value) return null
  if (/^wss?:\/\//.test(value)) return value
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${window.location.host}${value.startsWith('/') ? value : `/${value}`}`
}

/** 서버 소스를 먼저 쓰고, 없으면 Web Speech로 폴백합니다. */
export function createDefaultSource(options = {}) {
  if (options.engine === 'webspeech') return createWebSpeechSource(options)

  const url = resolveSocketUrl(options.url ?? import.meta.env?.VITE_STT_SOCKET_URL)
  if (options.engine === 'socket') return createSocketSource({ ...options, url: url ?? undefined })
  return url ? createSocketSource({ ...options, url }) : createWebSpeechSource(options)
}
