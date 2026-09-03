<script setup>
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useSttStore } from '@/stores/stt'
import WaveformMeter from './WaveformMeter.vue'

/**
 * 녹음 상태 패널 — 상단 고정.
 *
 * placement
 *   'console' (기본) 콘솔 스크롤 영역 안에서 position: sticky. 문장이 아래로 흘러가도
 *                    상태 · 타임코드 · 중지 버튼은 항상 제자리에 남습니다.
 *   'global'         헤더 바로 아래 전폭 고정 바. 콘솔을 접거나 다른 화면으로 이동해도
 *                    녹음 중이라는 사실이 화면에서 사라지지 않습니다.
 *
 * 두 배치가 같은 스토어를 보므로 동시에 띄워도 상태가 어긋나지 않습니다.
 */
const props = defineProps({
  placement: { type: String, default: 'console' },
})

const stt = useSttStore()
const { status, statusLabel, micLabel, timecode, isRecording, isBusy, errorMessage, waveformBars } =
  storeToRefs(stt)

const emit = defineEmits(['request-start'])

const isGlobal = computed(() => props.placement === 'global')
const showWave = computed(() => !isGlobal.value && (isRecording.value || isBusy.value))

function onToggle() {
  // 최초 1회 고지 모달은 부모가 띄웁니다 (UC-10 최초 1회 규칙).
  if (isRecording.value) stt.stop()
  else emit('request-start')
}
</script>

<template>
  <section class="rec" :class="[`rec--${placement}`, `is-${status}`]" aria-label="녹음 상태">
    <div class="rec__row">
      <!-- 점멸하지 않는 상태 점. 점 옆에 항상 상태 문구를 함께 둡니다. -->
      <span class="rec__dot" :class="{ 'is-on': isRecording }" aria-hidden="true"></span>

      <!-- 상태 변화만 조용히 읽히도록 polite. 문장 스트림과 분리해 둡니다. -->
      <strong class="rec__status" aria-live="polite">{{ statusLabel }}</strong>
      <span class="rec__mic">{{ micLabel }}</span>

      <time class="rec__time" :datetime="timecode">{{ timecode }}</time>

      <button
        v-if="isGlobal"
        type="button"
        class="btn btn--ghost rec__action rec__action--inline"
        :class="{ 'rec__action--start': !isRecording }"
        :disabled="isBusy"
        @click="onToggle"
      >
        {{ isRecording ? 'STT 중지' : 'STT 시작' }}
      </button>
    </div>

    <WaveformMeter v-if="showWave" :bars="waveformBars" :active="isRecording" />

    <button
      v-if="!isGlobal"
      type="button"
      class="btn btn--surface rec__action rec__action--block"
      :class="{ 'rec__action--start': !isRecording }"
      :disabled="isBusy"
      @click="onToggle"
    >
      {{ isRecording ? 'STT 중지' : 'STT 시작' }}
    </button>

    <p v-if="errorMessage" class="rec__error">
      <i class="ti ti-alert-triangle" aria-hidden="true"></i>
      <span>{{ errorMessage }}</span>
    </p>
  </section>
</template>

<style scoped>
.rec {
  display: flex;
  flex-direction: column;
  gap: var(--s-4);
  background: var(--c-surface-raised);
  border: 1px solid var(--c-border);
  border-radius: var(--r-card);
  padding: 18px;
}

/* --- 콘솔 안 상단 고정 --- */
.rec--console {
  position: sticky;
  top: 0;
  z-index: 2;
  /* 스크롤된 문장이 카드 모서리 옆으로 비쳐 보이지 않도록 콘솔 배경을 덧댑니다. */
  box-shadow: 0 -12px 0 6px var(--c-bg);
}

/* --- 헤더 아래 전폭 고정 --- */
.rec--global {
  position: sticky;
  top: 0;
  z-index: 20;
  flex-direction: row;
  align-items: center;
  border-radius: 0;
  border-width: 0 0 1px;
  padding: 10px var(--s-6);
}

.rec--global .rec__row {
  width: 100%;
}

.rec__row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rec__dot {
  width: 10px;
  height: 10px;
  flex: none;
  border-radius: 50%;
  border: 2px solid var(--c-text-muted);
  background: transparent;
}

/* 켜졌을 때만 채웁니다. 점멸은 쓰지 않습니다. */
.rec__dot.is-on {
  background: var(--c-text);
  border-color: var(--c-text);
}

.rec__status {
  font-weight: 800;
  font-size: 16px;
}

.rec__mic {
  font-size: 14px;
}

.rec__time {
  margin-left: auto;
  font-weight: 500;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.rec__action--block {
  width: 100%;
  height: 44px;
}

.rec__action--inline {
  margin-left: 12px;
  height: 34px;
  padding: 0 14px;
}

/* 녹음 시작(꺼진 상태) 버튼만 포인트 컬러로 강조합니다. 중지 버튼은 중립을 유지합니다. */
.rec__action--start {
  background: var(--c-accent);
  border-color: var(--c-accent);
  color: var(--c-on-accent);
}

.rec__action--start:hover:not(:disabled) {
  background: var(--c-text-strong);
  border-color: var(--c-text-strong);
  color: var(--c-on-dark);
}

.rec__error {
  display: flex;
  gap: 8px;
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--c-text);
}

.is-error .rec__dot {
  border-style: dashed;
}
</style>
