<script setup>
import { computed } from 'vue'
import EvidenceBadge from './EvidenceBadge.vue'
import HighlightText from './HighlightText.vue'

const props = defineProps({
  query: { type: Object, required: true },
  personaLabel: { type: String, default: '' },
  collapsible: { type: Boolean, default: true },
  showLessAnalogy: { type: Boolean, default: true },
  savedLabel: { type: String, default: '이력에 저장됨' },
})

const emit = defineEmits(['regenerate', 'collapse', 'less-analogy'])

const isStreaming = computed(() => props.query.status === 'streaming')
const hasOfficial = computed(() => Boolean(props.query.official))
const hasPersonalized = computed(() => Boolean(props.query.personalized))
const isGeneral = computed(() => props.query.evidenceType === 'general_knowledge')
</script>

<template>
  <article class="card" :aria-busy="isStreaming">
    <header class="card__head">
      <div class="card__title">
        <span class="u-label">{{ isStreaming ? '생성 중' : '최신 결과' }} · {{ query.at }}</span>
        <div class="card__term">
          <h3>{{ query.term }}</h3>
        </div>
      </div>

      <div class="card__head-right">
        <EvidenceBadge :type="query.evidenceType" />
        <button
          v-if="collapsible"
          type="button"
          class="card__toggle"
          aria-label="이 결과 접기"
          @click="emit('collapse')"
        >
          <i class="ti ti-chevron-down" aria-hidden="true"></i>
        </button>
      </div>
    </header>

    <!-- 공식 정의 — 왼쪽 짙은 바 + 흰 표면. 생성 모델이 재작성하지 않는 영역입니다. -->
    <section class="section section--official" aria-label="공식 정의">
      <span class="section__rule" aria-hidden="true"></span>
      <div class="section__body">
        <h4 class="section__label">
          <i class="ti ti-book-2" aria-hidden="true"></i>
          공식 정의
        </h4>
        <p v-if="hasOfficial" class="section__text">{{ query.official }}</p>
        <div v-else class="skeleton" aria-hidden="true">
          <span></span><span></span><span class="is-short"></span>
        </div>
        <span v-if="query.evidenceType === 'glossary'" class="u-meta">사내 Glossary</span>
        <span v-else-if="query.evidenceType === 'wiki'" class="u-meta">사내 위키</span>
        <span v-else-if="isGeneral && !isStreaming" class="u-meta">
          사내 Glossary와 위키에서 근거를 찾지 못했습니다.
        </span>
      </div>
    </section>

    <!-- 개인화 설명 — 다른 표면 위에 올려 공식 정의와 즉시 구분되게 합니다. -->
    <section class="section section--personal" aria-label="나에게 맞춘 설명">
      <div class="section__label-row">
        <h4 class="section__label">
          <i class="ti ti-bulb" aria-hidden="true"></i>
          나에게 맞춘 설명
        </h4>
        <span v-if="personaLabel" class="chip">{{ personaLabel }}</span>
      </div>
      <p v-if="hasPersonalized" class="section__text section__text--lead">
        <HighlightText :text="query.personalized" strong />
      </p>
      <div v-else class="skeleton" aria-hidden="true">
        <span></span><span></span><span class="is-short"></span>
      </div>
    </section>

    <!-- 일반 지식 폴백이면 이 문구는 반드시 카드 안에 노출됩니다. -->
    <p v-if="isGeneral && !isStreaming" class="notice">
      <i class="ti ti-alert-circle" aria-hidden="true"></i>
      <span>일반 지식에 기반한 답변이며 사내 공식 기준이 아닙니다.</span>
    </p>

    <p v-if="query.error" class="notice notice--error">
      <i class="ti ti-alert-triangle" aria-hidden="true"></i>
      <span>{{ query.error }}</span>
    </p>

    <footer class="card__foot">
      <button type="button" class="btn btn--surface" @click="emit('regenerate')">
        다시 설명하기
      </button>
      <button
        v-if="showLessAnalogy"
        type="button"
        class="btn btn--ghost"
        @click="emit('less-analogy')"
      >
        비유 더 적게
      </button>
      <span v-if="!isStreaming" class="u-meta card__saved">{{ savedLabel }}</span>
    </footer>
  </article>
</template>

<style scoped>
.card {
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px 26px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-card);
}

.card__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: var(--s-4);
  border-bottom: 1px solid var(--c-border);
}

.card__title {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.card__term {
  display: flex;
  align-items: baseline;
  gap: 13px;
  flex-wrap: wrap;
}

.card__term h3 {
  margin: 0;
  font-weight: 800;
  font-size: 32px;
  line-height: 1.1;
  letter-spacing: -0.02em;
}

.card__head-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}

.card__toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--r-control);
  font-size: 16px;
  color: var(--c-text);
}

.card__toggle:hover {
  background: var(--c-surface-raised);
}

.section {
  display: flex;
  gap: var(--s-4);
}

.section__rule {
  width: 4px;
  flex: none;
  background: var(--c-text);
}

.section__body {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
}

.section--personal {
  flex-direction: column;
  gap: 8px;
  padding: 20px;
  background: var(--c-surface-raised);
  border-radius: var(--r-control);
}

.section__label-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.section__label {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0;
  font-weight: 800;
  font-size: 14px;
}

.section__text {
  margin: 0;
  font-size: 16px;
  line-height: 1.75;
  text-wrap: pretty;
  overflow-wrap: anywhere;
}

.section__text--lead {
  line-height: 1.8;
}

.chip {
  padding: 3px 7px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-badge);
  font-weight: 500;
  font-size: 14px;
  color: var(--c-text-muted);
  white-space: nowrap;
}

.notice {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0;
  padding: 12px 14px;
  background: var(--c-bg);
  border: 1px solid var(--c-border);
  border-left: 4px solid var(--c-text);
  border-radius: var(--r-control);
  font-size: 14px;
  line-height: 1.6;
}

.card__foot {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.card__saved {
  margin-left: auto;
}

/* 스켈레톤 — 생성 중 자리를 잡아 카드가 튀지 않게 합니다. */
.skeleton {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 0;
}

.skeleton span {
  height: 12px;
  border-radius: 2px;
  background: linear-gradient(
    90deg,
    var(--c-border) 0%,
    var(--c-surface-raised) 50%,
    var(--c-border) 100%
  );
  background-size: 200% 100%;
  animation: shimmer 1.4s linear infinite;
}

.skeleton span.is-short {
  width: 62%;
}

@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}
</style>
