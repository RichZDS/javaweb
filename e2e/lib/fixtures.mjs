import assert from 'node:assert/strict';
import { admin, register, unique } from './http.mjs';

export function questionBody(categoryId, type, stem = unique('原创测试题')) {
  return {
    categoryId, type, stem, difficulty: 2, tags: ['qa'], source: '原创自动化测试',
    status: 'PUBLISHED', analysis: '本题为隔离测试夹具，依据题干指定的标准答案判分。',
    ...(type === 'SINGLE' || type === 'MULTIPLE' ? {
      options: ['甲', '乙', '丙'].map((text, i) => ({ key: 'ABC'[i], text })),
      correctAnswer: type === 'SINGLE' ? ['A'] : ['A', 'C'],
    } : type === 'JUDGE' ? { correctAnswer: ['T'] } : {
      acceptedAnswers: [['int', 'Integer'], ['5']],
      fillOptions: { ignoreCase: true, ignoreSpaces: true },
    }),
  };
}

export async function fixture({ count = 12, types = ['SINGLE', 'MULTIPLE', 'JUDGE', 'FILL'] } = {}) {
  const staff = await admin();
  const account = await register();
  const tag = unique('qa');
  const root = await staff.post('/api/admin/categories', { parentId: null, name: tag, sort: 0 }, 201);
  const subject = await staff.post('/api/admin/categories', { parentId: root.id, name: '测试科目', sort: 0 }, 201);
  const chapter = await staff.post('/api/admin/categories', { parentId: subject.id, name: '测试章节', sort: 0 }, 201);
  assert.ok(root.id && subject.id && chapter.id, '创建分类必须返回 id');
  await account.api.put('/api/me', { targetCategoryId: root.id });
  const questions = [];
  for (let i = 0; i < count; i++) {
    const body = questionBody(chapter.id, types[i % types.length], `${tag} 原创校验 ${i + 1}`);
    const question = await staff.post('/api/admin/questions', body, 201);
    assert.ok(question.id, '创建题目必须返回 id');
    questions.push({ ...question, fixtureBody: body });
  }
  return { ...account, staff, root, subject, chapter, questions, tag };
}

export const correct = question => question.type === 'FILL' ? [' INTEGER ', ' 5 '] :
  question.type === 'MULTIPLE' ? ['C', 'A'] : question.type === 'JUDGE' ? ['T'] : ['A'];
export const incorrect = question => question.type === 'FILL' ? ['wrong', '0'] :
  question.type === 'MULTIPLE' ? ['A'] : question.type === 'JUDGE' ? ['F'] : ['B'];

export function practice(f, overrides = {}, status = 201, code = 'OK') {
  return f.api.post('/api/practice/sessions', {
    mode: 'RANDOM', categoryIds: [f.root.id], count: 10, scope: 'ALL',
    reciteMode: false, acceptFewer: false, ...overrides,
  }, status, code);
}

export function answer(f, session, question, userAnswer = correct(question)) {
  return f.api.post(`/api/practice/sessions/${session.id}/answers`, {
    questionId: question.id, userAnswer, timeSpentSec: 2,
  });
}

export async function paper(f, overrides = {}, publish = true) {
  const result = await f.staff.post('/api/admin/papers', {
    title: `${f.tag} 固定卷`, categoryId: f.root.id, description: '原创测试卷',
    mode: 'FIXED', durationMin: 5, passScore: 6,
    questions: f.questions.slice(0, 4).map((q, i) => ({ questionId: q.id, score: 3, sort: i })),
    ...overrides,
  }, 201);
  if (publish) return f.staff.put(`/api/admin/papers/${result.id}/status`, { status: 'ONLINE' });
  return result;
}
