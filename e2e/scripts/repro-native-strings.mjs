import { BASE_URL } from '../lib/http.mjs';
import { m1Fixture, startM1 } from '../lib/m1-fixtures.mjs';

const f = await m1Fixture('single'); const session = await startM1(f, { acceptFewer: true });
const response = await fetch(`${BASE_URL}/api/practice/sessions/${session.id}/answers`, {
  method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${f.api.token}` },
  body: JSON.stringify({ questionId: f.questions[0].id, userAnswer: [1], timeSpentSec: 1 }), signal: AbortSignal.timeout(10000),
});
const payload = await response.json();
console.log(JSON.stringify({ contract: 'API-0.1 原生字符串', expected: 400, actual: response.status,
  code: payload.code, returnedUserAnswer: payload.data?.userAnswer, fields: payload.data?.fields }));
process.exitCode = response.status === 400 && payload.code === 'VALIDATION_FAILED' ? 0 : 1;
