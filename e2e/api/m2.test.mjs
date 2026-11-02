import assert from 'node:assert/strict';
import { apiSuite } from '../lib/api-suite.mjs';
import { register, items, noAnswers, approx } from '../lib/http.mjs';
import { m1Fixture, startM1 } from '../lib/m1-fixtures.mjs';
import { m2Fixture, startExam, saveExam, submitExam, waitPastDeadline, paperTitles } from '../lib/m2-fixtures.mjs';
import { answer, correct, incorrect } from '../lib/fixtures.mjs';
import { offlineProxy } from '../lib/offline-proxy.mjs';

const scenario = await apiSuite('M2');

scenario('EX-01/02/03/19：目录/须知、同卷复用、不泄露答案、考试中禁查题', async () => {
  const f = await m2Fixture();
  const catalog = await f.api.get('/api/exams');
  assert.ok(catalog.every(p => p.categoryId === f.root.id));
  assert.ok(catalog.some(p => p.title === paperTitles.fixed));
  const notice = await f.api.get(`/api/exams/${f.paper.paperId}`);
  assert.equal(notice.questionCount, 4); assert.equal(notice.totalScore, 10);
  assert.equal(notice.myAttempts, 0); assert.equal(notice.myBestScore, null);
  assert.equal(typeof notice.scoringRules, 'string');
  const sheet = await startExam(f); const reused = await startExam(f, 200);
  assert.equal(reused.recordId, sheet.recordId); assert.equal(reused.deadlineAt, sheet.deadlineAt);
  assert.equal((await f.api.get(`/api/exams/${f.paper.paperId}`)).myAttempts, 0);
  sheet.questions.forEach(noAnswers);
  assert.equal(new Set(sheet.questions.map(q => q.questionId)).size, 4);
  await f.api.get(`/api/questions/${sheet.questions[0].questionId}`, 403, 'EXAM_IN_PROGRESS');
  const outside = f.questions.find(q => !sheet.questions.some(x => x.questionId === q.id));
  await f.api.get(`/api/questions/${outside.id}`);
  await submitExam(f, sheet);
  await f.api.get(`/api/questions/${sheet.questions[0].questionId}`);
});

scenario('EX-11/12/23：答案版本、同版本/旧版本拒绝、恢复已保存答案和标记', async () => {
  const f = await m2Fixture(); const sheet = await startExam(f); const qid = sheet.questions[0].questionId;
  assert.equal((await saveExam(f, sheet, qid, ['A'], 2, true)).accepted, true);
  for (const version of [1, 2]) {
    const old = await saveExam(f, sheet, qid, ['B'], version);
    assert.equal(old.accepted, false); assert.equal(old.version, 2);
  }
  let saved = (await f.api.get(`/api/exam-records/${sheet.recordId}`)).answers.find(q => q.questionId === qid);
  assert.deepEqual(saved.userAnswer, ['A']); assert.equal(saved.marked, true); assert.ok(saved.savedAt);
  await saveExam(f, sheet, qid, ['A'], 3, false);
  saved = (await startExam(f, 200)).answers.find(q => q.questionId === qid);
  assert.equal(saved.version, 3); assert.equal(saved.marked, false);
  await submitExam(f, sheet);
});

scenario('EX-08/11：真实断开本地传输后补发，新版本保留；不替代 App 离线缓存测试', async t => {
  const f = await m2Fixture(); const sheet = await startExam(f); const qid = sheet.questions[0].questionId;
  const proxy = await offlineProxy(t, f.api.token);
  const path = `/api/exam-records/${sheet.recordId}/answers/${qid}`;
  await saveExam(f, sheet, qid, ['B'], 1);
  proxy.setOnline(false);
  const pending = { userAnswer: ['A'], version: 2, marked: true };
  await assert.rejects(proxy.put(path, pending));
  const offline = (await f.api.get(`/api/exam-records/${sheet.recordId}`)).answers.find(q => q.questionId === qid);
  assert.deepEqual(offline.userAnswer, ['B']); assert.equal(offline.version, 1);
  proxy.setOnline(true);
  const replay = await proxy.put(path, pending);
  assert.equal(replay.status, 200); assert.equal(replay.body.data.accepted, true);
  assert.equal((await saveExam(f, sheet, qid, ['B'], 1)).accepted, false);
  const result = await submitExam(f, sheet);
  assert.deepEqual(result.items[0].userAnswer, ['A']); assert.equal(result.items[0].score, 1);
});

