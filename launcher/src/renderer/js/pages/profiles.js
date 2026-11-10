import { icon } from '../icons.js';
import { $, $$, h, esc, state, on, timeAgo, fmtHours, fmtBytes, modal, toast, toastErr, busy, confirmDialog, setRange } from '../ui.js';
import { go, launchInstance, refreshInstances, openConsole } from '../app.js';
import { BLOCK_NAMES, blockIcon, iconUrl } from '../art.js';

let offs = [];
const FILTERS = [['all', 'All'], ['fabric', 'Fabric'], ['quilt', 'Quilt'], ['vanilla', 'Vanilla'], ['Modpacks', 'Modpacks'], ['fav', 'Favorites']];
const view = { filter: 'all', q: '', sort: 'recent' };

const loaderLabel = (i) => (i.loader === 'vanilla' ? 'Vanilla' : `${i.loader[0].toUpperCase() + i.loader.slice(1)} ${i.loaderVersion || ''}`);

export function render(params = {}) {
  const el = h(`<div>
    <div class="toolbar"><div class="chips" data-filters>${FILTERS.map(([k, l]) => `<button class="chip ${view.filter === k ? 'active' : ''}" data-f="${k}">${l}</button>`).join('')}</div></div>
    <div class="toolbar">
      <div class="field" style="width:380px">${icon('search')}<input placeholder="Search profiles..." value="${esc(view.q)}" data-q><span class="vsep"></span><button class="icon-btn ghost" style="width:28px;height:28px" data-sort title="Sort">${icon(view.sort === 'recent' ? 'clock' : 'sort')}</button></div>
      <div class="grow"></div>
      <button class="btn" data-console>${icon('terminal')} Console</button>
      <button class="btn primary" data-create>${icon('plus')} Create</button>
    </div>
    <div class="divider"></div>
    <div class="inst-list stagger" data-list></div>
  </div>`);

  const paint = () => {
    let list = state.instances.filter((i) => i.name.toLowerCase().includes(view.q.toLowerCase()));
    if (view.filter === 'fav') list = list.filter((i) => i.favorite);
    else if (view.filter === 'Modpacks') list = list.filter((i) => i.group === 'Modpacks');
    else if (view.filter !== 'all') list = list.filter((i) => i.loader === view.filter);
    if (view.sort === 'name') list = [...list].sort((a, b) => a.name.localeCompare(b.name));
    const box = $('[data-list]', el);
    box.innerHTML = list.map((i) => `<div class="card hover inst" data-id="${i.id}">
      <div class="inst-icon">${iconUrl(i.icon) ? `<img src="${esc(iconUrl(i.icon))}">` : icon('cube')}</div>
      <div class="grow">
        <div class="inst-name">${esc(i.name)} ${i.favorite ? '<span style="color:#ffcf4d">★</span>' : ''}</div>
        <div class="inst-meta">${state.running.has(i.id) ? '<span class="running-dot"></span><span style="color:var(--accent)">Running</span><span class="vsep"></span>' : ''}
          <span>${icon('cube')} ${esc(i.mcVersion)}</span><span class="vsep"></span><span>${esc(loaderLabel(i))}</span><span class="vsep"></span><span>${timeAgo(i.lastPlayed || i.createdAt)}</span>
          ${i.playtime ? `<span class="vsep"></span><span>${fmtHours(i.playtime)} played</span>` : ''}</div>
      </div>
      ${state.running.has(i.id) ? `<button class="btn danger" data-stop>${icon('stop')} Stop</button>` : `<button class="btn soft" data-play>${icon('play')} Play</button>`}
      <button class="btn" data-mods>${icon('cube')} Mods</button>
      <button class="icon-btn" data-gear title="Profile settings">${icon('settings')}</button>
    </div>`).join('') || `<div class="empty">${icon('profiles')}<h3>No profiles here</h3><p>Create a profile to start playing.</p></div>`;
    $$('.inst', box).forEach((card) => {
      const id = card.dataset.id;
      $('[data-play]', card)?.addEventListener('click', () => launchInstance(id));
      $('[data-stop]', card)?.addEventListener('click', () => nova.game.stop(id));
      $('[data-mods]', card).onclick = () => contentManager(id);
      $('[data-gear]', card).onclick = () => instanceSettings(id);
      card.ondblclick = (e) => !e.target.closest('button') && launchInstance(id);
    });
  };
  paint();

  $$('[data-f]', el).forEach((b) => (b.onclick = () => { view.filter = b.dataset.f; $$('[data-f]', el).forEach((x) => x.classList.toggle('active', x === b)); paint(); }));
  $('[data-q]', el).oninput = (e) => { view.q = e.target.value; paint(); };
  $('[data-sort]', el).onclick = (e) => { view.sort = view.sort === 'recent' ? 'name' : 'recent'; e.currentTarget.innerHTML = icon(view.sort === 'recent' ? 'clock' : 'sort'); toast('Sorted by ' + (view.sort === 'recent' ? 'last played' : 'name'), '', 'info', 1500); paint(); };
  $('[data-create]', el).onclick = () => createDialog();
  $('[data-console]', el).onclick = () => openConsole();
  offs = [on('instances', paint), on('running', paint)];
  if (params.create) setTimeout(createDialog, 150);
  return el;
}
export function destroy() { offs.forEach((f) => f()); offs = []; }

