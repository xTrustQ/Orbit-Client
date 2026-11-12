// Player profiles for orbit-client.com/players.html: play time, days played, streaks, the last days' activity,
// friends and badges. Only signed-in (Mojang-verified) players get a profile, so nobody can pad someone else's
// numbers; players can switch their profile to private in the Orbit menu.
const store = require('./store');

const cache = new Map();       // uuid -> profile (loaded on demand)
const dirty = new Set();
const names = new Map();       // lowercase name -> uuid
let board = null;              // recently active public players (for the leaderboards)

// names shown with the Developer badge (comma separated)
const DEVELOPERS = (process.env.ORBIT_DEVELOPERS || 'TrustQ').split(',').map((s) => s.trim().toLowerCase()).filter(Boolean);
const EARLY_UNTIL = Date.UTC(2027, 0, 1);

const day = (t = Date.now()) => new Date(t).toISOString().slice(0, 10); // YYYY-MM-DD (UTC)
const blank = (uuid, name) => ({ uuid, name, firstSeen: Date.now(), lastSeen: Date.now(), playMs: 0, sessions: 0,
  days: {}, daysPlayed: 0, curStreak: 0, bestStreak: 0, lastDay: null, friends: 0,
  kills: 0, deaths: 0, hits: 0, misses: 0, wtaps: 0, dealt: 0, taken: 0, bestCombo: 0, public: true });

function upgrade(p) { // profiles saved by older versions
  p.days = p.days || {}; p.daysPlayed = p.daysPlayed || 0; p.curStreak = p.curStreak || 0; p.bestStreak = p.bestStreak || 0;
  p.friends = p.friends || 0; if (p.lastDay === undefined) p.lastDay = null;
  return p;
}

async function get(uuid, name) {
  let p = cache.get(uuid);
  if (!p) {
    try { p = await store.get('player:' + uuid); } catch { p = null; }
    p = upgrade(p || blank(uuid, name));
    cache.set(uuid, p);
  }
  if (name && p.name !== name) { p.name = name; dirty.add(uuid); }
  if (p.name) names.set(p.name.toLowerCase(), uuid);
  return p;
}
function touch(uuid) { dirty.add(uuid); }

// ------------------------------------------------------------------ signed backups kept by the players
// Free hosting can wipe our files. Each signed-in player gets a copy of their own stats, signed with a key only the
// server knows (Render's service id, stable across restarts, or ORBIT_PROFILE_KEY); after a wipe the game sends it
// back and we restore it. The signature means nobody can hand us made-up numbers.
const crypto = require('crypto');
const KEY = process.env.ORBIT_PROFILE_KEY || process.env.RENDER_SERVICE_ID || crypto.randomBytes(32).toString('hex');
const KEPT = ['uuid', 'name', 'firstSeen', 'lastSeen', 'playMs', 'sessions', 'days', 'daysPlayed', 'curStreak', 'bestStreak', 'lastDay',
  'kills', 'deaths', 'hits', 'misses', 'wtaps', 'dealt', 'taken', 'bestCombo'];
const sign = (data) => crypto.createHmac('sha256', 'orbit-profile|' + KEY).update(data).digest('hex');
function backup(p) {
  const o = {};
  for (const k of KEPT) o[k] = p[k];
  const data = JSON.stringify(o);
  return { data, sig: sign(data) };
}
/** merge a backup the game sent us; true if it restored anything */
function restore(p, b) {
  if (!b || typeof b.data !== 'string' || typeof b.sig !== 'string' || b.data.length > 20000) return false;
  const want = sign(b.data);
  if (b.sig.length !== want.length || !crypto.timingSafeEqual(Buffer.from(b.sig), Buffer.from(want))) return false;
  let o;
  try { o = JSON.parse(b.data); } catch { return false; }
  // only after a wipe: our copy then starts later than the player really did. (Once merged, firstSeen matches,
  // so the same backup can never be added twice.)
  if (!o || o.uuid !== p.uuid || !(o.firstSeen < p.firstSeen)) return false;
  // what we have now = everything since the wipe; the backup = everything before it -> add them up
  const days = { ...(o.days || {}) };
  let newDays = 0;
  for (const [d, ms] of Object.entries(p.days || {})) { if (!days[d]) newDays++; days[d] = (days[d] || 0) + ms; }
  let cur = p.curStreak, last = p.lastDay;
  if (!p.lastDay) { cur = o.curStreak || 0; last = o.lastDay || null; }
  else if (o.lastDay === p.lastDay) cur = o.curStreak || 0;                                         // same day on both sides
  else if (o.lastDay && day(Date.parse(p.lastDay) - 86400_000) === o.lastDay) cur = (o.curStreak || 0) + 1; // the streak goes on
  Object.assign(p, {
    firstSeen: o.firstSeen, playMs: (o.playMs || 0) + p.playMs, sessions: (o.sessions || 0) + p.sessions,
    days, daysPlayed: (o.daysPlayed || 0) + newDays, curStreak: cur, lastDay: last,
    bestStreak: Math.max(o.bestStreak || 0, p.bestStreak, cur),
  });
  for (const k of ['kills', 'deaths', 'hits', 'misses', 'wtaps', 'dealt', 'taken']) p[k] = (p[k] || 0) + (o[k] || 0);
  p.bestCombo = Math.max(p.bestCombo || 0, o.bestCombo || 0);
  touch(p.uuid);
  return true;
}

