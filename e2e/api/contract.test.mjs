import assert from 'node:assert/strict';
import test from 'node:test';
import { setTimeout as delay } from 'node:timers/promises';
import ExcelJS from 'exceljs';
import { Api, available, serviceError, register, items, noAnswers, approx, BASE_URL, unique } from '../lib/http.mjs';
import { fixture, questionBody, practice, answer, correct, incorrect, paper } from '../lib/fixtures.mjs';

// A missing server is ONE explicit failed prerequisite, not dozens of request stack traces.
const online = await available();
function scenario(name, run, options = {}) {
  if (online) test(name, { timeout: 90000, ...options }, run);
}
if (!online) test('前置条件：本地 API 服务已启动', () => {
  const error = new Error(serviceError());
  error.stack = error.message;
  throw error;
});

scenario('账号：注册、登录、me、重复用户名、缺失或无效令牌', async () => {
  const f = await register();
  const logged = await new Api().post('/api/auth/login', f.credentials);
  assert.equal(logged.user.id, f.user.id);
  const me = await new Api(logged.token).get('/api/me');
  assert.equal(me.username, f.credentials.username);
  assert.equal(me.role, 'USER');
  assert.equal((await f.api.put('/api/me', { nickname: '测试同学' })).nickname, '测试同学');
  await new Api().post('/api/auth/register', f.credentials, 409, 'USERNAME_TAKEN');
  await new Api().get('/api/me', 401, 'UNAUTHORIZED');
  await new Api('invalid-token').get('/api/me', 401, 'UNAUTHORIZED');
});

scenario('分类、目标考试、搜题与详情', async () => {
  const f = await fixture({ count: 4 });
  const roots = await f.api.get('/api/categories');
  const root = roots.find(x => x.id === f.root.id);
  assert.ok(root);
  assert.equal(root.questionCount, 4);
  assert.equal(root.doneCount, 0);
  assert.equal(root.correctRate, null);
  assert.equal(root.children.length, 0);
  const tree = await f.api.get(`/api/categories?rootId=${f.root.id}`);
  // Contract says "整棵树" but not whether the root is wrapped in an array.
  const nodes = Array.isArray(tree) ? tree : [tree];
  assert.ok(JSON.stringify(nodes).includes('测试章节'));
  const found = await f.api.get(`/api/questions/search?keyword=${f.tag}&categoryId=${f.root.id}&page=1&size=20`);
  assert.equal(found.total, 4);
  for (const q of items(found)) {
    assert.ok(q.stemPreview.length <= 80);
    const detail = await f.api.get(`/api/questions/${q.id}`);
    assert.equal(detail.categoryId, f.chapter.id);
    assert.equal(typeof detail.stemHtml, 'string');
    assert.equal(typeof detail.analysisHtml, 'string');
    assert.ok(detail.correctAnswer || detail.acceptedAnswers);
  }
});

scenario('随机练习：固定题单不重复，进度保存，结束与续练', async () => {
  const f = await fixture();
  const session = await practice(f);
  assert.equal(session.questionCount, 10);
  assert.equal(new Set(session.questions.map(q => q.id)).size, 10);
  session.questions.forEach(noAnswers);
  assert.deepEqual((await f.api.get(`/api/practice/sessions/${session.id}`)).questions.map(q => q.id), session.questions.map(q => q.id));
  assert.equal((await f.api.get('/api/practice/sessions/current')).id, session.id);
  await f.api.put(`/api/practice/sessions/${session.id}/progress`, { currentIndex: 4 });
  assert.equal((await f.api.get(`/api/practice/sessions/${session.id}`)).currentIndex, 4);
  await answer(f, session, session.questions[0]);
  const result = await f.api.post(`/api/practice/sessions/${session.id}/finish`, {});
  assert.equal(result.answeredCount, 1);
  assert.equal(result.correctCount, 1);
  assert.ok(result.durationSec >= 0);
  assert.deepEqual(result.wrongQuestionIds, []);
  assert.equal(await f.api.get('/api/practice/sessions/current'), null);
  assert.equal(items(await f.api.get('/api/practice/sessions')).length, 1);
});

