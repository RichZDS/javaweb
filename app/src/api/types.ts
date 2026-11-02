/**
 * 接口数据类型，字段和 docs/api.md 一一对应。
 * 前端不判分、不算统计，下面所有结果字段都直接展示后端返回值。
 */

export type QuestionType = 'SINGLE' | 'MULTIPLE' | 'JUDGE' | 'FILL'
export type PracticeMode = 'RANDOM' | 'SEQUENTIAL' | 'WRONG' | 'FAVORITE' | 'DAILY' | 'TAG' | 'RETRY'
export type PracticeScope = 'ALL' | 'UNDONE' | 'WRONG_EVER'
export type FeedbackType = 'ANSWER_WRONG' | 'STEM_WRONG' | 'ANALYSIS_UNCLEAR' | 'OTHER'

/** 统一返回外壳 */
export interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
}

export interface Page<T> {
  items: T[]
  total: number
  page: number
  size: number
}

// ---------- 账号 ----------

export interface User {
  id: number
  username: string
  nickname: string | null
  avatarUrl: string | null
  role: 'USER' | 'ADMIN'
  status: string
  targetCategoryId: number | null
  targetCategoryName: string | null
  createdAt: string
}

export interface AuthResult {
  token: string
  expiresAt: string
  user: User
}

// ---------- 分类 ----------

export interface CategoryNode {
  id: number
  parentId: number | null
  name: string
  level: 1 | 2 | 3
  sort: number
  questionCount: number
  doneCount: number
  /** 本人在该节点下的正确率，0~1；没作答时为 null */
  correctRate: number | null
  children?: CategoryNode[]
}

// ---------- 题目 ----------

export interface QuestionOption {
  key: string
  text: string
  html?: string
}

export interface FillOptions {
  ignoreCase: boolean
  ignoreSpaces: boolean
}

/** 学员看到的题（未交答案时不带答案字段） */
export interface Question {
  id: number
  categoryId: number
  categoryPath: string
  type: QuestionType
  difficulty: number
  tags: string[]
  stem: string
  stemHtml: string
  options: QuestionOption[]
  blankCount: number
  imageUrl: string | null
  favorited: boolean
  note: string | null
}

/** 交卷或提交后附带的答案部分 */
export interface AnswerPart {
  correctAnswer: string[] | null
  acceptedAnswers: string[][] | null
  fillOptions?: FillOptions | null
  analysis?: string
  analysisHtml: string | null
}

/** GET /api/questions/{id}：题目 + 答案 + 解析 */
export type QuestionDetail = Question & AnswerPart

export interface SearchHit {
  id: number
  categoryId: number
  categoryPath: string
  type: QuestionType
  stemPreview: string
}

// ---------- 练习 ----------

export interface StartPracticeBody {
  mode: PracticeMode
  categoryIds?: number[]
  count?: 10 | 20 | 50
  types?: QuestionType[]
  scope?: PracticeScope
  reciteMode?: boolean
  acceptFewer?: boolean
  tag?: string
  /** RETRY 模式：重做某次练习的错题 */
  fromSessionId?: number
  /** RETRY 模式：重做某次已结算考试的错题 */
  fromExamRecordId?: number
}

/** 练习里的一道题：提交后（或背题模式）带答案字段 */
export interface PracticeQuestion extends Question, Partial<AnswerPart> {
  submitted: boolean
  userAnswer: string[] | null
  correct: boolean | null
}

export interface PracticeSession {
  id: number
  mode: PracticeMode
  reciteMode: boolean
  status: 'IN_PROGRESS' | 'COMPLETED' | string
  questionCount: number
  answeredCount: number
  correctCount: number
  currentIndex: number
  startedAt: string
  completedAt: string | null
  dailyDate: string | null
  questions: PracticeQuestion[]
}

export interface PracticeSummary {
  id: number
  mode: PracticeMode
  questionCount: number
  answeredCount: number
  correctCount: number
  startedAt: string
  completedAt: string | null
}

export interface SubmitAnswerBody {
  questionId: number
  userAnswer: string[]
  timeSpentSec: number
}

export interface SubmitAnswerResult {
  questionId: number
  correct: boolean
  userAnswer: string[]
  correctAnswer: string[] | null
  acceptedAnswers: string[][] | null
  analysisHtml: string | null
  alreadySubmitted: boolean
}

export interface FinishResult {
  durationSec: number
  answeredCount: number
  correctCount: number
  correctRate: number | null
  wrongQuestionIds: number[]
}

// ---------- 错题本和收藏 ----------

export interface WrongQuestion {
  questionId: number
  categoryId: number
  categoryPath: string
  type: QuestionType
  stemPreview: string
  wrongCount: number
  correctStreak: number
  lastWrongAnswer: string[] | null
  lastWrongAt: string
}

export interface WrongGroup {
  categoryId: number
  categoryPath: string
  count: number
}

export interface FavoriteItem {
  questionId: number
  categoryPath: string
  type: QuestionType
  stemPreview: string
  note: string | null
  createdAt: string
}

// ---------- 统计 ----------

export interface StatsOverview {
  answerCount: number
  correctCount: number
  correctRate: number | null
  studyDays: number
  streakDays: number
  todayAnswerCount: number
  examCount: number
}
