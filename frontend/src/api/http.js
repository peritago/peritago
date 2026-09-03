/**
 * 백엔드 공통 응답 봉투를 벗기고 JWT를 붙여주는 얇은 fetch 래퍼.
 *
 * 성공: { status, message, data }        → data만 반환
 * 실패: { status, message: ERROR_CODE }  → ApiError로 던짐
 *
 * 401(EXPIRED_TOKEN)이면 refreshToken으로 한 번 재발급한 뒤 원래 요청을 재시도합니다.
 * 동시에 여러 요청이 401을 맞아도 재발급은 한 번만 돕니다.
 */

const ACCESS_KEY = 'peritago.accessToken'
const REFRESH_KEY = 'peritago.refreshToken'

/** 백엔드 ErrorCode를 사용자 문구로 옮깁니다. 서버는 코드만 내려줍니다. */
const MESSAGES = {
  USER_NOT_FOUND: '사용자를 찾을 수 없습니다.',
  EMAIL_DUPLICATED: '이미 가입된 이메일입니다.',
  INVALID_CREDENTIALS: '이메일 또는 비밀번호가 올바르지 않습니다.',
  INVALID_TOKEN: '로그인이 필요합니다.',
  EXPIRED_TOKEN: '로그인이 만료되었습니다. 다시 로그인해 주세요.',
  PERSONA_NOT_FOUND: '설정된 페르소나가 없습니다.',
  GLOSSARY_TERM_DUPLICATED: '이미 등록된 용어입니다.',
  VALIDATION_ERROR: '입력값을 확인해 주세요.',
  SESSION_NOT_FOUND: '존재하지 않는 채팅입니다.',
  TRANSLATION_FAILED: '설명을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.',
}

export class ApiError extends Error {
  constructor(code, status, details) {
    super(MESSAGES[code] ?? '요청을 처리하지 못했습니다.')
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.details = details
  }
}

export const tokens = {
  get access() {
    return localStorage.getItem(ACCESS_KEY)
  },
  get refresh() {
    return localStorage.getItem(REFRESH_KEY)
  },
  set({ accessToken, refreshToken }) {
    if (accessToken) localStorage.setItem(ACCESS_KEY, accessToken)
    if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken)
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
  },
}

/** 토큰이 완전히 죽었을 때 앱에 알립니다. auth 스토어가 구독해 로그인으로 보냅니다. */
const listeners = new Set()
export function onAuthExpired(fn) {
  listeners.add(fn)
  return () => listeners.delete(fn)
}
function notifyExpired() {
  tokens.clear()
  listeners.forEach((fn) => fn())
}

export function authHeaders(extra = {}) {
  const token = tokens.access
  return token ? { ...extra, Authorization: `Bearer ${token}` } : { ...extra }
}

let reissuing = null

async function reissue() {
  const refreshToken = tokens.refresh
  if (!refreshToken) {
    notifyExpired()
    return false
  }

  // 이미 재발급 중이면 그 약속을 같이 기다립니다.
  reissuing ??= (async () => {
    try {
      const res = await fetch('/api/auth/reissue', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      })
      const body = await res.json().catch(() => null)
      if (!res.ok || !body?.data?.accessToken) {
        notifyExpired()
        return false
      }
      tokens.set(body.data)
      return true
    } catch {
      notifyExpired()
      return false
    } finally {
      // 다음 401은 다시 시도할 수 있도록 풀어줍니다.
      setTimeout(() => {
        reissuing = null
      }, 0)
    }
  })()

  return reissuing
}

export async function request(
  path,
  { method = 'GET', body, auth = true, retry = true, signal } = {},
) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (auth) Object.assign(headers, authHeaders())

  const init = { method, headers, signal }
  if (body !== undefined) init.body = JSON.stringify(body)
  const response = await fetch(path, init)

  if (response.status === 401 && auth && retry) {
    if (await reissue()) return request(path, { method, body, auth, retry: false, signal })
  }

  // 204 등 본문 없는 응답
  if (response.status === 204 || response.headers.get('content-length') === '0') return null

  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    if (response.status === 401) notifyExpired()
    throw new ApiError(payload?.message, response.status, payload?.data)
  }

  /*
   * GlossaryController.getAll()만 ApiResponse를 거치지 않고 배열을 그대로 내려줍니다.
   * 봉투가 아니면 그대로 반환합니다.
   */
  if (Array.isArray(payload)) return payload
  return payload?.data ?? null
}

export const api = {
  get: (path, options) => request(path, { ...options, method: 'GET' }),
  post: (path, body, options) => request(path, { ...options, method: 'POST', body }),
  put: (path, body, options) => request(path, { ...options, method: 'PUT', body }),
}
