import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// uni-app 的构建配置。接口地址走 .env.* 里的 VITE_API_BASE，H5 路由 base 在 src/manifest.json 的 h5.router.base。
export default defineConfig({
  plugins: [uni()],
})
