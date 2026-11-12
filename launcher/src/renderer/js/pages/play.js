import { icon } from '../icons.js';
import { $, $$, h, esc, state, on, timeAgo, fmtHours } from '../ui.js';
import { go, launchInstance, selectedAccount, openConsole } from '../app.js';
import { iconUrl } from '../art.js';

import { sfx } from '../sfx.js';

let viewer = null, offs = [], ro = null, director = null;

// ---------------------------------------------------------------- skin actions
// The 3D player cycles through moves on its own; hover waves, click plays a random one.
const SV = () => window.skinview3d;
function resetPose(p) {
  for (const part of ['head', 'body', 'leftArm', 'rightArm', 'leftLeg', 'rightLeg']) { const o = p.skin[part]; if (o) o.rotation.set(0, 0, 0); }
  p.position.y = 0; p.rotation.set(0, 0, 0);
}
const fn = (f) => new (SV().FunctionAnimation)(f);
const ACTIONS = {
  idle: { label: 'Chilling', make: () => new (SV().IdleAnimation)(), time: 5000 },
  walk: { label: 'Strolling', make: () => new (SV().WalkingAnimation)(), time: 4500 },
  run: { label: 'Sprinting', make: () => new (SV().RunningAnimation)(), time: 3500 },
  wave: { label: 'Waving', make: () => new (SV().WaveAnimation)(), time: 3000 },
  crouch: { label: 'Sneaking', make: () => { const a = new (SV().CrouchAnimation)(); a.speed = 0.7; return a; }, time: 3200 },
  punch: { label: 'Punching', make: () => { const a = new (SV().HitAnimation)(); a.speed = 1.4; return a; }, time: 2600 },
  fly: { label: 'Flying', make: () => new (SV().FlyingAnimation)(), time: 3500 },
  dance: { label: 'Dancing', time: 4500, make: () => fn((p, t) => {
    const s = Math.sin(t * 7);
    p.skin.leftArm.rotation.z = 0.4 + 0.9 * Math.max(0, s); p.skin.rightArm.rotation.z = -0.4 - 0.9 * Math.max(0, -s);
    p.skin.leftArm.rotation.x = s * 0.6; p.skin.rightArm.rotation.x = -s * 0.6;
    p.skin.leftLeg.rotation.x = -s * 0.35; p.skin.rightLeg.rotation.x = s * 0.35;
    p.skin.body.rotation.z = s * 0.08; p.skin.head.rotation.z = -s * 0.15; p.position.y = Math.abs(Math.sin(t * 7)) * 0.6;
  }) },
  jump: { label: 'Jumping', time: 3200, make: () => fn((p, t) => {
    const k = Math.abs(Math.sin(t * 4.2));
    p.position.y = k * 4; p.skin.leftArm.rotation.z = 0.3 + k * 2.3; p.skin.rightArm.rotation.z = -0.3 - k * 2.3;
    p.skin.leftLeg.rotation.x = -k * 0.3; p.skin.rightLeg.rotation.x = -k * 0.3;
  }) },
  dab: { label: 'Dabbing', time: 2600, make: () => fn((p, t) => {
    const k = Math.min(1, t * 4);
    p.skin.rightArm.rotation.z = -2.3 * k; p.skin.rightArm.rotation.x = -0.4 * k;
    p.skin.leftArm.rotation.x = -1.4 * k; p.skin.leftArm.rotation.z = 0.9 * k; p.skin.leftArm.rotation.y = -0.6 * k;
    p.skin.head.rotation.x = 0.55 * k; p.skin.head.rotation.y = 0.6 * k;
  }) },
  cheer: { label: 'Cheering', time: 3000, make: () => fn((p, t) => {
    const s = Math.sin(t * 10);
    p.skin.leftArm.rotation.z = 2.7 + s * 0.25; p.skin.rightArm.rotation.z = -2.7 + s * 0.25; p.position.y = Math.max(0, s) * 0.8;
  }) },
  look: { label: 'Looking around', time: 4000, make: () => fn((p, t) => {
    p.skin.head.rotation.y = Math.sin(t * 1.6) * 0.8; p.skin.head.rotation.x = Math.sin(t * 0.8) * 0.2;
    p.skin.leftArm.rotation.z = 0.05; p.skin.rightArm.rotation.z = -0.05;
  }) },
  spin: { label: 'Spinning', time: 2200, make: () => fn((p, t) => {
    p.rotation.y = Math.min(1, t / 1.6) * Math.PI * 2; p.skin.leftArm.rotation.z = 1.4; p.skin.rightArm.rotation.z = -1.4;
  }) },
};
const CYCLE = ['idle', 'walk', 'wave', 'dance', 'look', 'run', 'jump', 'crouch', 'cheer', 'punch', 'dab', 'spin', 'fly'];
// one move per launcher start (kept while the launcher stays open); click the skin for another
let sessionAction = CYCLE[Math.floor(Math.random() * CYCLE.length)];
function skinDirector(v) {
  const play = (name) => {
    const a = ACTIONS[name] || ACTIONS.idle;
    if (!v.playerObject) return;
    resetPose(v.playerObject);
    v.animation = a.make(); // loops until something else is picked
  };
  const random = () => { const pool = CYCLE.filter((n) => n !== sessionAction); sessionAction = pool[Math.floor(Math.random() * pool.length)]; play(sessionAction); };
  play(sessionAction);
  return { play, random, stop: () => {} };
}

