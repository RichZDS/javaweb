/** 枚举的中文名 */
import type { FeedbackType, PracticeMode, PracticeScope } from '@/api/types'

export const MODE_LABEL: Record<PracticeMode, string> = {
  RANDOM: '随机练习',
  SEQUENTIAL: '顺序练习',
  WRONG: '错题练习',
  FAVORITE: '收藏练习',
  DAILY: '每日一练',
  TAG: '专项练习',
  RETRY: '重做错题',
}

export const SCOPE_LABEL: Record<PracticeScope, string> = {
  ALL: '全部题',
  UNDONE: '只抽没做过的',
  WRONG_EVER: '只抽做错过的',
}

export const FEEDBACK_LABEL: Record<FeedbackType, string> = {
  ANSWER_WRONG: '答案有误',
  STEM_WRONG: '题干有误',
  ANALYSIS_UNCLEAR: '解析看不懂',
  OTHER: '其他问题',
}
