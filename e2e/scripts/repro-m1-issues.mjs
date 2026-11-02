import { register, BASE_URL } from '../lib/http.mjs';

const { api } = await register();
const probes = [
  { id: 'QA-001-missing', method: 'GET', path: '/api/questions/search?page=1&size=1', expected: 400 },
  { id: 'QA-001-empty', method: 'GET', path: '/api/questions/search?keyword=&page=1&size=1', expected: 400 },
  { id: 'QA-001-spaces', method: 'GET', path: '/api/questions/search?keyword=%20%20', expected: 400 },
  { id: 'QA-001-long', method: 'GET', path: `/api/questions/search?keyword=${'a'.repeat(51)}`, expected: 400 },
  { id: 'QA-002', method: 'PUT', path: '/api/me/settings', body: { wrongRemoveThreshold: 1.5 }, expected: 400 },
  { id: 'QA-003', method: 'PUT', path: '/api/me/settings', body: { wrongRemoveThreshold: '2' }, expected: 400 },
  { id: 'RETEST-01-sort', method: 'GET', path: '/api/wrong-questions?sort=UNKNOWN', expected: 400 },
];
let failed = 0;
for (const probe of probes) {
  const response = await fetch(`${BASE_URL}${probe.path}`, {
    method: probe.method, headers: { Authorization: `Bearer ${api.token}`, 'Content-Type': 'application/json' },
    ...(probe.body ? { body: JSON.stringify(probe.body) } : {}), signal: AbortSignal.timeout(10000),
  });
  const payload = await response.json();
  const pass = response.status === probe.expected;
  if (!pass) failed++;
  console.log(JSON.stringify({ id: probe.id, method: probe.method, path: probe.path, request: probe.body,
    expected: probe.expected, actual: response.status, code: payload.code, message: payload.message,
    ...(probe.body ? { savedThreshold: payload.data?.wrongRemoveThreshold } : {}),
    fields: payload.data?.fields, pass }));
}
process.exitCode = failed ? 1 : 0;
