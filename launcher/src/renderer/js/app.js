import { icon } from './icons.js';
import { $, $$, h, esc, state, emit, on, toast, toastErr, modal, busy, taskProgress, avatar, promptDialog } from './ui.js';
import * as play from './pages/play.js';
import * as profiles from './pages/profiles.js';
import * as mods from './pages/mods.js';
import * as skins from './pages/skins.js';
import * as capes from './pages/capes.js';
import * as settings from './pages/settings.js';
import { startStarfield } from './starfield.js';
import { sfx } from './sfx.js';
import { startFx } from './fx.js';

const PAGES = { play, profiles, mods, skins, capes, settings };
const NAV = [['play', 'Launch', 'rocket'], ['profiles', 'Profiles', 'profiles'], ['mods', 'Mods', 'mods'], ['skins', 'Skins', 'skins'], ['capes', 'Capes', 'capes']];
const THEMES = ['nebula', 'deepspace', 'supernova', 'aurora', 'mars', 'eclipse', 'lunar'];

// ---------------------------------------------------------------- theming
export function applyLook(s = state.settings) {
  const root = document.documentElement;
  root.dataset.theme = THEMES.includes(s.theme) ? s.theme : 'nebula';
  root.dataset.font = ['orbit', 'clean', 'pixel'].includes(s.font) ? s.font : 'orbit';
  root.dataset.bg = ['stars', 'nebula', 'minimal'].includes(s.background) ? s.background : 'stars';
  root.dataset.anim = s.animations === false ? 'off' : 'on';
  sfx.setEnabled(s.sounds !== false);
}

/** Slides the glowing highlight under the active tab. */
function moveGlider() {
  const active = $('.tab.active');
  const g = $('#glider');
  if (!active) { g.style.width = '0'; return; }
  g.style.left = active.offsetLeft + 'px';
  g.style.width = active.offsetWidth + 'px';
}

// ---------------------------------------------------------------- router
const history = { stack: [], idx: -1 };
let current = null;
export function go(name, params = {}, push = true) {
  if (!PAGES[name]) return;
  if (push) { history.stack = history.stack.slice(0, history.idx + 1); history.stack.push({ name, params }); history.idx = history.stack.length - 1; }
  const view = $('#view');
  const old = view.querySelector('.page:not(.leaving)');
  if (old) { old.classList.add('leaving'); setTimeout(() => old.remove(), 180); }
  if (current && current.name !== name) sfx.page();
  current?.mod.destroy?.();
  const el = PAGES[name].render(params);
  el.classList.add('page');
  view.append(el);
  current = { name, mod: PAGES[name] };
  $$('.tab').forEach((b) => b.classList.toggle('active', b.dataset.page === name));
  $('#nav-settings').classList.toggle('active', name === 'settings');
  moveGlider();
}
const step = (d) => { const i = history.idx + d; if (i < 0 || i >= history.stack.length) return; history.idx = i; const e = history.stack[i]; go(e.name, e.params, false); };

// ---------------------------------------------------------------- data refresh
export async function refreshInstances() { state.instances = await nova.instances.list(); emit('instances', state.instances); }
export async function refreshAccounts() { state.accounts = await nova.accounts.list(); renderAccount(); emit('accounts', state.accounts); }
export const selectedAccount = () => state.accounts.find((a) => a.selected) || state.accounts[0] || null;

// ---------------------------------------------------------------- account switcher
function renderAccount() {
  const box = $('#account');
  const acc = selectedAccount();
  box.innerHTML = `<button class="account-btn">${acc ? `<img src="${avatar(acc)}" alt="">` : `<span class="icon-btn ghost round" style="width:32px;height:32px;background:var(--accent-soft)">${icon('user')}</span>`}<span class="name">${esc(acc ? acc.name : 'Sign in')}</span>${icon('chevron')}</button>`;
  $('.account-btn', box).onclick = (e) => { e.stopPropagation(); toggleAccountMenu(); };
}
function toggleAccountMenu(force) {
  const box = $('#account');
  const open = force ?? !box.classList.contains('open');
  box.classList.toggle('open', open);
  $('.menu', box)?.remove();
  if (!open) return;
  const menu = h(`<div class="menu">
    <div class="menu-title"><span>Minecraft accounts</span><button class="icon-btn ghost" style="width:26px;height:26px" data-x>${icon('close')}</button></div>
    <div class="acc-list">${state.accounts.map((a) => `
      <div class="acc-row ${a.selected ? 'sel' : ''}" data-id="${a.id}">
        <img src="${avatar(a)}" alt=""><div class="grow"><div class="n ellipsis">${esc(a.name)}</div><div class="t">${a.type === 'msa' ? 'Microsoft' : 'Offline'}</div></div>
        <button class="icon-btn ghost" data-rm="${a.id}" title="Remove">${icon('trash')}</button>
      </div>`).join('') || '<div class="muted" style="padding:10px;font-size:13px">No accounts yet.</div>'}</div>
    <div class="sep" style="margin:10px 0"></div>
    <button class="btn primary" style="width:100%" data-add-ms>${icon('microsoft')} Add Microsoft account</button>
    <button class="btn" style="width:100%;margin-top:8px" data-add-off>${icon('user')} Add offline account</button>
  </div>`);
  menu.onclick = (e) => e.stopPropagation();
  $('[data-x]', menu).onclick = () => toggleAccountMenu(false);
  $$('.acc-row', menu).forEach((r) => (r.onclick = async (e) => {
    if (e.target.closest('[data-rm]')) return;
    state.accounts = await nova.accounts.select(r.dataset.id);
    renderAccount(); emit('accounts', state.accounts); toggleAccountMenu(false);
    toast('Switched account', selectedAccount()?.name);
  }));
  $$('[data-rm]', menu).forEach((b) => (b.onclick = async () => { state.accounts = await nova.accounts.remove(b.dataset.rm); renderAccount(); emit('accounts', state.accounts); toggleAccountMenu(true); }));
  $('[data-add-ms]', menu).onclick = (e) => busy(e.currentTarget, async () => {
    state.accounts = await nova.accounts.addMicrosoft();
    renderAccount(); emit('accounts', state.accounts); toggleAccountMenu(false);
    toast('Signed in', `Welcome, ${selectedAccount().name}!`);
  });
  $('[data-add-off]', menu).onclick = async () => {
    toggleAccountMenu(false);
    const name = await promptDialog('Offline account', { label: 'Username', placeholder: 'Steve', okLabel: 'Add' });
    if (!name) return;
    try { state.accounts = await nova.accounts.addOffline(name); renderAccount(); emit('accounts', state.accounts); } catch (e) { toastErr(e); }
  };
  box.append(menu);
}
document.addEventListener('click', () => toggleAccountMenu(false));

