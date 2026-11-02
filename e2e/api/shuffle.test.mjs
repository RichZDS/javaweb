import assert from 'node:assert/strict';
import { apiSuite } from '../lib/api-suite.mjs';
import { fixture, practice, answer, correct, paper } from '../lib/fixtures.mjs';
import { items, noAnswers } from '../lib/http.mjs';

const scenario = await apiSuite('选项打乱');
function displayAnswer(q, original) {
  if (q.type === 'FILL') return original.acceptedAnswers.map(values => values[0]);
  if (!q.optionMapping) return original.correctAnswer;
  return Object.entries(q.optionMapping).filter(([, key]) => original.correctAnswer.includes(key)).map(([key]) => key).sort();
}
function mappingMatches(q, original) {
  if (!['SINGLE', 'MULTIPLE'].includes(q.type)) { assert.equal(q.optionMapping, null); return; }
  const keys = original.options.map(o => o.key);
  assert.deepEqual(Object.keys(q.optionMapping).sort(), [...keys].sort());
  assert.deepEqual(Object.values(q.optionMapping).sort(), [...keys].sort());
  assert.deepEqual(q.options.map(o => o.key), keys);
  for (const option of q.options) assert.equal(option.text, original.options.find(o => o.key === q.optionMapping[option.key]).text);
}

scenario('D2-01：练习新标签与内容映射一致，四题型判分、重读映射固定', async () => {
  const f = await fixture(); const s = await practice(f, { shuffleOptions: true });
  assert.equal(s.shuffleOptions, true);
  for (const q of s.questions) {
    const original = f.questions.find(x => x.id === q.id); mappingMatches(q, original); noAnswers(q);
    const shown = displayAnswer(q, original);
    const result = await answer(f, s, q, q.type === 'MULTIPLE' ? [...shown].reverse() : shown);
    assert.equal(result.correct, true, q.type); assert.deepEqual(result.optionMapping, q.optionMapping);
    if (q.type !== 'FILL') assert.deepEqual(result.correctAnswer, shown);
    assert.equal(result.analysisHtml, original.analysisHtml);
  }
  const stored = await f.api.get(`/api/practice/sessions/${s.id}`);
  assert.equal(stored.correctCount, 10);
  for (let i = 0; i < s.questions.length; i++) assert.deepEqual(stored.questions[i].optionMapping, s.questions[i].optionMapping);
});

scenario('D2-02：原正确字母在换位后判错，错题历史保存原标签', async t => {
  const f = await fixture({ types: ['SINGLE'] }); let s, q;
  for (let attempt = 0; attempt < 8 && !q; attempt++) {
    s = await practice(f, { shuffleOptions: true }); q = s.questions.find(x => x.optionMapping.A !== 'A');
    if (!q) await f.api.post(`/api/practice/sessions/${s.id}/finish`, {});
  }
  assert.ok(q, '没有取得正确字母换位样本，不能声称做过差分验证');
  const original = f.questions.find(x => x.id === q.id); const shownCorrect = displayAnswer(q, original);
  t.diagnostic(`原A正确；显示A映射到原${q.optionMapping.A}；显示${shownCorrect[0]}才正确`);
  const result = await answer(f, s, q, ['A']); assert.equal(result.correct, false);
  assert.deepEqual(result.userAnswer, ['A']); assert.deepEqual(result.correctAnswer, shownCorrect);
  const wrong = items(await f.api.get('/api/wrong-questions')).find(x => x.questionId === q.id);
  assert.deepEqual(wrong.lastWrongAnswer, [q.optionMapping.A]);
  const detail = await f.api.get(`/api/questions/${q.id}`);
  assert.deepEqual(detail.correctAnswer, ['A']); assert.equal(Object.hasOwn(detail, 'optionMapping'), false);
});

scenario('D2-03：练习中选项数量改变后回退原顺序，非法标签仍判错', async () => {
  const f = await fixture({ count: 1, types: ['SINGLE'] }); const q = f.questions[0];
  const s = await practice(f, { shuffleOptions: true, acceptFewer: true });
  await f.staff.put(`/api/admin/questions/${q.id}`, { ...q.fixtureBody, options: [...q.fixtureBody.options, { key: 'D', text: '丁' }] });
  const updated = await f.api.get(`/api/practice/sessions/${s.id}`);
  assert.equal(updated.questions[0].optionMapping, null); assert.deepEqual(updated.questions[0].options.map(o => o.key), ['A', 'B', 'C', 'D']);
  assert.equal((await answer(f, updated, updated.questions[0], ['A'])).correct, true);
  const second = await practice(f, { shuffleOptions: true, acceptFewer: true });
  assert.equal((await answer(f, second, second.questions[0], ['Z'])).correct, false);
});

scenario('D2-04：考试保存新标签、快照不随改题变化、结算成绩单和错题映射正确', async () => {
  const f = await fixture({ count: 4 }); const p = await paper(f, { shuffleOptions: true });
  assert.equal(p.shuffleOptions, true);
  const sheet = await f.api.post(`/api/exams/${p.id}/start`, {}, 201);
  const sent = new Map();
  for (const q of sheet.questions) {
    const original = f.questions.find(x => x.id === q.questionId); mappingMatches(q, original); noAnswers(q);
    const correctShown = displayAnswer(q, original);
    const chosen = q.type === 'MULTIPLE' ? correctShown.slice(0, 1) : correctShown;
    sent.set(q.questionId, chosen);
    const save = await f.api.put(`/api/exam-records/${sheet.recordId}/answers/${q.questionId}`, { userAnswer: chosen, version: 2, marked: true });
    assert.equal(save.accepted, true); assert.equal(save.version, 2);
  }
  const single = f.questions.find(q => q.type === 'SINGLE');
  await f.staff.put(`/api/admin/questions/${single.id}`, { ...single.fixtureBody, correctAnswer: ['B'], options: [...single.fixtureBody.options, { key: 'D', text: '改后新选项' }] });
  const resumed = await f.api.get(`/api/exam-records/${sheet.recordId}`);
  for (const q of sheet.questions) {
    assert.deepEqual(resumed.questions.find(x => x.questionId === q.questionId).optionMapping, q.optionMapping);
    assert.deepEqual(resumed.answers.find(x => x.questionId === q.questionId).userAnswer, sent.get(q.questionId));
  }
  const result = await f.api.post(`/api/exam-records/${sheet.recordId}/submit`, {});
  assert.equal(result.score, 9); assert.equal(result.correctCount, 3); assert.equal(result.wrongCount, 1);
  for (const q of sheet.questions) {
    const item = result.items.find(x => x.questionId === q.questionId); const original = f.questions.find(x => x.id === q.questionId);
    assert.deepEqual(item.optionMapping, q.optionMapping); assert.deepEqual(item.userAnswer, sent.get(q.questionId));
    if (q.type !== 'FILL') assert.deepEqual(item.correctAnswer, displayAnswer(q, original));
  }
  const multi = sheet.questions.find(q => q.type === 'MULTIPLE');
  const wrong = items(await f.api.get('/api/wrong-questions')).find(q => q.questionId === multi.questionId);
  assert.deepEqual(wrong.lastWrongAnswer, sent.get(multi.questionId).map(k => multi.optionMapping[k]));
});
