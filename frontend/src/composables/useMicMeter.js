import { ref, onUnmounted, getCurrentInstance } from 'vue'

/**
 * 마이크 입력을 파형 막대 높이 배열로 바꿔줍니다.
 *
 * 가이드 5장: 파형은 장식이 아니라 "지금 인식이 진행 중"이라는 보조 신호입니다.
 * 그래서 파형이 없어도 상태 텍스트만으로 이해되게 두고, 여기서는 값만 만듭니다.
 * prefers-reduced-motion이면 rAF를 돌리지 않고 정지된 막대를 유지합니다.
 */
export function useMicMeter({ barCount = 30, min = 6, max = 44 } = {}) {
  const bars = ref(Array.from({ length: barCount }, () => min))
  const level = ref(0)

  let ctx = null
  let analyser = null
  let sourceNode = null
  let raf = null
  let buffer = null

  const reduceMotion =
    typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

  function attach(stream) {
    detach()
    const AudioCtx = window.AudioContext || window.webkitAudioContext
    if (!AudioCtx || !stream) return

    ctx = new AudioCtx()
    analyser = ctx.createAnalyser()
    analyser.fftSize = 256
    analyser.smoothingTimeConstant = 0.75
    sourceNode = ctx.createMediaStreamSource(stream)
    sourceNode.connect(analyser)
    buffer = new Uint8Array(analyser.frequencyBinCount)

    if (reduceMotion) {
      // 정지 상태에서도 "입력이 있다"는 것은 보이도록 중간 높이로 고정합니다.
      bars.value = bars.value.map(() => Math.round((min + max) / 2))
      return
    }
    tick()
  }

  function tick() {
    analyser.getByteFrequencyData(buffer)

    const step = Math.floor(buffer.length / barCount) || 1
    const next = Array.from({ length: barCount })
    let sum = 0

    for (let i = 0; i < barCount; i += 1) {
      let acc = 0
      for (let j = 0; j < step; j += 1) acc += buffer[i * step + j] ?? 0
      const value = acc / step / 255
      sum += value
      // 저역이 과하게 커지므로 살짝 눌러서 시각적으로 고르게 만듭니다.
      next[i] = Math.round(min + Math.min(1, value ** 0.7 * 1.6) * (max - min))
    }

    bars.value = next
    level.value = sum / barCount
    raf = requestAnimationFrame(tick)
  }

  function detach() {
    if (raf) cancelAnimationFrame(raf)
    raf = null
    try {
      sourceNode?.disconnect()
      ctx?.close()
    } catch {
      /* 무시 */
    }
    sourceNode = null
    analyser = null
    ctx = null
    bars.value = Array.from({ length: barCount }, () => min)
    level.value = 0
  }

  // 스토어 안에서도 쓰이므로 컴포넌트 스코프일 때만 자동 정리를 겁니다.
  if (getCurrentInstance()) onUnmounted(detach)

  return { bars, level, attach, detach }
}