// ---------------------------------------------------------------- running instances + console
const logs = new Map();
function renderRunning() {
  const pill = $('#running-pill');
  const n = state.running.size;
  pill.classList.toggle('live', n > 0);
  pill.innerHTML = `${icon(n ? 'rocket' : 'planet')}<span>${n ? `${n} in orbit` : 'Docked'}</span>`;
}
export function openConsole(instanceId) {
  const ids = instanceId ? [instanceId] : [...state.running, ...logs.keys()].filter((v, i, a) => a.indexOf(v) === i);
  if (!ids.length) return toast('Nothing running', 'Launch a profile to see its console.', 'info');
  const id = ids[0];
  const inst = state.instances.find((i) => i.id === id);
  const m = modal(`<div class="row" style="margin-bottom:12px"><h2 class="grow">${icon('terminal')} ${esc(inst?.name || id)}</h2>
    <button class="btn sm" data-copy>${icon('copy')} Copy</button>${state.running.has(id) ? `<button class="btn sm danger" data-kill>${icon('stop')} Stop</button>` : ''}<button class="icon-btn ghost" data-close>${icon('close')}</button></div>
    <div class="console"></div>`, { wide: true, onClose: () => off() });
  const con = $('.console', m.el);
  const add = (line) => { const d = document.createElement('div'); d.textContent = line; if (/WARN/.test(line)) d.className = 'w'; if (/ERROR|Exception/.test(line)) d.className = 'e'; con.append(d); };
  (logs.get(id) || []).forEach(add);
  con.scrollTop = con.scrollHeight;
  const off = on('log:' + id, (line) => { const stick = con.scrollTop + con.clientHeight >= con.scrollHeight - 30; add(line); if (stick) con.scrollTop = con.scrollHeight; });
  $('[data-copy]', m.el).onclick = () => { navigator.clipboard.writeText((logs.get(id) || []).join('\n')); toast('Copied log'); };
  const kill = $('[data-kill]', m.el); if (kill) kill.onclick = () => { nova.game.stop(id); m.close(); };
}

export async function launchInstance(id) {
  if (!selectedAccount()) { toggleAccountMenu(true); return toast('Add an account first', 'Sign in with Microsoft to play.', 'info'); }
  logs.set(id, []);
  sfx.launch();
  emit('launch:start', id);
  try { await nova.game.launch(id); } catch (e) { emit('launch:end', id); taskProgress({ id: 'game-' + id, finished: true }); toastErr(e, 'Launch failed'); }
}

nova.on('game:log', ({ instanceId, line }) => {
  const arr = logs.get(instanceId) || []; arr.push(line); if (arr.length > 4000) arr.splice(0, 1000); logs.set(instanceId, arr);
  emit('log:' + instanceId, line);
});
nova.on('game:progress', (p) => { taskProgress({ id: 'game-' + p.instanceId, label: p.label, done: p.done, total: p.total }); emit('launch:progress', p); });
nova.on('game:state', ({ instanceId, state: st, code }) => {
  if (st === 'starting') { state.running.add(instanceId); taskProgress({ id: 'game-' + instanceId, finished: true }); toast('Game starting', state.instances.find((i) => i.id === instanceId)?.name); }
  if (st === 'stopped') { state.running.delete(instanceId); emit('launch:end', instanceId); if (code && code !== 0) toast('Game crashed', `Exit code ${code} — open the console for details`, 'err', 9000); }
  if (st === 'running') emit('launch:end', instanceId);
  renderRunning(); refreshInstances(); emit('running', state.running);
});
nova.on('task:progress', taskProgress);

