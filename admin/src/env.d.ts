/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

interface ImportMetaEnv {
  /** 站点路径前缀，生产 /quiz/admin/ */
  readonly VITE_BASE: string
  /** 接口前缀，开发 /api（代理到本地后端），生产 /quiz/api */
  readonly VITE_API_BASE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
