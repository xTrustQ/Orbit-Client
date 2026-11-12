// Player lookup: type a Minecraft name, see that Orbit player's profile (from the Orbit server).
const out = document.getElementById('result');
const nameInput = document.getElementById('name');
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const head = (name, size) => `https://mc-heads.net/avatar/${encodeURIComponent(name)}/${size}`;

function hours(ms) {
  const m = Math.round(ms / 60000);
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60);
  return h < 100 ? `${h}h ${m % 60}m` : `${fmt(h)}h`;
}
function ago(t) {
  const s = Math.max(0, (Date.now() - t) / 1000);
  if (s < 90) return 'just now';
  if (s < 3600) return `${Math.round(s / 60)} min ago`;
  if (s < 86400) return `${Math.round(s / 3600)} h ago`;
  return `${Math.round(s / 86400)} days ago`;
}
const date = (t) => new Date(t).toLocaleDateString('en', { day: 'numeric', month: 'short', year: 'numeric' });

async function api(path) {
  // the free server sleeps when nobody is online: tell people why the first answer can take a moment
  const slow = setTimeout(() => { if (out.dataset.state === 'loading') out.querySelector('.hint')?.removeAttribute('hidden'); }, 2500);
  try { return await fetch(ORBIT_SERVER + path); } finally { clearTimeout(slow); }
}

async function lookup(name) {
  name = name.trim();
  if (!/^[A-Za-z0-9_]{1,16}$/.test(name)) { show(`<div class="notice">That isn’t a Minecraft name — use letters, numbers and _ (max 16).</div>`); return; }
  history.replaceState(null, '', `players.html?name=${encodeURIComponent(name)}`);
  out.dataset.state = 'loading';
  out.innerHTML = `<div class="card profile loading"><div class="spinner"></div><p>Looking up ${esc(name)}…</p><p class="hint" hidden>Waking up the Orbit server — this can take up to a minute the first time.</p></div>`;
  let r;
  try { r = await api(`/api/player/${encodeURIComponent(name)}`); }
  catch { show(`<div class="notice">Couldn’t reach the Orbit server. Try again in a moment.</div>`); return; }
  if (r.status === 404) { show(`<div class="notice"><img src="${head(name, 48)}" alt="" width="48" height="48"><div><b>${esc(name)}</b> has no Orbit stats yet<span>Stats appear when ${esc(name)} plays with Orbit signed in with Microsoft. If they played before, their stats come back the next time they open Orbit.</span></div></div>`); return; }
  if (!r.ok) { show(`<div class="notice">The Orbit server answered with an error (${r.status}). Try again later.</div>`); return; }
  const p = await r.json();
  if (p.private) { show(`<div class="notice"><img src="${head(p.name, 48)}" alt="" width="48" height="48"><div><b>${esc(p.name)}</b>’s profile is private<span>They turned off their public profile in Orbit.</span></div></div>`); return; }
  show(profile(p), p.name);
}

// levels: every level needs 30 minutes more than the one before (level 2 at 30 min, 3 at 1.5 h, 4 at 3 h, …)
const TITLES = ['Newcomer', 'Explorer', 'Regular', 'Veteran', 'Orbiter', 'Star', 'Legend'];
const needFor = (lvl) => 15 * (lvl - 1) * lvl; // total minutes to reach a level
function level(ms) {
  const min = ms / 60000;
  let l = 1;
  while (needFor(l + 1) <= min) l++;
  const from = needFor(l), to = needFor(l + 1);
  return { level: l, title: TITLES[Math.min(TITLES.length - 1, Math.floor((l - 1) / 3))], pct: ((min - from) / (to - from)) * 100, left: Math.ceil(to - min) };
}
const BADGES = { developer: ['Developer', 'b-dev'], early: ['Early supporter', 'b-early'] };
const MODES = { sword: 'Sword', axe: 'Axe', uhc: 'UHC', vanilla: 'Crystal', pot: 'Pot', nethop: 'NethOP', smp: 'SMP', mace: 'Mace' };
const minutes = (ms) => { const m = Math.round(ms / 60000); return m < 60 ? `${m}m` : `${Math.floor(m / 60)}h ${m % 60}m`; };

