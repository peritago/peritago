<script setup>
import { computed, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import AuthCanvas from '@/components/AuthCanvas.vue'

const router = useRouter()
const auth = useAuthStore()
const chat = useChatStore()
const { busy, error } = storeToRefs(auth)

/** 백엔드 UserCreateRequestDto는 email·password·name 세 개만 받습니다. */
const form = reactive({ name: '', email: '', password: '', agreed: false })
const touched = reactive({ email: false, password: false })

const emailState = computed(() => {
  if (!form.email) return null
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email))
    return { ok: false, text: '이메일 형식을 확인해 주세요.' }
  return { ok: true, text: '형식이 올바릅니다' }
})

const passwordError = computed(() => {
  if (!touched.password) return ''
  if (form.password.length < 10) return '10자 이상, 숫자와 기호를 각각 1개 이상 포함해야 합니다.'
  if (!/\d/.test(form.password) || !/[^\w\s]/.test(form.password))
    return '숫자와 기호를 각각 1개 이상 포함해야 합니다.'
  return ''
})

const canSubmit = computed(
  () => form.name && emailState.value?.ok && form.password && !passwordError.value && form.agreed,
)

async function submit() {
  touched.password = true
  if (!canSubmit.value) return

  try {
    await auth.signup({ email: form.email, password: form.password, name: form.name })
  } catch {
    return
  }

  chat.createChat({ title: '새 채팅' })
  router.push('/persona')
}
</script>

<template>
  <AuthCanvas eyebrow="회원가입 · 1 / 2 단계">
    <div class="intro">
      <h2>계정을 만들고<br />페르소나를 정합니다</h2>
      <ol class="steps">
        <li class="steps__item steps__item--on"><span>1</span> 계정 정보</li>
        <li class="steps__item"><span>2</span> 페르소나 설정</li>
      </ol>
    </div>

    <form class="form" @submit.prevent="submit">
      <div class="field">
        <label class="field__label" for="name">이름</label>
        <input id="name" v-model="form.name" type="text" class="input" placeholder="김하늘" />
      </div>

      <div class="field">
        <label class="field__label" for="signup-email">이메일</label>
        <input
          id="signup-email"
          v-model="form.email"
          type="email"
          class="input"
          placeholder="name@company.com"
          autocomplete="email"
          @blur="touched.email = true"
        />
        <div v-if="emailState" class="field__foot">
          <span :class="{ field__error: !emailState.ok }">{{ emailState.text }}</span>
        </div>
      </div>

      <div class="field">
        <label class="field__label" for="signup-password">비밀번호</label>
        <input
          id="signup-password"
          v-model="form.password"
          type="password"
          class="input"
          autocomplete="new-password"
          :aria-invalid="Boolean(passwordError)"
          @blur="touched.password = true"
        />
        <div class="field__foot">
          <span v-if="passwordError" class="field__error">{{ passwordError }}</span>
          <span v-else>10자 이상, 숫자와 기호를 각각 1개 이상 포함합니다.</span>
        </div>
      </div>

      <label class="check">
        <input v-model="form.agreed" type="checkbox" />
        <span>서비스 이용약관과 개인정보 처리방침에 동의합니다.</span>
      </label>
      <p class="u-note">녹음·STT 원문 보존 정책은 설정에서 다시 확인할 수 있습니다.</p>

      <p v-if="error" class="form__error">{{ error }}</p>

      <div class="form__actions">
        <router-link to="/login" class="btn btn--ghost">취소</router-link>
        <button type="submit" class="btn btn--primary form__next" :disabled="!canSubmit || busy">
          {{ busy ? '가입 중…' : '다음 · 페르소나 설정' }}
        </button>
      </div>
    </form>
  </AuthCanvas>
</template>

<style scoped>
.intro h2 {
  margin: 0 0 var(--s-4);
  font-weight: 800;
  font-size: 30px;
  line-height: 1.2;
  letter-spacing: -0.03em;
}

.steps {
  display: flex;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.steps__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-badge);
  font-size: 14px;
  color: var(--c-text-muted);
}

.steps__item span {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--c-border);
  font-weight: 700;
  font-size: 14px;
  color: var(--c-text);
}

.steps__item--on {
  background: var(--c-text);
  border-color: var(--c-text);
  color: var(--c-on-dark);
  font-weight: 700;
}

.steps__item--on span {
  background: var(--c-on-dark);
}

.form {
  display: flex;
  flex-direction: column;
  gap: var(--s-4);
}

.check {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 14px;
  cursor: pointer;
}

.check input {
  width: 18px;
  height: 18px;
  accent-color: var(--c-text);
}

.form__error {
  margin: 0;
  font-weight: 600;
  font-size: 14px;
}

.form__actions {
  display: flex;
  gap: 10px;
}

.form__actions .btn {
  text-decoration: none;
}

.form__next {
  flex: 1;
  height: 50px;
  font-size: 16px;
}
</style>