// ---------------------------------------------------------------- create
export async function createDialog() {
  const m = modal(`<h2>Create profile</h2><p class="desc">A profile is an isolated Minecraft install with its own mods, worlds and settings.</p>
    <div class="form-grid">
      <div class="full"><label class="lbl">Name</label><input class="input" data-name placeholder="My survival world"></div>
      <div><label class="lbl">Minecraft version</label><select class="input" data-ver><option>Loading…</option></select></div>
      <div><label class="lbl">Mod loader</label><div class="seg" data-loader style="width:100%"><button data-l="fabric" class="active">Fabric</button><button data-l="quilt">Quilt</button><button data-l="vanilla">Vanilla</button></div></div>
      <div><label class="lbl">Loader version</label><select class="input" data-lver></select></div>
      <div style="display:flex;align-items:flex-end"><span class="muted" style="height:42px;display:flex;align-items:center;font-size:12.5px">Orbit runs on Minecraft 1.21.11</span></div>
      <div class="full card" style="padding:12px 14px;display:flex;align-items:center;gap:12px"><span style="color:var(--accent)">${icon('zap')}</span>
        <div class="grow"><b>FPS boost pack</b><div class="muted" style="font-size:12.5px">Installs Sodium, Lithium, FerriteCore, EntityCulling, ImmediatelyFast, ModernFix & more</div></div>
        <button class="toggle ${state.settings.performancePack !== false ? 'on' : ''}" data-perf type="button"></button></div>
      <div class="full"><label class="lbl">Icon</label><div class="icon-picker">${BLOCK_NAMES.map((b, i) => `<div class="icon-pick ${i === 0 ? 'sel' : ''}" data-icon="blk:${b}" title="${b}" style="background-image:url(${blockIcon(b)})"></div>`).join('')}</div></div>
    </div>
    <div class="foot"><button class="btn" data-close>Cancel</button><button class="btn primary" data-ok>${icon('plus')} Create</button></div>`);
  const E = m.el;
  let loader = 'fabric', snaps = false, iconSel = 'blk:' + BLOCK_NAMES[0], versions = [];
  const verSel = $('[data-ver]', E), lverSel = $('[data-lver]', E);
  const fillVersions = () => {
    const list = versions.filter((v) => (state.info.novaVersions || ['1.21.11']).includes(v.id)); // Orbit supports 1.21.11 only
    const nova = state.info.novaVersions || [];
    verSel.innerHTML = list.map((v) => `<option value="${v.id}">${v.id}${v.type !== 'release' ? ' (snapshot)' : ''}${nova.includes(v.id) ? '  ★ Orbit HUD' : ''}</option>`).join('');
    const pref = list.find((v) => nova.includes(v.id)) || list[0];
    if (pref) verSel.value = pref.id;
    fillLoader();
  };
  const fillLoader = async () => {
    lverSel.disabled = loader === 'vanilla';
    $('[data-perf]', E).closest('.card').style.opacity = loader === 'vanilla' ? 0.4 : 1;
    if (loader === 'vanilla') { lverSel.innerHTML = '<option>—</option>'; return; }
    lverSel.innerHTML = '<option>Loading…</option>';
    try {
      const vs = await nova.instances.loaderVersions(loader, verSel.value);
      lverSel.innerHTML = vs.length ? vs.slice(0, 40).map((v) => `<option value="${v.version}">${v.version}${v.stable ? ' (stable)' : ''}</option>`).join('') : '<option value="">Not available for this version</option>';
      const st = vs.find((v) => v.stable); if (st) lverSel.value = st.version;
    } catch { lverSel.innerHTML = '<option value="">Offline?</option>'; }
  };
  nova.instances.versions().then((v) => { versions = v; fillVersions(); }).catch(toastErr);
  verSel.onchange = fillLoader;
  $$('[data-l]', E).forEach((b) => (b.onclick = () => { loader = b.dataset.l; $$('[data-l]', E).forEach((x) => x.classList.toggle('active', x === b)); fillLoader(); }));
  $('[data-perf]', E).onclick = (e) => e.currentTarget.classList.toggle('on');
  $$('[data-icon]', E).forEach((d) => (d.onclick = () => { iconSel = d.dataset.icon; $$('[data-icon]', E).forEach((x) => x.classList.toggle('sel', x === d)); }));
  setTimeout(() => $('[data-name]', E).focus(), 60);
  $('[data-ok]', E).onclick = (e) => busy(e.currentTarget, async () => {
    if (loader !== 'vanilla' && !lverSel.value) throw new Error(`${loader} isn't available for ${verSel.value}`);
    const opts = { name: $('[data-name]', E).value.trim() || `${loader === 'vanilla' ? 'Vanilla' : loader[0].toUpperCase() + loader.slice(1)} ${verSel.value}`, mcVersion: verSel.value, loader, loaderVersion: loader === 'vanilla' ? null : lverSel.value, icon: iconSel, performancePack: $('[data-perf]', E).classList.contains('on') };
    m.close();
    toast('Creating profile', opts.name + (opts.performancePack && loader !== 'vanilla' ? ' — installing FPS boost pack…' : ''), 'info');
    const inst = await nova.instances.create(opts);
    state.settings.lastInstance = inst.id; nova.settings.set({ lastInstance: inst.id });
    await refreshInstances();
    toast('Profile ready', inst.name);
  });
}

