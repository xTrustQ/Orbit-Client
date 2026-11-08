import { icon } from '../icons.js';
import { $, $$, h, esc, on, modal, toast, toastErr, busy, confirmDialog } from '../ui.js';
import { selectedAccount } from '../app.js';
import { drawSkinFront } from '../art.js';

let skins = [], offs = [], q = '';

export function render() {
  const el = h(`<div>
    <div class="toolbar">
      <div class="field" style="width:420px">${icon('search')}<input placeholder="Search skins..." data-q value="${esc(q)}"></div>
      <div class="grow"></div>
      <button class="btn primary" data-add>${icon('plus')} Add skin</button>
    </div>
    <div class="divider"></div>
    <div class="grid-cards stagger" data-grid></div>
  </div>`);
  const grid = $('[data-grid]', el);

  const paint = () => {
    const list = skins.filter((s) => s.name.toLowerCase().includes(q.toLowerCase()));
    grid.innerHTML = `<div class="card hover skin-card add-card" data-new><div class="plus">${icon('plus')}</div><div class="title">Add New Skin</div><div class="sub">${icon('upload').replace('<svg', '<svg style="width:12px;vertical-align:-2px"')} Upload or import a skin</div></div>` +
      list.map((s) => `<div class="card hover skin-card" data-id="${s.id}">
        <canvas class="preview"></canvas>
        <div class="card-actions"><button class="icon-btn" data-edit title="Edit">${icon('edit')}</button><button class="icon-btn" data-del title="Delete">${icon('trash')}</button></div>
        <div class="title ellipsis">${esc(s.name)}</div><div class="sub">${s.model === 'slim' ? 'Slim' : 'Classic'} · click to apply</div></div>`).join('');
    $('[data-new]', grid).onclick = addDialog;
    $$('.skin-card[data-id]', grid).forEach((c) => {
      const s = skins.find((x) => x.id === c.dataset.id);
      drawSkinFront($('canvas', c), s.data, s.model === 'slim', 8).catch(() => {});
      c.onclick = (e) => {
        if (e.target.closest('[data-edit]')) return editDialog(s);
        if (e.target.closest('[data-del]')) return confirmDialog('Delete skin?', `Remove "${s.name}" from your library?`, { okLabel: 'Delete', danger: true }).then(async (ok) => { if (ok) { skins = await nova.skins.delete(s.id); paint(); } });
        apply(s, c);
      };
    });
  };
  const load = async () => { try { skins = await nova.skins.list(); } catch (e) { toastErr(e); } paint(); };

  async function apply(s, card) {
    const acc = selectedAccount();
    if (!acc) return toast('Sign in first', 'Add a Microsoft account to change your skin.', 'info');
    card.style.opacity = 0.6;
    try { await nova.skins.apply(s.id); $$('.skin-card', grid).forEach((x) => x.classList.toggle('active-skin', x === card)); toast('Skin applied', `${s.name} is now your skin on ${acc.name}`); }
    catch (e) { toastErr(e, 'Could not apply skin'); }
    finally { card.style.opacity = 1; }
  }

  function addDialog() {
    let picked = null, model = 'classic';
    const m = modal(`<h2>Add skin</h2><p class="desc">Upload a 64×64 PNG or grab any player's skin by username.</p>
      <div class="tabs"><button class="active" data-tab="file">From file</button><button data-tab="user">From username</button></div>
      <div data-pane="file"><div class="row" style="gap:18px;align-items:flex-start">
        <div class="card" style="width:140px;height:250px;display:grid;place-items:center"><canvas data-prev style="image-rendering:pixelated;max-width:120px"></canvas></div>
        <div class="grow" style="display:flex;flex-direction:column;gap:12px">
          <button class="btn" data-pick>${icon('upload')} Choose PNG…</button>
          <div><label class="lbl">Name</label><input class="input" data-name placeholder="My skin"></div>
          <div><label class="lbl">Arm model</label><div class="seg"><button class="active" data-m="classic">Classic</button><button data-m="slim">Slim</button></div></div>
        </div></div></div>
      <div data-pane="user" hidden><label class="lbl">Minecraft username</label><input class="input" data-user placeholder="Notch"></div>
      <div class="foot"><button class="btn" data-close>Cancel</button><button class="btn primary" data-ok>Add</button></div>`);
    const E = m.el; let tab = 'file';
    $$('[data-tab]', E).forEach((b) => (b.onclick = () => { tab = b.dataset.tab; $$('[data-tab]', E).forEach((x) => x.classList.toggle('active', x === b)); $$('[data-pane]', E).forEach((p) => (p.hidden = p.dataset.pane !== tab)); }));
    $$('[data-m]', E).forEach((b) => (b.onclick = () => { model = b.dataset.m; $$('[data-m]', E).forEach((x) => x.classList.toggle('active', x === b)); if (picked) drawSkinFront($('[data-prev]', E), picked.data, model === 'slim', 7); }));
    $('[data-pick]', E).onclick = async () => {
      picked = await nova.skins.pickFile();
      if (!picked) return;
      if (!$('[data-name]', E).value) $('[data-name]', E).value = picked.name;
      drawSkinFront($('[data-prev]', E), picked.data, model === 'slim', 7);
    };
    $('[data-ok]', E).onclick = (e) => busy(e.currentTarget, async () => {
      if (tab === 'user') { const u = $('[data-user]', E).value.trim(); if (!u) throw new Error('Enter a username'); skins = await nova.skins.importUsername(u); }
      else { if (!picked) throw new Error('Choose a PNG first'); skins = await nova.skins.save({ name: $('[data-name]', E).value.trim() || picked.name, model, png: picked.data }); }
      paint(); m.close(); toast('Skin added');
    });
  }

  function editDialog(s) {
    let model = s.model;
    const m = modal(`<h2>Edit skin</h2><div style="margin-top:16px;display:flex;flex-direction:column;gap:14px">
      <div><label class="lbl">Name</label><input class="input" data-name value="${esc(s.name)}"></div>
      <div><label class="lbl">Arm model</label><div class="seg"><button data-m="classic" class="${model === 'classic' ? 'active' : ''}">Classic</button><button data-m="slim" class="${model === 'slim' ? 'active' : ''}">Slim</button></div></div></div>
      <div class="foot"><button class="btn" data-close>Cancel</button><button class="btn primary" data-ok>Save</button></div>`);
    $$('[data-m]', m.el).forEach((b) => (b.onclick = () => { model = b.dataset.m; $$('[data-m]', m.el).forEach((x) => x.classList.toggle('active', x === b)); }));
    $('[data-ok]', m.el).onclick = async () => { skins = await nova.skins.update(s.id, { name: $('[data-name]', m.el).value.trim() || s.name, model }); paint(); m.close(); };
  }

  $('[data-q]', el).oninput = (e) => { q = e.target.value; paint(); };
  $('[data-add]', el).onclick = addDialog;
  load();
  offs = [on('accounts', load)];
  return el;
}
export function destroy() { offs.forEach((f) => f()); offs = []; }
