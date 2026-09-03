<script setup>
import { storeToRefs } from 'pinia'
import { useSttStore } from '@/stores/stt'

const emit = defineEmits(['expand'])

const stt = useSttStore()
const { isRecording, statusLabel, timecode, waveformBars } = storeToRefs(stt)

/** 좁은 레일에는 막대 3개만 남깁니다 — "녹음 중"을 계속 알리는 정도면 충분합니다. */
function railBars(bars) {
  if (!bars.length) return [4, 4, 4]
  const step = Math.floor(bars.length / 3) || 1
  return [bars[step] ?? 4, bars[step * 2] ?? 4, bars[step * 3 - 1] ?? 4]
}
</script>

<template>
  <button
    type="button"
    class="rail"
    :aria-label="`STT 콘솔 펼치기. 현재 ${statusLabel}${isRecording ? `, ${timecode} 경과` : ''}`"
    @click="emit('expand')"
  >
    <i class="ti ti-chevron-left" aria-hidden="true"></i>

    <span class="rail__label" aria-hidden="true">
      <span>펼</span><span>치</span><span>기</span>
    </span>

    <span class="rail__state" aria-hidden="true">
      <span class="rail__dot" :class="{ 'is-on': isRecording }"></span>
      <span class="rail__wave">
        <i
          v-for="(value, i) in railBars(waveformBars)"
          :key="i"
          :style="{ height: `${isRecording ? Math.max(4, value * 0.5) : 4}px` }"
        ></i>
      </span>
      <span class="rail__time">{{ timecode }}</span>
    </span>
  </button>
</template>

<style scoped>
.rail {
  width: var(--w-console-rail);
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: var(--s-5) 0;
  background: var(--c-surface-raised);
  border-left: 1px solid var(--c-border);
  color: var(--c-text);
  font-size: 16px;
  transition:
    background-color var(--t-fast),
    color var(--t-fast);
}

.rail:hover {
  background: var(--c-text-strong);
  color: var(--c-on-dark);
}

.rail__label {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  font-weight: 600;
  font-size: 14px;
}

.rail__state {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.rail__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 2px solid currentColor;
}

.rail__dot.is-on {
  background: currentColor;
}

.rail__wave {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 22px;
}

.rail__wave i {
  width: 3px;
  background: var(--c-accent);
  transition: height 0.09s linear;
}

.rail:hover .rail__wave i {
  background: currentColor;
}

.rail__time {
  writing-mode: vertical-rl;
  font-weight: 500;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
}
</style>