scenario('抽题不足：409、acceptFewer 和空题池', async () => {
  const f = await fixture({ count: 3, types: ['SINGLE'] });
  const gap = await practice(f, {}, 409, 'POOL_INSUFFICIENT');
  assert.equal(gap.available, 3);
  assert.equal((await practice(f, { acceptFewer: true })).questionCount, 3);
  const empty = await practice(f, { types: ['FILL'], acceptFewer: true }, 409, 'POOL_INSUFFICIENT');
  assert.equal(empty.available, 0);
});

scenario('四种题型判分：全对、错答、多选少选/空选、填空归一化；重复提交不重计', async () => {
  const f = await fixture({ count: 4 });
  for (const shouldPass of [true, false]) {
    const session = await practice(f, { acceptFewer: true });
    for (const q of session.questions) {
      const value = shouldPass ? correct(q) : incorrect(q);
      const result = await answer(f, session, q, value);
      assert.equal(result.correct, shouldPass, q.type);
      assert.equal(result.alreadySubmitted, false);
      const replay = await answer(f, session, q, shouldPass ? incorrect(q) : correct(q));
      assert.equal(replay.alreadySubmitted, true);
      assert.equal(replay.correct, shouldPass);
      assert.deepEqual(replay.userAnswer, result.userAnswer);
    }
    const done = await f.api.get(`/api/practice/sessions/${session.id}`);
    assert.equal(done.status, 'COMPLETED');
    assert.equal(done.answeredCount, 4);
  }
  const empty = await practice(f, { types: ['MULTIPLE'], acceptFewer: true });
  assert.equal((await answer(f, empty, empty.questions[0], [])).correct, false);
  const overview = await f.api.get('/api/stats/overview');
  assert.equal(overview.answerCount, 9);
  assert.equal(overview.correctCount, 4);
});

scenario('背题带答案、拒绝提交且不计统计；UNDONE 排除已作答', async () => {
  const f = await fixture({ count: 4 });
  const recite = await practice(f, { reciteMode: true, acceptFewer: true });
  assert.ok(recite.questions.every(q => q.correctAnswer || q.acceptedAnswers));
  await f.api.post(`/api/practice/sessions/${recite.id}/answers`, {
    questionId: recite.questions[0].id, userAnswer: correct(recite.questions[0]), timeSpentSec: 1,
  }, 409, 'CONFLICT');
  await f.api.post(`/api/practice/sessions/${recite.id}/finish`, {});
  assert.equal((await f.api.get('/api/stats/overview')).answerCount, 0);
  const session = await practice(f, { acceptFewer: true });
  await answer(f, session, session.questions[0]);
  const undone = await practice(f, { scope: 'UNDONE', acceptFewer: true });
  assert.equal(undone.questionCount, 3);
  assert.ok(undone.questions.every(q => q.id !== session.questions[0].id));
});

scenario('顺序练习与每日一练复用；每日全部完成才记打卡', async () => {
  const f = await fixture();
  const seq = await practice(f, { mode: 'SEQUENTIAL', categoryIds: [f.chapter.id] });
  assert.equal(seq.questionCount, 12);
  assert.equal((await practice(f, { mode: 'SEQUENTIAL', categoryIds: [f.chapter.id] }, 200)).id, seq.id);
  const daily = await practice(f, { mode: 'DAILY' });
  assert.equal(daily.questionCount, 10);
  assert.equal((await practice(f, { mode: 'DAILY' }, 200)).id, daily.id);
  await f.api.post(`/api/practice/sessions/${daily.id}/finish`, {});
  assert.equal((await f.api.get(`/api/practice/sessions/${daily.id}`)).completedAt, null);
  for (const q of daily.questions) await answer(f, daily, q);
  assert.ok((await f.api.get(`/api/practice/sessions/${daily.id}`)).completedAt);
  assert.equal((await f.api.get('/api/stats/overview')).streakDays, 1);
});