scenario('EX-07/15/16/24 ST-02/03：加权成绩、未答排除、重复/并发交卷不重计', async t => {
  const f = await m2Fixture(); const sheet = await startExam(f);
  for (const q of sheet.questions) {
    if (q.type === 'FILL') continue;
    await saveExam(f, sheet, q.questionId, q.type === 'MULTIPLE' ? ['A'] : correct(q));
  }
  const [a, b] = await Promise.all([submitExam(f, sheet), submitExam(f, sheet)]);
  const persisted = await f.api.get(`/api/exam-records/${sheet.recordId}`);
  t.diagnostic(`并发交卷 record=${sheet.recordId}，两次响应分数=${a.score}/${b.score}，随后GET=${persisted.score}`);
  assert.deepEqual(a, persisted); assert.deepEqual(b, persisted);
  assert.deepEqual(a, b); assert.equal(a.score, 4); assert.equal(a.totalScore, 10);
  assert.equal(a.correctCount, 2); assert.equal(a.wrongCount, 1); assert.equal(a.unansweredCount, 1);
  assert.equal(a.passed, false); assert.equal(a.items.length, 4);
  assert.ok(a.items.every(q => Number.isInteger(q.blankCount) && Object.hasOwn(q, 'imageUrl')));
  assert.equal(a.items.find(q => q.type === 'FILL').blankCount, 2);
  assert.equal(a.items.reduce((sum, q) => sum + q.score, 0), a.score);
  assert.equal(a.byType.find(x => x.type === 'MULTIPLE').score, 0);
  assert.equal(a.byType.find(x => x.type === 'JUDGE').total, 3);
  assert.equal(a.byCategory[0].total, 10); approx(a.byCategory[0].rate, 0.4);
  const stats = await f.api.get('/api/stats/overview');
  assert.equal(stats.answerCount, 3); assert.equal(stats.correctCount, 2); assert.equal(stats.examCount, 1);
  approx(stats.correctRate, 2 / 3);
  const wrong = items(await f.api.get('/api/wrong-questions'));
  assert.deepEqual(wrong.map(q => q.questionId), [sheet.questions.find(q => q.type === 'MULTIPLE').questionId]);
  assert.equal(wrong[0].wrongCount, 1);
  const retry = await f.api.post('/api/practice/sessions', { mode: 'RETRY', fromExamRecordId: sheet.recordId }, 201);
  assert.deepEqual(retry.questions.map(q => q.id), wrong.map(q => q.questionId));
  assert.equal(items(await f.api.get(`/api/exam-records?paperId=${f.paper.paperId}`)).length, 1);
});

scenario('EX-09/10/14：一分钟卷断网改答，截止后补发拒绝，只按已收到答案结算', async t => {
  const f = await m2Fixture('minute'); const sheet = await startExam(f);
  const savedQ = sheet.questions[0]; const neverSent = sheet.questions[1];
  await saveExam(f, sheet, savedQ.questionId, ['A'], 1);
  const proxy = await offlineProxy(t, f.api.token); proxy.setOnline(false);
  const late = { userAnswer: ['B'], version: 2, marked: false };
  await assert.rejects(proxy.put(`/api/exam-records/${sheet.recordId}/answers/${savedQ.questionId}`, late));
  await waitPastDeadline(t, sheet, 1500);
  proxy.setOnline(true);
  const response = await proxy.put(`/api/exam-records/${sheet.recordId}/answers/${savedQ.questionId}`, late);
  assert.equal(response.status, 409); assert.equal(response.body.code, 'EXAM_EXPIRED');
  await saveExam(f, sheet, neverSent.questionId, ['A', 'C'], 1, false, 409, 'EXAM_EXPIRED');
  const result = await f.api.get(`/api/exam-records/${sheet.recordId}`);
  assert.equal(result.status, 'FINISHED'); assert.equal(result.score, 1); assert.equal(result.unansweredCount, 3);
  assert.ok(result.items.every(q => Number.isInteger(q.blankCount) && Object.hasOwn(q, 'imageUrl')));
  assert.equal(result.submittedAt, sheet.deadlineAt);
  assert.deepEqual(result.items[0].userAnswer, ['A']);
  assert.equal((await f.api.get('/api/stats/overview')).answerCount, 1);
  assert.deepEqual(items(await f.api.get('/api/wrong-questions')), []);
}, { timeout: 100000 });

