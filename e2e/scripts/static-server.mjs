import http from 'node:http';
import { createReadStream } from 'node:fs';
import { stat, realpath } from 'node:fs/promises';
import path from 'node:path';

const [rootArg, portArg, apiArg] = process.argv.slice(2);
const root = await realpath(rootArg);
const api = new URL(apiArg);
if (!['127.0.0.1', 'localhost', '[::1]'].includes(api.hostname)) throw new Error('仅允许代理本机 API');
const types = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json',
  '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg',
  '.webp': 'image/webp', '.woff2': 'font/woff2', '.ico': 'image/x-icon',
};
const server = http.createServer(async (req, res) => {
  const pathname = new URL(req.url, 'http://localhost').pathname;
  if (pathname.startsWith('/api/') || pathname.startsWith('/uploads/')) {
    const upstream = http.request({
      hostname: api.hostname, port: api.port, path: req.url, method: req.method,
      headers: { ...req.headers, host: api.host }, timeout: 15000,
    }, response => { res.writeHead(response.statusCode, response.headers); response.pipe(res); });
    upstream.on('timeout', () => upstream.destroy());
    upstream.on('error', () => { if (!res.headersSent) res.writeHead(502); res.end('本地 API 不可用'); });
    req.on('aborted', () => upstream.destroy());
    req.pipe(upstream);
    return;
  }
  if (!['GET', 'HEAD'].includes(req.method)) { res.writeHead(405); res.end(); return; }
  try {
    let file = path.resolve(root, `.${decodeURIComponent(pathname)}`);
    if (!file.startsWith(`${root}${path.sep}`) && file !== root) { res.writeHead(403); res.end(); return; }
    let info = await stat(file).catch(() => null);
    if (info?.isDirectory()) { file = path.join(file, 'index.html'); info = await stat(file).catch(() => null); }
    if (!info?.isFile()) {
      if (path.extname(pathname)) { res.writeHead(404); res.end(); return; }
      file = path.join(root, 'index.html');
    }
    file = await realpath(file);
    if (!file.startsWith(`${root}${path.sep}`)) { res.writeHead(403); res.end(); return; }
    res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-store' });
    if (req.method === 'HEAD') res.end();
    else createReadStream(file).on('error', () => res.destroy()).pipe(res);
  } catch { res.writeHead(404); res.end(); }
});
server.listen(Number(portArg), '127.0.0.1', () => console.log(`静态服务 http://127.0.0.1:${portArg}`));
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => {
  server.closeAllConnections();
  server.close(() => process.exit(0));
});
