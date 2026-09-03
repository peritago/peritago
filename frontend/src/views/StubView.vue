<script setup>
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { usePersonaStore } from '@/stores/persona'
import AppHeader from '@/components/AppHeader.vue'

defineProps({
  title: { type: String, required: true },
  screen: { type: String, default: '' },
  description: { type: String, default: '' },
})

const router = useRouter()
const auth = useAuthStore()
const persona = usePersonaStore()
const { user, initial } = storeToRefs(auth)
const { activeLabel, activePersona } = storeToRefs(persona)
</script>

<template>
  <div class="stub">
    <AppHeader
      :user-name="user?.name"
      :initial="initial"
      :lens-label="activeLabel"
      :lens-scope="activePersona.scope"
      @open-history="router.push('/history')"
      @open-glossary="router.push('/glossary')"
    />

    <main class="stub__body">
      <div class="stub__card">
        <span class="u-label">{{ screen }}</span>
        <h1>{{ title }}</h1>
        <p>{{ description }}</p>
        <router-link to="/" class="btn btn--primary stub__back">번역 홈으로 돌아가기</router-link>
      </div>
    </main>
  </div>
</template>

<style scoped>
.stub {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.stub__body {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--s-6);
}

.stub__card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: flex-start;
  max-width: 520px;
  padding: var(--s-6);
  background: var(--c-surface);
  border: 1px dashed var(--c-border);
  border-radius: var(--r-card);
}

.stub__card h1 {
  margin: 0;
  font-weight: 800;
  font-size: 28px;
  letter-spacing: -0.03em;
}

.stub__card p {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--c-text-muted);
}

.stub__back {
  margin-top: var(--s-2);
  text-decoration: none;
}
</style>
