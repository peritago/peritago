<script setup>
import { computed } from 'vue'

const props = defineProps({
  type: { type: String, default: null }, // glossary | wiki | general_knowledge
  size: { type: String, default: 'md' }, // md | sm
})

const VARIANTS = {
  glossary: { label: '공식 정의 기반', icon: 'ti-book-2' },
  wiki: { label: '사내 위키 기반', icon: 'ti-file-text' },
  general_knowledge: { label: '일반 지식 기반', icon: 'ti-message-circle' },
}

const variant = computed(() => VARIANTS[props.type] ?? null)
</script>

<template>
  <span v-if="variant" class="badges">
    <!-- 색상만으로 상태를 전하지 않습니다: 문구 + 아이콘 + 테두리 3중 표시. -->
    <span class="badge" :class="[`badge--${type}`, `badge--${size}`]">
      <i class="ti" :class="variant.icon" aria-hidden="true"></i>
      <span>{{ variant.label }}</span>
    </span>
  </span>
</template>

<style scoped>
.badges {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: var(--r-badge);
  border: 1px solid var(--c-border);
  white-space: nowrap;
  line-height: 1.2;
}

.badge--md {
  padding: 6px 10px;
  font-size: 14px;
}

.badge--sm {
  padding: 5px 9px;
  font-size: 14px;
}

/* 사내 공식 근거 — 가장 강한 테두리로 구분합니다. */
.badge--glossary {
  background: var(--c-surface);
  border-color: var(--c-accent);
  font-weight: 800;
}

.badge--wiki {
  background: var(--c-surface);
  font-weight: 600;
}

/* 일반 지식은 채우지 않아 '사내 근거 없음'이 시각적으로도 비어 보이게 둡니다. */
.badge--general_knowledge {
  background: transparent;
  font-weight: 500;
  color: var(--c-text-muted);
}
</style>
