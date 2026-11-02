/** 管理后台用到的接口类型，字段同 docs/api.md 第 2、9、9.1、10 节。 */

export type QuestionType = 'SINGLE' | 'MULTIPLE' | 'JUDGE' | 'FILL'
export type QuestionStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type PaperMode = 'FIXED' | 'RULE'
export type PaperStatus = 'ONLINE' | 'OFFLINE'
export type FeedbackStatus = 'PENDING' | 'RESOLVED' | 'REJECTED'
export type FeedbackType = 'ANSWER_WRONG' | 'STEM_WRONG' | 'ANALYSIS_UNCLEAR' | 'OTHER'
export type UserStatus = 'ACTIVE' | 'DISABLED'

export interface Page<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface User {
  id: number
  username: string
  nickname: string | null
  avatarUrl: string | null
  role: 'USER' | 'ADMIN'
  status: UserStatus
  createdAt: string
}

export interface AuthResult {
  token: string
  expiresAt: string
  user: User
}

// ---------- 分类 ----------

export interface AdminCategory {
  id: number
  parentId: number | null
  name: string
  level: 1 | 2 | 3
  sort: number
  questionCount: number
  children: AdminCategory[]
}

// ---------- 题目 ----------

export interface QuestionOptionInput {
  key: string
  text: string
}

export interface FillOptions {
  ignoreCase: boolean
  ignoreSpaces: boolean
}

/** POST / PUT /api/admin/questions 的请求体 */
export interface QuestionBody {
  categoryId: number
  type: QuestionType
  stem: string
  options?: QuestionOptionInput[]
  correctAnswer?: string[] | null
  acceptedAnswers?: string[][] | null
  fillOptions?: FillOptions | null
  analysis: string
  difficulty: number
  tags: string[]
  imageUrl: string | null
  source: string
  /** 不传时新建题排到章节末尾 */
  sort?: number
  status: 'DRAFT' | 'PUBLISHED'
}

/** 完整题目（详情、新建、修改返回） */
export interface AdminQuestion {
  id: number
  categoryId: number
  categoryPath: string
  type: QuestionType
  stem: string
  stemHtml?: string
  options: (QuestionOptionInput & { html?: string })[]
  correctAnswer: string[] | null
  acceptedAnswers: string[][] | null
  fillOptions: FillOptions | null
  analysis: string
  analysisHtml?: string
  difficulty: number
  tags: string[]
  imageUrl: string | null
  source: string
  sort: number
  status: QuestionStatus
  blankCount: number
  version: number
  answerCount: number
  correctRate: number | null
  createdAt: string
  updatedAt: string
}

/** 列表项：不含 stemHtml、analysisHtml，另带 stemPreview */
export type AdminQuestionRow = Omit<AdminQuestion, 'stemHtml' | 'analysisHtml'> & { stemPreview: string }

export interface DeleteQuestionResult {
  deleted: boolean
  archived: boolean
}

export interface ImportResult {
  total: number
  success: number
  failed: { row: number; reason: string }[]
}

// ---------- 试卷 ----------

export interface PaperQuestionRef {
  questionId: number
  score: number
  sort: number
  type?: QuestionType
  stemPreview?: string
}

export interface PaperRule {
  categoryId: number
  categoryPath?: string
  type: QuestionType
  count: number
  score: number
}

export interface PaperBody {
  title: string
  categoryId: number
  description: string
  mode: PaperMode
  durationMin: number
  passScore: number
  questions?: { questionId: number; score: number; sort: number }[]
  rules?: { categoryId: number; type: QuestionType; count: number; score: number }[]
  /** 开考时打乱单选、多选题的选项（只影响之后开考的场次） */
  shuffleOptions: boolean
}

export interface AdminPaper {
  id: number
  title: string
  categoryId: number
  categoryName: string
  description: string | null
  mode: PaperMode
  durationMin: number
  totalScore: number
  passScore: number
  status: PaperStatus
  shuffleOptions?: boolean
  questionCount: number
  attempts: number
  questions: PaperQuestionRef[] | null
  rules: PaperRule[] | null
  createdAt: string
  updatedAt: string
}

export interface PoolGap {
  categoryId: number
  categoryPath: string
  type: QuestionType
  need: number
  available: number
}

// ---------- 纠错、用户、看板 ----------

export interface FeedbackRow {
  id: number
  questionId: number
  stemPreview: string
  categoryPath: string
  /** 纠错类型 */
  type: FeedbackType
  /** 题型 */
  questionType?: QuestionType
  content: string
  status: FeedbackStatus
  reply: string | null
  username: string
  createdAt: string
  handledAt: string | null
}

export interface AdminUser {
  id: number
  username: string
  nickname: string | null
  role: 'USER' | 'ADMIN'
  status: UserStatus
  answerCount: number
  lastActiveAt: string | null
  createdAt: string
}

export interface Dashboard {
  userCount: number
  questionCount: number
  paperCount: number
  todayAnswerCount: number
  pendingFeedbackCount: number
  daily: { date: string; answerCount: number; activeUsers: number }[]
  papers: { paperId: number; title: string; attempts: number; avgScore: number | null; passRate: number | null }[]
}
