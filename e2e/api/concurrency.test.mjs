import assert from 'node:assert/strict';
import { apiSuite } from '../lib/api-suite.mjs';
import { m1Fixture, startM1 } from '../lib/m1-fixtures.mjs';
import { answer, correct } from '../lib/fixtures.mjs';

const scenario = await apiSuite('练习并发');
const stable = ({ alreadySubmitted, ...result }) => result;

scenario('QA-004 PR-12：同练习同题并发提交不同答案，保留唯一结果且只计一次', async t => {
  const f = await m1Fixture('single'); const q = f.questions[0];
  let correctCount = 0; let wrongCount = 0;
  for (let round = 1; round <= 3; round++) {
    const session = await startM1(f, { acceptFewer: true });
    await Promise.all([f.api.get('/api/me'), f.api.get(`/api/practice/sessions/${session.id}`)]);
    const replies = await Promise.all([answer(f, session, q, ['A']), answer(f, session, q, ['B'])]);
    const stored = await f.api.get(`/api/practice/sessions/${session.id}`);
    const stats = await f.api.get('/api/stats/overview');
    const wrong = (await f.api.get('/api/wrong-questions')).items.find(x => x.questionId === q.id);
    correctCount += replies[0].correct ? 1 : 0; wrongCount += replies[0].correct ? 0 : 1;
    t.diagnostic(JSON.stringify({ round, sessionId: session.id, replies, storedAnswer: stored.questions[0].userAnswer,
      answeredCount: stored.answeredCount, correctCount: stored.correctCount, stats, wrongCount: wrong?.wrongCount || 0 }));
    assert.deepEqual(stable(replies[0]), stable(replies[1]));
    assert.deepEqual(replies.map(r => r.alreadySubmitted).sort(), [false, true]);
    assert.deepEqual(stored.questions[0].userAnswer, replies[0].userAnswer);
    assert.equal(stored.questions[0].correct, replies[0].correct);
    assert.equal(stored.status, 'COMPLETED'); assert.equal(stored.answeredCount, 1);
    assert.equal(stored.correctCount, Number(replies[0].correct));
    assert.equal(stats.answerCount, round); assert.equal(stats.correctCount, correctCount);
    assert.equal(wrong?.wrongCount || 0, wrongCount);
  }
});

scenario('QA-004 WR-03：同题在两份练习并发答错，两次有效提交均计入错题次数', async t => {
  const f = await m1Fixture('single'); const q = f.questions[0];
  for (let round = 1; round <= 3; round++) {
    const first = await startM1(f, { acceptFewer: true });
    const second = await startM1(f, { acceptFewer: true });
    assert.notEqual(first.id, second.id);
    const replies = await Promise.all([answer(f, first, q, ['B']), answer(f, second, q, ['B'])]);
    const sessions = await Promise.all([first, second].map(s => f.api.get(`/api/practice/sessions/${s.id}`)));
    const stats = await f.api.get('/api/stats/overview');
    const wrong = (await f.api.get('/api/wrong-questions')).items.find(x => x.questionId === q.id);
    t.diagnostic(JSON.stringify({ round, sessionIds: sessions.map(s => s.id), answeredCounts: sessions.map(s => s.answeredCount), stats, wrongCount: wrong?.wrongCount }));
    replies.forEach(r => { assert.equal(r.correct, false); assert.equal(r.alreadySubmitted, false); });
    sessions.forEach(s => { assert.equal(s.answeredCount, 1); assert.equal(s.correctCount, 0); assert.equal(s.status, 'COMPLETED'); });
    assert.equal(stats.answerCount, round * 2); assert.equal(stats.correctCount, 0);
    assert.equal(wrong.wrongCount, round * 2);
  }
});

scenario('QA-004 PR-12 ST-03：同练习两道不同题并发提交，不丢失进度及统计增量', async t => {
  const f = await m1Fixture('four');
  for (let round = 1; round <= 3; round++) {
    const session = await startM1(f, { acceptFewer: true });
    const questions = session.questions.slice(0, 2);
    const replies = await Promise.all(questions.map(q => answer(f, session, q, correct(q))));
    const stored = await f.api.get(`/api/practice/sessions/${session.id}`);
    const stats = await f.api.get('/api/stats/overview');
    t.diagnostic(JSON.stringify({ round, sessionId: session.id, answeredCount: stored.answeredCount, correctCount: stored.correctCount, stats }));
    replies.forEach(r => { assert.equal(r.correct, true); assert.equal(r.alreadySubmitted, false); });
    assert.equal(stored.answeredCount, 2); assert.equal(stored.correctCount, 2);
    for (const q of questions) assert.equal(stored.questions.find(x => x.id === q.id).submitted, true);
    assert.equal(stats.answerCount, round * 2); assert.equal(stats.correctCount, round * 2);
    const finished = await f.api.post(`/api/practice/sessions/${session.id}/finish`, {});
    assert.equal(finished.answeredCount, 2); assert.equal(finished.correctCount, 2);
  }
});