// ---------------------------------------------------------------- content manager
const KINDS = [['mod', 'Mods'], ['resourcepack', 'Resource packs'], ['shader', 'Shaders']];
export function contentManager(id, kind = 'mod') {
  const inst = state.instances.find((i) => i.id === id);
  const m = modal(`<div class="row"><h2 class="grow">${esc(inst.name)}</h2><button class="icon-btn ghost" data-close>${icon('close')}</button></div>
    <p class="desc">${esc(inst.mcVersion)} · ${esc(loaderLabel(inst))}</p>
    <div class="tabs">${KINDS.map(([k, l]) => `<button data-k="${k}">${l}</button>`).join('')}</div>
    <div class="toolbar"><div class="field grow" style="height:38px">${icon('search')}<input placeholder="Filter…" data-q></div>
      <button class="btn sm" data-local>${icon('upload')} Add file</button><button class="btn sm" data-folder>${icon('folder')}</button><button class="btn sm primary" data-browse>${icon('download')} Browse</button></div>
    <div data-items style="max-height:52vh;overflow:auto"></div>`, { wide: true });
  const E = m.el;
  let items = [];
  const paint = () => {
    const q = $('[data-q]', E).value.toLowerCase();
    $('[data-items]', E).innerHTML = items.filter((c) => c.title.toLowerCase().includes(q)).map((c) => `<div class="content-row ${c.enabled ? '' : 'off'}" data-file="${esc(c.file)}">
      ${c.icon ? `<img src="${esc(c.icon)}">` : `<div class="ph">${icon(c.source === 'nova' ? 'zap' : 'cube')}</div>`}
      <div class="grow"><div class="ellipsis" style="font-weight:600">${esc(c.title)}</div><div class="faint ellipsis" style="font-size:12px">${esc(c.file)} · ${fmtBytes(c.size)}</div></div>
      <button class="toggle ${c.enabled ? 'on' : ''}" data-tog></button><button class="icon-btn ghost" data-rm>${icon('trash')}</button></div>`).join('') || `<div class="empty">${icon('cube')}<h3>Nothing installed</h3><p>Browse Modrinth or CurseForge to add content.</p></div>`;
    $$('.content-row', E).forEach((r) => {
      const file = r.dataset.file;
      $('[data-tog]', r).onclick = async () => { items = await nova.instances.toggleContent(id, kind, file, !items.find((c) => c.file === file).enabled); paint(); };
      $('[data-rm]', r).onclick = async () => { items = await nova.instances.removeContent(id, kind, file); paint(); };
    });
  };
  const load = async (k) => { kind = k; $$('[data-k]', E).forEach((b) => b.classList.toggle('active', b.dataset.k === k)); items = await nova.instances.contents(id, k); paint(); };
  $$('[data-k]', E).forEach((b) => (b.onclick = () => load(b.dataset.k)));
  $('[data-q]', E).oninput = paint;
  $('[data-local]', E).onclick = async () => { items = await nova.instances.addLocalFiles(id, kind); paint(); };
  $('[data-folder]', E).onclick = () => nova.instances.openFolder(id);
  $('[data-browse]', E).onclick = () => { m.close(); go('mods', { instanceId: id, type: kind }); };
  load(kind);
}

