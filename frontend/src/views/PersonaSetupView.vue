<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { usePersonaStore, LENGTH_OPTIONS } from '@/stores/persona'
import DomainTag from '@/components/DomainTag.vue'

const router = useRouter()
const persona = usePersonaStore()
const { domainTagOptions, userPersona, busy } = storeToRefs(persona)

const MAX_DESCRIPTION = 300

const domains = ref([])
const description = ref('')
const officialDefLength = ref('MEDIUM')
const personalizedExpLength = ref('MEDIUM')
const error = ref('')

const canSave = computed(() => domains.value.length > 0 && !busy.value)

onMounted(async () => {
  await persona.loadDomainTags()
  try {
    await persona.load()
  } catch {
    // 페르소나가 없으면 빈 폼으로 시작합니다.
  }
  domains.value = [...userPersona.value.domainTags]
  description.value = userPersona.value.personaDescription
  officialDefLength.value = userPersona.value.officialDefLength
  personalizedExpLength.value = userPersona.value.personalizedExpLength
})

function toggle(tag) {
  domains.value = domains.value.includes(tag)
    ? domains.value.filter((d) => d !== tag)
    : [...domains.value, tag]
}

async function save() {
  if (!canSave.value) return
  error.value = ''
  try {
    await persona.save({
      domainTags: domains.value,
      personaDescription: description.value,
      officialDefLength: officialDefLength.value,
      personalizedExpLength: personalizedExpLength.value,
    })
    router.push('/')
  } catch (err) {
    error.value = err?.message ?? '저장하지 못했습니다.'
  }
}
</script>

<template>
  <div class="persona">
    <div class="persona__inner">
      <header class="persona__head">
        <span class="u-label">2 / 2 단계</span>
        <h1>어떤 렌즈로<br />들으시나요?</h1>
        <p class="persona__lead">
          여기서 고른 도메인이 ‘낯섦’의 기준이 됩니다. 내 도메인 밖의 용어만 후보로 띄우고, 설명은
          내 도메인 비유로 재구성합니다.
        </p>
        <p class="persona__example">
          예) 기획을 고르고 개발 회의를 들으면 <mark class="u-highlight">컨테이너</mark>는 후보로
          뜨지만, <strong>벨로시티</strong>는 뜨지 않습니다.
        </p>
      </header>

      <section class="block">
        <div class="block__head">
          <h2 class="block__title">내 도메인</h2>
          <span class="u-meta">최소 1개 · {{ domains.length }}개 선택됨</span>
        </div>
        <div class="tags">
          <DomainTag
            v-for="tag in domainTagOptions"
            :key="tag"
            :label="tag"
            :selected="domains.includes(tag)"
            @toggle="toggle(tag)"
          />
        </div>
        <p v-if="!domains.length" class="block__error">도메인 태그를 최소 1개 선택해 주세요.</p>
      </section>

      <section class="block">
        <div class="field">
          <label class="field__label" for="description">
            업무 배경
            <small>(선택)</small>
          </label>
          <textarea
            id="description"
            v-model="description"
            class="input"
            rows="3"
            :maxlength="MAX_DESCRIPTION"
            placeholder="예) 신규 서비스 기획을 맡고 있고, 최근 인프라 교육을 듣고 있습니다."
          ></textarea>
          <div class="field__foot">
            <span>개인화 설명의 비유를 고를 때 참고합니다.</span>
            <span>{{ description.length }} / {{ MAX_DESCRIPTION }}</span>
          </div>
        </div>
      </section>

      <!-- 두 분량은 백엔드 ExplanationLength(SHORT/MEDIUM/DETAILED)와 1:1로 대응합니다. -->
      <section class="block block--split">
        <div class="block__col">
          <h2 class="block__title">공식 정의 분량</h2>
          <div class="segmented" role="radiogroup" aria-label="공식 정의 분량">
            <button
              v-for="option in LENGTH_OPTIONS"
              :key="option.value"
              type="button"
              role="radio"
              :aria-checked="officialDefLength === option.value"
              class="segmented__item"
              :class="{ 'segmented__item--on': officialDefLength === option.value }"
              @click="officialDefLength = option.value"
            >
              {{ option.label }}
            </button>
          </div>
          <span class="u-note">사내 근거를 얼마나 자세히 보여줄지 정합니다.</span>
        </div>

        <div class="block__col">
          <h2 class="block__title">개인화 설명 분량</h2>
          <div class="segmented" role="radiogroup" aria-label="개인화 설명 분량">
            <button
              v-for="option in LENGTH_OPTIONS"
              :key="option.value"
              type="button"
              role="radio"
              :aria-checked="personalizedExpLength === option.value"
              class="segmented__item"
              :class="{ 'segmented__item--on': personalizedExpLength === option.value }"
              @click="personalizedExpLength = option.value"
            >
              {{ option.label }}
            </button>
          </div>
          <span class="u-note">내 도메인 비유를 얼마나 길게 풀지 정합니다.</span>
        </div>
      </section>

      <p v-if="error" class="persona__error">{{ error }}</p>

      <footer class="persona__foot">
        <button
          type="button"
          class="btn btn--primary persona__save"
          :disabled="!canSave"
          @click="save"
        >
          {{ busy ? '저장 중…' : '저장하고 시작하기' }}
        </button>
        <span class="u-note">나중에 설정에서 변경할 수 있습니다.</span>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.persona {
  min-height: 100%;
  padding: 56px var(--s-5);
  overflow-y: auto;
}

.persona__inner {
  display: flex;
  flex-direction: column;
  gap: var(--s-6);
  max-width: 760px;
  margin: 0 auto;
}

.persona__head {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.persona__head h1 {
  margin: 0;
  font-weight: 800;
  font-size: clamp(32px, 4vw, 48px);
  line-height: 1.06;
  letter-spacing: -0.04em;
}

.persona__lead {
  margin: 0;
  max-width: 52ch;
  font-size: 16px;
  line-height: 1.7;
  color: var(--c-text-muted);
  text-wrap: pretty;
}

.persona__example {
  margin: 0;
  padding: 12px 14px;
  background: var(--c-surface);
  border-left: 4px solid var(--c-text);
  border-radius: var(--r-control);
  font-size: 14px;
  line-height: 1.7;
}

.block {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: var(--s-5);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-card);
}

.block--split {
  flex-direction: row;
  gap: var(--s-6);
}

.block__col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.block__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.block__title {
  margin: 0;
  font-weight: 800;
  font-size: 16px;
}

.block__error,
.persona__error {
  margin: 0;
  font-weight: 600;
  font-size: 14px;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.segmented {
  display: flex;
  gap: 1px;
  background: var(--c-border);
  border: 1px solid var(--c-border);
  border-radius: var(--r-control);
  overflow: hidden;
}

.segmented__item {
  flex: 1;
  height: 42px;
  background: var(--c-bg);
  font-weight: 600;
  font-size: 14px;
  color: var(--c-text-muted);
  transition:
    background-color var(--t-fast),
    color var(--t-fast);
}

.segmented__item:hover {
  background: var(--c-surface-raised);
  color: var(--c-text);
}

.segmented__item--on {
  background: var(--c-text);
  color: var(--c-on-dark);
  font-weight: 800;
}

.persona__foot {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-start;
}

.persona__save {
  height: 52px;
  padding: 0 28px;
  font-size: 16px;
}

@media (max-width: 720px) {
  .block--split {
    flex-direction: column;
  }
}
</style>
