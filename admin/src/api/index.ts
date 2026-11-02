/** 管理后台接口，按 docs/api.md 第 9 节分组 */
import { http } from './http'
import type {
  AdminCategory,
  AdminPaper,
  AdminQuestion,
  AdminQuestionRow,
  AdminUser,
  AuthResult,
  Dashboard,
  DeleteQuestionResult,
  FeedbackRow,
  FeedbackStatus,
  ImportResult,
  Page,
  PaperBody,
  PaperStatus,
  QuestionBody,
  QuestionStatus,
  QuestionType,
  UserStatus,
} from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResult>('/auth/login', { username, password }, { anonymous: true }),
  uploadImage: (file: File) => http.upload<{ url: string }>('/files', file),
}

export const categoryApi = {
  tree: () => http.get<AdminCategory[]>('/admin/categories'),
  create: (body: { parentId: number | null; name: string; sort: number }) => http.post<AdminCategory>('/admin/categories', body),
  update: (id: number, body: { name: string; sort: number }) => http.put<AdminCategory>(`/admin/categories/${id}`, body),
  remove: (id: number) => http.del<null>(`/admin/categories/${id}`),
}

export interface QuestionQuery {
  categoryId?: number | null
  type?: QuestionType | ''
  status?: QuestionStatus | ''
  keyword?: string
  page: number
  size: number
}

export const questionApi = {
  list: (q: QuestionQuery) => http.get<Page<AdminQuestionRow>>('/admin/questions', { ...q }),
  detail: (id: number) => http.get<AdminQuestion>(`/admin/questions/${id}`),
  create: (body: QuestionBody) => http.post<AdminQuestion>('/admin/questions', body),
  update: (id: number, body: QuestionBody) => http.put<AdminQuestion>(`/admin/questions/${id}`, body),
  remove: (id: number) => http.del<DeleteQuestionResult>(`/admin/questions/${id}`),
  preview: (markdown: string) => http.post<{ html: string }>('/admin/questions/preview', { markdown }, { silent: true }),
  template: () => http.download('/admin/questions/import/template'),
  import: (file: File) => http.upload<ImportResult>('/admin/questions/import', file),
}

export const paperApi = {
  list: (q: { categoryId?: number | null; status?: PaperStatus | ''; page: number; size: number }) =>
    http.get<Page<AdminPaper>>('/admin/papers', { ...q }),
  detail: (id: number) => http.get<AdminPaper>(`/admin/papers/${id}`),
  create: (body: PaperBody) => http.post<AdminPaper>('/admin/papers', body),
  update: (id: number, body: PaperBody) => http.put<AdminPaper>(`/admin/papers/${id}`, body),
  /** 上架时题不够返回 409 POOL_INSUFFICIENT（data.gaps），由页面展示缺口 */
  setStatus: (id: number, status: PaperStatus) =>
    http.put<AdminPaper>(`/admin/papers/${id}/status`, { status }, { silent: ['POOL_INSUFFICIENT'] }),
  remove: (id: number) => http.del<null>(`/admin/papers/${id}`),
}

export const feedbackApi = {
  list: (q: { status?: FeedbackStatus | ''; page: number; size: number }) => http.get<Page<FeedbackRow>>('/admin/feedback', { ...q }),
  handle: (id: number, body: { status: 'RESOLVED' | 'REJECTED'; reply?: string }) => http.put<FeedbackRow>(`/admin/feedback/${id}`, body),
}

export const userApi = {
  list: (q: { keyword?: string; page: number; size: number }) => http.get<Page<AdminUser>>('/admin/users', { ...q }),
  setStatus: (id: number, status: UserStatus) => http.put<AdminUser>(`/admin/users/${id}/status`, { status }),
}

export const dashboardApi = {
  get: () => http.get<Dashboard>('/admin/dashboard'),
}
