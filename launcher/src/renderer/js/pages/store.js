import { icon } from '../icons.js';
import { $, $$, h, esc, modal, toast, toastErr, fmtNum, confirmDialog } from '../ui.js';
import { buildCape, drawCapeFront } from '../art.js';

let cat = null;

const coin = (n, cls = '') => `<span class="coin ${cls}"><i></i>${fmtNum(n)}</span>`;
const capePng = {}; // preset -> built texture

export function render() {
  const el = h(`<div>
    <div class="page-head"><div><h1>Orbit Store</h1><p>Capes and emotes, paid with Orbit Coins.</p></div>
      <div class="row"><div class="balance" data-balance>${coin(0)}</div><span data-ownerbtn></span><button class="btn primary" data-getcoins>${icon('plus')} Get coins</button></div></div>
    <div data-banner></div>
    <div class="store-title">Premium capes</div>
    <div class="store-grid stagger" data-capes></div>
    <div class="store-title">Premium emotes</div>
    <div class="store-grid stagger" data-items></div>
    <div class="store-title">Orbit Coins</div>
    <div class="pack-grid stagger" data-packs></div>
    <div class="store-title">Included free</div>
    <div class="chips" data-free></div>
  </div>`);

  const card = (i) => {
    const preview = i.type === 'cape'
      ? `<div class="item-cape"><canvas data-cape="${esc(i.id)}"></canvas></div>`
      : `<div class="item-orb" style="background:radial-gradient(circle at 30% 30%,#fff,${i.orb[0]} 35%,${i.orb[1]})"><span class="ring"></span></div>`;
    const action = i.owned
      ? (i.type === 'cape' ? `<button class="btn sm soft" data-equip="${esc(i.id)}">${icon('check')} Equip</button>` : `<button class="btn sm soft" disabled>${icon('check')} Owned</button>`)
      : `<button class="btn sm primary" data-buy="${esc(i.id)}">${coin(i.price, 'dark')}</button>`;
    return `<div class="card hover item" data-id="${esc(i.id)}">
      ${cat.owner && i.custom ? `<button class="icon-btn ghost item-del" data-del="${esc(i.id)}" title="Remove from store">${icon('trash')}</button>` : ''}
      ${preview}
      <div class="item-body"><span class="tag">${i.type === 'emote' ? 'Emote' : i.custom ? 'Cape · Custom' : 'Cape'}</span><b>${esc(i.name)}</b><p>${esc(i.desc)}</p></div>
      ${action}
    </div>`;
  };

  const texture = async (i) => {
    if (i.image) return i.image;
    return (capePng[i.preset] ||= await buildCape({ preset: i.preset }));
  };

  const paint = () => {
    $('[data-balance]', el).innerHTML = cat.owner ? `<span class="coin"><i></i>∞</span>` : coin(cat.coins);
    $('[data-banner]', el).innerHTML = cat.owner
      ? `<div class="card soon owner">${icon('sparkles')}<div><b>Owner mode.</b> <span class="muted">You're signed in as the Orbit owner: unlimited coins, everything unlocked, and you can add your own cosmetics.</span></div></div>`
      : `<div class="card soon">${icon('info')}<div><b>Buying coins is coming soon.</b> <span class="muted">Payments aren't connected yet, so nothing can be charged.</span></div></div>`;
    $('[data-ownerbtn]', el).innerHTML = cat.owner ? `<button class="btn" data-add>${icon('upload')} Add cosmetic</button>` : '';
    $('[data-capes]', el).innerHTML = cat.items.filter((i) => i.type === 'cape').map(card).join('');
    $('[data-items]', el).innerHTML = cat.items.filter((i) => i.type === 'emote').map(card).join('');
    $('[data-packs]', el).innerHTML = cat.packs.map((p) => `<div class="card hover pack" data-pack="${p.id}">
      ${p.tag ? `<span class="pack-tag">${esc(p.tag)}</span>` : ''}
      <div class="pack-coins">${coin(p.coins + p.bonus)}</div>
      <div class="muted" style="font-size:12px;min-height:16px">${p.bonus ? `${fmtNum(p.coins)} + ${fmtNum(p.bonus)} bonus` : '&nbsp;'}</div>
      <button class="btn sm" data-pack-buy="${p.id}">${esc(p.price)}</button>
    </div>`).join('');
    $('[data-free]', el).innerHTML = cat.free.map((f) => `<span class="chip" style="cursor:default">${esc(f)}</span>`).join('');
    for (const cv of $$('[data-cape]', el)) {
      const i = cat.items.find((x) => x.id === cv.dataset.cape);
      texture(i).then((png) => drawCapeFront(cv, png)).catch(() => {});
    }
    $$('[data-buy]', el).forEach((b) => (b.onclick = () => buy(b.dataset.buy)));
    $$('[data-equip]', el).forEach((b) => (b.onclick = () => equip(b.dataset.equip)));
    $$('[data-del]', el).forEach((b) => (b.onclick = () => removeItem(b.dataset.del)));
    $$('[data-pack-buy]', el).forEach((b) => (b.onclick = () => coinsSoon()));
    const add = $('[data-add]', el); if (add) add.onclick = addCosmetic;
  };

  const equip = async (id) => {
    const i = cat.items.find((x) => x.id === id);
    try {
      const match = (c) => (i.custom ? c.name === i.name && !c.preset : c.preset === i.preset);
      let cape = (await nova.capes.nova()).find(match);
      if (!cape) {
        const png = await texture(i);
        cape = (await nova.capes.saveOrbit(i.custom ? { name: i.name, png } : { name: i.name, preset: i.preset, png })).find(match);
      }
      if (cape) await nova.capes.selectOrbit(cape.id);
      toast('Cape equipped', `${i.name} — visible next time you join a world`);
    } catch (e) { toastErr(e); }
  };

  const buy = async (id) => {
    const i = cat.items.find((x) => x.id === id);
    try {
      cat = await nova.store.buyItem(id);
      paint();
      toast('Unlocked!', i.type === 'cape' ? 'Press Equip to wear it — it also appears on the Capes page.' : 'It shows up in your emote wheel in-game (B).');
    } catch (e) {
      if (e.code === 'NOT_ENOUGH_COINS' || /more Orbit Coins/.test(e.message)) {
        const m = modal(`<h2>${icon('sparkles')} Not enough Orbit Coins</h2><p class="desc">${esc(e.message)}</p><div class="foot"><button class="btn" data-close>Later</button><button class="btn primary" data-get>Get coins</button></div>`);
        $('[data-get]', m.el).onclick = () => { m.close(); coinsSoon(); };
      } else toast('Something went wrong', e.message, 'err');
    }
  };

  const removeItem = async (id) => {
    const i = cat.items.find((x) => x.id === id);
    if (!(await confirmDialog('Remove cosmetic?', `Take "${i.name}" out of the store?`, { okLabel: 'Remove', danger: true }))) return;
    try { cat = await nova.store.removeItem(id); paint(); } catch (e) { toastErr(e); }
  };

  const addCosmetic = async () => {
    const img = await nova.capes.pickImage();
    if (!img) return;
    const png = await buildCape({ image: img.data });
    const m = modal(`<div class="row"><h2 class="grow">${icon('sparkles')} Add a cape to the store</h2><button class="icon-btn ghost" data-close>${icon('close')}</button></div>
      <div class="row" style="gap:18px;align-items:flex-start">
        <div class="item-cape big"><canvas data-prev></canvas></div>
        <div class="grow">
          <label class="lbl">Name</label><input class="input" data-name maxlength="32" value="${esc(img.name || '')}">
          <label class="lbl" style="margin-top:10px">Price (Orbit Coins)</label><input class="input" data-price type="number" min="0" max="100000" value="500">
          <label class="lbl" style="margin-top:10px">Description</label><input class="input" data-desc maxlength="80" placeholder="Shown under the name">
        </div>
      </div>
      <div class="foot"><button class="btn" data-close>Cancel</button><button class="btn primary" data-save>${icon('plus')} Add to store</button></div>`);
    drawCapeFront($('[data-prev]', m.el), png);
    $('[data-save]', m.el).onclick = async () => {
      try {
        cat = await nova.store.addItem({ name: $('[data-name]', m.el).value, price: $('[data-price]', m.el).value, desc: $('[data-desc]', m.el).value, png });
        m.close(); paint(); toast('Added to the store', 'It shows up under Premium capes.');
      } catch (e) { toastErr(e); }
    };
  };

  const coinsSoon = () => {
    modal(`<h2>${icon('sparkles')} Coming soon</h2><p class="desc">Orbit Coins can't be bought yet: the payment system isn't connected. Nothing was charged.</p>
      <div class="foot"><button class="btn primary" data-close>Got it</button></div>`);
  };

  $('[data-getcoins]', el).onclick = coinsSoon;
  nova.store.catalogue().then((c) => { cat = c; paint(); }).catch(toastErr);
  return el;
}
