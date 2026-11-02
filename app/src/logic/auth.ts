/** 账号相关的校验，规则同 docs/api.md 第 2 节。 */

const USERNAME_RE = /^[A-Za-z0-9_]{3,20}$/

/** 返回错误提示；合法时返回空串 */
export function validateUsername(username: string): string {
  if (!username) return '请输入用户名'
  if (!USERNAME_RE.test(username)) return '用户名为 3–20 位字母、数字或下划线'
  return ''
}

export function validatePassword(password: string): string {
  if (!password) return '请输入密码'
  if (password.length < 6 || password.length > 32) return '密码为 6–32 位'
  return ''
}

export function validateNickname(nickname: string): string {
  if (nickname.length > 30) return '昵称最多 30 个字'
  return ''
}

/** 令牌是否还在有效期内；expiresAt 缺失或无法解析都算无效 */
export function isTokenValid(token: string | null | undefined, expiresAt: string | null | undefined, now = Date.now()): boolean {
  if (!token || !expiresAt) return false
  const t = Date.parse(expiresAt)
  return !Number.isNaN(t) && t > now
}
