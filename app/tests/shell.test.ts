import { describe, expect, it } from 'vitest'
import { parseShellVersion } from '@/logic/shell'

const WEBVIEW_UA =
  'Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UQ1A.240205.004; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/141.0.0.0 Mobile Safari/537.36'

describe('Android 套壳的 User-Agent 标记', () => {
  it('套壳在 UA 末尾加 MaShangShuaTiApp/版本号', () => {
    expect(parseShellVersion(`${WEBVIEW_UA} MaShangShuaTiApp/1.0.0`)).toBe('1.0.0')
    expect(parseShellVersion(`${WEBVIEW_UA} MaShangShuaTiApp/1.2`)).toBe('1.2')
  })

  it('普通浏览器、空 UA 不是套壳', () => {
    expect(parseShellVersion(WEBVIEW_UA)).toBeNull()
    expect(parseShellVersion('')).toBeNull()
    expect(parseShellVersion(null)).toBeNull()
    expect(parseShellVersion(undefined)).toBeNull()
  })

  it('名字或版本号不完整的不算', () => {
    expect(parseShellVersion(`${WEBVIEW_UA} XMaShangShuaTiApp/1.0.0`)).toBeNull()
    expect(parseShellVersion(`${WEBVIEW_UA} MaShangShuaTiApp/`)).toBeNull()
    expect(parseShellVersion(`${WEBVIEW_UA} MaShangShuaTiApp/1.0.0beta`)).toBeNull()
  })
})