scenario('错题生命周期：累加、连对三次移出、再错恢复、手动移出', async () => {
  const f = await fixture({ count: 1, types: ['SINGLE'] });
  const q = f.questions[0];
  const list = async () => items(await f.api.get('/api/wrong-questions?sort=MOST'));
  for (let i = 1; i <= 2; i++) {
    const session = await practice(f, { acceptFewer: true });
    await answer(f, session, q, incorrect(q));
    assert.equal((await list())[0].wrongCount, i);
  }
  assert.equal((await f.api.get('/api/wrong-questions/groups'))[0].count, 1);
  for (let streak = 1; streak <= 3; streak++) {
    const session = await practice(f, { mode: 'WRONG', count: undefined, acceptFewer: true });
    await answer(f, session, q);
    const wrong = await list();
    if (streak < 3) assert.equal(wrong[0].correctStreak, streak);
    else assert.equal(wrong.length, 0);
  }
  const again = await practice(f, { acceptFewer: true });
  await answer(f, again, q, incorrect(q));
  const restored = (await list())[0];
  assert.equal(restored.wrongCount, 3);
  assert.equal(restored.correctStreak, 0);
  assert.deepEqual(restored.lastWrongAnswer, ['B']);
  await f.api.delete(`/api/wrong-questions/${q.id}`);
  assert.equal((await list()).length, 0);
});

scenario('RETRY：练习/已结算考试来源、原顺序、未答排除、无错题 409、他人记录 404', async () => {
  const f = await fixture({ count: 4, types: ['SINGLE'] });
  const source = await practice(f, { acceptFewer: true });
  for (const [i, q] of source.questions.entries()) await answer(f, source, q, i < 2 ? incorrect(q) : correct(q));
  const retry = await f.api.post('/api/practice/sessions', { mode: 'RETRY', fromSessionId: source.id }, 201);
  assert.deepEqual(retry.questions.map(q => q.id), source.questions.slice(0, 2).map(q => q.id));
  for (const q of retry.questions) await answer(f, retry, q);
  assert.ok(items(await f.api.get('/api/wrong-questions')).every(q => q.correctStreak === 1));
  const noWrong = await f.api.post('/api/practice/sessions', { mode: 'RETRY', fromSessionId: retry.id }, 409, 'POOL_INSUFFICIENT');
  assert.equal(noWrong.available, 0);
  const stranger = await register();
  await stranger.api.post('/api/practice/sessions', { mode: 'RETRY', fromSessionId: source.id }, 404, 'NOT_FOUND');
  const p = await paper(f);
  const sheet = await f.api.post(`/api/exams/${p.id}/start`, {}, 201);
  const qid = sheet.questions[0].questionId;
  await f.api.put(`/api/exam-records/${sheet.recordId}/answers/${qid}`, { userAnswer: ['B'], version: 1, marked: false });
  await f.api.post(`/api/exam-records/${sheet.recordId}/submit`, {});
  const examRetry = await f.api.post('/api/practice/sessions', { mode: 'RETRY', fromExamRecordId: sheet.recordId }, 201);
  assert.deepEqual(examRetry.questions.map(q => q.id), [qid]);
  await stranger.api.post('/api/practice/sessions', { mode: 'RETRY', fromExamRecordId: sheet.recordId }, 404, 'NOT_FOUND');
  const blankSheet = await f.api.post(`/api/exams/${p.id}/start`, {}, 201);
  await f.api.post(`/api/exam-records/${blankSheet.recordId}/submit`, {});
  const blank = await f.api.post('/api/practice/sessions', { mode: 'RETRY', fromExamRecordId: blankSheet.recordId }, 409, 'POOL_INSUFFICIENT');
  assert.equal(blank.available, 0);
});

scenario('收藏、笔记更新、收藏练习和取消清除笔记', async () => {
  const f = await fixture({ count: 1 });
  const q = f.questions[0];
  await f.api.put(`/api/favorites/${q.id}`, { note: '第一次笔记' });
  await f.api.put(`/api/favorites/${q.id}`, { note: '更新笔记' });
  const favorites = items(await f.api.get('/api/favorites'));
  assert.equal(favorites.length, 1);
  assert.equal(favorites[0].note, '更新笔记');
  const session = await practice(f, { mode: 'FAVORITE', count: undefined, acceptFewer: true });
  assert.equal(session.questions[0].id, q.id);
  const detail = await f.api.get(`/api/questions/${q.id}`);
  assert.equal(detail.favorited, true);
  assert.equal(detail.note, '更新笔记');
  await f.api.delete(`/api/favorites/${q.id}`);
  assert.equal(items(await f.api.get('/api/favorites')).length, 0);
  assert.equal((await f.api.get(`/api/questions/${q.id}`)).note, null);
});