scenario('EX-13：不访问记录详情/交卷，截止后由后台结算并进入考试统计', async t => {
  const f = await m2Fixture('minute'); const sheet = await startExam(f);
  await saveExam(f, sheet, sheet.questions[0].questionId, ['A']);
  await waitPastDeadline(t, sheet, 35000);
  // Observe through statistics before accessing the record; GET record itself can settle it.
  const stats = await f.api.get('/api/stats/overview');
  assert.equal(stats.examCount, 1); assert.equal(stats.answerCount, 1);
  const results = await f.api.get(`/api/stats/exams?paperId=${f.paper.paperId}`);
  assert.equal(results.length, 1); assert.equal(results[0].recordId, sheet.recordId);
  assert.equal(results[0].score, 1);
  const settled = await f.api.get(`/api/exam-records/${sheet.recordId}`);
  assert.equal(settled.status, 'FINISHED'); assert.equal(settled.submittedAt, sheet.deadlineAt);
}, { timeout: 135000 });

scenario('EX-20 ST-09：同卷两次成绩、最好成绩、考试历史与统计一致', async () => {
  const f = await m2Fixture();
  const first = await startExam(f);
  for (const q of first.questions) await saveExam(f, first, q.questionId, correct(q));
  const perfect = await submitExam(f, first); assert.equal(perfect.score, 10);
  const second = await startExam(f); const blank = await submitExam(f, second);
  assert.notEqual(second.recordId, first.recordId); assert.equal(blank.score, 0); assert.equal(blank.unansweredCount, 4);
  const current = (await f.api.get('/api/exams')).find(p => p.paperId === f.paper.paperId);
  assert.equal(current.myAttempts, 2); assert.equal(current.myBestScore, 10); assert.equal(current.inProgressRecordId, null);
  const trend = await f.api.get(`/api/stats/exams?paperId=${f.paper.paperId}`);
  assert.deepEqual(trend.map(x => x.score).sort((a, b) => a - b), [0, 10]);
  for (const item of trend) approx(item.rate, item.score / item.totalScore);
  const stats = await f.api.get('/api/stats/overview');
  assert.equal(stats.answerCount, 4); assert.equal(stats.correctCount, 4); assert.equal(stats.examCount, 2);
  assert.equal((await f.api.post('/api/practice/sessions', { mode: 'RETRY', fromExamRecordId: first.recordId }, 409, 'POOL_INSUFFICIENT')).available, 0);
});

scenario('EX-21：交叉规则分类按数量抽取，四道单选不重复、总分八分', async () => {
  const f = await m2Fixture('rule'); const sheet = await startExam(f);
  assert.equal(sheet.questions.length, 4); assert.equal(sheet.totalScore, 8);
  assert.equal(new Set(sheet.questions.map(q => q.questionId)).size, 4);
  assert.ok(sheet.questions.every(q => q.type === 'SINGLE' && q.score === 2));
  const mixedIds = new Set(f.questions.map(q => q.id));
  assert.ok(sheet.questions.filter(q => mixedIds.has(q.questionId)).length >= 2);
  await submitExam(f, sheet);
});

scenario('NF-01：他人考试读取、保存、交卷和 RETRY 全部 404', async () => {
  const f = await m2Fixture(); const sheet = await startExam(f); const other = await register();
  await other.api.get(`/api/exam-records/${sheet.recordId}`, 404, 'NOT_FOUND');
  await other.api.put(`/api/exam-records/${sheet.recordId}/answers/${sheet.questions[0].questionId}`, { userAnswer: ['A'], version: 1, marked: false }, 404, 'NOT_FOUND');
  await other.api.post(`/api/exam-records/${sheet.recordId}/submit`, {}, 404, 'NOT_FOUND');
  await submitExam(f, sheet);
  await other.api.post('/api/practice/sessions', { mode: 'RETRY', fromExamRecordId: sheet.recordId }, 404, 'NOT_FOUND');
});

