import { icon } from '../icons.js';
import { $, $$, h, esc, on, modal, toast, toastErr, busy, confirmDialog, promptDialog } from '../ui.js';
import { CAPE_PRESETS, PREMIUM_CAPES, buildCape, drawCapeFront } from '../art.js';

const TABS = [['all', 'All'], ['mine', 'My capes'], ['fav', 'Favorites'], ['vanilla', 'Minecraft']];
let tab = 'all', q = '', nova_ = [], mojang = { capes: [] }, offs = [];

export function render() {
  const el = h(`<div>
    <div class="toolbar"><div class="chips">${TABS.map(([k, l]) => `<button class="chip ${tab === k ? 'active' : ''}" data-tab="${k}">${l}</button>`).join('')}</div></div>
    <div class="toolbar">
      <div class="field" style="width:420px">${icon('search')}<input placeholder="Search capes..." data-q value="${esc(q)}"></div>
      <div class="grow"></div>
      <button class="btn" data-presets>${icon('sparkles')} Templates</button>
      <button class="btn primary" data-upload>${icon('upload')} Upload</button>
    </div>
    <div class="card" style="padding:10px 14px;margin-bottom:14px;display:flex;gap:10px;align-items:center;font-size:13px">${icon('info').replace('<svg', '<svg style="width:16px;color:var(--accent)"')}
      <span class="muted"><b style="color:var(--text)">Orbit capes</b> render in-game through the Orbit mod (Fabric/Quilt profiles on 1.21.11). <b style="color:var(--text)">Minecraft capes</b> are the official ones your account owns and everyone sees.</span></div>
    <div class="divider"></div>
    <div class="cape-grid stagger" data-grid></div>
  </div>`);
  const grid = $('[data-grid]', el);

  const capeCard = (c, kind) => `<div class="card hover cape-card ${c.selected || c.active ? 'sel' : ''}" data-kind="${kind}" data-id="${esc(c.id)}">
    ${kind === 'nova' ? `<button class="fav ${c.favorite ? 'on' : ''}" data-fav>${icon('heart')}</button>` : ''}
    ${kind === 'nova' && !c.preset ? `<div class="card-actions" style="top:22px;left:22px;right:auto"><button class="icon-btn" data-del>${icon('trash')}</button></div>` : ''}
    <canvas></canvas><div class="title ellipsis">${esc(c.name || c.alias)}</div>
    <div class="sub">${kind === 'nova' ? (c.selected ? 'Equipped · Orbit' : 'Orbit cape') : c.active ? 'Equipped · Minecraft' : 'Minecraft cape'}</div></div>`;

  const paint = () => {
    const match = (n) => String(n || '').toLowerCase().includes(q.toLowerCase());
    let html = '';
    const noneSel = !nova_.some((c) => c.selected) && (tab === 'vanilla' ? !mojang.capes.some((c) => c.active) : true);
    html += `<div class="card hover cape-card ${noneSel ? 'sel' : ''}" data-none><div class="none"><span>${icon('close')}</span></div><div class="title">No Cape</div><div class="sub">${tab === 'vanilla' ? 'Hide Minecraft cape' : 'Hide Orbit cape'}</div></div>`;
    if (tab !== 'vanilla') {
      let list = nova_.filter((c) => match(c.name));
      if (tab === 'mine') list = list.filter((c) => !c.preset);
      if (tab === 'fav') list = list.filter((c) => c.favorite);
      html += list.map((c) => capeCard(c, 'nova')).join('');
    }
    if (tab === 'all' || tab === 'vanilla') html += mojang.capes.filter((c) => match(c.alias)).map((c) => capeCard({ ...c, name: c.alias }, 'mojang')).join('');
    if (tab === 'vanilla' && !mojang.capes.length) html += `<div class="empty" style="grid-column:1/-1">${icon('capes')}<h3>${mojang.offline ? 'Offline account' : 'No Minecraft capes'}</h3><p>${mojang.offline ? 'Sign in with Microsoft to see your capes.' : "This account doesn't own any official capes."}</p></div>`;
    grid.innerHTML = html;

    $$('.cape-card[data-id]', grid).forEach((card) => {
      const kind = card.dataset.kind, id = card.dataset.id;
      const c = kind === 'nova' ? nova_.find((x) => x.id === id) : mojang.capes.find((x) => x.id === id);
      const cv = $('canvas', card);
      drawCapeFront(cv, kind === 'nova' ? c.data : c.url.replace(/^http:/, 'https:')).catch(() => {});
      card.onclick = async (e) => {
        try {
          if (e.target.closest('[data-fav]')) { nova_ = await nova.capes.favoriteOrbit(id, !c.favorite); return paint(); }
          if (e.target.closest('[data-del]')) { if (await confirmDialog('Delete cape?', `Remove "${c.name}"?`, { okLabel: 'Delete', danger: true })) { nova_ = await nova.capes.deleteOrbit(id); paint(); } return; }
          if (kind === 'nova') { nova_ = await nova.capes.selectOrbit(id); toast('Orbit cape equipped', `${c.name} — visible next time you join a world`); }
          else { mojang = await nova.capes.setMojang(id); toast('Minecraft cape equipped', c.alias); }
          paint();
        } catch (err) { toastErr(err); }
      };
    });
    $('[data-none]', grid).onclick = async () => {
      try {
        if (tab === 'vanilla') { mojang = await nova.capes.setMojang(null); toast('Minecraft cape hidden'); }
        else { nova_ = await nova.capes.selectOrbit(null); toast('Orbit cape removed'); }
        paint();
      } catch (err) { toastErr(err); }
    };
  };

  const load = async () => {
    try {
      nova_ = await nova.capes.nova();
      for (const c of nova_.filter((c) => c.preset && !CAPE_PRESETS[c.preset] && !PREMIUM_CAPES[c.preset])) nova_ = await nova.capes.deleteOrbit(c.id); // retired designs
      const ALL = { ...CAPE_PRESETS, ...PREMIUM_CAPES }; // every design is free now
      const missing = Object.keys(ALL).filter((k) => !nova_.some((c) => c.preset === k));
      for (const k of missing) nova_ = await nova.capes.saveOrbit({ name: ALL[k].name, preset: k, png: await buildCape({ preset: k }) });
    } catch (e) { toastErr(e); }
    paint();
    mojang = await nova.capes.mojang().catch(() => ({ capes: [] }));
    paint();
  };

  $$('[data-tab]', el).forEach((b) => (b.onclick = () => { tab = b.dataset.tab; $$('[data-tab]', el).forEach((x) => x.classList.toggle('active', x === b)); paint(); }));
  $('[data-q]', el).oninput = (e) => { q = e.target.value; paint(); };
  $('[data-upload]', el).onclick = (e) => busy(e.currentTarget, async () => {
    const img = await nova.capes.pickImage();
    if (!img) return;
    const name = (await promptDialog('Name your cape', { value: img.name, okLabel: 'Create' })) || img.name;
    nova_ = await nova.capes.saveOrbit({ name, png: await buildCape({ image: img.data }) });
    tab = 'mine'; $$('[data-tab]', el).forEach((x) => x.classList.toggle('active', x.dataset.tab === 'mine'));
    paint(); toast('Cape created', 'Your image was fitted onto a cape template.');
  });
  $('[data-presets]', el).onclick = () => {
    const m = modal(`<div class="row"><h2 class="grow">Cape templates</h2><button class="icon-btn ghost" data-close>${icon('close')}</button></div><p class="desc">Hand-made Orbit designs. Click one to equip it. Templates follow your launcher theme where it makes sense (Orbit).</p>
      <div class="cape-grid">${Object.entries({ ...CAPE_PRESETS, ...PREMIUM_CAPES }).map(([k, p]) => `<div class="card hover cape-card" data-p="${k}"><canvas></canvas><div class="title">${p.name}</div></div>`).join('')}</div>`, { wide: true });
    $$('[data-p]', m.el).forEach(async (c) => {
      const k = c.dataset.p;
      const png = await buildCape({ preset: k });
      drawCapeFront($('canvas', c), png);
      c.onclick = async () => { nova_ = await nova.capes.saveOrbit({ name: ({ ...CAPE_PRESETS, ...PREMIUM_CAPES })[k].name, preset: k, png }); nova_ = await nova.capes.selectOrbit('preset-' + k); paint(); m.close(); toast('Orbit cape equipped', ({ ...CAPE_PRESETS, ...PREMIUM_CAPES })[k].name); };
    });
  };

  load();
  offs = [on('accounts', load)];
  return el;
}
export function destroy() { offs.forEach((f) => f()); offs = []; }
