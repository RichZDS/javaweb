/**
 * 选项打乱（docs/api.md 6.1）：打乱的题里选项、作答、正确答案都用「新标签」（A、B、C… 按显示顺序），
 * 解析原文里的字母是「原标签」。optionMapping 是 新标签 → 原标签，没打乱时为 null。
 * 这里只做标签换算和展示文字，不参与判分。
 */

export type OptionMapping = Record<string, string> | null | undefined

/** 这道题是否真的换了顺序（mapping 存在且至少一个字母不同） */
export function isShuffled(mapping: OptionMapping): boolean {
  return !!mapping && Object.entries(mapping).some(([shown, original]) => shown !== original)
}

/** 新标签 → 原标签；没打乱或找不到时原样返回 */
export function originalKey(mapping: OptionMapping, shown: string): string {
  return (mapping && mapping[shown]) || shown
}

/** 一组新标签换成原标签，保持顺序 */
export function originalKeys(mapping: OptionMapping, shown: readonly string[] | null | undefined): string[] {
  return (shown ?? []).map((k) => originalKey(mapping, k))
}

/** 选项旁的注明：「原选项 C」；没打乱或字母没变时为空串 */
export function originalNote(mapping: OptionMapping, shown: string): string {
  if (!isShuffled(mapping)) return ''
  const original = originalKey(mapping, shown)
  return original === shown ? '' : `原选项 ${original}`
}

/**
 * 正确答案旁的注明：「对应解析里的 C、A」。
 * 显示顺序按原标签字母排，方便和解析对照；没打乱、或换算后和显示的完全一样时为空串。
 */
export function analysisNote(mapping: OptionMapping, shownCorrect: readonly string[] | null | undefined): string {
  if (!isShuffled(mapping) || !shownCorrect?.length) return ''
  const originals = originalKeys(mapping, shownCorrect)
  if (originals.every((k, i) => k === shownCorrect[i])) return ''
  return `对应解析里的 ${[...originals].sort().join('、')}`
}
