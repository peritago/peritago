<script setup>
defineProps({
  userName: { type: String, default: '' },
  initial: { type: String, default: '' },
  lensLabel: { type: String, default: '' },
  lensScope: { type: String, default: 'user' },
})

defineEmits(['open-lens', 'open-history', 'open-glossary', 'logout'])
</script>

<template>
  <header class="head">
    <router-link to="/" class="head__brand">
      <img src="/assets/peritago-parrot.png" alt="" width="32" height="32" />
      <span class="head__name">PERITAGO</span>
      <span class="head__rule" aria-hidden="true"></span>
      <span class="head__tagline">도메인 용어 &amp; 은어 실시간 번역기</span>
    </router-link>

    <div class="head__right">
      <!-- 지금 어떤 렌즈로 듣고 있는지는 항상 헤더에서 읽힙니다 (UC-16). -->
      <button
        type="button"
        class="lens"
        :aria-label="`세션 렌즈 ${lensLabel} 바꾸기`"
        @click="$emit('open-lens')"
      >
        <span class="lens__label">세션 렌즈</span>
        <span class="lens__value">{{ lensLabel }}</span>
        <span v-if="lensScope === 'user'" class="lens__scope">기본</span>
        <i class="ti ti-adjustments-horizontal" aria-hidden="true"></i>
      </button>

      <button type="button" class="head__link" @click="$emit('open-history')">질의 이력</button>
      <button type="button" class="head__link" @click="$emit('open-glossary')">용어집</button>

      <div class="head__user">
        <span class="head__avatar" aria-hidden="true">{{ initial }}</span>
        <span class="head__username">{{ userName }}</span>
        <button type="button" class="head__logout" aria-label="로그아웃" @click="$emit('logout')">
          <i class="ti ti-logout" aria-hidden="true"></i>
        </button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.head {
  height: var(--h-header);
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 0 var(--s-5);
  border-bottom: 1px solid var(--c-border);
  background: var(--c-bg);
}

.head__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  color: inherit;
  text-decoration: none;
}

.head__brand img {
  display: block;
  object-fit: contain;
  flex: none;
}

.head__name {
  font-weight: 800;
  font-size: 17px;
}

.head__rule {
  width: 1px;
  height: 18px;
  background: var(--c-border);
}

.head__tagline {
  font-size: 14px;
  color: var(--c-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.head__right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
}

.lens {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 12px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-control);
  color: var(--c-text);
  transition:
    background-color var(--t-fast),
    border-color var(--t-fast);
}

.lens:hover {
  background: var(--c-surface-raised);
  border-color: var(--c-text);
}

.lens__label {
  font-weight: 600;
  font-size: 14px;
}

.lens__value {
  font-weight: 500;
  font-size: 14px;
}

.lens__scope {
  padding: 2px 6px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-badge);
  font-size: 14px;
  color: var(--c-text-muted);
}

.head__link {
  display: flex;
  align-items: center;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-control);
  font-weight: 500;
  font-size: 14px;
  color: var(--c-text);
  transition:
    background-color var(--t-fast),
    border-color var(--t-fast);
}

.head__link:hover {
  background: var(--c-surface-raised);
  border-color: var(--c-text);
}

.head__user {
  display: flex;
  align-items: center;
  gap: 9px;
  padding-left: 6px;
}

.head__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  font-weight: 700;
  font-size: 14px;
}

.head__username {
  font-weight: 500;
  font-size: 14px;
}

.head__logout {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--r-control);
  font-size: 16px;
  color: var(--c-text-muted);
  transition:
    background-color var(--t-fast),
    color var(--t-fast);
}

.head__logout:hover {
  background: var(--c-surface-raised);
  color: var(--c-text);
}

@media (max-width: 1100px) {
  .head__tagline,
  .head__username {
    display: none;
  }
}
</style>
