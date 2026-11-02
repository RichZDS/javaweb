import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// base 取 VITE_BASE（生产 /quiz/admin/）；开发时 /api 和 /uploads 代理到本地后端
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backend = env.VITE_DEV_BACKEND || 'http://localhost:8080'
  return {
    base: env.VITE_BASE || '/',
    plugins: [vue()],
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: {
      proxy: {
        '/api': { target: backend, changeOrigin: true },
        '/uploads': { target: backend, changeOrigin: true },
      },
    },
    build: { chunkSizeWarningLimit: 1500 },
  }
})