function badges(p) {
  const b = [];
  if (p.name && DEVELOPERS.includes(p.name.toLowerCase())) b.push('developer');
  if (p.firstSeen < EARLY_UNTIL) b.push('early');
  return b;
}

/** a verified player came online / changed server / went away */
function online(p) { p.lastSeen = Date.now(); touch(p.uuid); }
function startSession(p) { p.sessions++; p.lastSeen = Date.now(); touch(p.uuid); }
function addPlaytime(p, ms) {
  if (!(ms > 0 && ms < 12 * 3600_000)) return;
  const today = day();
  if (!p.days[today]) { // first minutes today: days played + streak
    p.days[today] = 0;
    p.daysPlayed++;
    p.curStreak = p.lastDay === day(Date.now() - 86400_000) ? p.curStreak + 1 : 1;
    p.bestStreak = Math.max(p.bestStreak, p.curStreak);
    p.lastDay = today;
  }
  p.days[today] += ms;
  p.playMs += ms;
  p.lastSeen = Date.now();
  for (const d of Object.keys(p.days)) if (d < day(Date.now() - 40 * 86400_000)) delete p.days[d]; // keep ~40 days
  touch(p.uuid);
}
function setFriends(p, n) { if (p.friends !== n) { p.friends = n; touch(p.uuid); } }

const INT = (v, max) => Math.max(0, Math.min(max, Math.floor(Number(v) || 0)));
/** PvP numbers since the game's last report (kept, but not shown on the website) */
function addStats(p, s) {
  p.kills += INT(s.kills, 500); p.deaths += INT(s.deaths, 500); p.hits += INT(s.hits, 20000); p.misses += INT(s.misses, 20000);
  p.wtaps += INT(s.wtaps, 20000); p.dealt += INT(s.dealt, 100000); p.taken += INT(s.taken, 100000);
  p.bestCombo = Math.max(p.bestCombo, INT(s.bestCombo, 1000));
  touch(p.uuid);
}

/** play time per day for the last n days (oldest first) */
function lastDays(p, n = 7) {
  const out = [];
  for (let i = n - 1; i >= 0; i--) { const d = day(Date.now() - i * 86400_000); out.push({ date: d, ms: p.days[d] || 0 }); }
  return out;
}
const weekMs = (days) => lastDays({ days }, 7).reduce((s, d) => s + d.ms, 0);

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
  try { board = (await store.get('board2')) || []; } catch { board = []; }
  return board;
}
async function updateBoard(changed) {
  const b = await loadBoard();
  for (const p of changed) {
    const i = b.findIndex((e) => e.uuid === p.uuid);
    if (i >= 0) b.splice(i, 1);
    if (p.public && p.playMs > 0) {
      const recent = {};
      for (const d of lastDays(p, 8)) if (d.ms) recent[d.date] = d.ms;
      b.push({ uuid: p.uuid, name: p.name, playMs: p.playMs, daysPlayed: p.daysPlayed, bestStreak: p.bestStreak, days: recent, badges: badges(p), lastSeen: p.lastSeen });
    }
  }
  // keep the 300 most recently active players
  b.sort((a, c) => c.lastSeen - a.lastSeen);
  board = b.slice(0, 300);
  try { await store.set('board2', board); } catch { /* next time */ }
}
setInterval(() => flush().catch(() => {}), 60_000);

/** leaderboard: week (play time in the last 7 days), all (all-time play time), streak, days */
async function leaderboard(type) {
  const b = await loadBoard();
  const value = { week: (e) => weekMs(e.days || {}), all: (e) => e.playMs, streak: (e) => e.bestStreak, days: (e) => e.daysPlayed }[type] || ((e) => weekMs(e.days || {}));
  return b.map((e) => ({ ...e, value: value(e) })).filter((e) => e.value > 0).sort((a, c) => c.value - a.value).slice(0, 25);
}

/** for the website: profile by Minecraft name */
async function lookup(name) {
  const key = String(name || '').toLowerCase();
  if (!/^[a-z0-9_]{1,16}$/.test(key)) return null;
  let uuid = names.get(key);
  if (!uuid) { try { uuid = await store.get('name:' + key); } catch { uuid = null; } }
  if (!uuid) return null;
  return get(uuid);
}

module.exports = { get, online, startSession, addPlaytime, addStats, setFriends, flush, lookup, leaderboard, loadBoard, touch, badges, lastDays, backup, restore };
