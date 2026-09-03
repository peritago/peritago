<script setup>
import { computed } from 'vue'

const props = defineProps({
  bars: { type: Array, default: () => [] },
  height: { type: Number, default: 44 },
  active: { type: Boolean, default: false },
})

/** 높이에 따라 3단계 회색 — 깜빡임 없이 진폭만 읽히게 합니다. */
function tone(value) {
  const ratio = value / props.height
  if (ratio > 0.62) return 'is-high'
  if (ratio > 0.3) return 'is-mid'
  return 'is-low'
}

const displayBars = computed(() =>
  props.bars.length ? props.bars : Array.from({ length: 30 }, () => 8),
)
</script>

<template>
  <!--
    파형은 보조 신호입니다. 스크린리더에는 상태 텍스트만 읽히면 되므로 숨깁니다.
    (가이드 5장: 파형이 보이지 않아도 상태 텍스트만으로 이해되어야 함)
  -->
  <div
    class="wave"
    :style="{ height: `${height}px` }"
    :class="{ 'is-idle': !active }"
    aria-hidden="true"
  >
    <div
      v-for="(value, i) in displayBars"
      :key="i"
      class="wave__bar"
      :class="tone(value)"
      :style="{ height: `${active ? value : 4}px` }"
    ></div>
  </div>
</template>

<style scoped>
.wave {
  display: flex;
  align-items: center;
  gap: 3px;
  overflow: hidden;
}

.wave__bar {
  width: 3px;
  flex: none;
  background: var(--c-wave-mid);
  transition:
    height 0.09s linear,
    background-color 0.18s ease;
}

.wave__bar.is-low {
  background: var(--c-wave-low);
}
.wave__bar.is-mid {
  background: var(--c-wave-mid);
}
.wave__bar.is-high {
  background: var(--c-wave-high);
}

.wave.is-idle .wave__bar {
  background: var(--c-border);
}
</style>
