import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api } from '@/api/http'
import { GLOSSARY as SEED, buildIndex, matchIn, detectIn } from '@/data/glossary'

/**
 * Glossary 소스.
 *
 * 서버 Glossary 엔터티에는 아직 도메인 태그가 없습니다(term, officialDefinition, createdBy만 존재).
 * UC-13은 "용어의 도메인이 내 페르소나 도메인과 다른가"로 낯섦을 판정하므로,
 * 도메인을 모르는 서버 항목은 **자동 감지 후보로 올리지 않습니다**(오탐 방지 원칙과 같은 이유).
 * 대신 수동 질의로 찾을 때는 공식 정의 근거로 그대로 씁니다.
 *
 * 백엔드에 domain_tags가 붙으면 detectable을 true로 바꾸기만 하면 됩니다.
 */
export const useGlossaryStore = defineStore('glossary', () => {
  const serverEntries = ref([])
  const loaded = ref(false)
  const loadError = ref('')

  /** 서버 항목이 우선, 없는 용어만 로컬 시드로 채웁니다. */
  const entries = computed(() => {
    const serverTerms = new Set(serverEntries.value.map((e) => e.term))
    return [...serverEntries.value, ...SEED.filter((e) => !serverTerms.has(e.term))]
  })

  const index = computed(() => buildIndex(entries.value))

  /** 도메인이 있는 항목만 자동 감지 대상입니다. */
  const detectIndex = computed(() =>
    buildIndex(entries.value.filter((e) => e.detectable !== false)),
  )

  async function load() {
    if (loaded.value) return entries.value
    try {
      const list = await api.get('/api/glossary/admin')
      serverEntries.value = (list ?? []).map((g) => ({
        id: g.id,
        term: g.term,
        aliases: [],
        domains: [],
        subdomain: null,
        definition: g.officialDefinition,
        source: '사내 Glossary',
        evidenceType: 'glossary',
        detectable: false, // 도메인 태그가 없어 낯섦 판정 불가
      }))
      loaded.value = true
    } catch (err) {
      /*
       * /api/glossary/admin은 ADMIN 전용이라 일반 사용자는 403을 받습니다.
       * 오류로 취급하지 않고 로컬 시드만 씁니다.
       */
      loadError.value = err?.status === 403 ? '' : (err?.message ?? '')
      loaded.value = true
    }
    return entries.value
  }

  function match(term) {
    return matchIn(index.value, term)
  }

  function detect(sentences, personaDomains) {
    return detectIn(detectIndex.value, sentences, personaDomains)
  }

  return { serverEntries, entries, loaded, loadError, load, match, detect }
})
