// A clickable copy of Orbit's in-game menu (Right Shift), built from modules.json — same tabs, search, filters,
// toggles and settings as in the game. Nothing here changes your game; it's just to try it out.
(function () {
  const root = document.getElementById('orbit-menu');
  if (!root) return;
  const CATS = [['RENDER', 'Render', 'See the world in a different light'], ['COMBAT', 'Combat', 'Everything you need in a fight'],
    ['HUD', 'HUD', 'Information on your screen'], ['PLAYER', 'Player', 'Movement, camera & emotes'], ['CLIENT', 'Settings', 'Orbit itself: theme, menus & keys']];
  const ICON = {
    RENDER: '<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
    COMBAT: '<path d="M14.5 3H21v6.5L9.5 21 3 14.5z"/><path d="M5 19l-2 2M8 13l3 3"/>',
    HUD: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    PLAYER: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4.4 3.6-7 8-7s8 2.6 8 7"/>',
    CLIENT: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
  };
  const SWATCHES = ['#FFFFFF', '#B8C2CC', '#000000', '#FF5D6C', '#FFA24D', '#FFD84D', '#4DFF88', '#34F5A5', '#4DD8FF', '#4D7BFF', '#B18CFF', '#FF7BD0'];
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const icon = (cat) => `<svg viewBox="0 0 24 24" aria-hidden="true">${ICON[cat] || ICON.CLIENT}</svg>`;

  let mods = [], cat = 'RENDER', filter = 'all', search = '', open = null;
  const fav = new Set(['zoom', 'freelook', 'fps', 'keystrokes']);

  root.innerHTML = `
    <div class="om-top">
      <div class="om-brand"><span class="om-logo"></span>Orbit</div>
      <div class="om-tabs" role="tablist">${CATS.map(([k, t]) => `<button role="tab" data-cat="${k}">${t}</button>`).join('')}<i class="om-pill"></i></div>
      <label class="om-search"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/></svg><input placeholder="Search" aria-label="Search mods" spellcheck="false"></label>
    </div>
    <div class="om-body"></div>
    <p class="om-hint"></p>`;
  const body = root.querySelector('.om-body'), hint = root.querySelector('.om-hint'), input = root.querySelector('.om-search input');
  const tabs = root.querySelector('.om-tabs'), pill = root.querySelector('.om-pill');

  function movePill() {
    const b = tabs.querySelector(`[data-cat="${cat}"]`);
    tabs.querySelectorAll('button').forEach((x) => x.classList.toggle('on', !search && x === b));
    pill.style.opacity = search ? 0 : 1;
    if (b) { pill.style.width = b.offsetWidth + 'px'; pill.style.transform = `translateX(${b.offsetLeft}px)`; }
  }

  function visible() {
    const q = search.toLowerCase();
    return mods.filter((m) => (q ? (m.name + ' ' + m.desc).toLowerCase().includes(q) : m.category === cat)
      && (filter !== 'on' || m.state.on) && (filter !== 'fav' || fav.has(m.id)));
  }

  function grid() {
    open = null;
    const list = visible(), c = CATS.find(([k]) => k === cat);
    hint.textContent = matchMedia('(hover: none)').matches ? 'Tap to toggle  ·  Tap ••• for settings'
      : 'Click to toggle  ·  Right-click for settings  ·  Middle-click to favourite';
    body.innerHTML = `
      <div class="om-head">
        <div><h3>${search ? 'Results' : c[1]}</h3><p>${search ? `for “${esc(search)}”` : c[2]}</p></div>
        <div class="om-filters">${[['all', 'All'], ['on', 'On'], ['fav', 'Favourites']].map(([k, t]) => `<button data-f="${k}" class="${filter === k ? 'on' : ''}">${t}</button>`).join('')}
          <span class="om-count">● ${list.filter((m) => m.state.on).length} of ${list.length} active</span></div>
      </div>
      <div class="om-grid">${list.length ? list.map((m, i) => `
        <div class="om-card${m.state.on ? ' on' : ''}" data-id="${m.id}" tabindex="0" style="--d:${Math.min(i, 18) * 18}ms">
          <span class="om-ic">${icon(m.category)}</span>
          <span class="om-txt"><b>${esc(m.name)}${fav.has(m.id) ? ' <i class="om-star">★</i>' : ''}</b><small>${esc(m.desc)}</small></span>
          ${m.opts.length ? '<button class="om-gear" aria-label="Settings"><i></i><i></i><i></i></button>' : '<span class="om-gear-pad"></span>'}
          ${m.toggle ? `<span class="om-toggle${m.state.on ? ' on' : ''}"></span>` : ''}
        </div>`).join('') : `<p class="om-empty">${filter === 'fav' ? 'No favourites yet — middle-click a mod to add it' : 'Nothing here'}</p>`}</div>`;
    movePill();
    body.querySelectorAll('[data-f]').forEach((b) => (b.onclick = () => { filter = b.dataset.f; grid(); }));
    body.querySelectorAll('.om-card').forEach((el) => {
      const m = mods.find((x) => x.id === el.dataset.id);
      el.onclick = (e) => {
        if (e.target.closest('.om-gear')) { settings(m); return; }
        if (!m.toggle) { settings(m); return; }
        m.state.on = !m.state.on;
        el.classList.toggle('on', m.state.on);
        el.querySelector('.om-toggle')?.classList.toggle('on', m.state.on);
        const l = visible();
        body.querySelector('.om-count').textContent = `● ${l.filter((x) => x.state.on).length} of ${l.length} active`;
        if (filter === 'on' && !m.state.on) grid();
      };
      el.oncontextmenu = (e) => { e.preventDefault(); if (m.opts.length) settings(m); };
      el.onauxclick = (e) => { if (e.button === 1) { e.preventDefault(); fav.has(m.id) ? fav.delete(m.id) : fav.add(m.id); grid(); } };
      el.onmousedown = (e) => { if (e.button === 1) e.preventDefault(); }; // no auto-scroll
      el.onkeydown = (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); el.click(); } };
    });
  }

  function row(m, s, i) {
    const v = m.state.s[s.id];
    let ctl = '';
    if (s.type === 'bool') ctl = `<span class="om-toggle${v ? ' on' : ''}"></span>`;
    else if (s.type === 'slider') {
      const dec = (String(s.step).split('.')[1] || '').length;
      ctl = `<span class="om-val">${Number(v).toFixed(dec)}${esc(s.unit)}</span><input type="range" min="${s.min}" max="${s.max}" step="${s.step}" value="${v}" aria-label="${esc(s.name)}" style="--f:${((v - s.min) / (s.max - s.min)) * 100}%">`;
    } else if (s.type === 'color') ctl = `<span class="om-sw">${SWATCHES.map((c) => `<button style="background:${c}" class="${c.toLowerCase() === String(v).toLowerCase() ? 'on' : ''}" data-c="${c}" aria-label="${c}"></button>`).join('')}</span>`;
    else ctl = `<span class="om-mode"><button data-d="-1" aria-label="Previous">‹</button><b>${esc(v)}</b><button data-d="1" aria-label="Next">›</button></span>`;
    return `<div class="om-row" data-i="${i}" data-type="${s.type}"><span>${esc(s.name)}</span><span class="om-ctl">${ctl}</span></div>`;
  }

  function settings(m) {
    open = m;
    hint.textContent = 'Esc or ‹ to go back  ·  only a preview — your game isn’t changed';
    body.innerHTML = `
      <div class="om-panel">
        <div class="om-ph">
          <button class="om-back" aria-label="Back">‹</button>
          <span class="om-ic">${icon(m.category)}</span>
          <span class="om-txt"><b>${esc(m.name)}</b><small>${esc(m.desc)}</small></span>
          <button class="om-fav${fav.has(m.id) ? ' on' : ''}" aria-label="Favourite">★</button>
          ${m.toggle ? `<span class="om-toggle${m.state.on ? ' on' : ''}" role="switch"></span>` : ''}
        </div>
        <div class="om-rows">${m.opts.map((s, i) => (shown(m, s) ? row(m, s, i) : '')).join('')}</div>
        <div class="om-actions"><button class="om-reset">Reset settings</button>${m.category === 'HUD' ? '<button class="om-move" disabled title="Only in the game">Move on screen</button>' : ''}</div>
      </div>`;
    body.querySelector('.om-back').onclick = grid;
    body.querySelector('.om-fav').onclick = (e) => { fav.has(m.id) ? fav.delete(m.id) : fav.add(m.id); e.currentTarget.classList.toggle('on', fav.has(m.id)); };
    const head = body.querySelector('.om-ph .om-toggle');
    if (head) head.onclick = () => { m.state.on = !m.state.on; head.classList.toggle('on', m.state.on); };
    body.querySelector('.om-reset').onclick = () => { m.state.s = defaults(m); settings(m); };
    body.querySelectorAll('.om-row').forEach((r) => {
      const s = m.opts[r.dataset.i];
      if (s.type === 'bool') r.onclick = () => { m.state.s[s.id] = !m.state.s[s.id]; r.querySelector('.om-toggle').classList.toggle('on', m.state.s[s.id]); relayout(m, s); };
      else if (s.type === 'slider') {
        const range = r.querySelector('input'), val = r.querySelector('.om-val'), dec = (String(s.step).split('.')[1] || '').length;
        range.oninput = () => { const v = +range.value; m.state.s[s.id] = v; val.textContent = v.toFixed(dec) + s.unit; range.style.setProperty('--f', ((v - s.min) / (s.max - s.min)) * 100 + '%'); };
      } else if (s.type === 'color') r.querySelectorAll('[data-c]').forEach((b) => (b.onclick = () => { m.state.s[s.id] = b.dataset.c; r.querySelectorAll('[data-c]').forEach((x) => x.classList.toggle('on', x === b)); }));
      else {
        const step = (d) => { const o = s.options, i = (o.indexOf(m.state.s[s.id]) + d + o.length) % o.length; m.state.s[s.id] = o[i]; r.querySelector('b').textContent = o[i]; relayout(m, s); };
        r.querySelectorAll('[data-d]').forEach((b) => (b.onclick = (e) => { e.stopPropagation(); step(+b.dataset.d); }));
        r.querySelector('b').onclick = () => step(1);
        r.oncontextmenu = (e) => { e.preventDefault(); step(-1); };
      }
    });
  }

  // HUD elements: the background options only show with the Default theme and Background on (as in the game)
  const BG = ['bgColor', 'bgOpacity', 'rounded', 'border'];
  const shown = (m, s) => m.category !== 'HUD' || !BG.includes(s.id) || !('background' in m.state.s) || (m.state.s.background && m.state.s.theme === 'Default');
  const relayout = (m, s) => { if (m.category === 'HUD' && (s.id === 'background' || s.id === 'theme')) { const y = body.scrollTop; settings(m); body.scrollTop = y; } };
  const defaults = (m) => Object.fromEntries(m.opts.map((s) => [s.id, s.value]));

  tabs.querySelectorAll('button').forEach((b) => (b.onclick = () => { cat = b.dataset.cat; search = ''; input.value = ''; grid(); }));
  input.addEventListener('input', () => { search = input.value.trim(); grid(); });
  root.addEventListener('keydown', (e) => { if (e.key === 'Escape') { if (open) grid(); else if (search) { search = ''; input.value = ''; grid(); } } });
  addEventListener('resize', movePill);

  fetch('modules.json').then((r) => r.json()).then((list) => {
    mods = list.map((m) => ({ ...m, opts: m.opts || [], state: { on: m.on, s: defaults({ opts: m.opts || [] }) } }));
    grid();
    requestAnimationFrame(movePill);
    document.fonts?.ready.then(movePill);
  }).catch(() => { body.innerHTML = '<p class="om-empty">Couldn’t load the mods.</p>'; });
})();
