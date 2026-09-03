<script setup>
import { computed } from 'vue'
import EvidenceBadge from './EvidenceBadge.vue'

const props = defineProps({
  query: { type: Object, required: true },
})

defineEmits(['expand'])

const preview = computed(() => {
  const text = props.query.official || props.query.personalized || '생성 중…'
  return text.length > 26 ? `${text.slice(0, 26)}…` : text
})
</script>

<template>
  <button
    type="button"
    class="row"
    :aria-label="`${query.term} 결과 펼치기`"
    @click="$emit('expand')"
  >
    <span class="row__main">
      <span class="row__term">{{ query.term }}</span>
      <span class="row__meta">{{ query.domain || '분류 없음' }} · {{ query.at }}</span>
      <span class="row__preview">{{ preview }}</span>
    </span>
    <span class="row__right">
      <EvidenceBadge :type="query.evidenceType" :cached="query.cached" size="sm" />
      <i class="ti ti-chevron-right" aria-hidden="true"></i>
    </span>
  </button>
</template>

<style scoped>
.row {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  padding: 16px 22px;
  text-align: left;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-card);
  color: var(--c-text);
  transition:
    background-color var(--t-fast),
    border-color var(--t-fast);
}

.row:hover {
  background: var(--c-surface-raised);
  border-color: var(--c-text);
}

.row__main {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
  flex-wrap: wrap;
}

.row__term {
  font-weight: 700;
  font-size: 18px;
}

.row__meta {
  font-weight: 500;
  font-size: 14px;
  color: var(--c-text-muted);
}

.row__preview {
  font-size: 14px;
  color: var(--c-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row__right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
  font-size: 16px;
}
</style>
