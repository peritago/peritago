<script setup>
defineProps({
  label: { type: String, required: true },
  selected: { type: Boolean, default: false },
  removable: { type: Boolean, default: false },
})

defineEmits(['toggle', 'remove'])
</script>

<template>
  <button
    type="button"
    class="tag"
    :class="{ 'tag--on': selected }"
    :aria-pressed="removable ? undefined : String(selected)"
    :aria-label="removable ? `${label} 제거` : undefined"
    @click="removable ? $emit('remove') : $emit('toggle')"
  >
    <!-- 선택 여부를 색이 아니라 체크 표시로도 알립니다. -->
    <i v-if="selected && !removable" class="ti ti-check" aria-hidden="true"></i>
    <span>{{ label }}</span>
    <i v-if="removable" class="ti ti-x" aria-hidden="true"></i>
  </button>
</template>

<style scoped>
.tag {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 38px;
  padding: 0 14px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-badge);
  color: var(--c-text-muted);
  font-weight: 500;
  font-size: 14px;
  white-space: nowrap;
  transition:
    background-color var(--t-fast),
    color var(--t-fast),
    border-color var(--t-fast);
}

.tag:hover {
  background: var(--c-surface-raised);
  border-color: var(--c-text);
  color: var(--c-text);
}

.tag--on {
  background: var(--c-accent);
  border-color: var(--c-accent);
  color: var(--c-on-accent);
  font-weight: 700;
}

.tag--on:hover {
  background: var(--c-text-strong);
  border-color: var(--c-text-strong);
  color: var(--c-on-dark);
}
</style>
