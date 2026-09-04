import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api, tokens, ApiError, onAuthExpired, decodeTokenRole } from '@/api/http'

/**
 * UC-01. 실제 백엔드 연결.
 *   POST /api/users        회원가입 → userId
 *   POST /api/auth/login   로그인   → { accessToken, refreshToken }
 *   GET  /api/users/me     내 정보  → { id, email, name }
 *   POST /api/auth/logout  로그아웃
 */
export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  /** 새로고침 직후에는 토큰만 있고 user는 아직 없습니다. 그 사이를 구분하는 플래그입니다. */
  const ready = ref(false)
  const error = ref('')
  const busy = ref(false)
  /** 액세스 토큰의 role 클레임 — 관리자 메뉴 노출 등 화면 표시용입니다. */
  const role = ref(null)

  const hasToken = computed(() => Boolean(tokens.access))
  const isAuthenticated = computed(() => Boolean(user.value))
  const isAdmin = computed(() => role.value === 'ADMIN')
  const initial = computed(() => user.value?.name?.slice(-1) ?? '')

  onAuthExpired(() => {
    user.value = null
    role.value = null
    ready.value = true
  })

  /** 새로고침 복구 — 저장된 토큰으로 내 정보를 다시 받아옵니다. */
  async function restore() {
    if (ready.value) return user.value
    if (!tokens.access) {
      ready.value = true
      return null
    }
    try {
      user.value = await api.get('/api/users/me')
      role.value = decodeTokenRole(tokens.access)
    } catch {
      tokens.clear()
      user.value = null
      role.value = null
    } finally {
      ready.value = true
    }
    return user.value
  }

  async function login({ email, password }) {
    error.value = ''
    busy.value = true
    try {
      const token = await api.post('/api/auth/login', { email, password }, { auth: false })
      tokens.set(token)
      role.value = decodeTokenRole(token.accessToken)
      user.value = await api.get('/api/users/me')
      ready.value = true
      return user.value
    } catch (err) {
      error.value = err instanceof ApiError ? err.message : '로그인 중 문제가 발생했습니다.'
      throw err
    } finally {
      busy.value = false
    }
  }

  /** 가입은 토큰을 주지 않으므로 곧바로 로그인까지 이어서 처리합니다. */
  async function signup({ email, password, name }) {
    error.value = ''
    busy.value = true
    try {
      await api.post('/api/users', { email, password, name }, { auth: false })
      return await login({ email, password })
    } catch (err) {
      error.value = err instanceof ApiError ? err.message : '가입 중 문제가 발생했습니다.'
      throw err
    } finally {
      busy.value = false
    }
  }

  async function logout() {
    try {
      await api.post('/api/auth/logout')
    } catch {
      // 서버가 실패해도 로컬 세션은 지웁니다.
    }
    tokens.clear()
    user.value = null
    role.value = null
  }

  return {
    user,
    ready,
    error,
    busy,
    hasToken,
    isAuthenticated,
    isAdmin,
    initial,
    restore,
    login,
    signup,
    logout,
  }
})
