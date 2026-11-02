import { readFile, readlink, writeFile, mkdir, rename, realpath } from 'node:fs/promises';
import path from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

export async function processInfo(pid) {
  if (!Number.isInteger(pid) || pid <= 1) throw new Error('拒绝使用无效进程 PID');
  try {
    const raw = await readFile(`/proc/${pid}/stat`, 'utf8');
    const fields = raw.slice(raw.lastIndexOf(')') + 2).split(' ');
    if (['Z', 'X'].includes(fields[0])) return null;
    return { pid, birth: fields[19], parent: Number(fields[1]), cwd: await readlink(`/proc/${pid}/cwd`) };
  } catch (error) { if (error.code === 'ENOENT' || error.code === 'ESRCH') return null; throw error; }
}

function within(cwd, root) { return cwd === root || cwd.startsWith(`${root}${path.sep}`); }
export async function checkOwned(record, ownerRoot) {
  const root = await realpath(ownerRoot);
  if (!record.cwd || !record.birth || !within(record.cwd, root)) throw new Error(`拒绝停止 PID ${record.pid}：登记 cwd 不属于本工作树`);
  const current = await processInfo(record.pid);
  if (!current) return false;
  if (current.birth !== record.birth || current.cwd !== record.cwd || !within(current.cwd, root)) {
    throw new Error(`拒绝停止 PID ${record.pid}：cwd 或启动时间与登记不符，保留状态文件`);
  }
  return true;
}

export async function writeRegistry(file, ownerRoot, processes) {
  await mkdir(path.dirname(file), { recursive: true });
  await writeFile(`${file}.tmp`, JSON.stringify({ ownerRoot, processes }, null, 2), { mode: 0o600 });
  await rename(`${file}.tmp`, file);
}

export async function ownedTree(record, ownerRoot) {
  if (!await checkOwned(record, ownerRoot)) return [];
  const records = [record];
  for (let i = 0; i < records.length; i++) {
    const parent = records[i];
    if (!await checkOwned(parent, ownerRoot)) continue;
    const children = await readFile(`/proc/${parent.pid}/task/${parent.pid}/children`, 'utf8').catch(error => {
      if (error.code === 'ENOENT') return ''; throw error;
    });
    for (const pid of children.trim().split(/\s+/).filter(Boolean).map(Number)) {
      const child = await processInfo(pid);
      if (!child || child.parent !== parent.pid) continue;
      await checkOwned(child, ownerRoot);
      if (!records.some(p => p.pid === child.pid)) records.push(child);
    }
  }
  return records;
}

export async function stopOwned(records, ownerRoot, registryFile, signal = 'SIGTERM') {
  const tree = [];
  for (const record of records) for (const child of await ownedTree(record, ownerRoot)) {
    if (!tree.some(p => p.pid === child.pid)) tree.push(child);
  }
  // Persist every exact PID and identity before any signal; no process-group/name matching.
  await writeRegistry(registryFile, ownerRoot, tree);
  for (const record of tree) await checkOwned(record, ownerRoot);
  for (const record of [...tree].reverse()) if (await checkOwned(record, ownerRoot)) {
    try { process.kill(record.pid, signal); } catch (error) { if (error.code !== 'ESRCH') throw error; }
  }
  for (let i = 0; i < 40; i++) {
    if ((await Promise.all(tree.map(p => processInfo(p.pid)))).every(p => !p)) return;
    await delay(100);
  }
  for (const record of [...tree].reverse()) if (await checkOwned(record, ownerRoot)) {
    try { process.kill(record.pid, 'SIGKILL'); } catch (error) { if (error.code !== 'ESRCH') throw error; }
  }
  for (let i = 0; i < 20; i++) {
    if ((await Promise.all(tree.map(p => processInfo(p.pid)))).every(p => !p)) return;
    await delay(100);
  }
  throw new Error('本次已登记进程尚未全部退出，保留状态及临时目录');
}

export function trackChild(child, ownerRoot, registryDir, label) {
  const file = path.join(registryDir, `${label}-${child.pid}.json`);
  const ready = new Promise((resolve, reject) => {
    child.once('error', reject);
    child.once('spawn', async () => {
      try {
        const record = await processInfo(child.pid);
        if (record) await checkOwned(record, ownerRoot);
        await writeRegistry(file, ownerRoot, record ? [{ ...record, label }] : []);
        resolve(record ? { ...record, label } : null);
      } catch (error) { reject(error); }
    });
  });
  return { ready, file, async stop(signal = 'SIGTERM') { const record = await ready; if (record) await stopOwned([record], ownerRoot, file, signal); } };
}
