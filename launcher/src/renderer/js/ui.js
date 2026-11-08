import { icon } from './icons.js';

export const $ = (sel, root = document) => root.querySelector(sel);
export const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
export const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
export function h(html) { const t = document.createElement('template'); t.innerHTML = html.trim(); return t.content.firstElementChild; }

export const fmtNum = (n) => Number(n || 0).toLocaleString('de-DE');
export const fmtShort = (n) => (n >= 1e6 ? (n / 1e6).toFixed(1) + 'M' : n >= 1e3 ? (n / 1e3).toFixed(1) + 'k' : String(n || 0));
export function timeAgo(ts) {
  if (!ts) return 'never';
  const s = (Date.now() - ts) / 1000;
  const units = [[31536000, 'y'], [2592000, 'mo'], [86400, 'd'], [3600, 'h'], [60, 'm']];
  for (const [sec, u] of units) if (s >= sec) return Math.floor(s / sec) + u + ' ago';
  return 'just now';
}
export const fmtHours = (ms) => (ms > 3600000 ? (ms / 3600000).toFixed(1) + 'h' : Math.round(ms / 60000) + 'm');
export const fmtBytes = (b) => (b > 1048576 ? (b / 1048576).toFixed(1) + ' MB' : Math.round(b / 1024) + ' KB');

// ---------------- reactive app state ----------------
const listeners = new Map();
export const state = { accounts: [], instances: [], running: new Set(), settings: {}, info: {} };
export function on(evt, fn) { if (!listeners.has(evt)) listeners.set(evt, new Set()); listeners.get(evt).add(fn); return () => listeners.get(evt).delete(fn); }
export function emit(evt, data) { listeners.get(evt)?.forEach((fn) => fn(data)); }

// ---------------- toasts ----------------
export function toast(title, msg = '', kind = 'ok', ms = 4200) {
  const ic = kind === 'err' ? 'alert' : kind === 'info' ? 'info' : 'check';
  const el = h(`<div class="toast ${kind}">${icon(ic)}<div class="grow"><b>${esc(title)}</b>${msg ? `<span>${esc(msg)}</span>` : ''}</div></div>`);
  $('#toasts').append(el);
  const kill = () => { el.classList.add('out'); setTimeout(() => el.remove(), 220); };
  el.onclick = kill;
  setTimeout(kill, ms);
}
export const toastErr = (e, title = 'Something went wrong') => toast(title, e?.message || String(e), 'err', 7000);

// ---------------- modals ----------------
export function modal(html, { wide = false, onClose } = {}) {
  const wrap = h(`<div class="modal-wrap"><div class="modal ${wide ? 'wide' : ''}">${html}</div></div>`);
  const close = () => { if (wrap.classList.contains('closing')) return; wrap.classList.add('closing'); setTimeout(() => wrap.remove(), 180); onClose?.(); document.removeEventListener('keydown', key); };
  const key = (e) => e.key === 'Escape' && close();
  wrap.addEventListener('mousedown', (e) => { if (e.target === wrap) close(); });
  document.addEventListener('keydown', key);
  $('#modal-root').append(wrap);
  $$('[data-close]', wrap).forEach((b) => (b.onclick = close));
  return { el: $('.modal', wrap), close };
}
export function confirmDialog(title, text, { okLabel = 'Confirm', danger = false } = {}) {
  return new Promise((resolve) => {
    let done = false;
    const m = modal(`<h2>${esc(title)}</h2><p class="desc">${esc(text)}</p><div class="foot"><button class="btn" data-close>Cancel</button><button class="btn ${danger ? 'danger' : 'primary'}" data-ok>${esc(okLabel)}</button></div>`, { onClose: () => !done && resolve(false) });
    $('[data-ok]', m.el).onclick = () => { done = true; resolve(true); m.close(); };
  });
}
export function promptDialog(title, { label = '', value = '', placeholder = '', okLabel = 'Save' } = {}) {
  return new Promise((resolve) => {
    let done = false;
    const m = modal(`<h2>${esc(title)}</h2><div style="margin-top:16px">${label ? `<label class="lbl">${esc(label)}</label>` : ''}<input class="input" value="${esc(value)}" placeholder="${esc(placeholder)}"></div><div class="foot"><button class="btn" data-close>Cancel</button><button class="btn primary" data-ok>${esc(okLabel)}</button></div>`, { onClose: () => !done && resolve(null) });
    const input = $('input', m.el);
    setTimeout(() => { input.focus(); input.select(); }, 50);
    const ok = () => { done = true; resolve(input.value.trim()); m.close(); };
    $('[data-ok]', m.el).onclick = ok;
    input.onkeydown = (e) => e.key === 'Enter' && ok();
  });
}

/** Wraps an async button action with a spinner + error toast. */
export async function busy(btn, fn) {
  const old = btn.innerHTML;
  btn.disabled = true;
  btn.innerHTML = `${icon('refresh', 'spin')}${btn.dataset.busy || ''}`;
  try { return await fn(); } catch (e) { toastErr(e); } finally { btn.disabled = false; btn.innerHTML = old; }
}

// ---------------- progress dock ----------------
const tasks = new Map();
export function taskProgress({ id, label, done, total, finished }) {
  let el = tasks.get(id);
  if (finished) { if (el) { el.classList.add('out'); setTimeout(() => el.remove(), 200); tasks.delete(id); } return; }
  if (!el) { el = h(`<div class="task"><div class="lab"></div><div class="progress"><i></i></div></div>`); $('#dock').append(el); tasks.set(id, el); }
  $('.lab', el).textContent = label;
  const pr = $('.progress', el);
  pr.classList.toggle('indet', !total);
  if (total) $('i', pr).style.width = Math.min(100, (done / total) * 100) + '%';
  clearTimeout(el._t);
  el._t = setTimeout(() => taskProgress({ id, finished: true }), 8000);
}
export function endTask(id) { taskProgress({ id, finished: true }); }

export function setRange(input) { const p = ((input.value - input.min) / (input.max - input.min)) * 100; input.style.setProperty('--p', p + '%'); }
export const avatar = (acc) => (acc ? `https://mc-heads.net/avatar/${acc.type === 'msa' ? acc.id : acc.name}/32` : '');
