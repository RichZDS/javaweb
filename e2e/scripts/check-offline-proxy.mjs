import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';

test('断网代理自检：在线转发、断开不触达上游、重连恢复、关闭端口', async t => {
  let received = 0;
  const upstream = http.createServer(async (req, res) => {
    for await (const chunk of req) void chunk;
    received++;
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ code: 'OK', data: { accepted: true } }));
  });
  await new Promise(resolve => upstream.listen(0, '127.0.0.1', resolve));
  t.after(async () => { upstream.closeAllConnections(); await new Promise(resolve => upstream.close(resolve)); });
  process.env.BASE_URL = `http://127.0.0.1:${upstream.address().port}`;
  const { offlineProxy } = await import('../lib/offline-proxy.mjs');
  const proxy = await offlineProxy(t, 'local-harness-token');
  assert.equal((await proxy.put('/answers/1', { version: 1 })).status, 200);
  assert.equal(received, 1);
  proxy.setOnline(false);
  await assert.rejects(proxy.put('/answers/1', { version: 2 }));
  assert.equal(received, 1);
  proxy.setOnline(true);
  assert.equal((await proxy.put('/answers/1', { version: 2 })).body.data.accepted, true);
  assert.equal(received, 2);
});