// ---------------------------------------------------------------- updates (GitHub releases)
const mb = (n) => (n / 1048576).toFixed(1) + ' MB';
export function updateDialog(u) {
  const m = modal(`<h2>${icon('download')} Orbit ${esc(u.version)} is out</h2>
    <p class="muted">You have v${esc(u.current)}. ${u.asset ? 'Download ' + mb(u.asset.size) + ' — the launcher restarts by itself when it is done.' : ''}</p>
    ${u.notes ? `<div class="card" style="max-height:220px;overflow:auto;white-space:pre-wrap;font-size:13px;padding:12px;margin:10px 0">${esc(u.notes)}</div>` : ''}
    <div data-upbar class="muted" style="font-size:13px;min-height:18px"></div>
    <div class="row" style="justify-content:flex-end;margin-top:12px"><button class="btn" data-close>Later</button><button class="btn primary" data-upgo>${icon('download')} Update now</button></div>`);
  const bar = $('[data-upbar]', m.el);
  const off = nova.on('update:progress', ({ done, total }) => (bar.textContent = `Downloading… ${mb(done)}${total ? ' / ' + mb(total) + ' (' + Math.round(done / total * 100) + '%)' : ''}`));
  $('[data-upgo]', m.el).onclick = async (e) => {
    e.currentTarget.disabled = true;
    try { await nova.update.install(); bar.textContent = 'Restarting Orbit…'; }
    catch (err) { off(); bar.innerHTML = `<span style="color:var(--warn)">${esc(err.message)}</span>`; e.currentTarget.disabled = false; }
  };
}
export function showUpdate(box, u) {
  if (!u.asset) { box.innerHTML = `Newest release <b>${esc(u.name)}</b> has no Orbit.exe attached.`; return; }
  if (!u.available) { box.innerHTML = `<span style="color:var(--ok)">You're up to date</span> — v${esc(u.current)} (newest release: v${esc(u.version)})`; return; }
  box.innerHTML = `<b style="color:var(--accent)">v${esc(u.version)} is ${u.ready ? 'downloaded — it installs when you close Orbit' : 'available'}</b> (you have v${esc(u.current)}) <button class="btn sm primary" data-upopen style="margin-left:8px">Update</button>`;
  $('[data-upopen]', box).onclick = () => updateDialog(u);
}
// silent update downloaded in the background: a small notice, nothing to click (it installs when Orbit closes)
nova.on('update:ready', (u) => {
  if (!u || document.querySelector('.update-note')) return;
  const n = h(`<div class="toast info update-note">${icon('download')}<div class="grow"><b>Orbit ${esc(u.version)} is ready</b><span>It installs by itself when you close Orbit.</span></div><button class="btn sm primary" data-now>Restart now</button></div>`);
  $('#toasts').append(n);
  $('[data-now]', n).onclick = async (e) => {
    e.stopPropagation(); e.currentTarget.disabled = true;
    try { await nova.update.install(); } catch (err) { toastErr(err, 'Update failed'); }
  };
  n.onclick = () => { n.classList.add('out'); setTimeout(() => n.remove(), 220); };
  setTimeout(() => n.isConnected && n.onclick(), 15000);
});
// the game changed something through the in-game Accounts / Locker tabs
nova.on('bridge:changed', (kind) => { if (kind === 'accounts') refreshAccounts(); emit('bridge:' + kind); });
nova.on('win:maximized', (m) => ($('#win-max').innerHTML = icon(m ? 'copy' : 'max')));

// ---------------------------------------------------------------- boot
async function boot() {
  [state.settings, state.info] = await Promise.all([nova.settings.get(), nova.app.info()]);
  applyLook();
  $('#app-version').textContent = 'v' + state.info.version;
  startStarfield($('#starfield'));
  startFx();
  $('#nav').insertAdjacentHTML('beforeend', NAV.map(([k, l, ic]) => `<button class="tab" data-page="${k}">${icon(ic)}<span>${l}</span></button>`).join(''));
  $$('.tab').forEach((b) => (b.onclick = () => go(b.dataset.page)));
  window.addEventListener('resize', moveGlider);
  document.fonts?.ready.then(moveGlider);
  $('#nav-settings').innerHTML = icon('settings'); $('#nav-settings').onclick = () => go('settings');
  $('#win-min').innerHTML = icon('min'); $('#win-min').onclick = () => nova.win.minimize();
  $('#win-max').innerHTML = icon('max'); $('#win-max').onclick = () => nova.win.maximize();
  $('#win-close').innerHTML = icon('close'); $('#win-close').onclick = () => nova.win.close();
  $('#running-pill').onclick = () => openConsole();
  (await nova.game.running()).forEach((id) => state.running.add(id));
  renderRunning();
  await Promise.all([refreshAccounts(), refreshInstances()]);
  go('play');
  document.addEventListener('keydown', (e) => { if (e.altKey && e.key === 'ArrowLeft') step(-1); if (e.altKey && e.key === 'ArrowRight') step(1); });
}
boot().catch(toastErr);
