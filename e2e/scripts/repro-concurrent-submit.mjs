import { m2Fixture, startExam, saveExam, submitExam } from '../lib/m2-fixtures.mjs';
import { correct } from '../lib/fixtures.mjs';

const f = await m2Fixture();
let failures = 0;
for (let attempt = 1; attempt <= 3; attempt++) {
  const sheet = await startExam(f);
  for (const q of sheet.questions) if (q.type !== 'FILL') await saveExam(f, sheet, q.questionId, q.type === 'MULTIPLE' ? ['A'] : correct(q));
  // Warm independent requests before the simultaneous submit pair.
  await Promise.all([f.api.get(`/api/exam-records/${sheet.recordId}`), f.api.get('/api/me')]);
  const responses = await Promise.all([submitExam(f, sheet), submitExam(f, sheet)]);
  const persisted = await f.api.get(`/api/exam-records/${sheet.recordId}`);
  const stats = await f.api.get('/api/stats/overview');
  const multi = sheet.questions.find(q => q.type === 'MULTIPLE');
  const wrong = (await f.api.get('/api/wrong-questions')).items.find(q => q.questionId === multi.questionId);
  const same = responses.every(r => JSON.stringify(r) === JSON.stringify(persisted));
  const once = stats.answerCount === attempt * 3 && stats.correctCount === attempt * 2 && stats.examCount === attempt && wrong?.wrongCount === attempt;
  if (!same || !once) failures++;
  console.log(JSON.stringify({ attempt, recordId: sheet.recordId,
    responses: responses.map(r => ({ score: r.score, correctCount: r.correctCount, status: r.status, submittedAt: r.submittedAt })),
    get: { score: persisted.score, correctCount: persisted.correctCount, status: persisted.status, submittedAt: persisted.submittedAt },
    stats: { answerCount: stats.answerCount, correctCount: stats.correctCount, examCount: stats.examCount },
    wrongCount: wrong?.wrongCount, responsesMatchGet: same, settledOnce: once }));
}
process.exitCode = failures ? 1 : 0;