scenario('考试：开考复用、版本防覆盖、查题受限、快照、交卷幂等、成绩单', async () => {
  const f = await fixture({ count: 4 });
  const p = await paper(f);
  assert.equal(p.totalScore, 12);
  assert.ok((await f.api.get('/api/exams')).some(x => x.paperId === p.id));
  assert.equal(typeof (await f.api.get(`/api/exams/${p.id}`)).scoringRules, 'string');
  const sheet = await f.api.post(`/api/exams/${p.id}/start`, {}, 201);
  assert.equal((await f.api.post(`/api/exams/${p.id}/start`, {})).recordId, sheet.recordId);
  assert.equal(sheet.questions.length, 4);
  sheet.questions.forEach(noAnswers);
  assert.ok(Date.parse(sheet.deadlineAt) > Date.parse(sheet.serverTime));
  const single = f.questions.find(q => q.type === 'SINGLE');
  await f.api.get(`/api/questions/${single.id}`, 403, 'EXAM_IN_PROGRESS');
  const save = (q, version, userAnswer) => f.api.put(`/api/exam-records/${sheet.recordId}/answers/${q.id}`, { userAnswer, version, marked: true });
  assert.equal((await save(single, 2, correct(single))).accepted, true);
  const old = await save(single, 1, incorrect(single));
  assert.equal(old.accepted, false);
  assert.equal(old.version, 2);
  assert.equal((await save(single, 2, incorrect(single))).accepted, false);
  const saved = (await f.api.get(`/api/exam-records/${sheet.recordId}`)).answers.find(a => a.questionId === single.id);
  assert.deepEqual(saved.userAnswer, ['A']);
  assert.equal(saved.marked, true);
  await f.staff.put(`/api/admin/questions/${single.id}`, { ...single.fixtureBody, correctAnswer: ['B'] });
  // Snapshot must retain original A. MULTIPLE is deliberately only partly correct.
  const multi = f.questions.find(q => q.type === 'MULTIPLE');
  const judge = f.questions.find(q => q.type === 'JUDGE');
  await save(multi, 1, ['A']);
  await save(judge, 1, ['T']);
  const first = await f.api.post(`/api/exam-records/${sheet.recordId}/submit`, {});
  const second = await f.api.post(`/api/exam-records/${sheet.recordId}/submit`, {});
  assert.deepEqual(second, first);
  assert.equal(first.status, 'FINISHED');
  assert.equal(first.score, 6);
  assert.equal(first.correctCount, 2);
  assert.equal(first.wrongCount, 1);
  assert.equal(first.unansweredCount, 1);
  assert.equal(first.passed, true);
  assert.equal(first.items.length, 4);
  assert.ok(first.byType.length >= 3 && first.byCategory.length === 1);
  assert.equal(first.items.find(x => x.questionId === multi.id).score, 0);
  assert.deepEqual(first.items.find(x => x.questionId === single.id).correctAnswer, ['A']);
  assert.ok(Date.parse(first.submittedAt) >= Date.parse(first.startedAt));
  assert.ok(first.durationSec >= 0);
  const wrong = items(await f.api.get('/api/wrong-questions'));
  assert.deepEqual(wrong.map(x => x.questionId), [multi.id]);
  assert.equal(items(await f.api.get(`/api/exam-records?paperId=${p.id}`)).length, 1);
  assert.equal((await f.api.get('/api/stats/overview')).examCount, 1);
  await f.api.get(`/api/questions/${single.id}`);
});