// ---------------------------------------------------------------- instance settings
function instanceSettings(id) {
  const inst = state.instances.find((i) => i.id === id);
  const total = state.settings.totalMemory || 8192;
  const mem = inst.memory || state.settings.memory || state.settings.defaultMemory;
  const m = modal(`<h2>Profile settings</h2><p class="desc">${esc(inst.mcVersion)} · ${esc(loaderLabel(inst))}</p>
    <div class="form-grid">
      <div class="full"><label class="lbl">Name</label><input class="input" data-name value="${esc(inst.name)}"></div>
      <div class="full"><label class="lbl">Memory — <span data-memv>${(mem / 1024).toFixed(1)} GB</span></label><input type="range" min="1024" max="${Math.max(2048, total - 1024)}" step="256" value="${mem}" data-mem></div>
      <div class="full"><label class="lbl">Performance profile — one click sets video options, Sodium and shaders</label>
        <div class="perf-presets">${[['fps', 'Max FPS', 'Short view, no effects, no shaders'], ['balanced', 'Balanced', 'Good looks, smooth play'], ['beautiful', 'Beautiful', 'Far view, every effect, shaders on']]
          .map(([k, t, d]) => `<button type="button" class="perf-preset ${inst.performanceProfile === k ? 'sel' : ''}" data-preset="${k}"><b>${t}</b><span>${d}</span></button>`).join('')}</div></div>
      <div class="full"><label class="lbl">Extra JVM arguments</label><input class="input" data-jvm value="${esc(inst.jvmArgs || '')}" placeholder="-Dsome.flag=true"></div>
      <div class="full"><label class="lbl">Icon</label><div class="icon-picker">${BLOCK_NAMES.map((b) => `<div class="icon-pick ${inst.icon === 'blk:' + b ? 'sel' : ''}" data-icon="blk:${b}" style="background-image:url(${blockIcon(b)})"></div>`).join('')}</div></div>
      <div class="full row" style="flex-wrap:wrap">
        <button class="btn sm" data-fav>${icon('heart')} ${inst.favorite ? 'Unfavorite' : 'Favorite'}</button>
        <button class="btn sm" data-folder>${icon('folder')} Open folder</button>
        <button class="btn sm" data-dup>${icon('copy')} Duplicate</button>
        <button class="btn sm soft" data-import>${icon('download')} Import settings</button>
        ${inst.loader !== 'vanilla' ? `<button class="btn sm soft" data-perf>${icon('zap')} Install FPS pack</button>` : ''}
        ${inst.loader !== 'vanilla' ? `<button class="btn sm" data-repair>${icon('refresh')} Repair mods</button>` : ''}
        <div class="grow"></div><button class="btn sm danger" data-del>${icon('trash')} Delete</button>
      </div>
    </div>
    <div class="foot"><button class="btn" data-close>Cancel</button><button class="btn primary" data-save>Save</button></div>`);
  const E = m.el;
  let iconSel = inst.icon;
  const r = $('[data-mem]', E); setRange(r);
  r.oninput = () => { setRange(r); $('[data-memv]', E).textContent = (r.value / 1024).toFixed(1) + ' GB'; };
  $$('[data-icon]', E).forEach((d) => (d.onclick = () => { iconSel = d.dataset.icon; $$('[data-icon]', E).forEach((x) => x.classList.toggle('sel', x === d)); }));
  $('[data-folder]', E).onclick = () => nova.instances.openFolder(id);
  $('[data-fav]', E).onclick = async () => { await nova.instances.update(id, { favorite: !inst.favorite }); await refreshInstances(); m.close(); };
  $('[data-dup]', E).onclick = (e) => busy(e.currentTarget, async () => { await nova.instances.duplicate(id); await refreshInstances(); m.close(); toast('Profile duplicated'); });
  $('[data-perf]', E)?.addEventListener('click', (e) => busy(e.currentTarget, async () => { const got = await nova.instances.performancePack(id); toast('FPS pack installed', got.join(', ') || 'Everything was already installed'); }));
  $('[data-import]', E).onclick = () => { m.close(); importSettingsDialog(id); };
  $$('[data-preset]', E).forEach((b) => (b.onclick = () => busy(b, async () => {
    const r = await nova.instances.performanceProfile(id, b.dataset.preset);
    $$('[data-preset]', E).forEach((x) => x.classList.toggle('sel', x === b));
    const name = b.querySelector('b').textContent;
    const sh = r.shaders === 'none' ? 'No shader pack installed — get one under Mods → Shaders.' : r.shaders === 'off' ? 'Shaders off.' : `Shaders on (${r.shaders}).`;
    toast(`${name} applied`, `Video settings and Sodium set. ${sh}`, r.shaders === 'none' ? 'info' : 'ok', 6000);
    await refreshInstances();
  })));
  $('[data-repair]', E)?.addEventListener('click', (e) => busy(e.currentTarget, async () => { const fixed = await nova.instances.repair(id); toast(fixed.length ? 'Mods repaired' : 'All mods are healthy', fixed.join(', ')); }));
  $('[data-del]', E).onclick = async () => {
    if (!(await confirmDialog('Delete profile?', `"${inst.name}" and all its worlds, mods and settings will be permanently deleted.`, { okLabel: 'Delete', danger: true }))) return;
    await nova.instances.remove(id); await refreshInstances(); m.close(); toast('Profile deleted');
  };
  $('[data-save]', E).onclick = (e) => busy(e.currentTarget, async () => {
    await nova.instances.update(id, { name: $('[data-name]', E).value.trim() || inst.name, memory: +r.value, jvmArgs: $('[data-jvm]', E).value.trim(), icon: iconSel });
    await refreshInstances(); m.close(); toast('Saved');
  });
}

