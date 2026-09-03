<script setup>
import { onErrorCaptured, ref } from 'vue'

/** 스트림·마이크 실패가 화면 전체를 내리지 않도록 최상위에서 잡아둡니다. */
const fatal = ref('')
onErrorCaptured((err) => {
  fatal.value = err?.message ?? '알 수 없는 오류가 발생했습니다.'
  return false
})
</script>

<template>
  <div class="app">
    <p v-if="fatal" class="app__fatal" role="alert">{{ fatal }} — 새로고침하면 복구됩니다.</p>
    <router-view />
  </div>
</template>

<style scoped>
.app {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.app__fatal {
  margin: 0;
  padding: 10px var(--s-5);
  background: var(--c-surface-raised);
  border-bottom: 1px solid var(--c-text);
  font-size: 14px;
}
</style>