scenario('考试截止：管理员创建一分钟卷，迟到保存被拒且访问自动结算', async t => {
  const f = await fixture({ count: 1 });
  const p = await paper(f, { durationMin: 1, passScore: 3 });
  const sheet = await f.api.post(`/api/exams/${p.id}/start`, {}, 201);
  const q = sheet.questions[0];
  await f.api.put(`/api/exam-records/${sheet.recordId}/answers/${q.questionId}`, {
    userAnswer: ['A'], version: 1, marked: false,
  });
  const waitMs = Date.parse(sheet.deadlineAt) - Date.parse(sheet.serverTime) + 1500;
  assert.ok(waitMs > 0 && waitMs <= 62000, '一分钟卷截止时间必须在约一分钟后');
  let remaining = waitMs;
  while (remaining > 0) {
    const chunk = Math.min(15000, remaining);
    t.diagnostic(`等待服务器截止，剩余 ${Math.ceil(remaining / 1000)} 秒`);
    await delay(chunk);
    remaining -= chunk;
  }
  await f.api.put(`/api/exam-records/${sheet.recordId}/answers/${q.questionId}`, {
    userAnswer: ['B'], version: 2, marked: false,
  }, 409, 'EXAM_EXPIRED');
  const result = await f.api.get(`/api/exam-records/${sheet.recordId}`);
  assert.equal(result.status, 'FINISHED');
  assert.equal(result.score, 3);
  assert.deepEqual(result.items[0].userAnswer, ['A']);
  assert.deepEqual(await f.api.post(`/api/exam-records/${sheet.recordId}/submit`, {}), result);
}, { timeout: 100000 });

scenario('统计对账：作答次数、正确率、北京时间趋势、不同题已做数、薄弱点', async () => {
  const f = await fixture({ count: 6, types: ['SINGLE'] });
  const first = await practice(f, { acceptFewer: true });
  for (const [i, q] of first.questions.entries()) await answer(f, first, q, i < 3 ? correct(q) : incorrect(q));
  const second = await practice(f, { acceptFewer: true });
  await answer(f, second, second.questions[0]);
  await answer(f, second, second.questions[0]);
  const overview = await f.api.get('/api/stats/overview');
  assert.equal(overview.answerCount, 7);
  assert.equal(overview.correctCount, 4);
  approx(overview.correctRate, 4 / 7);
  assert.equal(overview.studyDays, 1);
  assert.equal(overview.todayAnswerCount, 7);
  const chapters = await f.api.get('/api/stats/categories');
  const chapter = chapters.find(x => x.categoryId === f.chapter.id);
  assert.equal(chapter.questionCount, 6);
  assert.equal(chapter.doneCount, 6);
  assert.equal(chapter.answerCount, 7);
  approx(chapter.correctRate, 4 / 7);
  const tree = await f.api.get(`/api/categories?rootId=${f.root.id}`);
  const walk = nodes => nodes.flatMap(n => [n, ...walk(n.children || [])]);
  const node = walk(Array.isArray(tree) ? tree : [tree]).find(x => x.id === f.chapter.id);
  assert.equal(node.doneCount, 6);
  approx(node.correctRate, 4 / 7);
  const daily = await f.api.get('/api/stats/daily?days=30');
  assert.equal(daily.length, 30);
  assert.equal(daily.reduce((sum, d) => sum + d.answerCount, 0), 7);
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date());
  assert.equal(daily.find(x => x.date === today).answerCount, 7);
  assert.ok(daily.filter(x => x.answerCount === 0).every(x => x.correctRate === null));
  const weak = await f.api.get('/api/stats/weak?days=30&limit=5');
  assert.equal(weak[0].sampleCount, 6);
  const firstCorrect = first.questions.slice(0, 3).some(q => q.id === second.questions[0].id);
  approx(weak[0].correctRate, (firstCorrect ? 3 : 4) / 6);
  assert.deepEqual(await f.api.get('/api/stats/exams'), []);
  assert.equal((await f.api.get('/api/stats/leaderboard?period=WEEK')).me.answerCount, 7);
});

