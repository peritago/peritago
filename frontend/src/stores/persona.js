import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api } from '@/api/http'

/** 백엔드 ExplanationLength enum과 1:1로 맞춥니다. */
export const LENGTH_OPTIONS = [
  { value: 'SHORT', label: '짧게' },
  { value: 'MEDIUM', label: '보통' },
  { value: 'DETAILED', label: '자세히' },
]

/**
 * data.sql이 넣어두는 기본 도메인 태그.
 * GET /api/domain-tags가 아직 없어서 폴백으로 씁니다.
 * (SecurityConfig에는 이미 permitAll로 열려 있으니 컨트롤러만 추가되면 자동으로 서버 값을 씁니다.)
 */
export const FALLBACK_DOMAIN_TAGS = ['개발', '기획', '디자인', '영업', '경영', '기타']

/**
 * UC-02 기본 페르소나(서버 저장) + UC-16 세션 페르소나(클라이언트 전용).
 * 세션 페르소나는 백엔드에 엔터티가 없어 세션 동안 메모리에만 둡니다.
 */
export const usePersonaStore = defineStore('persona', () => {
  const domainTagOptions = ref([...FALLBACK_DOMAIN_TAGS])

  const userPersona = ref({
    domainTags: [],
    personaDescription: '',
    officialDefLength: 'MEDIUM',
    personalizedExpLength: 'MEDIUM',
  })

  /** 서버가 exists:false를 주면 아직 페르소나를 만들지 않은 상태입니다. */
  const exists = ref(false)
  const loaded = ref(false)
  const busy = ref(false)

  const sessionPersona = ref(null)

  const isConfigured = computed(() => exists.value && userPersona.value.domainTags.length > 0)

  const activeDomains = computed(
    () => sessionPersona.value?.domains ?? userPersona.value.domainTags,
  )

  const activeLabel = computed(() =>
    activeDomains.value.length ? activeDomains.value.slice(0, 2).join(' · ') : '미설정',
  )

  const activePersona = computed(() => ({
    domains: activeDomains.value,
    description: sessionPersona.value?.memo || userPersona.value.personaDescription,
    officialDefLength: userPersona.value.officialDefLength,
    personalizedExpLength: userPersona.value.personalizedExpLength,
    scope: sessionPersona.value ? 'session' : 'user',
  }))

  async function loadDomainTags() {
    try {
      const list = await api.get('/api/domain-tags', { auth: false })
      const names = (list ?? []).map((t) => (typeof t === 'string' ? t : t.name)).filter(Boolean)
      if (names.length) domainTagOptions.value = names
    } catch {
      // 컨트롤러가 아직 없으면 조용히 폴백을 씁니다.
    }
  }

  async function load({ force = false } = {}) {
    if (loaded.value && !force) return userPersona.value
    busy.value = true
    try {
      const data = await api.get('/api/users/me/persona')
      exists.value = Boolean(data?.exists)
      userPersona.value = {
        domainTags: data?.domainTags ?? [],
        personaDescription: data?.personaDescription ?? '',
        officialDefLength: data?.officialDefLength ?? 'MEDIUM',
        personalizedExpLength: data?.personalizedExpLength ?? 'MEDIUM',
      }
      loaded.value = true
    } catch (err) {
      // PERSONA_NOT_FOUND는 오류가 아니라 "아직 설정 안 함" 상태입니다.
      if (err?.code === 'PERSONA_NOT_FOUND') {
        exists.value = false
        loaded.value = true
      } else {
        throw err
      }
    } finally {
      busy.value = false
    }
    return userPersona.value
  }

  async function save(next) {
    busy.value = true
    try {
      const payload = {
        domainTags: next.domainTags,
        personaDescription: next.personaDescription ?? '',
        officialDefLength: next.officialDefLength,
        personalizedExpLength: next.personalizedExpLength,
      }
      const data = await api.put('/api/users/me/persona', payload)
      userPersona.value = {
        domainTags: data?.domainTags ?? payload.domainTags,
        personaDescription: data?.personaDescription ?? payload.personaDescription,
        officialDefLength: data?.officialDefLength ?? payload.officialDefLength,
        personalizedExpLength: data?.personalizedExpLength ?? payload.personalizedExpLength,
      }
      exists.value = true
      loaded.value = true
    } finally {
      busy.value = false
    }
    return userPersona.value
  }

  function applySessionPersona({ domains, memo }) {
    sessionPersona.value = { domains: [...domains], memo: memo ?? '' }
  }

  function keepPreviousSession() {
    sessionPersona.value = {
      domains: [...userPersona.value.domainTags],
      memo: sessionPersona.value?.memo ?? '',
    }
  }

  function clearSessionPersona() {
    sessionPersona.value = null
  }

  function reset() {
    userPersona.value = {
      domainTags: [],
      personaDescription: '',
      officialDefLength: 'MEDIUM',
      personalizedExpLength: 'MEDIUM',
    }
    sessionPersona.value = null
    exists.value = false
    loaded.value = false
  }

  return {
    domainTagOptions,
    userPersona,
    sessionPersona,
    exists,
    loaded,
    busy,
    isConfigured,
    activeDomains,
    activeLabel,
    activePersona,
    loadDomainTags,
    load,
    save,
    applySessionPersona,
    keepPreviousSession,
    clearSessionPersona,
    reset,
  }
})
