import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { usePersonaStore } from '@/stores/persona'
import { onAuthExpired } from '@/api/http'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/signup',
      name: 'signup',
      component: () => import('@/views/SignupView.vue'),
      meta: { public: true },
    },
    { path: '/persona', name: 'persona', component: () => import('@/views/PersonaSetupView.vue') },
    { path: '/', name: 'home', component: () => import('@/views/TranslateHomeView.vue') },
    /**
     * 특정 채팅을 딥링크/새로고침으로 복원하기 위한 라우트 — home과 같은 컴포넌트를 씁니다.
     * TranslateHomeView가 마운트 시 :id를 읽어 그 채팅을 선택하고, 채팅을 전환할 때마다
     * 여기로 URL을 맞춰(router.replace) 둡니다.
     */
    { path: '/chat/:id', name: 'chat', component: () => import('@/views/TranslateHomeView.vue') },

    /**
     * S-06. 전용 백엔드 엔터티 없이, GET /api/translate/history를 용어 기준으로 묶어서 만듭니다.
     * chat 스토어의 glossaryTerms 참고.
     */
    { path: '/my-glossary', name: 'my-glossary', component: () => import('@/views/MyGlossaryView.vue') },

    // S-07~S-09는 아직 시안·API가 없어 자리만 잡아둡니다.
    {
      path: '/glossary',
      name: 'glossary',
      component: () => import('@/views/StubView.vue'),
      meta: { adminOnly: true },
      props: {
        screen: 'S-07 · S-08',
        title: '용어집 관리',
        description:
          '관리자 전용 화면입니다. /api/glossary/admin이 ADMIN 역할로 막혀 있어, 일반 계정으로는 목록이 보이지 않습니다.',
      },
    },

    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  const persona = usePersonaStore()

  // 새로고침 직후에는 저장된 토큰으로 세션을 먼저 복구합니다.
  await auth.restore()

  if (!to.meta.public && !auth.isAuthenticated) return { name: 'login' }

  // 로그인된 채로 로그인/가입 화면에 들어오면 홈으로 보냅니다.
  if (to.meta.public && auth.isAuthenticated) return { name: 'home' }

  if (auth.isAuthenticated && to.name !== 'persona') {
    // UC-02를 통과하지 않았으면 페르소나 설정이 먼저입니다.
    if (!persona.loaded) await persona.load().catch(() => {})
    if (!persona.isConfigured) return { name: 'persona' }
  }

  // 관리자 전용 화면 — role은 JWT 클레임을 읽은 화면 표시용 판단이라, 실제 차단은 서버가 합니다.
  if (to.meta.adminOnly && !auth.isAdmin) return { name: 'home' }

  return true
})

// 재발급도 실패해 토큰이 완전히 죽었을 때: 지금 보고 있는 화면과 무관하게 즉시 로그인으로 보냅니다.
onAuthExpired(() => {
  if (!router.currentRoute.value.meta.public) router.push({ name: 'login' })
})

export default router
