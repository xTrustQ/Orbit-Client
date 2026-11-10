// Orbit server: cosmetics API (see api.js) + relays emotes between Orbit players who are on the same Minecraft server,
// and tells them who else runs Orbit there (for the Orbit badge on name tags and in Tab).
// Run: npm install && npm start   (PORT env var, default 8787). Put it behind TLS (wss://) when public.
const http = require('http');
const { WebSocketServer } = require('ws');
const api = require('./api');

const PORT = Number(process.env.PORT) || 8787;
const EMOTE = /^[a-z_]{2,24}$/; // any emote name (new emotes work without updating the server)
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const rooms = new Map(); // room -> Set<socket>
const users = new Map(); // uuid -> Set<socket> (everyone connected, also on the main menu) — friends & chat

// one port for everything: the cosmetics API (HTTP) and the live sync (WebSocket)
const server = http.createServer((req, res) => api.handle(req, res));
const wss = new WebSocketServer({ server, maxPayload: 16384 }); // hello carries the friend list
server.listen(PORT);
// for the website: how many Orbit players are online right now (and how many of them are in a game)
let peak = 0;
api.setStats(() => {
  let inGame = 0;
  for (const set of users.values()) if ([...set].some((s) => s.where)) inGame++;
  peak = Math.max(peak, users.size);
  return { online: users.size, inGame, peak };
});
/** does this player have `friendUuid` in their friend list (on any of their connections)? */
const lists = (uuid, friendUuid) => [...(users.get(uuid) || [])].some((s) => s.friends?.has(friendUuid));
/** where a player is playing, shown only to people they have added as a friend themselves */
function whereFor(uuid, asker) {
  for (const s of users.get(uuid) || []) if (s.where && s.share !== false && s.friends?.has(asker)) return s.where;
  return '';
}
const sendTo = (uuid, msg) => { const set = users.get(uuid); if (!set) return false; const data = JSON.stringify(msg); for (const s of set) if (s.readyState === 1) s.send(data); return set.size > 0; };

function leave(ws) {
  if (!ws.room) return;
  if (ws.uuid) broadcast(ws, { type: 'gone', uuid: ws.uuid }); // badge disappears for the others
  const set = rooms.get(ws.room);
  if (set) { set.delete(ws); if (!set.size) rooms.delete(ws.room); }
  ws.room = null;
}

function broadcast(ws, msg) {
  const set = rooms.get(ws.room);
  if (!set) return;
  const data = JSON.stringify(msg);
  for (const peer of set) if (peer !== ws && peer.readyState === 1) peer.send(data);
}

wss.on('connection', (ws) => {
  ws.alive = true;
  ws.tokens = 10; // simple rate limit: 10 messages, refilled 2/s
  ws.on('pong', () => (ws.alive = true));
  ws.on('message', (raw) => {
    if (ws.tokens <= 0) return;
    ws.tokens--;
    let m;
    try { m = JSON.parse(raw); } catch { return; }
    if (m.type === 'hello') {
      if (!UUID.test(m.uuid || '') || typeof m.room !== 'string' || m.room.length > 120) return;
      leave(ws);
      if (ws.uuid && ws.uuid !== m.uuid) users.get(ws.uuid)?.delete(ws);
      ws.uuid = m.uuid;
      ws.name = String(m.name || '').slice(0, 16);
      // Friends 2.0: which server you're on (shared only with your friends) and who your friends are
      ws.where = typeof m.where === 'string' ? m.where.replace(/[^\w.:\- ]/g, '').slice(0, 100) : '';
      ws.share = m.share !== false;
      if (Array.isArray(m.friends)) ws.friends = new Set(m.friends.slice(0, 300).filter((u) => typeof u === 'string' && UUID.test(u)).map((u) => u.toLowerCase()));
      if (!users.has(ws.uuid)) users.set(ws.uuid, new Set());
      users.get(ws.uuid).add(ws);
      ws.room = m.room.toLowerCase();
      if (!ws.room) { ws.room = null; return; } // on the main menu: online for friends, but in no room
      if (!rooms.has(ws.room)) rooms.set(ws.room, new Set());
      rooms.get(ws.room).add(ws);
      // Orbit badges: tell the newcomer who else runs Orbit here, and tell them about the newcomer
      for (const peer of rooms.get(ws.room)) if (peer !== ws && peer.uuid && peer.readyState === 1) ws.send(JSON.stringify({ type: 'here', uuid: peer.uuid }));
      broadcast(ws, { type: 'here', uuid: ws.uuid });
    } else if (m.type === 'emote' && ws.room && EMOTE.test(m.emote || '')) {
      broadcast(ws, { type: 'emote', uuid: ws.uuid, emote: m.emote });
    } else if (m.type === 'online?' && Array.isArray(m.uuids)) {
      // which of these players are online right now (for the friends list) + where they play (friends only)
      const on = m.uuids.slice(0, 200).filter((u) => typeof u === 'string' && users.get(u)?.size);
      const status = {};
      if (ws.uuid) for (const u of on) { const w = whereFor(u, ws.uuid.toLowerCase()); if (w) status[u] = w; }
      ws.send(JSON.stringify({ type: 'online', uuids: on, status }));
    } else if (m.type === 'invite' && ws.uuid && UUID.test(m.to || '') && typeof m.server === 'string') {
      // "come play with me" — only to players who have you as a friend, so nobody gets spammed
      const server = m.server.replace(/[^\w.:\-]/g, '').slice(0, 100);
      if (!server || !lists(m.to, ws.uuid.toLowerCase())) { ws.send(JSON.stringify({ type: 'invite-failed', uuid: m.to })); return; }
      const ok = sendTo(m.to, { type: 'invite', uuid: ws.uuid, name: ws.name, server, at: Date.now() });
      ws.send(JSON.stringify({ type: ok ? 'invite-sent' : 'invite-failed', uuid: m.to }));
    } else if (m.type === 'dm' && ws.uuid && UUID.test(m.to || '') && typeof m.text === 'string') {
      const text = m.text.replace(/[\u0000-\u001f]/g, ' ').trim().slice(0, 256);
      if (!text) return;
      const ok = sendTo(m.to, { type: 'dm', uuid: ws.uuid, name: ws.name, text, at: Date.now() });
      ws.send(JSON.stringify({ type: ok ? 'dm-sent' : 'dm-offline', uuid: m.to }));
    } else if (m.type === 'stop' && ws.room) {
      broadcast(ws, { type: 'stop', uuid: ws.uuid });
    }
  });
  ws.on('close', () => { leave(ws); if (ws.uuid) { users.get(ws.uuid)?.delete(ws); if (!users.get(ws.uuid)?.size) users.delete(ws.uuid); } });
});

setInterval(() => {
  for (const ws of wss.clients) {
    ws.tokens = Math.min(10, ws.tokens + 2);
  }
}, 1000);
setInterval(() => {
  for (const ws of wss.clients) {
    if (!ws.alive) { ws.terminate(); continue; }
    ws.alive = false;
    ws.ping();
  }
}, 30000);

console.log(`Orbit sync server listening on :${PORT} — ${[...rooms.keys()].length} rooms`);
