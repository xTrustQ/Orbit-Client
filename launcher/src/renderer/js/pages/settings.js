import { icon } from '../icons.js';
import { $, $$, h, esc, state, toast, setRange } from '../ui.js';
import { applyLook, showUpdate } from '../app.js';

const THEMES = [['nebula', 'Nebula'], ['deepspace', 'Deep Space'], ['supernova', 'Supernova'], ['aurora', 'Aurora'], ['mars', 'Mars'], ['eclipse', 'Eclipse'], ['lunar', 'Lunar']];
const SECTIONS = [['appearance', 'Appearance', 'palette'], ['game', 'Game & performance', 'cpu'], ['ingame', 'Orbit mod', 'zap'], ['spotify', 'Spotify lyrics', 'music'], ['integrations', 'Integrations', 'key'], ['about', 'About', 'info']];

async function save(patch) { Object.assign(state.settings, patch); await nova.settings.set(patch); }
const toggle = (key, on) => `<button class="toggle ${on ? 'on' : ''}" data-tog="${key}"></button>`;
const seg = (key, opts, cur) => `<div class="seg" data-seg="${key}">${opts.map(([v, l]) => `<button data-v="${v}" class="${cur === v ? 'active' : ''}">${l}</button>`).join('')}</div>`;

export function render(params = {}) {
  const s = state.settings;
  const mem = s.memory || s.defaultMemory;
  const res = s.resolution || {};
  const el = h(`<div><div class="page-head"><div><h1>Settings</h1><p>Make Orbit yours.</p></div></div>
  <div class="settings">
    <nav class="settings-nav">${SECTIONS.map(([k, l, i]) => `<button data-sec="${k}">${icon(i)} ${l}</button>`).join('')}</nav>
    <div class="stagger">
      <section class="card section" id="sec-appearance"><h2>Appearance</h2><p>Switch the whole launcher design instantly.</p>
        <label class="lbl">Theme</label>
        <div class="theme-grid">${THEMES.map(([t, n]) => `<div class="theme sw-${t} ${(s.theme || 'nebula') === t ? 'sel' : ''}" data-th="${t}"><div class="stars"></div><div class="orb"></div><div class="nm">${n}</div></div>`).join('')}</div>
        <div class="sep"></div>
        <div class="setting"><div class="l"><b>Typography</b><span>Futuristic Orbitron, clean modern, or retro pixel headings</span></div>${seg('font', [['orbit', 'Orbit'], ['clean', 'Clean'], ['pixel', 'Pixel']], s.font || 'clean')}</div>
        <div class="setting"><div class="l"><b>Background</b><span>Live starfield, thick nebula clouds, or a calm minimal sky</span></div>${seg('background', [['stars', 'Starfield'], ['nebula', 'Nebula'], ['minimal', 'Minimal']], s.background || 'stars')}</div>
        <div class="setting"><div class="l"><b>Animations</b><span>Page transitions and motion</span></div>${toggle('animations', s.animations !== false)}</div>
        <div class="setting"><div class="l"><b>Sound effects</b><span>Soft clicks, swooshes and a liftoff sound when you launch</span></div>${toggle('sounds', s.sounds !== false)}</div>
      </section>

      <section class="card section" id="sec-game"><h2>Game & performance</h2><p>Defaults for every profile (each profile can override memory).</p>
        <div class="setting" style="display:block"><div class="row" style="justify-content:space-between;margin-bottom:12px"><div class="l"><b>Memory</b><span>RAM given to Minecraft. More isn't always faster — 4–6 GB is the sweet spot.</span></div><b data-memv>${(mem / 1024).toFixed(1)} GB</b></div>
          <input type="range" min="1024" max="${Math.max(2048, (s.totalMemory || 8192) - 1024)}" step="256" value="${mem}" data-mem></div>
        <div class="setting"><div class="l"><b>Optimized JVM flags</b><span>Tuned G1 garbage collector for fewer lag spikes</span></div>${toggle('optimizedJvm', s.optimizedJvm !== false)}</div>
        <div class="setting"><div class="l"><b>FPS boost pack on new profiles</b><span>Sodium, Lithium, FerriteCore, EntityCulling, ImmediatelyFast, ModernFix, MoreCulling, Dynamic FPS, Krypton</span></div>${toggle('performancePack', s.performancePack !== false)}</div>
        <div class="setting"><div class="l"><b>Standard mods on new profiles</b><span>Simple Voice Chat (proximity voice on servers that have it) and TierTagger (PvP tiers next to names)</span></div>${toggle('standardMods', s.standardMods !== false)}</div>
        <div class="setting"><div class="l"><b>Minimize launcher on launch</b><span>Get it out of the way while you play</span></div>${toggle('closeOnLaunch', !!s.closeOnLaunch)}</div>
        <div class="setting"><div class="l"><b>Game window size</b><span>Leave empty for Minecraft's default</span></div><div class="row"><input class="input" style="width:90px" placeholder="1920" data-w value="${res.width || ''}"><span class="muted">×</span><input class="input" style="width:90px" placeholder="1080" data-h value="${res.height || ''}"></div></div>
        <div class="setting"><div class="l"><b>Java</b><span>${s.javaPath ? esc(s.javaPath) : 'Automatic — the right Java is downloaded per version'}</span></div><div class="row"><button class="btn sm" data-java>${icon('folder')} Choose</button>${s.javaPath ? `<button class="btn sm" data-java-auto>Auto</button>` : ''}</div></div>
        <div class="setting" style="display:block"><div class="l" style="margin-bottom:8px"><b>Global JVM arguments</b></div><input class="input" data-jvm value="${esc(s.jvmArgs || '')}" placeholder="-Dfoo=bar"></div>
      </section>

      <section class="card section" id="sec-ingame"><h2>Orbit mod (in-game)</h2>
        <p>${state.info.novaVersions?.length ? `Auto-installed into every Fabric or Quilt profile on <b>${esc(state.info.novaVersions.join(', '))}</b>.` : `<span style="color:var(--warn)">No Orbit mod jars were found in <code>launcher/resources</code>. Build them from <code>client-mod</code> (see README).</span>`}</p>
        <div class="setting"><div class="l"><b>Right Shift</b><span>Open the Orbit menu — toggle mods, change colors, scale, backgrounds, and drag HUD elements</span></div><span class="tag accent">RSHIFT</span></div>
        <div class="setting"><div class="l"><b>Zoom</b><span>Hold to zoom, scroll to adjust</span></div><span class="tag accent">C</span></div>
        <div class="setting"><div class="l"><b>Orbit mods</b><span>Render, Combat, HUD and Player tabs: freelook, motion blur, crosshair painter, no fog, time changer, own nametag, scoreboard, saturation, clips, pets & cosmetics, emotes (B) and more</span></div><span class="tag">65 mods</span></div>
        <div class="setting"><div class="l"><b>Sky changer</b><span>Aurora, Nebula, Synthwave, Blood Moon, Deep Space, Cotton Candy, Golden Hour & Void</span></div><span class="tag">8 skies</span></div>
        <div class="setting"><div class="l"><b>Space main menu</b><span>Orbit replaces Minecraft's title screen with a starfield menu (switch back to vanilla any time)</span></div><span class="tag accent">NEW</span></div>
        <div class="setting"><div class="l"><b>Spotify lyrics</b><span>Synced lyrics of what you're playing, shown in the sky above your crosshair</span></div><span class="tag accent">NEW</span></div>
      </section>

      <section class="card section" id="sec-spotify"><h2>${icon('music').replace('<svg', '<svg style="width:16px;vertical-align:-2px"')} Spotify lyrics</h2>
        <p>Connect Spotify so the Orbit mod can show synced lyrics of the song you're playing. Lyrics come from the free LRCLIB database.</p>
        <div data-spotify></div>
      </section>

      <section class="card section" id="sec-integrations"><h2>Integrations</h2><p>Modrinth works out of the box. CurseForge requires a free API key from <a href="#" data-cf style="color:var(--accent)">console.curseforge.com</a>.</p>
        <label class="lbl">CurseForge API key</label><div class="row"><input class="input grow" type="password" data-cfkey value="${esc(s.curseforgeKey || '')}" placeholder="$2a$10$…"><button class="btn primary" data-cfsave>Save</button></div>
        <div class="sep"></div>
        <div class="setting"><div class="l"><b>Discord status</b><span>Shows "Playing Orbit Client" on your Discord profile, with the version and profile you're playing</span></div>${toggle('discordEnabled', s.discordEnabled !== false)}</div>
        <ol class="muted" style="margin:0 0 12px;padding-left:18px;font-size:13px;line-height:1.8">
          <li>Open <a href="#" data-ddev style="color:var(--accent)">discord.com/developers/applications</a> → <b>New Application</b> and name it <b>Orbit Client</b> (that name is what Discord shows).</li>
          <li>Optional: under Rich Presence → Art Assets upload a logo named <code>orbit</code>.</li>
          <li>Paste the <b>Application ID</b> below. Discord desktop must be running.</li></ol>
        <label class="lbl">Discord Application ID</label><div class="row"><input class="input grow" data-did value="${esc(s.discordAppId || '')}" placeholder="e.g. 1234567890123456789"><button class="btn primary" data-dsave>Save</button></div>
        <div class="sep"></div>
        <label class="lbl">Orbit sync server (emotes for everyone)</label>
        <p class="muted" style="margin:-2px 0 10px;font-size:12.5px">Address of the server in the <code>orbit-server</code> folder once you host it, e.g. <code>wss://sync.yourdomain.com</code>. Empty = emotes only show for you.</p>
        <div class="row"><input class="input grow" data-sync value="${esc(s.syncServer || '')}" placeholder="wss://…"><button class="btn primary" data-syncsave>Save</button></div>
        <div class="sep"></div>
        <label class="lbl">Cosmetics API (others see your hats, wings, auras & cape)</label>
        <p class="muted" style="margin:-2px 0 10px;font-size:12.5px">Leave empty to use the sync server above — the same <code>orbit-server</code> hosts the API. Only needed if you run the API somewhere else, e.g. <code>https://api.yourdomain.com</code>.</p>
        <div class="row"><input class="input grow" data-api value="${esc(s.apiServer || '')}" placeholder="https://… (optional)"><button class="btn primary" data-apisave>Save</button></div>
      </section>

      <section class="card section" id="sec-about"><h2>About</h2><p>Orbit v${esc(state.info.version)} — data stored in <code>${esc(state.info.dataDir)}</code></p>
        <div class="row"><button class="btn" data-data>${icon('folder')} Open data folder</button></div>
        <div class="sep"></div>
        <div class="setting"><div class="l"><b>Automatic updates</b><span>New Orbit versions download in the background and install by themselves when you close the launcher</span></div>${toggle('autoUpdate', s.autoUpdate !== false)}</div>
        <label class="lbl">Update repository (GitHub)</label>
        <div class="row"><input class="input grow" data-repo value="${esc(s.updateRepo || '')}" placeholder="owner/repo, e.g. yourfriend/orbit"><button class="btn" data-reposave>Save</button><button class="btn primary" data-upcheck>${icon('download')} Check for updates</button></div>
        <div data-upstate class="muted" style="margin-top:10px;font-size:13px"></div>
      </section>
    </div></div></div>`);

  $$('[data-th]', el).forEach((t) => (t.onclick = async () => { await save({ theme: t.dataset.th }); applyLook(); $$('[data-th]', el).forEach((x) => x.classList.toggle('sel', x === t)); }));
  $$('[data-seg]', el).forEach((sg) => $$('button', sg).forEach((b) => (b.onclick = async () => { await save({ [sg.dataset.seg]: b.dataset.v }); applyLook(); $$('button', sg).forEach((x) => x.classList.toggle('active', x === b)); })));
  $$('[data-tog]', el).forEach((t) => (t.onclick = async () => { const on = !t.classList.contains('on'); t.classList.toggle('on', on); await save({ [t.dataset.tog]: on }); applyLook(); }));
  const r = $('[data-mem]', el); setRange(r);
  r.oninput = () => { setRange(r); $('[data-memv]', el).textContent = (r.value / 1024).toFixed(1) + ' GB'; };
  r.onchange = () => save({ memory: +r.value });
  const saveRes = () => save({ resolution: { width: +$('[data-w]', el).value || null, height: +$('[data-h]', el).value || null } });
  $('[data-w]', el).onchange = saveRes; $('[data-h]', el).onchange = saveRes;
  $('[data-jvm]', el).onchange = (e) => save({ jvmArgs: e.target.value.trim() });
  $('[data-java]', el).onclick = async () => { const p = await nova.settings.pickJava(); if (p) { await save({ javaPath: p }); toast('Java set', p); rerender(); } };
  $('[data-java-auto]', el)?.addEventListener('click', async () => { await save({ javaPath: '' }); rerender(); });
  $('[data-cf]', el).onclick = (e) => { e.preventDefault(); nova.app.openExternal('https://console.curseforge.com/'); };
  $('[data-cfsave]', el).onclick = async () => { await save({ curseforgeKey: $('[data-cfkey]', el).value.trim() }); toast('CurseForge key saved'); };
  $('[data-data]', el).onclick = () => nova.app.openPath();
  $('[data-ddev]', el).onclick = (e) => { e.preventDefault(); nova.app.openExternal('https://discord.com/developers/applications'); };
  $('[data-dsave]', el).onclick = async () => {
    const id = $('[data-did]', el).value.trim();
    if (id && !/^\d{15,22}$/.test(id)) return toast('That isn\'t an Application ID', 'It should be a long number.', 'err');
    await save({ discordAppId: id }); toast('Discord status saved', id ? 'Shows while Discord is open.' : 'Turned off.');
  };
  $('[data-syncsave]', el).onclick = async () => {
    const u = $('[data-sync]', el).value.trim();
    if (u && !/^wss?:\/\//.test(u)) return toast('Use a ws:// or wss:// address', '', 'err');
    await save({ syncServer: u }); toast('Sync server saved', 'Applies the next time you launch.');
  };
  $('[data-apisave]', el).onclick = async () => {
    const u = $('[data-api]', el).value.trim().replace(/\/+$/, '');
    if (u && !/^https?:\/\//.test(u)) return toast('Use an http:// or https:// address', '', 'err');
    await save({ apiServer: u }); toast('Cosmetics API saved', 'Applies the next time you launch.');
  };

  // updates
  const up = $('[data-upstate]', el);
  const saveRepo = async () => {
    const r = $('[data-repo]', el).value.trim().replace(/^https?:\/\/github\.com\//i, '').replace(/\.git$|\/+$/g, '');
    if (r && !/^[\w.-]+\/[\w.-]+$/.test(r)) { toast('Use owner/repo', 'For example yourfriend/orbit', 'err'); return false; }
    $('[data-repo]', el).value = r;
    await save({ updateRepo: r }); return true;
  };
  $('[data-reposave]', el).onclick = async () => { if (await saveRepo()) toast('Update repository saved'); };
  $('[data-upcheck]', el).onclick = async (e) => {
    const b = e.currentTarget;
    if (!(await saveRepo())) return;
    b.disabled = true; up.textContent = 'Checking GitHub…';
    try { showUpdate(up, await nova.update.check()); }
    catch (err) { up.innerHTML = `<span style="color:var(--warn)">${esc(err.message)}</span>`; }
    b.disabled = false;
  };

  // Spotify connection
  const sp = $('[data-spotify]', el);
  const paintSpotify = async () => {
    const st = await nova.spotify.status().catch(() => ({}));
    if (st.connected) {
      sp.innerHTML = `<div class="setting"><div class="l"><b style="color:var(--ok)">● Connected${st.name ? ' as ' + esc(st.name) : ''}</b><span>Turn on <b>Spotify</b> and <b>Spotify Lyrics</b> in the Orbit menu (Right Shift). Open chat (T) in-game to pause or skip songs on the card.</span></div><button class="btn sm danger" data-spdis>Disconnect</button></div>
        ${st.controls ? '' : `<div class="setting"><div class="l"><b>Enable pause & skip</b><span>Your connection is from an older Orbit version without playback controls. Reconnect once to allow them (controls need Spotify Premium).</span></div><button class="btn sm primary" data-sprecon>${icon('music')} Reconnect</button></div>`}`;
      $('[data-spdis]', sp).onclick = async () => { await nova.spotify.disconnect(); paintSpotify(); };
      const rc = $('[data-sprecon]', sp);
      if (rc) rc.onclick = async () => {
        rc.disabled = true; rc.textContent = 'Waiting for browser…';
        try { await nova.spotify.connect(st.clientId); toast('Spotify reconnected', 'Pause & skip now work in-game.'); }
        catch (err) { toast('Spotify reconnect failed', err.message, 'err', 8000); }
        paintSpotify();
      };
      return;
    }
    sp.innerHTML = `<ol class="muted" style="margin:0 0 14px;padding-left:18px;font-size:13px;line-height:1.8">
        <li>Open <a href="#" data-spdev style="color:var(--accent)">developer.spotify.com/dashboard</a> and create an app (any name, choose <b>Web API</b>).</li>
        <li>Add this Redirect URI: <code style="user-select:text;color:var(--text)">${esc(st.redirectUri || 'http://127.0.0.1:43821/callback')}</code></li>
        <li>Copy the app's <b>Client ID</b> below and press Connect.</li></ol>
      <label class="lbl">Spotify Client ID</label>
      <div class="row"><input class="input grow" data-spid value="${esc(st.clientId || '')}" placeholder="32-character client ID"><button class="btn primary" data-spcon>${icon('music')} Connect</button></div>`;
    $('[data-spdev]', sp).onclick = (e) => { e.preventDefault(); nova.app.openExternal('https://developer.spotify.com/dashboard'); };
    $('[data-spcon]', sp).onclick = async (e) => {
      const b = e.currentTarget; b.disabled = true; b.textContent = 'Waiting for browser…';
      try { await nova.spotify.connect($('[data-spid]', sp).value.trim()); toast('Spotify connected', 'Lyrics will show in-game.'); }
      catch (err) { toast('Spotify connection failed', err.message, 'err', 8000); }
      paintSpotify();
    };
  };
  paintSpotify();
  $$('[data-sec]', el).forEach((b) => (b.onclick = () => { $$('[data-sec]', el).forEach((x) => x.classList.toggle('active', x === b)); $('#sec-' + b.dataset.sec, el).scrollIntoView({ behavior: 'smooth', block: 'start' }); }));
  $(`[data-sec="${params.section || 'appearance'}"]`, el).classList.add('active');
  if (params.section) setTimeout(() => $('#sec-' + params.section, el)?.scrollIntoView({ behavior: 'smooth' }), 300);
  function rerender() { const fresh = render(params); fresh.className = el.className; el.replaceWith(fresh); }
  return el;
}
