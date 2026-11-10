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
//   GET  /api/settings                 Bearer token -> {data, updated}   (Settings sync: your Orbit setup on any PC)
//   PUT  /api/settings                 Bearer token, {data, updated}
//   GET  /api/stats                    -> {online, inGame, players, downloads?}  (for the website)
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const store = require('./store');
let statsSource = () => ({});
let liveSource = () => ({ online: false, inGame: false });
/** server.js tells us how many players are connected right now, and whether one player is */
function setStats(fn) { statsSource = fn; }
function setLive(fn) { liveSource = fn; }
const profiles = require('./profiles');
const tierCache = new Map(); // name -> {at, status, body}

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
/** the signed-in player behind a Bearer token (memory first, then the persistent store) */
async function auth(req) {
  const tok = (req.headers.authorization || '').replace(/^Bearer\s+/i, '');
  if (!tok || tok.length > 100) return null;
  let t = tokens[tok];
  if (!t) { try { t = await store.get('token:' + tok); } catch { t = null; } if (t) tokens[tok] = t; }
  return t && t.exp > Date.now() ? t : null;
}

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
      store.set('token:' + token, tokens[token], 30 * 24 * 3600).catch(() => {}); // survives server restarts
      return send(res, 200, { token, uuid });
    }

    if (url.pathname === '/api/stats' && req.method === 'GET') {
      if (!allow(ip, 1)) return send(res, 429, { error: 'Slow down' });
      return send(res, 200, { ...statsSource(), players: Object.keys(db).length, at: Date.now() });
    }

    // player lookup for the website: /api/player/<minecraft name>
    const pm = url.pathname.match(/^\/api\/player\/([A-Za-z0-9_]{1,16})$/);
    if (pm && req.method === 'GET') {
      if (!allow(ip, 2)) return send(res, 429, { error: 'Slow down' });
      const p = await profiles.lookup(pm[1]);
      if (!p) return send(res, 404, { error: 'No Orbit player with that name yet' });
      if (!p.public) return send(res, 200, { name: p.name, private: true });
      const live = liveSource(p.uuid);
      const cos = db[p.uuid] || null;
      return send(res, 200, {
        name: p.name, uuid: p.uuid, online: live.online, inGame: live.inGame,
        firstSeen: p.firstSeen, lastSeen: live.online ? Date.now() : p.lastSeen, playMs: p.playMs, sessions: p.sessions,
        daysPlayed: p.daysPlayed, bestStreak: p.bestStreak, curStreak: p.curStreak, friends: p.friends,
        last7: profiles.lastDays(p, 7), badges: profiles.badges(p),
        cosmetics: cos ? { hat: cos.hat, wings: cos.wings, aura: cos.aura, cape: cos.cape } : null,
      });
    }
    if (url.pathname === '/api/leaderboard' && req.method === 'GET') {
      if (!allow(ip, 1)) return send(res, 429, { error: 'Slow down' });
      const type = url.searchParams.get('type') || 'week';
      const list = await profiles.leaderboard(type);
      return send(res, 200, list.slice(0, Math.min(25, Number(url.searchParams.get('limit')) || 10))
        .map((e) => ({ name: e.name, value: e.value, badges: e.badges || [], online: liveSource(e.uuid).online })));
    }
    // PvP tiers from pvptiers.com (their API doesn't allow websites to ask directly), cached for 10 minutes
    const tm = url.pathname.match(/^\/api\/tiers\/([A-Za-z0-9_]{1,16})$/);
    if (tm && req.method === 'GET') {
      if (!allow(ip, 2)) return send(res, 429, { error: 'Slow down' });
      const key = tm[1].toLowerCase(), hit = tierCache.get(key);
      if (hit && hit.at > Date.now() - 600_000) return send(res, hit.status, hit.body);
      let status = 200, out;
      try {
        const r = await fetch(`https://pvptiers.com/api/search_profile/${encodeURIComponent(tm[1])}`, { signal: AbortSignal.timeout(8000) });
        if (r.status === 404) { status = 404; out = { error: 'Not ranked' }; }
        else if (!r.ok) { status = 502; out = { error: 'Tier list unavailable' }; }
        else {
          const j = await r.json();
          out = { region: j.region || null, points: j.points || 0, rankings: Object.fromEntries(Object.entries(j.rankings || {})
            .filter(([, v]) => v && v.tier && !v.retired).map(([k, v]) => [k, { tier: v.tier, high: v.pos === 0, peak: v.peak_tier, peakHigh: v.peak_pos === 0 }])) };
        }
      } catch { status = 502; out = { error: 'Tier list unavailable' }; }
      if (status !== 502) tierCache.set(key, { at: Date.now(), status, body: out });
      if (tierCache.size > 2000) tierCache.clear();
      return send(res, status, out);
    }

    if (url.pathname === '/api/settings') {
      if (!allow(ip, req.method === 'PUT' ? 4 : 2)) return send(res, 429, { error: 'Slow down' });
      const t = await auth(req);
      if (!t) return send(res, 401, { error: 'Sign in again' });
      if (req.method === 'GET') {
        const s = await store.get('settings:' + t.uuid);
        return s ? send(res, 200, s) : send(res, 404, { error: 'Nothing saved yet' });
      }
      if (req.method === 'PUT') {
        const b = await body(req, 400_000);
        if (!b.data || typeof b.data !== 'object') return send(res, 400, { error: 'Missing data' });
        const entry = { data: b.data, updated: Number(b.updated) || Date.now() };
        await store.set('settings:' + t.uuid, entry);
        return send(res, 200, { ok: true, updated: entry.updated, persistent: store.persistent });
      }
    }

    if (url.pathname === '/api/cosmetics' && req.method === 'PUT') {
      if (!allow(ip, 3)) return send(res, 429, { error: 'Slow down' });
      const t = await auth(req);
      if (!t) return send(res, 401, { error: 'Sign in again' });
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

/** the player a login token belongs to (for the live connection), or null */
async function tokenOwner(tok) {
  if (typeof tok !== 'string' || !tok || tok.length > 100) return null;
  return auth({ headers: { authorization: 'Bearer ' + tok } });
}

module.exports = { handle, setStats, setLive, tokenOwner };
