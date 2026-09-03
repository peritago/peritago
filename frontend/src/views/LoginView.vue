<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { usePersonaStore } from '@/stores/persona'
import { useChatStore } from '@/stores/chat'
import AuthCanvas from '@/components/AuthCanvas.vue'

const router = useRouter()
const auth = useAuthStore()
const persona = usePersonaStore()
const chat = useChatStore()
const { busy, error } = storeToRefs(auth)

const email = ref('')
const password = ref('')
const showPassword = ref(false)
const localError = ref('')

async function submit() {
  localError.value = ''
  if (!email.value.includes('@')) {
    localError.value = '이메일 형식을 확인해 주세요.'
    return
  }
  if (!password.value) {
    localError.value = '비밀번호를 입력해 주세요.'
    return
  }

  try {
    await auth.login({ email: email.value, password: password.value })
  } catch {
    return // 서버 오류 문구는 store의 error가 들고 있습니다.
  }

  // UC-01 → UC-16: 로그인 성공 시 새 채팅이 자동 생성됩니다.
  chat.createChat({ title: '새 채팅' })

  await persona.load({ force: true }).catch(() => {})
  router.push(persona.isConfigured ? '/' : '/persona')
}
</script>

<template>
  <AuthCanvas>
    <form class="form" @submit.prevent="submit">
      <div class="field">
        <label class="field__label" for="email">이메일</label>
        <input
          id="email"
          v-model="email"
          type="email"
          class="input"
          autocomplete="email"
          placeholder="name@company.com"
        />
      </div>

      <div class="field">
        <label class="field__label" for="password">비밀번호</label>
        <div class="password">
          <input
            id="password"
            v-model="password"
            :type="showPassword ? 'text' : 'password'"
            class="input"
            autocomplete="current-password"
            placeholder="비밀번호를 입력하세요"
          />
          <button type="button" class="password__toggle" @click="showPassword = !showPassword">
            {{ showPassword ? '숨기기' : '표시' }}
          </button>
        </div>
        <div class="field__foot">
          <span v-if="localError || error" class="field__error">{{ localError || error }}</span>
        </div>
      </div>

      <button type="submit" class="btn btn--primary btn--block form__submit" :disabled="busy">
        {{ busy ? '로그인 중…' : '로그인' }}
      </button>

      <p class="u-note">
        로그인하면 <strong>새 채팅이 자동으로 생성</strong>됩니다. 페르소나가 없으면 설정 화면으로,
        있으면 번역 홈으로 바로 이동합니다.
      </p>

      <div class="links">
        <span class="u-meta">비밀번호를 잊으셨나요? 관리자에게 문의해 주세요.</span>
        <router-link to="/signup">처음이신가요? 회원가입</router-link>
      </div>
    </form>
  </AuthCanvas>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: var(--s-4);
}

.password {
  position: relative;
  display: flex;
  align-items: center;
}

.password__toggle {
  position: absolute;
  right: 14px;
  font-weight: 600;
  font-size: 14px;
  color: var(--c-text-muted);
}

.password__toggle:hover {
  color: var(--c-text);
}

.form__submit {
  height: 50px;
  font-size: 16px;
}

.links {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: var(--s-2);
  border-top: 1px solid var(--c-border);
  font-size: 14px;
}

.links a {
  color: var(--c-text-muted);
  text-decoration: none;
}

.links a:hover {
  color: var(--c-text);
  text-decoration: underline;
}
</style>
