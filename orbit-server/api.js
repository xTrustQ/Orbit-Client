// Orbit cosmetics API: every Orbit player publishes what they wear (hat, wings, aura, Orbit cape) and
// fetches what the players around them wear. Publishing is protected with Mojang's own "joined server"
// check, so nobody can change someone else's cosmetics.
//
//   POST /api/auth/start   {uuid, name}            -> {serverId}
//        (the game calls Mojang's sessionserver "join" with that serverId, like joining a real server)
//   POST /api/auth/finish  {name, serverId}        -> {token, uuid}      (we ask Mojang "hasJoined")
//   PUT  /api/cosmetics    Bearer token, {hat, wings, aura, cape: base64 PNG | null}
//   GET  /api/cosmetics?uuids=a,b,c                -> {uuid: {hat, wings, aura, cape, updated}}
//   GET  /api/capes/<sha1>.png                     -> the cape image
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const DATA = process.env.DATA_DIR || path.join(__dirname, 'data');
const CAPES = path.join(DATA, 'capes');
fs.mkdirSync(CAPES, { recursive: true });
const DB_FILE = path.join(DATA, 'cosmetics.json');
const TOKENS_FILE = path.join(DATA, 'tokens.json');
const UUID = /^[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}$/i;
const NAME = /^[A-Za-z0-9_ ]{1,24}$/;
const dash = (u) => u.replace(/-/g, '').toLowerCase().replace(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/, '$1-$2-$3-$4-$5');

const load = (f, d) => { try { return JSON.parse(fs.readFileSync(f, 'utf8')); } catch { return d; } };
let db = load(DB_FILE, {});
let tokens = load(TOKENS_FILE, {}); // token -> {uuid, exp}
const challenges = new Map();       // serverId -> {uuid, name, exp}
let saveTimer = null;
const save = () => {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    fs.writeFileSync(DB_FILE + '.tmp', JSON.stringify(db)); fs.renameSync(DB_FILE + '.tmp', DB_FILE);
    fs.writeFileSync(TOKENS_FILE + '.tmp', JSON.stringify(tokens)); fs.renameSync(TOKENS_FILE + '.tmp', TOKENS_FILE);
  }, 500);
};

// tiny per-IP rate limit
const buckets = new Map();
function allow(ip, cost = 1) {
  const now = Date.now();
  const b = buckets.get(ip) || { t: 60, at: now };
  b.t = Math.min(60, b.t + (now - b.at) / 1000 * 2); b.at = now;
  if (b.t < cost) { buckets.set(ip, b); return false; }
  b.t -= cost; buckets.set(ip, b); return true;
}
setInterval(() => { const now = Date.now(); for (const [k, v] of challenges) if (v.exp < now) challenges.delete(k); for (const [k, v] of Object.entries(tokens)) if (v.exp < now) delete tokens[k]; }, 60_000);

function body(req, limit) {
  return new Promise((resolve, reject) => {
    let size = 0; const chunks = [];
    req.on('data', (c) => { size += c.length; if (size > limit) { reject(new Error('Too large')); req.destroy(); } else chunks.push(c); });
    req.on('end', () => { try { resolve(chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {}); } catch { reject(new Error('Bad JSON')); } });
    req.on('error', reject);
  });
}
const send = (res, code, obj, type = 'application/json') => {
  res.writeHead(code, { 'Content-Type': type, 'Access-Control-Allow-Origin': '*', 'Cache-Control': type === 'image/png' ? 'public, max-age=31536000, immutable' : 'no-store' });
  res.end(type === 'application/json' ? JSON.stringify(obj) : obj);
};
const clean = (v) => (typeof v === 'string' && NAME.test(v) ? v : 'None');