const LOG = [
  { t: 'Warp drive', s: 'FPS pack auto-installs Sodium, Lithium & more', orb: 'radial-gradient(circle at 30% 30%,#fff,#4cc9ff 40%,#1d4ed8)', go: 'profiles' },
  { t: 'Comms online', s: 'Voice Chat, Shield Fixes & tier tags come standard', orb: 'radial-gradient(circle at 30% 30%,#fff,#34f5a5 40%,#047857)', go: 'profiles' },
  { t: 'In-game Locker', s: 'Switch accounts, skins & capes from the title screen', orb: 'radial-gradient(circle at 30% 30%,#fff,#ff6fd8 40%,#7e22ce)', go: 'skins' },
  { t: 'Sky lyrics', s: 'Spotify lyrics pinned in the sky, karaoke style', orb: 'radial-gradient(circle at 30% 30%,#fff,#ffb347 40%,#b91c1c)', go: 'settings' },
];

function greeting() {
  const hr = new Date().getHours();
  return hr < 5 ? 'Night shift' : hr < 12 ? 'Good morning' : hr < 18 ? 'Good afternoon' : 'Good evening';
}

export function render() {
  const el = h(`<div class="pad">
    <div class="pad-left stagger">
      <div class="eyebrow">${greeting()}, commander</div>
      <h1 class="hero-title">Ready for <span>liftoff</span>?</h1>
      <p class="hero-sub">Pick a profile, hit launch, and Orbit takes care of Java, mods and performance.</p>
      <div class="card mission">
        <div class="mission-head"><label class="lbl" style="margin:0">Mission profile</label><button class="icon-btn ghost" style="width:30px;height:30px" data-console title="Console">${icon('terminal')}</button></div>
        <div style="position:relative">
          <div class="mission-select" data-pick><div class="inst-icon ico" data-ico></div><div class="grow"><div class="ellipsis" style="font-weight:700" data-iname>—</div><div class="muted ellipsis" style="font-size:12px" data-imeta></div></div>${icon('chevron', 'chev')}</div>
        </div>
      </div>
      <button class="liftoff" data-launch>${icon('rocket')}<span data-label>LAUNCH</span><i class="bar" style="width:0"></i></button>
      <div class="liftoff-sub" data-status></div>
    </div>
    <div class="pad-center">
      <div class="callsign"><span class="dot">${icon('planet')}</span><b data-name></b></div>
      <canvas class="skin-canvas"></canvas>
      <div class="orbit-rings"><i></i><i></i><i></i><span class="sat"></span></div>
    </div>
    <div class="pad-right stagger">
      <div class="stats">
        <div class="card stat"><b data-count>0</b><span>Profiles</span></div>
        <div class="card stat"><b data-time>0m</b><span>Flight time</span></div>
      </div>
      <div class="log-title">Mission log</div>
      ${LOG.map((f, i) => `<div class="card hover log" data-log="${i}"><div class="orb" style="background:${f.orb}"></div><div><b>${f.t}</b><span>${f.s}</span></div></div>`).join('')}
    </div>
  </div>`);

  // 3D skin
  const canvas = $('.skin-canvas', el);
  const stage = $('.pad-center', el);
  viewer = new skinview3d.SkinViewer({ canvas, width: 300, height: 420 });
  viewer.background = null;
  viewer.fov = 38; viewer.zoom = 0.8;
  viewer.controls.enableZoom = false; viewer.controls.enablePan = false;
  viewer.autoRotate = true; viewer.autoRotateSpeed = 0.55;
  const fit = () => { const hgt = Math.max(300, Math.min(540, stage.clientHeight - 120)); viewer.setSize(hgt * 0.72, hgt); };
  ro = new ResizeObserver(fit); ro.observe(stage);
  director = skinDirector(viewer);
  canvas.addEventListener('click', () => { director?.random(); sfx.pop(); });

  const loadSkin = async () => {
    const acc = selectedAccount();
    $('[data-name]', el).textContent = acc ? acc.name : 'Guest pilot';
    const [skin, capes] = await Promise.all([nova.skins.current().catch(() => null), nova.capes.nova().catch(() => [])]);
    if (!viewer) return;
    if (skin) viewer.loadSkin(skin.data, { model: skin.model === 'slim' ? 'slim' : 'default' });
    const cape = capes.find((c) => c.selected);
    viewer.loadCape(cape ? cape.data : null);
  };
  loadSkin();

  // profile + launch
  const pick = () => state.instances.find((i) => i.id === state.settings.lastInstance) || state.instances[0];
  const btn = $('[data-launch]', el);
  const paint = () => {
    const inst = pick();
    $('[data-iname]', el).textContent = inst ? inst.name : 'No profile yet';
    $('[data-imeta]', el).textContent = inst ? `${inst.mcVersion} · ${inst.loader} · ${timeAgo(inst.lastPlayed)}` : 'Create one to start';
    $('[data-ico]', el).innerHTML = inst && iconUrl(inst.icon) ? `<img src="${esc(iconUrl(inst.icon))}">` : icon('cube');
    $('[data-count]', el).textContent = state.instances.length;
    $('[data-time]', el).textContent = fmtHours(state.instances.reduce((s, i) => s + (i.playtime || 0), 0));
    const running = inst && state.running.has(inst.id);
    $('[data-label]', el).textContent = !inst ? 'CREATE PROFILE' : running ? 'IN ORBIT' : 'LAUNCH';
    $('[data-status]', el).textContent = running ? 'Game is running — click to open the console' : '';
  };
  paint();
  btn.onclick = () => {
    const inst = pick();
    if (!inst) return go('profiles', { create: true });
    if (state.running.has(inst.id)) return openConsole(inst.id);
    launchInstance(inst.id);
  };
  $('[data-console]', el).onclick = () => openConsole();
  $('[data-pick]', el).onclick = (e) => {
    e.stopPropagation();
    const wrap = $('[data-pick]', el).parentElement;
    if ($('.picker', wrap)) return $('.picker', wrap).remove();
    const cur = pick();
    const p = h(`<div class="picker">${state.instances.map((i) => `<div class="picker-row ${cur?.id === i.id ? 'sel' : ''}" data-id="${i.id}">
      <div class="inst-icon ico">${iconUrl(i.icon) ? `<img src="${esc(iconUrl(i.icon))}">` : icon('cube')}</div>
      <div class="grow"><div class="ellipsis" style="font-weight:600">${esc(i.name)}</div><div class="muted" style="font-size:12px">${esc(i.mcVersion)} · ${esc(i.loader)}</div></div></div>`).join('') || '<div class="muted" style="padding:10px">No profiles yet</div>'}
      <div class="sep" style="margin:6px 0"></div><div class="picker-row" data-new>${icon('plus').replace('<svg', '<svg style="width:16px"')} <span>New profile</span></div></div>`);
    $$('.picker-row[data-id]', p).forEach((r) => (r.onclick = () => { state.settings.lastInstance = r.dataset.id; nova.settings.set({ lastInstance: r.dataset.id }); p.remove(); paint(); }));
    $('[data-new]', p).onclick = () => go('profiles', { create: true });
    wrap.append(p);
    setTimeout(() => document.addEventListener('click', () => p.remove(), { once: true }));
  };
  $$('[data-log]', el).forEach((c) => (c.onclick = () => go(LOG[c.dataset.log].go)));

  offs = [
    on('instances', paint), on('running', paint), on('accounts', loadSkin),
    on('launch:start', (id) => { if (id === pick()?.id) { btn.classList.add('busy'); $('[data-label]', el).textContent = 'COUNTDOWN'; } }),
    on('launch:progress', (p) => { if (p.instanceId === pick()?.id) { $('.bar', btn).style.width = (p.total ? (p.done / p.total) * 100 : 15) + '%'; $('[data-status]', el).textContent = p.label; } }),
    on('launch:end', () => { btn.classList.remove('busy'); $('.bar', btn).style.width = '0'; paint(); }),
  ];
  return el;
}

export function destroy() {
  offs.forEach((f) => f()); offs = [];
  ro?.disconnect(); ro = null;
  director?.stop(); director = null;
  viewer?.dispose(); viewer = null;
}
