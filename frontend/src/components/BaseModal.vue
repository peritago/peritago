<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  label: { type: String, default: '' },
  eyebrow: { type: String, default: '' },
  title: { type: String, default: '' },
  closable: { type: Boolean, default: true },
  width: { type: Number, default: 520 },
})

const emit = defineEmits(['close'])

const dialog = ref(null)
const heading = ref(null)
let previouslyFocused = null

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'

onMounted(async () => {
  previouslyFocused = document.activeElement
  document.body.style.overflow = 'hidden'
  await nextTick()
  // 열리면 제목으로 포커스를 옮깁니다 (가이드 7장).
  heading.value?.focus()
})

onBeforeUnmount(() => {
  document.body.style.overflow = ''
  // 닫히면 모달을 연 버튼으로 포커스를 되돌립니다.
  previouslyFocused?.focus?.()
})

function onKeydown(event) {
  if (event.key === 'Escape' && props.closable) {
    emit('close')
    return
  }
  if (event.key !== 'Tab') return

  const items = [...(dialog.value?.querySelectorAll(FOCUSABLE) ?? [])]
  if (!items.length) return
  const first = items[0]
  const last = items[items.length - 1]

  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
</script>

<template>
  <div class="overlay" @keydown="onKeydown">
    <div
      ref="dialog"
      class="dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="label || title"
      :style="{ maxWidth: `${width}px` }"
    >
      <header class="dialog__head">
        <div class="dialog__head-row">
          <span v-if="eyebrow" class="u-label">{{ eyebrow }}</span>
          <button
            v-if="closable"
            type="button"
            class="dialog__close"
            aria-label="닫기"
            @click="emit('close')"
          >
            <i class="ti ti-x" aria-hidden="true"></i>
          </button>
        </div>
        <h2 ref="heading" class="dialog__title" tabindex="-1">{{ title }}</h2>
        <p v-if="$slots.lead" class="dialog__lead"><slot name="lead" /></p>
      </header>

      <div class="dialog__body">
        <slot />
      </div>

      <footer v-if="$slots.footer" class="dialog__foot">
        <slot name="footer" />
      </footer>

      <p v-if="$slots.note" class="u-note dialog__note"><slot name="note" /></p>
    </div>
  </div>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--s-5);
  background: rgba(20, 20, 17, 0.5);
}

.dialog {
  width: 100%;
  max-height: 100%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: var(--s-6) 28px;
  background: var(--c-bg);
  border: 1px solid var(--c-border);
  border-radius: var(--r-card);
}

.dialog__head {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dialog__head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.dialog__close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  margin: -4px -6px -4px auto;
  border-radius: var(--r-control);
  font-size: 16px;
  color: var(--c-text-muted);
}

.dialog__close:hover {
  background: var(--c-surface-raised);
  color: var(--c-text);
}

.dialog__title {
  margin: 0;
  font-weight: 800;
  font-size: 26px;
  line-height: 1.2;
  letter-spacing: -0.03em;
}

.dialog__title:focus {
  outline: none;
}

.dialog__lead {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--c-text-muted);
}

.dialog__body {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.dialog__foot {
  display: flex;
  gap: 10px;
}

.dialog__note {
  margin: 0;
}
</style>