scenario('权限：后台 403、他人练习和考试 404、禁用后登录 403', async () => {
  const f = await fixture({ count: 1 });
  for (const path of ['categories', 'questions', 'papers', 'users', 'feedback', 'dashboard']) {
    await f.api.get(`/api/admin/${path}`, 403, 'FORBIDDEN');
  }
  const stranger = await register();
  const session = await practice(f, { acceptFewer: true });
  await stranger.api.get(`/api/practice/sessions/${session.id}`, 404, 'NOT_FOUND');
  await stranger.api.post(`/api/practice/sessions/${session.id}/answers`, {
    questionId: f.questions[0].id, userAnswer: ['A'], timeSpentSec: 1,
  }, 404, 'NOT_FOUND');
  const p = await paper(f, { passScore: 3 });
  const sheet = await f.api.post(`/api/exams/${p.id}/start`, {}, 201);
  await stranger.api.get(`/api/exam-records/${sheet.recordId}`, 404, 'NOT_FOUND');
  await f.staff.put(`/api/admin/users/${f.user.id}/status`, { status: 'DISABLED' });
  await new Api().post('/api/auth/login', f.credentials, 403, 'FORBIDDEN');
});

scenario('后台分类 CRUD 与非空分类删除冲突', async () => {
  const f = await fixture({ count: 0 });
  await f.staff.delete(`/api/admin/categories/${f.root.id}`, 409, 'CONFLICT');
  const renamed = await f.staff.put(`/api/admin/categories/${f.chapter.id}`, { name: '改名章节', sort: 2 });
  assert.equal(renamed.name, '改名章节');
  assert.equal(renamed.sort, 2);
  assert.ok(Array.isArray(await f.staff.get('/api/admin/categories')));
  assert.equal(await f.staff.delete(`/api/admin/categories/${f.chapter.id}`), null);
  await f.staff.delete(`/api/admin/categories/${f.subject.id}`);
  await f.staff.delete(`/api/admin/categories/${f.root.id}`);
});

