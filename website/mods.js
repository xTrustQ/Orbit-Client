// Mod browser: categories on the left (with counts), a searchable list on the right. Data: modules.json,
// generated from the mod's source code (tools/gen-modules.js).
const CATS = [['ALL', 'All mods'], ['COMBAT', 'Combat'], ['HUD', 'HUD'], ['RENDER', 'Render'], ['PLAYER', 'Player'], ['CLIENT', 'Settings']];
const NEW = new Set(['clips', 'autoclicker', 'killeffects', 'damagenumbers', 'hitcolors', 'trajectories']); // recently added
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
let mods = [], cat = new URLSearchParams(location.search).get('cat')?.toUpperCase() || 'ALL', query = '';

const catsEl = document.getElementById('cats'), list = document.getElementById('list');
const input = document.getElementById('q');

function renderCats() {
  catsEl.innerHTML = CATS.map(([k, label]) => {
    const n = k === 'ALL' ? mods.length : mods.filter((m) => m.category === k).length;
    return `<button class="cat ${k === cat ? 'active' : ''}" data-cat="${k}"><span>${label}</span><b>${n}</b></button>`;
  }).join('');
  catsEl.querySelectorAll('.cat').forEach((b) => (b.onclick = () => {
    cat = b.dataset.cat;
    history.replaceState(null, '', cat === 'ALL' ? 'mods.html' : `mods.html?cat=${cat.toLowerCase()}`);
    renderCats(); renderList();
  }));
}

function renderList() {
  const q = query.trim().toLowerCase();
  const shown = mods.filter((m) => (cat === 'ALL' || m.category === cat) && (!q || (m.name + ' ' + m.desc).toLowerCase().includes(q)));
  document.getElementById('cat-title').textContent = q ? `Results for “${query.trim()}”` : CATS.find(([k]) => k === cat)[1];
  document.getElementById('count').textContent = `${shown.length}/${cat === 'ALL' ? mods.length : mods.filter((m) => m.category === cat).length}`;
  if (!shown.length) { list.innerHTML = `<p class="muted-row">No mod matches “${esc(query.trim())}”.</p>`; return; }
  list.innerHTML = shown.map((m) => `<div class="mod-row" tabindex="0">
      <div class="mod-name"><span>${esc(m.name)}</span>${NEW.has(m.id) ? '<em>new</em>' : ''}</div>
      <div class="mod-desc">${esc(m.desc)}</div>
      <div class="mod-meta">${cat === 'ALL' || q ? `<span class="pill-cat c-${m.category.toLowerCase()}">${CATS.find(([k]) => k === m.category)[1]}</span>` : ''}${m.settings ? `<span>${m.settings} setting${m.settings === 1 ? '' : 's'}</span>` : ''}</div>
    </div>`).join('');
}

input.addEventListener('input', () => { query = input.value; renderList(); });
addEventListener('keydown', (e) => { if (e.key === '/' && document.activeElement !== input) { e.preventDefault(); input.focus(); } });

fetch('modules.json').then((r) => r.json()).then((m) => {
  mods = m;
  if (!CATS.some(([k]) => k === cat)) cat = 'ALL';
  renderCats(); renderList();
}).catch(() => { list.innerHTML = '<p class="muted-row">Couldn’t load the mod list.</p>'; });