function profile(p) {
  const status = p.inGame ? '<span class="status on">● In a game</span>' : p.online ? '<span class="status idle">● In the Orbit menu</span>' : `<span class="status off">Last seen ${ago(p.lastSeen)}</span>`;
  const stat = (label, value) => `<div class="stat-box"><b>${value}</b><span>${label}</span></div>`;
  const lv = level(p.playMs);
  const week = p.last7 || [];
  const max = Math.max(...week.map((d) => d.ms), 1);
  const weekTotal = week.reduce((s, d) => s + d.ms, 0);
  const c = p.cosmetics;
  const wears = c ? [['Hat', c.hat], ['Wings', c.wings], ['Aura', c.aura], ['Pet', c.pet]].filter(([, v]) => v && v !== 'None') : [];
  return `<div class="card profile">
    <div class="pf-top">
      <div class="skin3d"><canvas id="skin" width="220" height="300" aria-label="${esc(p.name)}'s skin"></canvas><span>Drag to turn</span></div>
      <div class="pf-main">
        <div class="pf-name"><img src="${head(p.name, 56)}" alt="" width="56" height="56"><div>
          <h2>${esc(p.name)}</h2>
          <p class="lvl">Level ${lv.level} · ${lv.title}</p>
        </div></div>
        <div class="badges">${(p.badges || []).map((b) => BADGES[b] ? `<span class="badge ${BADGES[b][1]}">${BADGES[b][0]}</span>` : '').join('')}${status}</div>
        <div class="stat-grid">
          ${stat('played', hours(p.playMs))}
          ${stat('days played', fmt(p.daysPlayed || 0))}
          ${stat('best streak', `${fmt(p.bestStreak || 0)} ${p.bestStreak === 1 ? 'day' : 'days'}`)}
          ${stat('friends', fmt(p.friends || 0))}
          ${stat('member since', date(p.firstSeen))}
        </div>
        <div class="lvl-bar"><div class="lvl-row"><b>Level ${lv.level}</b><span>${fmt(lv.left)} min to level ${lv.level + 1}</span></div>
          <div class="track"><i style="width:${lv.pct.toFixed(1)}%"></i></div></div>
      </div>
    </div>
    <div class="pf-cols">
      <div class="pf-panel">
        <h3>Last 7 days <span>${minutes(weekTotal)}</span></h3>
        <div class="week">${week.map((d) => {
          const wd = new Date(d.date + 'T12:00:00Z').toLocaleDateString('en', { weekday: 'short' });
          return `<div class="day" title="${wd}: ${minutes(d.ms)}"><div class="col"><i style="height:${d.ms ? Math.max(6, (d.ms / max) * 100) : 0}%"></i></div><span>${wd}</span></div>`;
        }).join('')}</div>
      </div>
      <div class="pf-panel">
        <h3>PvP tiers <a href="https://pvptiers.com/" target="_blank" rel="noopener">pvptiers.com</a></h3>
        <div id="tiers" class="tiers"><p class="muted-row">Loading…</p></div>
      </div>
    </div>
    ${wears.length || c?.cape ? `<h3>Cosmetics</h3><div class="wears">${wears.map(([k, v]) => `<span class="pill-cat">${k}: ${esc(v)}</span>`).join('')}
      ${c?.cape ? `<span class="cape"><img src="${ORBIT_SERVER}/api/capes/${esc(c.cape)}.png" alt="Orbit cape" width="40" height="64"> Orbit cape</span>` : ''}</div>` : ''}
  </div>`;
}

/** 3D skin you can drag around (skinview3d); falls back to a flat render if WebGL isn't there */
let viewer = null;
function skin(name) {
  const canvas = document.getElementById('skin');
  if (!canvas) return;
  viewer?.dispose?.();
  viewer = null;
  try {
    if (!window.skinview3d) throw new Error('no 3d');
    viewer = new skinview3d.SkinViewer({ canvas, width: 220, height: 300, skin: `https://mc-heads.net/skin/${encodeURIComponent(name)}` });
    viewer.animation = new skinview3d.IdleAnimation();
    viewer.autoRotate = true;
    viewer.autoRotateSpeed = 0.6;
    viewer.controls.enableZoom = false;
    viewer.zoom = 0.85;
    canvas.addEventListener('pointerdown', () => (viewer.autoRotate = false), { once: true });
  } catch {
    canvas.replaceWith(Object.assign(new Image(110, 240), { src: `https://mc-heads.net/body/${encodeURIComponent(name)}/240`, alt: name, className: 'body' }));
  }
}

