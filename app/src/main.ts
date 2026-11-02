import { createSSRApp } from 'vue'
import * as Pinia from 'pinia'
import App from './App.vue'
import { configureHttp } from '@/api/http'
import { useUserStore } from '@/stores/user'
import { goLogin } from '@/utils/nav'

export function createApp() {
  const app = createSSRApp(App)
  const pinia = Pinia.createPinia()
  app.use(pinia)

  // 请求层只认识这两个回调，不直接依赖 store
  configureHttp({
    getToken: () => useUserStore(pinia).token,
    onUnauthorized: () => {
      useUserStore(pinia).clear()
      goLogin('登录已过期，请重新登录')
    },
  })

  return { app, Pinia }
}
