/** 按 docs/api.md 分组的接口函数。只做参数整理，不加业务逻辑。 */
import { http, upload } from './http'
import type {
  AuthResult,
  CategoryNode,
  CategoryStat,
  DailyStat,
  ExamNotice,
  ExamPaper,
  ExamRecordSummary,
  ExamResult,
  ExamScoreStat,
  ExamSheet,
  Leaderboard,
  WeakPoint,
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
  /** 全部一级分类（选目标考试），data 是数组 */
  exams: () => http.get<CategoryNode[]>('/categories'),
  /** 某个考试的整棵树，data 是根节点对象 */
  tree: (rootId: number) => http.get<CategoryNode>('/categories', { rootId }),
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

// ---------- 6. 模拟考试 ----------
export const examApi = {
  /** 默认按本人目标考试过滤 */
  list: (categoryId?: number) => http.get<ExamPaper[]>('/exams', { categoryId }),
  notice: (paperId: number) => http.get<ExamNotice>(`/exams/${paperId}`),
  /** 已有进行中的同卷考试则返回它；规则卷题不够 409 POOL_INSUFFICIENT（data.gaps） */
  start: (paperId: number) => http.post<ExamSheet>(`/exams/${paperId}/start`, undefined, { silent: ['POOL_INSUFFICIENT'] }),
  /** 进行中返回 ExamSheet，已结算返回 ExamResult */
  record: (recordId: number, silent?: boolean) =>
    http.get<ExamSheet | ExamResult>(`/exam-records/${recordId}`, undefined, { silent }),
  /** 同步一题答案；错误由同步队列处理，不弹提示 */
  saveAnswer: (recordId: number, questionId: number, body: { userAnswer: string[]; version: number; marked: boolean }) =>
    http.put<{ accepted: boolean; version: number }>(`/exam-records/${recordId}/answers/${questionId}`, body, { silent: true }),
  submit: (recordId: number) => http.post<ExamResult>(`/exam-records/${recordId}/submit`),
  records: (q: { paperId?: number; page?: number; size?: number }) => http.get<Page<ExamRecordSummary>>('/exam-records', q),
}

/** 进行中 / 已结算的区分以 status 为准 */
export function isExamSheet(data: ExamSheet | ExamResult): data is ExamSheet {
  return data.status === 'IN_PROGRESS'
}

// ---------- 7. 统计 ----------
export const statsApi = {
  overview: (categoryId?: number) => http.get<StatsOverview>('/stats/overview', { categoryId }),
  daily: (days = 30) => http.get<DailyStat[]>('/stats/daily', { days }),
  categories: () => http.get<CategoryStat[]>('/stats/categories'),
  weak: (days = 30, limit = 5) => http.get<WeakPoint[]>('/stats/weak', { days, limit }),
  exams: (paperId?: number) => http.get<ExamScoreStat[]>('/stats/exams', { paperId }),
  leaderboard: () => http.get<Leaderboard>('/stats/leaderboard', { period: 'WEEK' }),
}
