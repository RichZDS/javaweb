import http from 'node:http';
import assert from 'node:assert/strict';
import { BASE_URL } from './http.mjs';

// This cuts transport to the real API. Tests explicitly resend after reconnect;
// it does not implement or claim to verify the App's offline storage/queue.
export async function offlineProxy(t, token) {
  const target = new URL(BASE_URL);
  assert.ok(['localhost', '127.0.0.1', '[::1]'].includes(target.hostname) || process.env.QA_STAGING === '1' && target.hostname === '43.142.166.108', '断网代理仅连接本机或已授权预发布目标');
  let online = true;
  const server = http.createServer(async (req, res) => {
    if (!online) { req.socket.destroy(); return; }
    try {
      const chunks = [];
      for await (const chunk of req) chunks.push(chunk);
      const body = Buffer.concat(chunks);
      const upstream = await fetch(`${BASE_URL}${req.url}`, {
        method: req.method, headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        ...(body.length ? { body } : {}), signal: AbortSignal.timeout(10000),
      });
      res.writeHead(upstream.status, { 'Content-Type': 'application/json' });
      res.end(await upstream.text());
    } catch { res.writeHead(502); res.end('{"code":"PROXY_ERROR"}'); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(async () => { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); });
  const url = `http://127.0.0.1:${server.address().port}`;
  return {
    setOnline(value) { online = value; },
    async put(path, body) {
      const response = await fetch(`${url}${path}`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), signal: AbortSignal.timeout(5000),
      });
      return { status: response.status, body: await response.json() };
    },
  };
}
