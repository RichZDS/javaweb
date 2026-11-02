import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { register, BASE_URL } from '../lib/http.mjs';

const account = await register();
const bytes = await readFile(new URL('../../app/src/static/tab/home-active.png', import.meta.url));
const body = new FormData(); body.set('file', new Blob([bytes], { type: 'image/png' }), 'qa-home-icon.png');
const saved = await account.api.post('/api/files', body, 201);
assert.match(saved.url, /^\/uploads\//);
const response = await fetch(`${BASE_URL}${saved.url}`, { signal: AbortSignal.timeout(15000) });
assert.equal(response.status, 200); assert.match(response.headers.get('content-type'), /image\/png/);
const actual = Buffer.from(await response.arrayBuffer()); assert.deepEqual(actual, bytes);
console.log(JSON.stringify({ userId: account.user.id, uploadStatus: 201, staticStatus: response.status,
  url: saved.url, bytes: actual.length, sha256: createHash('sha256').update(actual).digest('hex'), pass: true }));
