// Orbit website: starfield, scroll reveals, screenshot tabs and the live version number.

// twinkling starfield (like the launcher)
(() => {
  const c = document.getElementById('stars');
  const x = c.getContext('2d');
  let stars = [];
  const resize = () => {
    c.width = innerWidth * devicePixelRatio; c.height = innerHeight * devicePixelRatio;
    stars = Array.from({ length: Math.round(innerWidth * innerHeight / 9000) }, () => ({
      x: Math.random() * c.width, y: Math.random() * c.height, r: (Math.random() * 1.1 + .3) * devicePixelRatio,
      p: Math.random() * Math.PI * 2, s: .4 + Math.random() * 1.2,
    }));
  };
  const still = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const draw = (t) => {
    x.clearRect(0, 0, c.width, c.height);
    for (const s of stars) {
      x.globalAlpha = .25 + .55 * (still ? .6 : (Math.sin(t / 1000 * s.s + s.p) + 1) / 2);
      x.fillStyle = '#d8fff1';
      x.beginPath(); x.arc(s.x, s.y, s.r, 0, Math.PI * 2); x.fill();
    }
    if (!still) requestAnimationFrame(draw);
  };
  addEventListener('resize', resize);
  resize(); requestAnimationFrame(draw);
})();

// nav border once scrolled
const nav = document.querySelector('.nav');
addEventListener('scroll', () => nav.classList.toggle('scrolled', scrollY > 8), { passive: true });

// fade sections in as they scroll into view
const io = new IntersectionObserver((entries) => entries.forEach((e) => { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } }), { threshold: .12 });
document.querySelectorAll('.reveal').forEach((el) => io.observe(el));

document.getElementById('year').textContent = new Date().getFullYear();
// highlight the current page in the menu
document.querySelectorAll('.links a').forEach((a) => { if (a.getAttribute('href') === location.pathname.split('/').pop()) a.classList.add('here'); });
const ORBIT_SERVER = 'https://orbit-server-nyn5.onrender.com';

// Download buttons: right after a new release is published, GitHub is still building its files for a few minutes,
// so "latest/download" would 404. Point every Download button at the newest release that already has the installer.
(async () => {
  const links = [...document.querySelectorAll('a[href*="/releases/latest/download/"]')];
  if (!links.length) return;
  try {
    const r = await fetch('https://api.github.com/repos/xTrustQ/Orbit-Client/releases?per_page=6');
    if (!r.ok) return;
    const rel = (await r.json()).find((x) => !x.draft && !x.prerelease && x.assets.some((a) => a.name === 'Orbit-Setup.exe'));
    if (!rel) return;
    for (const a of links) {
      const file = a.href.split('/').pop();
      const asset = rel.assets.find((x) => x.name === file);
      if (asset) a.href = asset.browser_download_url;
    }
  } catch { /* keep the plain link */ }
})();
const fmt = (n) => new Intl.NumberFormat('en').format(n);
const countUp = (el, to) => {
  const start = performance.now(), from = 0, dur = 900;
  const step = (t) => { const k = Math.min(1, (t - start) / dur), e = 1 - Math.pow(1 - k, 3); el.textContent = fmt(Math.round(from + (to - from) * e)); if (k < 1) requestAnimationFrame(step); };
  requestAnimationFrame(step);
};

// ------------------------------------------------------------------ home page only
if (document.getElementById('shot')) {
// screenshot tabs
const captions = {
  menu: 'The main menu, in the same clean style as the launcher.',
  pause: 'A slim pause menu — the game stays visible next to it.',
  mods: 'The Orbit menu (Right Shift): toggle mods, edit the HUD, save configs.',
  friends: 'Friends and chat, right in the main menu.',
  locker: 'The Locker: skins, capes, cosmetics and emotes on a 3D preview.',
  sky: 'Sky Changer, the HUD and the Spotify widget in-game.',
  'launcher-mods': 'Browse Modrinth and CurseForge — installed mods are marked.',
};
const shot = document.getElementById('shot');
const caption = document.getElementById('caption');
document.querySelectorAll('.tab').forEach((b) => b.addEventListener('click', () => {
  document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t === b));
  shot.classList.add('fade');
  setTimeout(() => { shot.src = `img/${b.dataset.shot}.png?v=2`; caption.textContent = captions[b.dataset.shot]; }, 180);
}));
shot.addEventListener('load', () => shot.classList.remove('fade'));

// newest version from GitHub
fetch('https://api.github.com/repos/xTrustQ/Orbit-Client/releases/latest')
  .then((r) => (r.ok ? r.json() : null))
  .then((rel) => { if (rel?.tag_name) document.getElementById('ver').textContent = 'Version ' + rel.tag_name.replace(/^v/i, ''); })
  .catch(() => {});

// live statistics: downloads from GitHub (all versions), players from the Orbit server
fetch('https://api.github.com/repos/xTrustQ/Orbit-Client/releases?per_page=100')
  .then((r) => (r.ok ? r.json() : []))
  .then((rels) => {
    const total = rels.reduce((s, rel) => s + (rel.assets || []).filter((a) => /\.exe$/i.test(a.name)).reduce((x, a) => x + a.download_count, 0), 0);
    countUp(document.getElementById('st-downloads'), total);
  })
  .catch(() => {});
const loadOnline = () => fetch(ORBIT_SERVER + '/api/stats')
  .then((r) => (r.ok ? r.json() : null))
  .then((s) => {
    if (!s) return;
    const on = document.getElementById('st-online'), ig = document.getElementById('st-ingame');
    if (on.textContent === '—') countUp(on, s.online || 0); else on.textContent = fmt(s.online || 0);
    if (ig.textContent === '—') countUp(ig, s.inGame || 0); else ig.textContent = fmt(s.inGame || 0);
  })
  .catch(() => {});
loadOnline(); // the free server may need a moment to wake up — the numbers appear when it answers
setInterval(loadOnline, 60_000);
}
