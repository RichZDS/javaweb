import assert from 'node:assert/strict';
import { setTimeout as delay } from 'node:timers/promises';
import { m1Seed, m1Fixture } from './m1-fixtures.mjs';

export const paperTitles = { fixed: 'QA M2 固定卷', minute: 'QA M2 一分钟截止卷', rule: 'QA M2 交叉规则卷' };

export function m2Seed() {
  const seed = m1Seed();
  seed.questions = seed.questions.map((q, index) => ({ ...q, ref: `qa-m2-q${index + 1}` }));
  const questions = seed.questions.slice(0, 4).map((q, index) => ({ ref: q.ref, score: index + 1 }));
  seed.papers = [
    ...['fixed', 'minute'].map(kind => ({
      title: paperTitles[kind], category: 'qa-m1', mode: 'FIXED', durationMin: kind === 'minute' ? 1 : 5,
      passScore: 6, description: '隔离验收，四题型分值分别 1/2/3/4', questions,
    })),
    { title: paperTitles.rule, category: 'qa-m1', mode: 'RULE', durationMin: 5, passScore: 4,
      description: '覆盖有交集的规则分类，验证同场题目不重复', rules: [
        { category: 'qa-m1', type: 'SINGLE', count: 2, score: 2 },
        { category: 'qa-m1-mixed', type: 'SINGLE', count: 2, score: 2 },
      ] },
  ];
  return seed;
}

export async function m2Fixture(kind = 'fixed') {
  const f = await m1Fixture('mixed');
  const papers = await f.api.get(`/api/exams?categoryId=${f.root.id}`);
  const paper = papers.find(p => p.title === paperTitles[kind]);
  assert.ok(paper, '缺少 M2 试卷，请 QA_SCOPE=m2 npm run stack:up');
  return { ...f, paper };
}

export const startExam = (f, status = 201) => f.api.post(`/api/exams/${f.paper.paperId}/start`, {}, status);
export const saveExam = (f, sheet, questionId, userAnswer, version = 1, marked = false, status = 200, code = 'OK') =>
  f.api.put(`/api/exam-records/${sheet.recordId}/answers/${questionId}`, { userAnswer, version, marked }, status, code);
export const submitExam = (f, sheet) => f.api.post(`/api/exam-records/${sheet.recordId}/submit`, {});

export async function waitPastDeadline(t, sheet, afterMs) {
  const duration = Date.parse(sheet.deadlineAt) - Date.parse(sheet.serverTime);
  assert.ok(duration > 0 && duration <= 61000, '仅允许一分钟夹具；避免意外等待正式卷时长');
  let remaining = duration + afterMs;
  while (remaining > 0) {
    t.diagnostic(`等待一分钟卷截止，剩余 ${Math.ceil(remaining / 1000)} 秒`);
    const chunk = Math.min(15000, remaining);
    await delay(chunk); remaining -= chunk;
  }
}
