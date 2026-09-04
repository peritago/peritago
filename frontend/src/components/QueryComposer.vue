<script setup>
import { computed, ref } from 'vue'
import CandidateChips from './CandidateChips.vue'

const props = defineProps({
  candidates: { type: Array, default: () => [] },
  error: { type: String, default: '' },
  busy: { type: Boolean, default: false },
  maxLength: { type: Number, default: 100 },
})

const emit = defineEmits(['submit', 'pick'])

const term = ref('')
const input = ref(null)

const canSubmit = computed(() => term.value.trim().length > 0 && !props.busy)

function submit() {
  if (!canSubmit.value) return
  emit('submit', term.value.trim())
  term.value = ''
}

/**
 * 한글 등 조합형 입력 중에 Enter를 누르면(마지막 글자를 조합 확정하는 Enter) keydown이
 * 그 글자가 term에 반영되기 전에 먼저 도착해서, 그대로 제출하면 마지막 글자가 잘립니다.
 * event.isComposing이면 IME 확정용 Enter이니 제출하지 않고 넘깁니다.
 */
function onEnterKeydown(event) {
  if (event.isComposing) return
  event.preventDefault()
  submit()
}

function focus() {
  input.value?.focus()
}

defineExpose({ focus })
</script>

<template>
  <div class="composer">
    <div class="composer__top">
      <label class="composer__label" for="term-input">
        궁금한 용어
        <span aria-hidden="true">*</span>
      </label>

      <span class="composer__divider" aria-hidden="true"></span>

      <template v-if="candidates.length">
        <span class="u-label composer__candidates-label">낯선 용어 후보</span>
        <CandidateChips :candidates="candidates" @pick="$emit('pick', $event)" />
        <span class="u-note composer__hint">클릭하면 해석이 시작됩니다</span>
      </template>
      <span v-else class="u-note composer__hint composer__hint--empty">
        녹음을 켜면 내 도메인 밖 용어가 여기에 후보로 뜹니다
      </span>
    </div>

    <div class="composer__row">
      <div class="composer__field">
        <input
          id="term-input"
          ref="input"
          v-model="term"
          type="text"
          class="composer__input"
          :maxlength="maxLength"
          placeholder="용어 또는 짧은 표현을 입력하세요"
          autocomplete="off"
          :aria-describedby="error ? 'term-error' : 'term-help'"
          :aria-invalid="Boolean(error)"
          @keydown.enter="onEnterKeydown"
        />
        <span class="composer__count">{{ term.length }} / {{ maxLength }}</span>
      </div>

      <button
        type="button"
        class="btn btn--primary composer__submit"
        :disabled="!canSubmit"
        @click="submit"
      >
        {{ busy ? '해석 중…' : '해석하기' }}
      </button>
    </div>

    <p v-if="error" id="term-error" class="composer__error">{{ error }}</p>
    <p v-else id="term-help" class="u-note">
      공식 정의는 용어 원래 도메인 기준, 개인화 설명만 내 페르소나로 재구성합니다. · Enter로 실행
    </p>
  </div>
</template>

<style scoped>
.composer {
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding: var(--s-4) var(--s-7) var(--s-6);
  border-top: 1px solid var(--c-border);
  background: var(--c-bg);
}

.composer__top {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 34px;
}

.composer__label {
  display: flex;
  align-items: baseline;
  gap: 4px;
  flex: none;
  font-weight: 700;
  font-size: 14px;
}

.composer__divider {
  width: 1px;
  height: 16px;
  flex: none;
  background: var(--c-border);
}

.composer__candidates-label {
  flex: none;
}

.composer__hint {
  flex: none;
}

.composer__hint--empty {
  flex: 1;
}

.composer__row {
  display: flex;
  gap: 12px;
  align-items: stretch;
}

/* 시안의 3px 외곽선은 포커스 링입니다 — 실제 포커스에 반응하게 구현했습니다. */
.composer__field {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  height: 50px;
  padding: 0 18px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-control);
  transition:
    box-shadow var(--t-fast),
    border-color var(--t-fast);
}

.composer__field:focus-within {
  border-color: var(--c-accent);
  box-shadow: 0 0 0 3px var(--c-accent);
}

.composer__input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: none;
  background: none;
  font-size: 18px;
}

.composer__input:focus {
  outline: none;
}

.composer__input::placeholder {
  color: var(--c-text-muted);
}

.composer__count {
  flex: none;
  font-weight: 500;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  color: var(--c-text-muted);
}

.composer__submit {
  width: 150px;
  height: 50px;
  flex: none;
  font-size: 16px;
}

.composer__error {
  margin: 0;
  font-weight: 600;
  font-size: 14px;
}
</style>
