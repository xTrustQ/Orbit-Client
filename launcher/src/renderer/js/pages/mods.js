import { icon } from '../icons.js';
import { $, $$, h, esc, state, on, fmtNum, toast, toastErr, busy } from '../ui.js';
import { go, refreshInstances } from '../app.js';
import { iconUrl } from '../art.js';

const TYPES = [['modpack', 'Modpacks'], ['mod', 'Mods'], ['resourcepack', 'Resourcepacks'], ['shader', 'Shaders'], ['datapack', 'Datapacks']];
const SORTS = [['downloads', 'Downloads'], ['relevance', 'Relevance'], ['follows', 'Popularity'], ['newest', 'Newest'], ['updated', 'Updated']];
const q = { source: 'modrinth', type: 'mod', query: '', gameVersion: '', loader: '', category: '', environment: '', sort: 'downloads', offset: 0 };
let target = null, offs = [], token = 0, loading = false, total = 0;

export function render(params = {}) {
  if (params.type) q.type = params.type;
  if (params.instanceId) target = params.instanceId;
  if (!state.instances.find((i) => i.id === target)) target = state.settings.lastInstance && state.instances.find((i) => i.id === state.settings.lastInstance) ? state.settings.lastInstance : state.instances[0]?.id || null;
  syncFiltersToTarget();

  const el = h(`<div>
    <div class="browse">
      <div style="min-width:0">
        <div class="toolbar"><div class="chips">${TYPES.map(([k, l]) => `<button class="chip ${q.type === k ? 'active' : ''}" data-t="${k}">${l}</button>`).join('')}</div></div>
        <div class="toolbar">
          <div class="field grow" style="max-width:420px">${icon('search')}<input placeholder="Search ${q.type}s..." value="${esc(q.query)}" data-q></div>
          <select class="input" style="width:150px;height:42px" data-sort>${SORTS.map(([k, l]) => `<option value="${k}" ${q.sort === k ? 'selected' : ''}>${l}</option>`).join('')}</select>
          <div class="grow"></div>
          <div class="source-toggle"><button data-s="modrinth" class="${q.source === 'modrinth' ? 'active' : ''}">${icon('modrinth')} MODRINTH</button><button data-s="curseforge" class="${q.source === 'curseforge' ? 'active' : ''}">${icon('anvil')} CURSEFORGE</button></div>
        </div>
        <div class="target-bar" data-target></div>
        <div class="mod-list" data-list></div>
        <div style="text-align:center;padding:16px" data-more></div>
      </div>
      <div class="filters" data-filters></div>
    </div>
  </div>`);

  const list = $('[data-list]', el);
  const renderTarget = () => {
    const bar = $('[data-target]', el);
    if (q.type === 'modpack') { bar.innerHTML = `${icon('info')}<span class="muted">Installing a modpack creates a new profile automatically.</span>`; return; }
    if (!state.instances.length) { bar.innerHTML = `${icon('info')}<span class="muted grow">Create a profile first to install content.</span><button class="btn sm primary" data-mk>${icon('plus')} Create</button>`; $('[data-mk]', bar).onclick = () => go('profiles', { create: true }); return; }
    bar.innerHTML = `${icon('download')}<span class="muted">Install into</span><select class="input" style="width:auto;min-width:240px;height:34px" data-inst>${state.instances.map((i) => `<option value="${i.id}" ${i.id === target ? 'selected' : ''}>${esc(i.name)} — ${esc(i.mcVersion)} ${esc(i.loader)}</option>`).join('')}</select>
      <label class="row muted" style="font-size:12.5px;margin-left:auto">Only show compatible <button class="toggle ${q.gameVersion ? 'on' : ''}" data-compat></button></label>`;
    $('[data-inst]', bar).onchange = (e) => { target = e.target.value; syncFiltersToTarget(true); renderFilters(); search(); };
    $('[data-compat]', bar).onclick = (e) => { const on = !e.currentTarget.classList.contains('on'); e.currentTarget.classList.toggle('on', on); if (on) syncFiltersToTarget(true); else { q.gameVersion = ''; q.loader = ''; } renderFilters(); search(); };
  };

  const card = (m) => `<div class="mod" data-id="${esc(m.id)}">
    ${m.icon ? `<img class="mod-icon" src="${esc(m.icon)}" loading="lazy">` : `<div class="mod-icon" style="display:grid;place-items:center">${icon('cube')}</div>`}
    <div style="min-width:0"><div class="mod-title"><b class="ellipsis">${esc(m.title)}</b><span>by ${esc(m.author)}</span></div>
      <div class="mod-desc">${esc(m.description)}</div>
      <div class="row" style="flex-wrap:wrap;gap:6px">${(m.categories || []).slice(0, 4).map((c) => `<span class="tag">${esc(c)}</span>`).join('')}</div></div>
    <div class="mod-side"><span class="dl">${icon('download')} ${fmtNum(m.downloads)}</span>
      <div class="split"><button class="btn sm soft" data-install>${icon('download')} Install</button><button class="btn sm" data-open title="Open website">${icon('external')}</button></div></div>
  </div>`;

  // what the selected profile already has, so the same mod can't be installed twice
  const norm = (s) => String(s || '').toLowerCase().replace(/[^a-z0-9]/g, '');
  let have = { ids: new Set(), names: new Set(), files: [] };
  const loadInstalled = async () => {
    have = { ids: new Set(), names: new Set(), files: [] };
    if (!target || q.type === 'modpack') return;
    try {
      for (const c of await nova.instances.contents(target, q.type)) {
        if (c.projectId) have.ids.add(String(c.projectId));
        have.names.add(norm(c.title));
        have.files.push(norm(c.file));
      }
    } catch { /* profile gone */ }
  };
  const isInstalled = (m) => q.type !== 'modpack' && (have.ids.has(String(m.id)) || have.names.has(norm(m.title))
    || (norm(m.slug).length >= 4 && have.files.some((f) => f.startsWith(norm(m.slug)))));
  const markInstalled = (btn) => { btn.innerHTML = `${icon('check')} Installed`; btn.disabled = true; btn.classList.add('installed'); };

  const bind = (hits) => hits.forEach((m) => {
    const row = list.querySelector(`.mod[data-id="${CSS.escape(m.id)}"]`);
    if (!row) return;
    $('[data-open]', row).onclick = () => m.url && nova.app.openExternal(m.url);
    if (isInstalled(m)) return markInstalled($('[data-install]', row));
    $('[data-install]', row).onclick = (e) => busy(e.currentTarget, async () => {
      if (q.type === 'modpack') {
        toast('Installing modpack', m.title, 'info');
        const inst = await nova.content.installModpack({ source: m.source, projectId: m.id, meta: { title: m.title, icon: m.icon } });
        await refreshInstances();
        toast('Modpack installed', `Profile "${inst.name}" created`);
        return;
      }
      if (!target) return go('profiles', { create: true });
      await loadInstalled();
      if (isInstalled(m)) { toast('Already installed', `${m.title} is already in this profile`, 'info'); return true; }
      await nova.content.install({ source: m.source, projectId: m.id, type: q.type, instanceId: target, meta: { title: m.title, icon: m.icon } });
      const inst = state.instances.find((i) => i.id === target);
      toast('Installed', `${m.title} → ${inst?.name}`);
      await loadInstalled();
      return true;
    }).then((ok) => { if (ok) markInstalled($('[data-install]', row)); });
  });

  async function search(append = false) {
    const my = ++token;
    if (!append) { q.offset = 0; list.innerHTML = Array.from({ length: 5 }, () => `<div class="mod"><div class="skeleton" style="width:80px;height:80px"></div><div><div class="skeleton" style="height:18px;width:40%;margin-bottom:10px"></div><div class="skeleton" style="height:12px;width:80%"></div></div><div></div></div>`).join(''); }
    loading = true;
    try {
      const [r] = await Promise.all([nova.content.search({ ...q }), append ? null : loadInstalled()]);
      if (my !== token) return;
      total = r.total;
      const html = r.hits.map(card).join('');
      if (append) list.insertAdjacentHTML('beforeend', html); else list.innerHTML = html || `<div class="empty">${icon('search')}<h3>No results</h3><p>Try another search or loosen the filters.</p></div>`;
      bind(r.hits);
      $('[data-more]', el).innerHTML = q.offset + 20 < total ? `<span class="muted">Scroll for more · ${fmtNum(total)} results</span>` : '';
    } catch (e) {
      if (my !== token) return;
      list.innerHTML = `<div class="empty">${icon('alert')}<h3>${e.code === 'NO_CF_KEY' ? 'CurseForge API key needed' : 'Could not load'}</h3><p>${esc(e.message)}</p>${e.code === 'NO_CF_KEY' ? `<button class="btn primary" data-key>${icon('key')} Open settings</button>` : ''}</div>`;
      $('[data-key]', list)?.addEventListener('click', () => go('settings', { section: 'integrations' }));
    } finally { loading = false; }
  }

  // filters panel
  const filterBox = (key, title, opts, open = false) => `<div class="filter ${open ? 'open' : ''}" data-fk="${key}"><div class="filter-head">${title}${icon('chevron')}</div><div class="filter-body"><div><div class="inner">${opts.map(([v, l]) => `<div class="opt ${q[key] === v ? 'sel' : ''}" data-v="${esc(v)}"><span class="box"></span>${esc(l)}</div>`).join('')}</div></div></div></div>`;
  let versions = [], cats = [];
  const renderFilters = () => {
    const fb = $('[data-filters]', el);
    const openKeys = $$('.filter.open', fb).map((f) => f.dataset.fk);
    fb.innerHTML = [
      filterBox('gameVersion', 'Game versions', [['', 'Any version'], ...versions.map((v) => [v, v])], openKeys.includes('gameVersion')),
      filterBox('category', 'Categories', [['', 'All categories'], ...cats.map((c) => [c.id, c.name])], openKeys.includes('category')),
      q.type === 'mod' || q.type === 'modpack' ? filterBox('loader', 'Mod loaders', [['', 'Any loader'], ['fabric', 'Fabric'], ['quilt', 'Quilt'], ['forge', 'Forge'], ['neoforge', 'NeoForge']], openKeys.includes('loader')) : '',
      q.source === 'modrinth' ? filterBox('environment', 'Environment', [['', 'Any'], ['client', 'Client'], ['server', 'Server']], openKeys.includes('environment')) : '',
    ].join('');
    $$('.filter', fb).forEach((f) => {
      $('.filter-head', f).onclick = () => f.classList.toggle('open');
      $$('.opt', f).forEach((o) => (o.onclick = () => { q[f.dataset.fk] = o.dataset.v; $$('.opt', f).forEach((x) => x.classList.toggle('sel', x === o)); renderTarget(); search(); }));
    });
  };
  const loadCats = async () => { cats = []; renderFilters(); try { cats = await nova.content.categories(q.source, q.type); } catch { cats = []; } renderFilters(); };
  nova.instances.versions().then((v) => { versions = v.filter((x) => x.type === 'release').map((x) => x.id).slice(0, 60); renderFilters(); }).catch(() => {});

  $$('[data-t]', el).forEach((b) => (b.onclick = () => { q.type = b.dataset.t; q.category = ''; $$('[data-t]', el).forEach((x) => x.classList.toggle('active', x === b)); $('[data-q]', el).placeholder = `Search ${q.type}s...`; syncFiltersToTarget(); renderTarget(); loadCats(); search(); }));
  $$('[data-s]', el).forEach((b) => (b.onclick = () => { q.source = b.dataset.s; q.category = ''; $$('[data-s]', el).forEach((x) => x.classList.toggle('active', x === b)); loadCats(); search(); }));
  let deb; $('[data-q]', el).oninput = (e) => { clearTimeout(deb); deb = setTimeout(() => { q.query = e.target.value; if (q.query && q.sort === 'downloads') q.sort = 'relevance'; $('[data-sort]', el).value = q.sort; search(); }, 280); };
  $('[data-sort]', el).onchange = (e) => { q.sort = e.target.value; search(); };
  el.addEventListener('scroll', () => { if (!loading && el.scrollTop + el.clientHeight > el.scrollHeight - 300 && q.offset + 20 < total) { q.offset += 20; search(true); } });

  renderTarget(); loadCats(); search();
  offs = [on('instances', renderTarget)];
  return el;
}
export function destroy() { offs.forEach((f) => f()); offs = []; token++; }

function syncFiltersToTarget(force = false) {
  const inst = state.instances.find((i) => i.id === target);
  if (!inst || q.type === 'modpack') { if (q.type === 'modpack') { q.gameVersion = ''; q.loader = ''; } return; }
  if (force || !q.gameVersion) q.gameVersion = inst.mcVersion;
  q.loader = q.type === 'mod' && inst.loader !== 'vanilla' ? inst.loader : '';
}
