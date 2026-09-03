<script setup>
import HighlightText from './HighlightText.vue'

defineProps({
  at: { type: String, default: '' },
  text: { type: String, default: '' },
  terms: { type: Array, default: () => [] },
  interim: { type: Boolean, default: false },
})
</script>

<template>
  <div class="line" :class="{ 'line--interim': interim }">
    <span v-if="interim" class="line__bar" aria-hidden="true"></span>

    <div class="line__body">
      <span class="line__at">{{ at }}</span>
      <div class="line__content">
        <p class="line__text">
          <HighlightText :text="text" :terms="terms" />
        </p>
        <span v-if="interim" class="line__state">인식 중</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.line {
  display: flex;
  background: var(--c-surface);
  border-radius: var(--r-control);
}

.line--interim {
  background: var(--c-surface-raised);
}

.line__bar {
  width: 3px;
  flex: none;
  background: var(--c-text);
}

.line__body {
  display: flex;
  gap: 11px;
  padding: 11px 13px;
}

.line__at {
  flex: none;
  padding-top: 2px;
  font-weight: 500;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  color: var(--c-text-muted);
}

.line--interim .line__at {
  color: var(--c-text);
}

.line__content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.line__text {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.line__state {
  font-weight: 500;
  font-size: 14px;
}
</style>
