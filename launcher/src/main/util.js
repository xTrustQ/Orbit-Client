// Shared helpers: paths, JSON stores, downloads.
const { app } = require('electron');
const fs = require('fs');
const fsp = require('fs/promises');
const path = require('path');
const crypto = require('crypto');

const ROOT = path.join(app.getPath('appData'), 'OrbitClient');
// Carry over data from the client's previous name.
const LEGACY = path.join(app.getPath('appData'), 'NovaClient');
if (!fs.existsSync(ROOT) && fs.existsSync(path.join(LEGACY, 'settings.json'))) { try { fs.renameSync(LEGACY, ROOT); } catch { /* in use: start fresh */ } }
const PATHS = {
  root: ROOT,
  minecraft: path.join(ROOT, 'minecraft'), // shared libraries / assets / versions
  instances: path.join(ROOT, 'instances'),
  java: path.join(ROOT, 'java'),
  skins: path.join(ROOT, 'skins'),
  capes: path.join(ROOT, 'capes'),
  cache: path.join(ROOT, 'cache'),
  cosmetics: path.join(ROOT, 'cosmetics.json'),
};
for (const p of Object.values(PATHS)) if (!path.extname(p)) fs.mkdirSync(p, { recursive: true });

const UA = 'OrbitClient/1.0.0';

function readJson(file, fallback) {
  try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch { return fallback; }
}
function writeJson(file, data) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const tmp = file + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2));
  fs.renameSync(tmp, file);
}

/** Tiny persistent key/value store backed by a JSON file. */
class Store {
  constructor(name, defaults) {
    this.file = path.join(ROOT, name + '.json');
    this.data = { ...defaults, ...readJson(this.file, {}) };
  }
  get(k) { return this.data[k]; }
  set(k, v) { this.data[k] = v; writeJson(this.file, this.data); }
  all() { return this.data; }
  patch(obj) { Object.assign(this.data, obj); writeJson(this.file, this.data); }
}

async function fetchJson(url, opts = {}) {
  const res = await fetch(url, { ...opts, headers: { 'User-Agent': UA, ...(opts.headers || {}) } });
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    const err = new Error(`HTTP ${res.status} for ${url}${body ? ': ' + body.slice(0, 300) : ''}`);
    err.status = res.status;
    throw err;
  }
  if (res.status === 204) return null;
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

/**
 * Download a URL to a file with optional sha1 verification + progress callback.
 * Concurrent requests for the same destination share one download, each attempt writes to its
 * own temp file, size/checksum are verified, and a broken transfer is retried.
 */
const inflight = new Map();
function download(url, dest, opts = {}) {
  const key = path.resolve(dest).toLowerCase();
  if (inflight.has(key)) return inflight.get(key);
  const p = downloadWithRetry(url, dest, opts).finally(() => inflight.delete(key));
  inflight.set(key, p);
  return p;
}

async function downloadWithRetry(url, dest, { sha1, onProgress, headers, attempts = 3 } = {}) {
  if (sha1 && fs.existsSync(dest) && (await sha1File(dest)) === sha1) return dest;
  await fsp.mkdir(path.dirname(dest), { recursive: true });
  let lastErr;
  for (let i = 0; i < attempts; i++) {
    const tmp = `${dest}.${crypto.randomBytes(4).toString('hex')}.part`;
    try {
      const res = await fetch(url, { headers: { 'User-Agent': UA, ...(headers || {}) }, redirect: 'follow' });
      if (!res.ok) throw new Error(`Download failed (${res.status}): ${url}`);
      const total = Number(res.headers.get('content-length')) || 0;
      let done = 0, lastReport = 0;
      // Read → write strictly one chunk at a time so bytes can never land out of order.
      const fh = await fsp.open(tmp, 'w');
      try {
        const reader = res.body.getReader();
        for (;;) {
          const { done: end, value } = await reader.read();
          if (end) break;
          await fh.write(value);
          done += value.length;
          const now = Date.now();
          if (onProgress && (now - lastReport > 100 || done === total)) { lastReport = now; onProgress(done, total); }
        }
      } finally { await fh.close(); }
      const size = (await fsp.stat(tmp)).size;
      if (total && size !== total) throw new Error(`Incomplete download of ${path.basename(dest)} (${size}/${total} bytes)`);
      if (sha1 && (await sha1File(tmp)) !== sha1) throw new Error('Checksum mismatch for ' + path.basename(dest));
      await fsp.rm(dest, { force: true });
      await fsp.rename(tmp, dest);
      return dest;
    } catch (e) {
      lastErr = e;
      await fsp.rm(tmp, { force: true }).catch(() => {});
      if (/\((4\d\d)\)/.test(e.message)) break; // 4xx won't fix itself
      await new Promise((r) => setTimeout(r, 800 * (i + 1)));
    }
  }
  throw lastErr;
}

function sha1File(file) {
  return new Promise((resolve, reject) => {
    const h = crypto.createHash('sha1');
    fs.createReadStream(file).on('data', (d) => h.update(d)).on('end', () => resolve(h.digest('hex'))).on('error', reject);
  });
}

/** Keep a relative path inside a base directory (guards mrpack/zip entries). */
function safeJoin(base, rel) {
  const out = path.resolve(base, rel);
  if (!out.startsWith(path.resolve(base) + path.sep)) throw new Error('Unsafe path: ' + rel);
  return out;
}

const uid = () => crypto.randomBytes(6).toString('hex');

module.exports = { PATHS, UA, Store, readJson, writeJson, fetchJson, download, sha1File, safeJoin, uid };
