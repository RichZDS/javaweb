import { defineConfig } from 'vitest/config'
import { fileURLToPath, URL } from 'node:url'

// 单元测试只覆盖 src/logic 下的纯函数
export default defineConfig({
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  test: { include: ['tests/**/*.test.ts'], environment: 'node' },
})