scenario('后台题目 CRUD、版本、发布校验、已作答删除归档、Markdown XSS', async () => {
  const f = await fixture({ count: 1 });
  const q = f.questions[0];
  const changed = await f.staff.put(`/api/admin/questions/${q.id}`, { ...q.fixtureBody, stem: '修改后的原创题' });
  assert.equal(changed.version, q.version + 1);
  assert.equal((await f.staff.get(`/api/admin/questions/${q.id}`)).stem, changed.stem);
  await f.staff.post('/api/admin/questions', { ...q.fixtureBody, correctAnswer: [], analysis: '' }, 400, 'VALIDATION_FAILED');
  const draft = await f.staff.post('/api/admin/questions', { ...q.fixtureBody, stem: `${f.tag} 未发布`, status: 'DRAFT' }, 201);
  await f.api.get(`/api/questions/${draft.id}`, 404, 'NOT_FOUND');
  const deleted = await f.staff.delete(`/api/admin/questions/${draft.id}`);
  assert.deepEqual(deleted, { deleted: true, archived: false });
  const session = await practice(f, { acceptFewer: true });
  await answer(f, session, q);
  assert.deepEqual(await f.staff.delete(`/api/admin/questions/${q.id}`), { deleted: false, archived: true });
  await f.api.get(`/api/questions/${q.id}`, 404, 'NOT_FOUND');
  const preview = await f.staff.post('/api/admin/questions/preview', { markdown: '<script>alert(1)</script>\n<img src=x onerror=alert(1)>\n```java\nint x = 1;\n```' });
  assert.doesNotMatch(preview.html, /<script\b|<[^>]+\sonerror\s*=|href\s*=\s*["']\s*javascript:/i);
  assert.match(preview.html, /<pre[\s>]/);
});

scenario('后台固定/规则试卷 CRUD、上架题量缺口与上下架', async () => {
  const f = await fixture({ count: 4, types: ['SINGLE'] });
  const p = await paper(f, {}, false);
  const changed = await f.staff.put(`/api/admin/papers/${p.id}`, {
    title: `${f.tag} 修改卷`, categoryId: f.root.id, mode: 'FIXED', durationMin: 5, passScore: 6,
    questions: p.questions.map(q => ({ questionId: q.questionId, score: q.score, sort: q.sort })),
  });
  assert.equal(changed.title, `${f.tag} 修改卷`);
  assert.equal((await f.staff.get(`/api/admin/papers/${p.id}`)).totalScore, 12);
  await f.staff.put(`/api/admin/papers/${p.id}/status`, { status: 'ONLINE' });
  assert.ok((await f.api.get('/api/exams')).some(x => x.paperId === p.id));
  await f.staff.put(`/api/admin/papers/${p.id}/status`, { status: 'OFFLINE' });
  assert.ok(!(await f.api.get('/api/exams')).some(x => x.paperId === p.id));
  await f.staff.delete(`/api/admin/papers/${p.id}`);
  await f.staff.get(`/api/admin/papers/${p.id}`, 404, 'NOT_FOUND');
  const rule = await paper(f, { mode: 'RULE', questions: undefined, rules: [
    { categoryId: f.chapter.id, type: 'SINGLE', count: 10, score: 2 },
  ] }, false);
  const gaps = await f.staff.put(`/api/admin/papers/${rule.id}/status`, { status: 'ONLINE' }, 409, 'POOL_INSUFFICIENT');
  assert.equal(gaps.gaps[0].need, 10);
  assert.equal(gaps.gaps[0].available, 4);
  assert.equal(gaps.gaps[0].categoryId, f.chapter.id);
});

scenario('题目纠错提交、管理员处理与看板', async () => {
  const f = await fixture({ count: 1 });
  const feedback = await f.api.post(`/api/questions/${f.questions[0].id}/feedback`, {
    type: 'ANALYSIS_UNCLEAR', content: `${f.tag} 请补充说明`,
  }, 201);
  const page = await f.staff.get('/api/admin/feedback?status=PENDING&page=1&size=100');
  const row = items(page).find(x => x.questionId === f.questions[0].id);
  assert.ok(row, `缺少本测试纠错记录 ${feedback?.id || ''}`);
  await f.staff.put(`/api/admin/feedback/${row.id}`, { status: 'RESOLVED', reply: '已补充原创说明' });
  const resolved = items(await f.staff.get('/api/admin/feedback?status=RESOLVED&page=1&size=100')).find(x => x.id === row.id);
  assert.equal(resolved.reply, '已补充原创说明');
  assert.ok(resolved.handledAt);
  const dashboard = await f.staff.get('/api/admin/dashboard');
  for (const key of ['userCount', 'questionCount', 'paperCount', 'todayAnswerCount', 'pendingFeedbackCount']) assert.equal(typeof dashboard[key], 'number');
  assert.equal(dashboard.daily.length, 14);
});

scenario('Excel：生成有效/错误行，验证部分导入及模板下载', async () => {
  const f = await fixture({ count: 0 });
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet('题目');
  sheet.addRow(['分类路径', '题型', '题干', '选项A', '选项B', '选项C', '选项D', '选项E', '选项F', '答案', '解析', '难度', '标签', '来源']);
  const path = `${f.root.name}/${f.subject.name}/${f.chapter.name}`;
  sheet.addRow([path, '单选', `${f.tag} Excel 原创有效行`, '甲', '乙', '', '', '', '', 'A', '甲为指定正确项', 2, 'qa', '原创']);
  sheet.addRow([path, '单选', `${f.tag} Excel 原创错误行`, '甲', '乙', '', '', '', '', 'Z', '答案键不存在', 2, 'qa', '原创']);
  const form = new FormData();
  form.set('file', new Blob([await workbook.xlsx.writeBuffer()], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), `${unique()}.xlsx`);
  const result = await f.staff.post('/api/admin/questions/import', form);
  assert.equal(result.total, 2);
  assert.equal(result.success, 1);
  assert.equal(result.failed.length, 1);
  assert.equal(result.failed[0].row, 3);
  assert.ok(result.failed[0].reason);
  const imported = await f.staff.get(`/api/admin/questions?categoryId=${f.chapter.id}`);
  assert.equal(imported.total, 1);
  assert.match(imported.items[0].stem, /有效行/);
  const response = await fetch(`${BASE_URL}/api/admin/questions/import/template`, {
    headers: { Authorization: `Bearer ${f.staff.token}` }, signal: AbortSignal.timeout(10000),
  });
  assert.equal(response.status, 200);
  const template = new ExcelJS.Workbook();
  await template.xlsx.load(Buffer.from(await response.arrayBuffer()));
  assert.equal(template.worksheets[0].getRow(1).getCell(1).value, '分类路径');
});
