<script setup>
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { usePersonaStore } from '@/stores/persona'
import BaseModal from './BaseModal.vue'
import DomainTag from './DomainTag.vue'

/**
 * UC-10 최초 1회 규칙.
 * 권한·고지 섹션과 UC-16 렌즈 확인을 한 화면에 합칩니다.
 * 두 번째 녹음부터는 SessionLensModal만 뜹니다.
 */
const emit = defineEmits(['close', 'confirm'])

const persona = usePersonaStore()
const { userPersona, domainTagOptions } = storeToRefs(persona)

const understood = ref(false)
const domains = ref([...userPersona.value.domainTags])
const adding = ref(false)

const available = computed(() => domainTagOptions.value.filter((d) => !domains.value.includes(d)))

function remove(tag) {
  domains.value = domains.value.filter((d) => d !== tag)
}

function add(tag) {
  if (!domains.value.includes(tag)) domains.value.push(tag)
  adding.value = false
}

function confirm() {
  if (!understood.value || !domains.value.length) return
  persona.applySessionPersona({ domains: domains.value, memo: '' })
  emit('confirm')
}
</script>

<template>
  <BaseModal
    eyebrow="처음 녹음할 때 한 번만"
    title="녹음을 시작하기 전에"
    label="마이크 권한과 데이터 처리 고지"
    :width="600"
    @close="emit('close')"
  >
    <section class="block">
      <h3 class="block__title">마이크 권한과 데이터 처리 고지</h3>
      <p class="block__text">
        브라우저 마이크 권한이 필요합니다. 인식된 문장은 용어 해석을 위해 서버로 전송되며, 슬라이딩
        윈도우를 벗어난 문장은 화면에서 삭제됩니다. 녹음 원본은 저장하지 않습니다.
      </p>

      <label class="check">
        <input v-model="understood" type="checkbox" />
        <span>이해했습니다</span>
      </label>
    </section>

    <section class="block">
      <h3 class="block__title">오늘 이 자리의 렌즈</h3>
      <p class="block__text">
        여기서 고른 도메인 밖의 용어만 후보로 뜹니다. 기본 페르소나는 그대로 유지됩니다.
      </p>

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
    </section>

    <template #footer>
      <button type="button" class="btn btn--ghost" @click="emit('close')">취소</button>
      <button
        type="button"
        class="btn btn--primary"
        :disabled="!understood || !domains.length"
        @click="confirm"
      >
        허용하고 STT 시작
      </button>
    </template>

    <template #note> 두 번째 녹음부터는 이 고지 없이 렌즈 확인만 표시됩니다. </template>
  </BaseModal>
</template>

<style scoped>
.block {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-control);
}

.block__title {
  margin: 0;
  font-weight: 800;
  font-size: 16px;
}

.block__text {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--c-text-muted);
}

.check {
  display: flex;
  align-items: center;
  gap: 9px;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
}

.check input {
  width: 18px;
  height: 18px;
  accent-color: var(--c-text);
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tags--add {
  padding: 12px;
  background: var(--c-bg);
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
</style>
