import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, import.meta.dirname, '')
  const backend = env.VITE_BACKEND_ORIGIN || 'http://localhost:8080'

  return {
    plugins: [vue(), vueDevTools()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 5173,
      proxy: {
        // 백엔드를 같은 오리진으로 붙여 CORS·프리플라이트 없이 SSE까지 그대로 흐르게 합니다.
        '/api': {
          target: backend,
          changeOrigin: true,
        },
        // STT 서버 스트리밍 소켓 (백엔드 구현 후 사용)
        '/ws': {
          target: backend.replace(/^http/, 'ws'),
          ws: true,
        },
      },
    },
  }
})
