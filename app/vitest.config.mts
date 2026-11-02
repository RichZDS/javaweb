import { defineConfig } from 'vitest/config'
import { fileURLToPath } from 'node:url'

// 单元测试只覆盖 src/logic 下的纯函数模块，不加载 uni 插件。
export default defineConfig({
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  test: {
    include: ['tests/**/*.test.ts'],
    environment: 'node',
  },
})
