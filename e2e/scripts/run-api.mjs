import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { available, serviceError } from '../lib/http.mjs';

if (!await available()) {
  console.error(serviceError());
  process.exitCode = 2;
} else {
  const child = spawn(process.execPath,
    ['--test', '--test-concurrency=1', process.env.QA_SCOPE === 'm1' ? 'api/m1.test.mjs' : 'api/contract.test.mjs'],
    { cwd: fileURLToPath(new URL('..', import.meta.url)), stdio: 'inherit' });
  child.on('exit', (code, signal) => { process.exitCode = signal ? 1 : code; });
  child.on('error', error => { console.error(error.message); process.exitCode = 1; });
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => child.kill(signal));
}
