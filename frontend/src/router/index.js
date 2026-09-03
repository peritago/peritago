import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { usePersonaStore } from '@/stores/persona'

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

    // S-06~S-09는 아직 시안·API가 없어 자리만 잡아둡니다.
    {
      path: '/history',
      name: 'history',
      component: () => import('@/views/StubView.vue'),
      props: {
        screen: 'S-06',
        title: '질의 이력',
        description:
          '채팅 목록 → 채팅 내 질의 목록의 2단계 구조로 만들 화면입니다. 백엔드에 Chat·Query 엔터티가 추가되면 연결합니다.',
      },
    },
    {
      path: '/glossary',
      name: 'glossary',
      component: () => import('@/views/StubView.vue'),
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

  return true
})

export default router