async function tiers(name) {
  const el = document.getElementById('tiers');
  let t = null;
  try { const r = await fetch(`${ORBIT_SERVER}/api/tiers/${encodeURIComponent(name)}`); if (r.ok) t = await r.json(); } catch { /* shown below */ }
  if (!el.isConnected) return;
  const list = t ? Object.entries(t.rankings || {}).filter(([, v]) => v && v.tier) : [];
  if (!list.length) { el.innerHTML = '<p class="muted-row">Not ranked on PvP Tiers.</p>'; return; }
  list.sort((a, b) => a[1].tier - b[1].tier || b[1].high - a[1].high);
  el.innerHTML = list.map(([mode, v]) => {
    const tag = `${v.high ? 'HT' : 'LT'}${v.tier}`;
    const peak = v.peak && (v.peak < v.tier || (v.peak === v.tier && v.peakHigh && !v.high)) ? `<small>peak ${v.peakHigh ? 'HT' : 'LT'}${v.peak}</small>` : '';
    return `<div class="tier t${v.tier}"><b>${tag}</b><span>${MODES[mode] || esc(mode)}</span>${peak}</div>`;
  }).join('') + (t.region ? `<p class="tier-meta">${esc(t.region)} · ${fmt(t.points || 0)} points</p>` : '');
}

function show(html, name) {
  out.dataset.state = 'done';
  out.innerHTML = html;
  if (name && document.getElementById('skin')) { skin(name); tiers(name); }
}

document.getElementById('lookup').addEventListener('submit', (e) => { e.preventDefault(); lookup(nameInput.value); });

// leaderboards
const BOARDS = {
  week: ['Most played this week', (v) => hours(v)],
  all: ['Most played of all time', (v) => hours(v)],
  streak: ['Longest daily streak', (v) => `${fmt(v)} ${v === 1 ? 'day' : 'days'}`],
  days: ['Most days played', (v) => `${fmt(v)} ${v === 1 ? 'day' : 'days'}`],
};
const boardCache = {};
async function board(type) {
  const el = document.getElementById('board');
  document.querySelectorAll('.board-tab').forEach((t) => t.classList.toggle('active', t.dataset.type === type));
  document.getElementById('board-sub').textContent = BOARDS[type][0];
  if (!boardCache[type]) el.innerHTML = '<p class="muted-row">Loading…</p>';
  let b;
  try { b = boardCache[type] || (boardCache[type] = await api(`/api/leaderboard?type=${type}`).then((r) => (r.ok ? r.json() : Promise.reject()))); }
  catch { el.innerHTML = '<p class="muted-row">Couldn’t load the leaderboard.</p>'; return; }
  if (document.querySelector('.board-tab.active')?.dataset.type !== type) return; // switched meanwhile
  if (!b.length) { el.innerHTML = '<p class="muted-row">Nobody here yet — be the first!</p>'; return; }
  el.innerHTML = b.map((e, i) => `<button class="board-row${i < 3 ? ` top${i + 1}` : ''}" data-name="${esc(e.name)}">
      <span class="rank">${i + 1}</span><img src="${head(e.name, 32)}" alt="" width="32" height="32" loading="lazy">
      <span class="who">${esc(e.name)}${e.online ? ' <i class="dot-on" title="online"></i>' : ''}${(e.badges || []).map((x) => BADGES[x] ? `<i class="mini ${BADGES[x][1]}" title="${BADGES[x][0]}"></i>` : '').join('')}</span>
      <span class="time">${BOARDS[type][1](e.value)}</span></button>`).join('');
  el.querySelectorAll('.board-row').forEach((r) => (r.onclick = () => { nameInput.value = r.dataset.name; lookup(r.dataset.name); scrollTo({ top: 0, behavior: 'smooth' }); }));
}
document.querySelectorAll('.board-tab').forEach((t) => (t.onclick = () => board(t.dataset.type)));
board('week');

// shared link: players.html?name=TrustQ
const preset = new URLSearchParams(location.search).get('name');
if (preset) { nameInput.value = preset; lookup(preset); } else nameInput.focus();