// ---------------------------------------------------------------- import settings from other launchers
/** Pick an options.txt from Modrinth, Lunar, NoRisk, CurseForge, the vanilla launcher … and copy keybinds / settings. */
export async function importSettingsDialog(id) {
  const inst = state.instances.find((i) => i.id === id);
  const m = modal(`<div class="row"><h2 class="grow">Import settings</h2><button class="icon-btn ghost" data-close>${icon('close')}</button></div>
    <p class="desc">Copies keybinds and game settings (render distance, mouse sensitivity, FOV, video settings …) into <b>${esc(inst.name)}</b>. Mods, worlds and resource packs are not copied. The old settings are kept as a backup.</p>
    <div data-list class="import-list"><div class="muted" style="padding:14px">Searching your launchers…</div></div>
    <div class="row" style="margin-top:12px;gap:18px">
      <label class="row" style="gap:8px;cursor:pointer"><button class="toggle on" data-k type="button"></button><span>Keybinds</span></label>
      <label class="row" style="gap:8px;cursor:pointer"><button class="toggle on" data-s type="button"></button><span>Settings</span></label>
    </div>
    <div class="foot"><button class="btn" data-close>Cancel</button><button class="btn primary" data-ok disabled>${icon('download')} Import</button></div>`, { wide: true });
  const E = m.el;
  let pick = null;
  const ago = (t) => { const d = (Date.now() - t) / 864e5; return d < 1 ? 'today' : d < 2 ? 'yesterday' : Math.round(d) + ' days ago'; };
  try {
    const list = await nova.instances.settingsSources();
    const box = $('[data-list]', E);
    if (!list.length) { box.innerHTML = '<div class="muted" style="padding:14px">No other Minecraft launchers found on this PC.</div>'; return; }
    box.innerHTML = list.map((s, i) => `<div class="import-row" data-i="${i}">
        <div class="grow"><b>${esc(s.launcher)}</b> <span class="muted">· ${esc(s.name)}</span>
          <div class="muted" style="font-size:12px">${s.keybinds} keybinds · render distance ${esc(s.renderDistance || '?')} · sensitivity ${s.sensitivity ? Math.round(+s.sensitivity * 200) + '%' : '?'} · ${ago(s.modified)}</div></div>
        <span class="import-dot"></span></div>`).join('');
    $$('.import-row', box).forEach((r) => (r.onclick = () => {
      pick = list[+r.dataset.i];
      $$('.import-row', box).forEach((x) => x.classList.toggle('sel', x === r));
      $('[data-ok]', E).disabled = false;
    }));
  } catch (e) { toastErr(e); }
  $('[data-k]', E).onclick = (e) => e.currentTarget.classList.toggle('on');
  $('[data-s]', E).onclick = (e) => e.currentTarget.classList.toggle('on');
  $('[data-ok]', E).onclick = (e) => busy(e.currentTarget, async () => {
    const what = { keybinds: $('[data-k]', E).classList.contains('on'), settings: $('[data-s]', E).classList.contains('on') };
    const r = await nova.instances.importSettings(id, pick.path, what);
    m.close();
    toast('Settings imported', `${r.changed} settings from ${pick.launcher} (${pick.name}) — used next time you launch ${inst.name}`);
  });
}