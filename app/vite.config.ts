import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// uni-app 的构建配置。接口地址走 .env.* 里的 VITE_API_BASE，H5 路由 base 在 src/manifest.json 的 h5.router.base。
export default defineConfig({
  plugins: [uni()],
  build: {
    // Vite 默认按 Safari 14 出代码，会保留 `??` 这类语法；iOS 13.4 以下的 JavaScriptCore 读到就整个文件解析失败，
    // App 一页都打不开。按 iOS 12 改写语法。内置函数构建不会补，见 src/utils/compat.ts。
    target: ['es2018', 'ios12'],
  },
})
