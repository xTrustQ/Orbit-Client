// Small persistent key-value store for things that must survive restarts (synced settings, login tokens).
// With UPSTASH_REDIS_REST_URL + UPSTASH_REDIS_REST_TOKEN set (free at upstash.com) data lives in Redis —
// needed on hosts whose disk is wiped on every restart, like Render's free plan. Otherwise: JSON files in DATA_DIR.
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const URL_ = (process.env.UPSTASH_REDIS_REST_URL || '').replace(/\/+$/, '');
const TOKEN = process.env.UPSTASH_REDIS_REST_TOKEN || '';
const DIR = path.join(process.env.DATA_DIR || path.join(__dirname, 'data'), 'kv');
const redis = !!(URL_ && TOKEN);
if (!redis) fs.mkdirSync(DIR, { recursive: true });

async function cmd(args) {
  const r = await fetch(URL_, { method: 'POST', headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' }, body: JSON.stringify(args) });
  const j = await r.json();
  if (j.error) throw new Error('Store: ' + j.error);
  return j.result;
}
const file = (key) => path.join(DIR, crypto.createHash('sha1').update(key).digest('hex') + '.json');

async function get(key) {
  if (redis) { const v = await cmd(['GET', key]); return v == null ? null : JSON.parse(v); }
  try { return JSON.parse(fs.readFileSync(file(key), 'utf8')); } catch { return null; }
}
/** ttlSeconds: optional expiry */
async function set(key, value, ttlSeconds) {
  const s = JSON.stringify(value);
  if (redis) return cmd(ttlSeconds ? ['SET', key, s, 'EX', String(ttlSeconds)] : ['SET', key, s]);
  fs.writeFileSync(file(key) + '.tmp', s); fs.renameSync(file(key) + '.tmp', file(key));
}

module.exports = { get, set, persistent: redis };