async function handle(req, res) {
  const url = new URL(req.url, 'http://x');
  const ip = req.headers['x-forwarded-for']?.split(',')[0].trim() || req.socket.remoteAddress;
  if (req.method === 'OPTIONS') { res.writeHead(204, { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'Authorization, Content-Type', 'Access-Control-Allow-Methods': 'GET, POST, PUT' }); return res.end(); }
  try {
    if (url.pathname === '/' || url.pathname === '/api') return send(res, 200, { name: 'Orbit server', players: Object.keys(db).length });

    if (url.pathname === '/api/auth/start' && req.method === 'POST') {
      if (!allow(ip, 5)) return send(res, 429, { error: 'Slow down' });
      const b = await body(req, 1024);
      if (!UUID.test(b.uuid || '') || !/^\w{1,16}$/.test(b.name || '')) return send(res, 400, { error: 'Bad uuid/name' });
      const serverId = crypto.randomBytes(16).toString('hex');
      challenges.set(serverId, { uuid: dash(b.uuid), name: b.name, exp: Date.now() + 60_000 });
      return send(res, 200, { serverId });
    }

    if (url.pathname === '/api/auth/finish' && req.method === 'POST') {
      if (!allow(ip, 5)) return send(res, 429, { error: 'Slow down' });
      const b = await body(req, 1024);
      const ch = challenges.get(b.serverId);
      if (!ch || ch.name !== b.name) return send(res, 400, { error: 'Unknown challenge' });
      challenges.delete(b.serverId);
      const r = await fetch(`https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${encodeURIComponent(ch.name)}&serverId=${encodeURIComponent(b.serverId)}`);
      if (r.status !== 200) return send(res, 401, { error: 'Mojang could not confirm this account' });
      const prof = await r.json();
      const uuid = dash(prof.id);
      if (uuid !== ch.uuid) return send(res, 401, { error: 'Account mismatch' });
      const token = crypto.randomBytes(24).toString('base64url');
      tokens[token] = { uuid, exp: Date.now() + 30 * 24 * 3600_000 };
      save();
      return send(res, 200, { token, uuid });
    }

    if (url.pathname === '/api/cosmetics' && req.method === 'PUT') {
      if (!allow(ip, 3)) return send(res, 429, { error: 'Slow down' });
      const tok = (req.headers.authorization || '').replace(/^Bearer\s+/i, '');
      const t = tokens[tok];
      if (!t || t.exp < Date.now()) return send(res, 401, { error: 'Sign in again' });
      const b = await body(req, 600_000);
      let cape = null;
      if (typeof b.cape === 'string' && b.cape.length) {
        const png = Buffer.from(b.cape, 'base64');
        if (png.length > 400_000 || png.readUInt32BE(0) !== 0x89504e47) return send(res, 400, { error: 'Cape must be a PNG under 400 KB' });
        cape = crypto.createHash('sha1').update(png).digest('hex');
        const f = path.join(CAPES, cape + '.png');
        if (!fs.existsSync(f)) fs.writeFileSync(f, png);
      } else if (typeof b.capeHash === 'string' && /^[0-9a-f]{40}$/.test(b.capeHash) && fs.existsSync(path.join(CAPES, b.capeHash + '.png'))) cape = b.capeHash;
      db[t.uuid] = { hat: clean(b.hat), wings: clean(b.wings), aura: clean(b.aura), cape, updated: Date.now() };
      save();
      return send(res, 200, db[t.uuid]);
    }

    if (url.pathname === '/api/cosmetics' && req.method === 'GET') {
      if (!allow(ip, 1)) return send(res, 429, { error: 'Slow down' });
      const out = {};
      for (const u of (url.searchParams.get('uuids') || '').split(',').slice(0, 120)) if (UUID.test(u)) { const k = dash(u); if (db[k]) out[k] = db[k]; }
      return send(res, 200, out);
    }

    const m = url.pathname.match(/^\/api\/capes\/([0-9a-f]{40})\.png$/);
    if (m && req.method === 'GET') {
      const f = path.join(CAPES, m[1] + '.png');
      if (!fs.existsSync(f)) return send(res, 404, { error: 'No such cape' });
      return send(res, 200, fs.readFileSync(f), 'image/png');
    }
    send(res, 404, { error: 'Not found' });
  } catch (e) {
    send(res, 400, { error: e.message || String(e) });
  }
}

module.exports = { handle };
