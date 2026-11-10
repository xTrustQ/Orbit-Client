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
  if (r.status === 404) { show(`<div class="notice"><img src="${head(name, 48)}" alt="" width="48" height="48"><div><b>${esc(name)}</b> hasn’t played with Orbit yet<span>Profiles appear after someone plays with Orbit signed in with Microsoft.</span></div></div>`); return; }
  if (!r.ok) { show(`<div class="notice">The Orbit server answered with an error (${r.status}). Try again later.</div>`); return; }
  const p = await r.json();
  if (p.private) { show(`<div class="notice"><img src="${head(p.name, 48)}" alt="" width="48" height="48"><div><b>${esc(p.name)}</b>’s profile is private<span>They turned off their public profile in Orbit.</span></div></div>`); return; }
  show(profile(p));
}

function profile(p) {
  const shots = p.hits + p.misses;
  const kd = p.deaths ? (p.kills / p.deaths).toFixed(2) : p.kills ? `${p.kills}.00` : '0.00';
  const status = p.inGame ? '<span class="status on">● In a game</span>' : p.online ? '<span class="status idle">● In the Orbit menu</span>' : `<span class="status off">Last seen ${ago(p.lastSeen)}</span>`;
  const stat = (label, value, sub = '') => `<div class="stat-box"><b>${value}</b><span>${label}</span>${sub ? `<small>${sub}</small>` : ''}</div>`;
  const c = p.cosmetics;
  const wears = c ? [['Hat', c.hat], ['Wings', c.wings], ['Aura', c.aura]].filter(([, v]) => v && v !== 'None') : [];
  return `<div class="card profile">
    <div class="profile-head">
      <img class="body" src="https://mc-heads.net/body/${encodeURIComponent(p.name)}/120" alt="${esc(p.name)}" width="60" height="120">
      <div>
        <h2>${esc(p.name)}</h2>
        ${status}
        <p class="since">Orbit player since ${date(p.firstSeen)}</p>
      </div>
    </div>
    <h3>Time with Orbit</h3>
    <div class="stat-grid">
      ${stat('played', hours(p.playMs))}
      ${stat('sessions', fmt(p.sessions))}
      ${stat('per session', p.sessions ? hours(p.playMs / p.sessions) : '—')}
    </div>
    <h3>PvP</h3>
    <div class="stat-grid">
      ${stat('kills', fmt(p.kills))}
      ${stat('deaths', fmt(p.deaths))}
      ${stat('K/D', kd)}
      ${stat('hits', fmt(p.hits), shots ? `${Math.round((p.hits / shots) * 100)}% accuracy` : '')}
      ${stat('best combo', fmt(p.bestCombo))}
      ${stat('W-taps', fmt(p.wtaps))}
    </div>
    ${wears.length || c?.cape ? `<h3>Cosmetics</h3><div class="wears">${wears.map(([k, v]) => `<span class="pill-cat">${k}: ${esc(v)}</span>`).join('')}
      ${c?.cape ? `<span class="cape"><img src="${ORBIT_SERVER}/api/capes/${esc(c.cape)}.png" alt="Orbit cape" width="40" height="64"> Orbit cape</span>` : ''}</div>` : ''}
  </div>`;
}

function show(html) { out.dataset.state = 'done'; out.innerHTML = html; }

document.getElementById('lookup').addEventListener('submit', (e) => { e.preventDefault(); lookup(nameInput.value); });

// top players
api('/api/leaderboard').then((r) => (r.ok ? r.json() : [])).then((b) => {
  const el = document.getElementById('board');
  if (!b.length) { el.innerHTML = '<p class="muted-row">No players yet — be the first!</p>'; return; }
  el.innerHTML = b.map((e, i) => `<button class="board-row" data-name="${esc(e.name)}">
      <span class="rank">${i + 1}</span><img src="${head(e.name, 32)}" alt="" width="32" height="32" loading="lazy">
      <span class="who">${esc(e.name)}${e.online ? ' <i class="dot-on" title="online"></i>' : ''}</span>
      <span class="time">${hours(e.playMs)}</span><span class="kills">${fmt(e.kills)} kills</span></button>`).join('');
  el.querySelectorAll('.board-row').forEach((b) => (b.onclick = () => { nameInput.value = b.dataset.name; lookup(b.dataset.name); scrollTo({ top: 0, behavior: 'smooth' }); }));
}).catch(() => { document.getElementById('board').innerHTML = '<p class="muted-row">Couldn’t load the top players.</p>'; });

// shared link: players.html?name=TrustQ
const preset = new URLSearchParams(location.search).get('name');
if (preset) { nameInput.value = preset; lookup(preset); } else nameInput.focus();
