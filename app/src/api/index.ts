/** 按 docs/api.md 分组的接口函数。只做参数整理，不加业务逻辑。 */
import { http, upload } from './http'
import type {
  AuthResult,
  CategoryNode,
  FavoriteItem,
  FeedbackType,
  FinishResult,
  Page,
  PracticeSession,
  PracticeSummary,
  QuestionDetail,
  SearchHit,
  StartPracticeBody,
  StatsOverview,
  SubmitAnswerBody,
  SubmitAnswerResult,
  User,
  WrongGroup,
  WrongQuestion,
} from './types'

// ---------- 2. 账号 ----------
export const authApi = {
  login: (body: { username: string; password: string }) =>
    http.post<AuthResult>('/auth/login', body, { anonymous: true }),
  register: (body: { username: string; password: string; nickname?: string }) =>
    http.post<AuthResult>('/auth/register', body, { anonymous: true }),
  me: () => http.get<User>('/me'),
  updateMe: (body: { nickname?: string; avatarUrl?: string; targetCategoryId?: number }) => http.put<User>('/me', body),
  changePassword: (body: { oldPassword: string; newPassword: string }) => http.put<null>('/me/password', body),
  uploadImage: (filePath: string) => upload<{ url: string }>('/files', filePath),
}

// ---------- 3. 分类与搜题 ----------
export const categoryApi = {
  /** 不传 rootId：全部一级分类（选目标考试）；传了：该考试整棵树 */
  tree: (rootId?: number) => http.get<CategoryNode[]>('/categories', { rootId }),
}

export const questionApi = {
  search: (q: { keyword: string; categoryId?: number; page?: number; size?: number }) =>
    http.get<Page<SearchHit>>('/questions/search', q),
  detail: (id: number, silent?: string[]) => http.get<QuestionDetail>(`/questions/${id}`, undefined, { silent }),
  feedback: (id: number, body: { type: FeedbackType; content: string }) =>
    http.post<unknown>(`/questions/${id}/feedback`, body),
}

// ---------- 4. 练习 ----------
export const practiceApi = {
  start: (body: StartPracticeBody, silent?: string[]) => http.post<PracticeSession>('/practice/sessions', body, { silent }),
  get: (id: number) => http.get<PracticeSession>(`/practice/sessions/${id}`),
  current: () => http.get<PracticeSession | null>('/practice/sessions/current'),
  list: (page = 1, size = 20) => http.get<Page<PracticeSummary>>('/practice/sessions', { page, size }),
  submit: (id: number, body: SubmitAnswerBody) => http.post<SubmitAnswerResult>(`/practice/sessions/${id}/answers`, body),
  /** 进度保存失败不打扰用户 */
  progress: (id: number, currentIndex: number) =>
    http.put<unknown>(`/practice/sessions/${id}/progress`, { currentIndex }, { silent: true }),
  finish: (id: number) => http.post<FinishResult>(`/practice/sessions/${id}/finish`),
}

// ---------- 5. 错题本和收藏 ----------
export const wrongApi = {
  list: (q: { categoryId?: number; sort?: 'RECENT' | 'MOST'; page?: number; size?: number }) =>
    http.get<Page<WrongQuestion>>('/wrong-questions', q),
  groups: () => http.get<WrongGroup[]>('/wrong-questions/groups'),
  remove: (questionId: number) => http.del<null>(`/wrong-questions/${questionId}`),
}

export const favoriteApi = {
  list: (q: { categoryId?: number; page?: number; size?: number }) => http.get<Page<FavoriteItem>>('/favorites', q),
  /** 收藏，或修改笔记 */
  save: (questionId: number, note?: string | null) => http.put<unknown>(`/favorites/${questionId}`, { note }),
  remove: (questionId: number) => http.del<null>(`/favorites/${questionId}`),
}

// ---------- 7. 统计 ----------
export const statsApi = {
  overview: (categoryId?: number) => http.get<StatsOverview>('/stats/overview', { categoryId }),
}
