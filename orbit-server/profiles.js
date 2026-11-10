// Player profiles for the website's player lookup (orbit-client.com/players): playtime, sessions and PvP stats
// of Orbit players. Only signed-in (Mojang-verified) players get a profile, so nobody can pad someone else's
// numbers; players can switch their profile to private in the Orbit menu.
const store = require('./store');

const cache = new Map();       // uuid -> profile (loaded on demand)
const dirty = new Set();
const names = new Map();       // lowercase name -> uuid
let board = null;              // top players by playtime: [{uuid, name, playMs}]

const blank = (uuid, name) => ({ uuid, name, firstSeen: Date.now(), lastSeen: Date.now(), playMs: 0, sessions: 0,
  kills: 0, deaths: 0, hits: 0, misses: 0, wtaps: 0, dealt: 0, taken: 0, bestCombo: 0, public: true });

async function get(uuid, name) {
  let p = cache.get(uuid);
  if (!p) {
    try { p = await store.get('player:' + uuid); } catch { p = null; }
    p = p || blank(uuid, name);
    cache.set(uuid, p);
  }
  if (name && p.name !== name) { p.name = name; dirty.add(uuid); }
  if (p.name) names.set(p.name.toLowerCase(), uuid);
  return p;
}
function touch(uuid) { dirty.add(uuid); }

/** a verified player came online / changed server / went away */
function online(p) { p.lastSeen = Date.now(); touch(p.uuid); }
function startSession(p) { p.sessions++; p.lastSeen = Date.now(); touch(p.uuid); }
function addPlaytime(p, ms) { if (ms > 0 && ms < 12 * 3600_000) { p.playMs += ms; p.lastSeen = Date.now(); touch(p.uuid); } }

const INT = (v, max) => Math.max(0, Math.min(max, Math.floor(Number(v) || 0)));
/** PvP numbers since the game's last report (small, sane deltas only) */
function addStats(p, s) {
  p.kills += INT(s.kills, 500); p.deaths += INT(s.deaths, 500); p.hits += INT(s.hits, 20000); p.misses += INT(s.misses, 20000);
  p.wtaps += INT(s.wtaps, 20000); p.dealt += INT(s.dealt, 100000); p.taken += INT(s.taken, 100000);
  p.bestCombo = Math.max(p.bestCombo, INT(s.bestCombo, 1000));
  touch(p.uuid);
}

async function flush() {
  const ids = [...dirty];
  dirty.clear();
  for (const id of ids) {
    const p = cache.get(id);
    if (!p) continue;
    try {
      await store.set('player:' + id, p);
      if (p.name) await store.set('name:' + p.name.toLowerCase(), id);
    } catch (e) { dirty.add(id); }
  }
  if (ids.length) await updateBoard(ids.map((id) => cache.get(id)).filter(Boolean));
}
async function loadBoard() {
  if (board) return board;
  try { board = (await store.get('leaderboard')) || []; } catch { board = []; }
  return board;
}
async function updateBoard(changed) {
  const b = await loadBoard();
  for (const p of changed) {
    const i = b.findIndex((e) => e.uuid === p.uuid);
    if (i >= 0) b.splice(i, 1);
    if (p.public && p.playMs > 0) b.push({ uuid: p.uuid, name: p.name, playMs: p.playMs, kills: p.kills });
  }
  b.sort((a, c) => c.playMs - a.playMs);
  board = b.slice(0, 25);
  try { await store.set('leaderboard', board); } catch { /* next time */ }
}
setInterval(() => flush().catch(() => {}), 60_000);

/** for the website: profile by Minecraft name */
async function lookup(name) {
  const key = String(name || '').toLowerCase();
  if (!/^[a-z0-9_]{1,16}$/.test(key)) return null;
  let uuid = names.get(key);
  if (!uuid) { try { uuid = await store.get('name:' + key); } catch { uuid = null; } }
  if (!uuid) return null;
  return get(uuid);
}

module.exports = { get, online, startSession, addPlaytime, addStats, flush, lookup, loadBoard, touch };
