import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { available, serviceError } from '../lib/http.mjs';

const suites = { m1: ['api/m1.test.mjs'], m2: ['api/m2.test.mjs'], 'm1+m2': ['api/m1.test.mjs', 'api/m2.test.mjs'], m3: ['api/m3.test.mjs'], full: ['api/contract.test.mjs'] };
const scope = process.env.QA_SCOPE || 'full';
if (!suites[scope]) {
  console.error('QA_SCOPE 只能是 m1 / m2 / m1+m2 / m3 / full');
  process.exitCode = 2;
} else if (process.env.QA_COLLECT_ONLY === '1' && !['m2', 'm3'].includes(scope)) {
  console.error('QA_COLLECT_ONLY 仅支持 m2 / m3；本次未运行任何业务用例。');
  process.exitCode = 2;
} else if (process.env.QA_COLLECT_ONLY !== '1' && !await available()) {
  console.error(serviceError());
  process.exitCode = 2;
} else {
  const child = spawn(process.execPath,
    ['--test', '--test-concurrency=1', ...suites[scope]],
    { cwd: fileURLToPath(new URL('..', import.meta.url)), stdio: 'inherit' });
  child.on('exit', (code, signal) => { process.exitCode = signal ? 1 : code; });
  child.on('error', error => { console.error(error.message); process.exitCode = 1; });
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => child.kill(signal));
}
