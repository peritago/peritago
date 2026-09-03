<script setup>
defineProps({
  candidates: { type: Array, default: () => [] },
})

defineEmits(['pick'])
</script>

<template>
  <!--
    후보는 장식이 아니라 버튼입니다. 클릭하기 전에는 어떤 결과도 생성되지 않습니다.
    가장 최근에 감지된 하나만 진하게 두어 "방금 나온 말"이라는 정보를 담습니다.
  -->
  <div class="chips">
    <button
      v-for="(candidate, index) in candidates"
      :key="candidate.term"
      type="button"
      class="chip"
      :class="{ 'chip--fresh': index === 0 }"
      :aria-label="`${candidate.term} 후보를 해석하기`"
      @click="$emit('pick', candidate)"
    >
      <span class="chip__term">{{ candidate.term }}</span>
    </button>
  </div>
</template>

<style scoped>
.chips {
  display: flex;
  gap: 8px;
  flex: 1;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: thin;
}

.chip {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 34px;
  flex: none;
  padding: 0 13px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-badge);
  color: var(--c-text);
  white-space: nowrap;
  transition:
    background-color var(--t-fast),
    color var(--t-fast),
    border-color var(--t-fast);
}

.chip:hover {
  background: var(--c-surface-raised);
  border-color: var(--c-text);
}

.chip--fresh {
  background: var(--c-text);
  border-color: var(--c-text);
  color: var(--c-on-dark);
}

.chip--fresh:hover {
  background: var(--c-text-strong);
  border-color: var(--c-text-strong);
  color: var(--c-on-dark);
}

.chip__term {
  font-weight: 700;
  font-size: 14px;
}

.chip--fresh .chip__term {
  font-weight: 800;
}
</style>
