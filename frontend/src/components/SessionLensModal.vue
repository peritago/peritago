<script setup>
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { usePersonaStore } from '@/stores/persona'
import BaseModal from './BaseModal.vue'
import DomainTag from './DomainTag.vue'

defineProps({
  sessionNo: { type: [Number, String], default: '04' },
})

const emit = defineEmits(['close', 'confirm'])

const persona = usePersonaStore()
const { userPersona, domainTagOptions } = storeToRefs(persona)

/** 기본 페르소나의 태그를 프리필합니다. 원본은 건드리지 않습니다. */
const domains = ref([...userPersona.value.domainTags])
const memo = ref(persona.sessionPersona?.memo ?? '')
const adding = ref(false)

const available = computed(() => domainTagOptions.value.filter((d) => !domains.value.includes(d)))

function remove(tag) {
  domains.value = domains.value.filter((d) => d !== tag)
}

function add(tag) {
  if (!domains.value.includes(tag)) domains.value.push(tag)
  adding.value = false
}

/** 마찰 최소화: 바꾼 게 없으면 1탭으로 닫힙니다. */
function keepPrevious() {
  persona.keepPreviousSession()
  emit('confirm')
}

function confirm() {
  if (!domains.value.length) return
  persona.applySessionPersona({ domains: domains.value, memo: memo.value })
  emit('confirm')
}
</script>

<template>
  <BaseModal
    :eyebrow="`세션 렌즈 · 트랙 ${sessionNo}`"
    title="오늘은 어떤 렌즈로 들을까요?"
    label="세션 페르소나 확인"
    :width="560"
    @close="emit('close')"
  >
    <template #lead> 이 세션에만 적용됩니다. 기본 페르소나는 그대로 유지됩니다. </template>

    <div class="block">
      <div class="block__head">
        <span class="block__label">도메인 태그</span>
        <span class="u-meta">기본 페르소나에서 가져옴</span>
      </div>

      <div class="tags">
        <DomainTag
          v-for="tag in domains"
          :key="tag"
          :label="tag"
          selected
          removable
          @remove="remove(tag)"
        />
        <button v-if="!adding" type="button" class="add" @click="adding = true">+ 태그 추가</button>
      </div>

      <div v-if="adding" class="tags tags--add">
        <DomainTag v-for="tag in available" :key="tag" :label="tag" @toggle="add(tag)" />
      </div>

      <p v-if="!domains.length" class="block__error">도메인 태그를 최소 1개 남겨 주세요.</p>
    </div>

    <div class="field">
      <label class="field__label" for="session-memo">
        이 세션 메모
        <small>(선택)</small>
      </label>
      <textarea
        id="session-memo"
        v-model="memo"
        class="input"
        rows="2"
        placeholder="예) 사내 IT 인프라 교육 — 배포·운영 용어가 많이 나옵니다"
      ></textarea>
    </div>

    <template #footer>
      <button type="button" class="btn btn--ghost" @click="keepPrevious">
        이전과 동일하게 진행
      </button>
      <button type="button" class="btn btn--primary" :disabled="!domains.length" @click="confirm">
        확인
      </button>
    </template>

    <template #note>
      태그를 바꾸지 않으면 1탭으로 닫힙니다. 세션이 끝나면 이 렌즈는 사라집니다.
    </template>
  </BaseModal>
</template>

<style scoped>
.block {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.block__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.block__label {
  font-weight: 700;
  font-size: 14px;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tags--add {
  padding: 12px;
  background: var(--c-surface);
  border: 1px dashed var(--c-border);
  border-radius: var(--r-control);
}

.add {
  height: 38px;
  padding: 0 14px;
  border: 1px dashed var(--c-border);
  border-radius: var(--r-badge);
  font-weight: 500;
  font-size: 14px;
  color: var(--c-text-muted);
}

.add:hover {
  border-color: var(--c-text);
  color: var(--c-text);
}

.block__error {
  margin: 0;
  font-weight: 600;
  font-size: 14px;
}
</style>
