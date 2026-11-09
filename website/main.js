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
  setTimeout(() => { shot.src = `img/${b.dataset.shot}.png`; caption.textContent = captions[b.dataset.shot]; }, 180);
}));
shot.addEventListener('load', () => shot.classList.remove('fade'));

// newest version from GitHub
fetch('https://api.github.com/repos/xTrustQ/Orbit-Client/releases/latest')
  .then((r) => (r.ok ? r.json() : null))
  .then((rel) => { if (rel?.tag_name) document.getElementById('ver').textContent = 'Version ' + rel.tag_name.replace(/^v/i, ''); })
  .catch(() => {});
document.getElementById('year').textContent = new Date().getFullYear();