scenario('ST-01/02/03/07/08：空态、作答条数、不同题数、最近作答薄弱点、30日连续', async () => {
  const f = await m1Fixture();
  const initial = await f.api.get('/api/stats/overview');
  assert.equal(initial.answerCount, 0); assert.equal(initial.correctRate, null);
  const s = await startM1(f); const selected = s.questions.slice(0, 6);
  for (const [i, q] of selected.entries()) await answer(f, s, q, i < 3 ? correct(q) : incorrect(q));
  await answer(f, s, selected[0], incorrect(selected[0]));
  const again = await startM1(f, { mode: 'SEQUENTIAL' }); await answer(f, again, selected[3]);
  const overview = await f.api.get('/api/stats/overview');
  assert.equal(overview.answerCount, 7); assert.equal(overview.correctCount, 4); approx(overview.correctRate, 4 / 7);
  assert.equal(overview.studyDays, 1); assert.equal(overview.todayAnswerCount, 7);
  const chapter = (await f.api.get('/api/stats/categories')).find(c => c.categoryId === f.chapter.id);
  assert.equal(chapter.doneCount, 6); assert.equal(chapter.answerCount, 7); approx(chapter.correctRate, 4 / 7);
  const weak = await f.api.get('/api/stats/weak?days=30&limit=5');
  assert.equal(weak[0].sampleCount, 6); approx(weak[0].correctRate, 4 / 6);
  const daily = await f.api.get('/api/stats/daily?days=30');
  assert.equal(daily.length, 30); assert.equal(new Set(daily.map(d => d.date)).size, 30);
  assert.equal(daily.reduce((sum, d) => sum + d.answerCount, 0), 7);
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date());
  assert.equal(daily.find(d => d.date === today).answerCount, 7);
  const dates = daily.map(d => Date.parse(`${d.date}T00:00:00Z`)).sort((a, b) => a - b);
  for (let i = 1; i < dates.length; i++) assert.equal(dates[i] - dates[i - 1], 86400000);
  assert.ok(daily.filter(d => d.answerCount === 0).every(d => d.correctRate === null));
});

scenario('ST-04/08：不足五道不进薄弱点，切换目标隔离统计并可显式查询原考试', async () => {
  const f = await m1Fixture('four'); const s = await startM1(f, { acceptFewer: true });
  for (const q of s.questions) await answer(f, s, q, incorrect(q));
  assert.deepEqual(await f.api.get('/api/stats/weak?days=30&limit=5'), []);
  const otherRoot = (await f.api.get('/api/categories')).find(r => r.id !== f.root.id);
  assert.ok(otherRoot); await f.api.put('/api/me', { targetCategoryId: otherRoot.id });
  assert.equal((await f.api.get('/api/stats/overview')).answerCount, 0);
  assert.equal((await f.api.get(`/api/stats/overview?categoryId=${f.root.id}`)).answerCount, 4);
});

scenario('DY-08：周榜按作答条数排序，最多20项，本人计数与排名可核对', async () => {
  const high = await m1Fixture('single'); const peer = await m1Fixture('single'); const low = await m1Fixture('single');
  for (const [f, count] of [[high, 5], [peer, 5], [low, 3]]) {
    for (let i = 0; i < count; i++) await answer(f, await startM1(f, { acceptFewer: true }), f.questions[0]);
  }
  const a = await high.api.get('/api/stats/leaderboard?period=WEEK');
  const b = await low.api.get('/api/stats/leaderboard?period=WEEK');
  const tied = await peer.api.get('/api/stats/leaderboard?period=WEEK');
  assert.equal(a.me.answerCount, 5); assert.equal(b.me.answerCount, 3); assert.ok(a.me.rank < b.me.rank);
  assert.equal(tied.me.rank, a.me.rank);
  assert.ok(a.items.length <= 20);
  assert.equal(a.items[0].rank, 1);
  for (let i = 1; i < a.items.length; i++) {
    assert.ok(a.items[i - 1].answerCount >= a.items[i].answerCount);
    assert.equal(a.items[i].rank, a.items[i - 1].answerCount === a.items[i].answerCount ? a.items[i - 1].rank : i + 1);
  }
  assert.equal((await high.api.get('/api/stats/overview')).answerCount, a.me.answerCount);
});

scenario('API-0.2：无目标考试列全卷，统计 days/limit/period 越界返回400', async () => {
  const f = await register();
  const papers = await f.api.get('/api/exams');
  assert.ok(new Set(papers.map(p => p.categoryId)).size > 1);
  for (const path of ['/api/stats/daily?days=0', '/api/stats/daily?days=366', '/api/stats/weak?limit=0', '/api/stats/weak?limit=51', '/api/stats/leaderboard?period=MONTH']) {
    await f.api.get(path, 400, 'VALIDATION_FAILED');
  }
});
