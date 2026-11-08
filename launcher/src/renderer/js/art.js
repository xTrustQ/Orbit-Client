// Procedural art: profile icons, Orbit cape designs, 2D skin + cape previews.

function rng(seed) { let s = seed >>> 0 || 1; return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296); }
const loadImg = (src) => new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = src; });

// ---------------------------------------------------------------- profile icons (16x16 pixel blocks)
const BLOCKS = {
  grass: { base: ['#5d8f34', '#6aa23c', '#4f7d2b'], top: true, dirt: ['#7a5636', '#8b6240', '#6b4a2e'] },
  stone: { base: ['#7d7d7d', '#8a8a8a', '#6f6f6f', '#949494'] },
  diamond: { base: ['#7d7d7d', '#8a8a8a', '#6f6f6f'], ore: ['#5ce1e6', '#a5fbff', '#2bb3b9'] },
  emerald: { base: ['#7d7d7d', '#8a8a8a', '#6f6f6f'], ore: ['#2fd36b', '#8dffb5', '#16964a'] },
  gold: { base: ['#f9d849', '#fdf06b', '#e3b324', '#c99a1b'], bevel: true },
  netherrack: { base: ['#6e2b2b', '#843535', '#5a2020', '#9c4141'] },
  obsidian: { base: ['#14101f', '#1c1530', '#0c0a14', '#2d1f4d'] },
  end: { base: ['#dcdf9e', '#e9eba9', '#c8cb87', '#f1f3bd'] },
  amethyst: { base: ['#8656c4', '#a27ae0', '#6c3fa8', '#c39bf5'] },
  ice: { base: ['#8db4f8', '#a6c6ff', '#7aa2ea', '#c4dbff'] },
  sculk: { base: ['#0b1d24', '#0f2a33', '#062027'], ore: ['#29dfeb', '#0b6e75', '#1aa9b3'] },
  tnt: { base: ['#c43a2a', '#d84a37', '#a82e21'], band: true },
};
export const BLOCK_NAMES = Object.keys(BLOCKS);
const iconCache = {};
export function blockIcon(name) {
  if (iconCache[name]) return iconCache[name];
  const b = BLOCKS[name] || BLOCKS.stone;
  const c = document.createElement('canvas'); c.width = c.height = 16;
  const x = c.getContext('2d'); const r = rng(name.length * 977 + name.charCodeAt(0));
  const pick = (arr) => arr[Math.floor(r() * arr.length)];
  for (let i = 0; i < 16; i++) for (let j = 0; j < 16; j++) {
    let col = pick(b.base);
    if (b.top && j > 3 + (r() < 0.5 ? 1 : 0)) col = pick(b.dirt);
    if (b.bevel && (i === 0 || j === 0)) col = '#fff6a8';
    if (b.bevel && (i === 15 || j === 15)) col = '#b07f12';
    if (b.band && j >= 6 && j <= 9) col = j === 6 || j === 9 ? '#e8e8e8' : pick(['#f4f4f4', '#dcdcdc']);
    x.fillStyle = col; x.fillRect(i, j, 1, 1);
  }
  if (b.ore) for (let k = 0; k < 6; k++) { const ox = 2 + Math.floor(r() * 11), oy = 2 + Math.floor(r() * 11); x.fillStyle = b.ore[0]; x.fillRect(ox, oy, 2, 2); x.fillStyle = b.ore[1]; x.fillRect(ox, oy, 1, 1); x.fillStyle = b.ore[2]; x.fillRect(ox + 1, oy + 1, 1, 1); }
  return (iconCache[name] = c.toDataURL());
}
export const iconUrl = (icon) => (!icon ? null : icon.startsWith('blk:') ? blockIcon(icon.slice(4)) : icon);

// ---------------------------------------------------------------- Orbit capes (HD 512x256 textures)
const S = 8; // 64x32 cape template scaled 8x
const FRONT = { x: 1 * S, y: 1 * S, w: 10 * S, h: 16 * S };

function stars(x, w, h, n, r, color = '#fff') { for (let i = 0; i < n; i++) { x.globalAlpha = 0.3 + r() * 0.7; x.fillStyle = color; const s = r() < 0.1 ? 2 : 1; x.fillRect(Math.floor(r() * w), Math.floor(r() * h), s, s); } x.globalAlpha = 1; }
function grad(x, w, h, stops, vertical = true) { const g = vertical ? x.createLinearGradient(0, 0, 0, h) : x.createLinearGradient(0, 0, w, 0); stops.forEach((c, i) => g.addColorStop(i / (stops.length - 1), c)); x.fillStyle = g; x.fillRect(0, 0, w, h); }

export const CAPE_PRESETS = {
  aurora: { name: 'Aurora', draw(x, w, h, r) { grad(x, w, h, ['#020617', '#06233a', '#0a3d3a']); stars(x, w, h * 0.6, 50, r); for (let b = 0; b < 3; b++) { x.beginPath(); for (let i = 0; i <= w; i++) x.lineTo(i, h * (0.3 + b * 0.12) + Math.sin(i / 9 + b * 2) * 8); x.strokeStyle = ['#34f5a5', '#22d3ee', '#a78bfa'][b]; x.lineWidth = 10 - b * 2; x.shadowColor = x.strokeStyle; x.shadowBlur = 18; x.globalAlpha = 0.55; x.stroke(); } x.globalAlpha = 1; x.shadowBlur = 0; } },
  nebula: { name: 'Nebula', draw(x, w, h, r) { grad(x, w, h, ['#0b0221', '#1c0740', '#09021a']); for (let i = 0; i < 9; i++) { const g = x.createRadialGradient(r() * w, r() * h, 0, r() * w, r() * h, 40 + r() * 40); const c = ['#ff3ea5', '#7c3aed', '#22d3ee'][i % 3]; g.addColorStop(0, c + '88'); g.addColorStop(1, c + '00'); x.fillStyle = g; x.fillRect(0, 0, w, h); } stars(x, w, h, 90, r); } },
  sunset: { name: 'Synthwave', draw(x, w, h) { grad(x, w, h, ['#1a0533', '#7b1fa2', '#ff4f81', '#ffb347']); x.fillStyle = '#ffd36e'; x.beginPath(); x.arc(w / 2, h * 0.55, w * 0.32, Math.PI, 0); x.fill(); x.fillStyle = '#1a0533'; for (let i = 0; i < 6; i++) x.fillRect(0, h * 0.42 + i * i * 2.2 + i * 3, w, 2 + i * 0.6); x.fillStyle = '#12002a'; x.fillRect(0, h * 0.62, w, h); x.strokeStyle = '#ff4fd8'; x.lineWidth = 1; for (let i = 0; i < 10; i++) { x.beginPath(); x.moveTo(w / 2, h * 0.62); x.lineTo((i / 9) * w * 2 - w / 2, h); x.stroke(); } for (let j = 0; j < 7; j++) { const y = h * 0.62 + (j * j) * 1.4; x.beginPath(); x.moveTo(0, y); x.lineTo(w, y); x.stroke(); } } },
  matrix: { name: 'Matrix', draw(x, w, h, r) { x.fillStyle = '#010a03'; x.fillRect(0, 0, w, h); x.font = 'bold 8px monospace'; for (let c = 0; c < w / 6; c++) { const len = 6 + r() * 14, start = r() * h; for (let k = 0; k < len; k++) { x.fillStyle = k === len - 1 ? '#d6ffe0' : `rgba(34,255,102,${(k / len) * 0.9})`; x.fillText(String.fromCharCode(0x30a0 + Math.floor(r() * 96)), c * 6, (start + k * 8) % h); } } } },
  sakura: { name: 'Sakura', draw(x, w, h, r) { grad(x, w, h, ['#ffd9ea', '#ff9cc9', '#c2457f']); x.strokeStyle = '#3b1626'; x.lineWidth = 4; x.beginPath(); x.moveTo(-5, h * 0.8); x.quadraticCurveTo(w * 0.4, h * 0.6, w * 0.6, h * 0.2); x.stroke(); x.lineWidth = 2; x.beginPath(); x.moveTo(w * 0.35, h * 0.62); x.quadraticCurveTo(w * 0.7, h * 0.55, w + 5, h * 0.45); x.stroke(); for (let i = 0; i < 40; i++) { x.fillStyle = ['#fff0f6', '#ffc2dc', '#ff7eb6'][i % 3]; x.beginPath(); x.ellipse(r() * w, r() * h, 3, 2, r() * 3, 0, 7); x.fill(); } } },
  ember: { name: 'Ember', draw(x, w, h, r) { grad(x, w, h, ['#0d0000', '#3a0500', '#a01b00', '#ff6a00']); for (let i = 0; i < 70; i++) { const y = r() * h; x.globalAlpha = y / h; x.fillStyle = r() < 0.5 ? '#ffd27a' : '#ff7b1c'; x.fillRect(r() * w, y, 2, 2); } x.globalAlpha = 1; } },
  glacier: { name: 'Glacier', draw(x, w, h, r) { grad(x, w, h, ['#e9f8ff', '#9ad7ff', '#2f6fb6']); for (let i = 0; i < 9; i++) { x.fillStyle = `rgba(255,255,255,${0.15 + r() * 0.3})`; x.beginPath(); const cx = r() * w, cy = r() * h, s = 10 + r() * 20; x.moveTo(cx, cy - s); x.lineTo(cx + s * 0.5, cy); x.lineTo(cx, cy + s); x.lineTo(cx - s * 0.5, cy); x.fill(); } } },
  void: { name: 'Void Rift', draw(x, w, h, r) { x.fillStyle = '#05010a'; x.fillRect(0, 0, w, h); stars(x, w, h, 40, r, '#c4b5fd'); x.shadowColor = '#c026d3'; x.shadowBlur = 24; x.strokeStyle = '#f0abfc'; x.lineWidth = 3; x.beginPath(); let px = w / 2; x.moveTo(px, 0); for (let y = 0; y <= h; y += 8) { px += (r() - 0.5) * 14; x.lineTo(px, y); } x.stroke(); x.shadowBlur = 0; } },
  ocean: { name: 'Deep Ocean', draw(x, w, h, r) { grad(x, w, h, ['#5ee7ff', '#0a6fb5', '#04203f', '#010818']); for (let i = 0; i < 6; i++) { x.globalAlpha = 0.2; x.strokeStyle = '#bff6ff'; x.lineWidth = 2; x.beginPath(); for (let k = 0; k <= w; k++) x.lineTo(k, 12 + i * 10 + Math.sin(k / 7 + i) * 3); x.stroke(); } x.globalAlpha = 0.6; for (let i = 0; i < 18; i++) { x.strokeStyle = '#d6fbff'; x.beginPath(); x.arc(r() * w, h * 0.4 + r() * h * 0.6, 1 + r() * 3, 0, 7); x.stroke(); } x.globalAlpha = 1; } },
  gold: { name: 'Gilded', draw(x, w, h) { grad(x, w, h, ['#fff1a8', '#f5c542', '#b8860b', '#6b4a05']); x.globalAlpha = 0.35; x.fillStyle = '#fff'; for (let i = -h; i < w + h; i += 22) { x.beginPath(); x.moveTo(i, 0); x.lineTo(i + 8, 0); x.lineTo(i + 8 - h, h); x.lineTo(i - h, h); x.fill(); } x.globalAlpha = 1; x.strokeStyle = '#4a3304'; x.lineWidth = 3; x.strokeRect(4, 4, w - 8, h - 8); } },
  orbit: { name: 'Orbit', draw(x, w, h, r) { const acc = getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#b18cff'; grad(x, w, h, ['#05030d', '#160a33', '#05030d']); stars(x, w, h, 70, r); const cx = w / 2, cy = h * 0.46, R = w * 0.26; const g = x.createRadialGradient(cx - R * 0.4, cy - R * 0.4, 1, cx, cy, R); g.addColorStop(0, '#ffffff'); g.addColorStop(0.35, acc); g.addColorStop(1, '#14052e'); x.save(); x.shadowColor = acc; x.shadowBlur = 24; x.fillStyle = g; x.beginPath(); x.arc(cx, cy, R, 0, 7); x.fill(); x.restore(); x.strokeStyle = acc; x.lineWidth = 3; x.globalAlpha = 0.9; x.beginPath(); x.ellipse(cx, cy, R * 1.75, R * 0.42, -0.35, 0, Math.PI * 2); x.stroke(); x.globalAlpha = 1; x.fillStyle = '#fff'; x.beginPath(); x.arc(cx + R * 1.5, cy - R * 0.62, 3, 0, 7); x.fill(); } },
  galaxy: { name: 'Galaxy', draw(x, w, h, r) { x.fillStyle = '#03020a'; x.fillRect(0, 0, w, h); stars(x, w, h, 50, r); const cx = w / 2, cy = h / 2; for (let i = 0; i < 1600; i++) { const arm = i % 2, t = r() * 4.2, rad = t * w * 0.11 + r() * 6; const a = t * 1.9 + arm * Math.PI + (r() - 0.5) * 0.5; x.globalAlpha = 0.25 + r() * 0.6; x.fillStyle = ['#c4b5fd', '#f0abfc', '#93c5fd', '#ffffff'][i % 4]; x.fillRect(cx + Math.cos(a) * rad, cy + Math.sin(a) * rad * 1.3, 1.6, 1.6); } x.globalAlpha = 1; const g = x.createRadialGradient(cx, cy, 0, cx, cy, w * 0.18); g.addColorStop(0, '#fffbe8'); g.addColorStop(1, 'rgba(255,240,200,0)'); x.fillStyle = g; x.fillRect(0, 0, w, h); } },
  saturn: { name: 'Saturn', draw(x, w, h, r) { grad(x, w, h, ['#0b0612', '#1b1030', '#07040c']); stars(x, w, h, 50, r); const cx = w / 2, cy = h * 0.55, R = w * 0.3; x.strokeStyle = '#e9d3a6'; x.lineWidth = 6; x.globalAlpha = 0.85; x.beginPath(); x.ellipse(cx, cy, R * 1.7, R * 0.45, -0.25, Math.PI, Math.PI * 2); x.stroke(); x.globalAlpha = 1; const g = x.createLinearGradient(0, cy - R, 0, cy + R); ['#f5deb3', '#d9a066', '#f2c98a', '#b07b45', '#e8c48c'].forEach((c, k, a) => g.addColorStop(k / (a.length - 1), c)); x.fillStyle = g; x.beginPath(); x.arc(cx, cy, R, 0, 7); x.fill(); x.fillStyle = 'rgba(0,0,0,.35)'; x.beginPath(); x.arc(cx + R * 0.35, cy + R * 0.2, R, 0, 7); x.arc(cx, cy, R, 0, 7, true); x.fill(); x.strokeStyle = '#e9d3a6'; x.globalAlpha = 0.85; x.beginPath(); x.ellipse(cx, cy, R * 1.7, R * 0.45, -0.25, 0, Math.PI); x.stroke(); x.globalAlpha = 1; } },
  moon: { name: 'Lunar', draw(x, w, h, r) { grad(x, w, h, ['#05070f', '#111a33', '#273a66']); stars(x, w, h, 60, r); const g = x.createRadialGradient(w / 2, h * 0.35, 0, w / 2, h * 0.35, w * 0.45); g.addColorStop(0, '#ffffff'); g.addColorStop(0.45, '#e7ecff'); g.addColorStop(0.5, '#ffffff22'); g.addColorStop(1, '#ffffff00'); x.fillStyle = g; x.fillRect(0, 0, w, h); x.fillStyle = '#0b1020'; for (let i = 0; i < 5; i++) { x.beginPath(); x.moveTo(0, h); x.lineTo(r() * w, h * (0.75 + r() * 0.1)); x.lineTo(w, h); x.fill(); } } },
};

function glowDot(x, cx, cy, r, color) { const g = x.createRadialGradient(cx, cy, 0, cx, cy, r); g.addColorStop(0, color); g.addColorStop(1, color.slice(0, 7) + '00'); x.fillStyle = g; x.fillRect(cx - r, cy - r, r * 2, r * 2); }

/** Store-only cape designs (unlocked with Orbit Coins). Keys match the store's `preset`. */
export const PREMIUM_CAPES = {
  supernova: { name: 'Supernova', draw(x, w, h, r) {
    grad(x, w, h, ['#0a0418', '#1a0636', '#05020c']); stars(x, w, h, 70, r);
    const cx = w / 2, cy = h * 0.42;
    for (let i = 0; i < 48; i++) { const a = r() * Math.PI * 2, len = w * (0.25 + r() * 0.55); x.strokeStyle = r() < 0.5 ? '#ffd166' : '#c084fc'; x.globalAlpha = 0.25 + r() * 0.5; x.lineWidth = 1 + r() * 2; x.beginPath(); x.moveTo(cx, cy); x.lineTo(cx + Math.cos(a) * len, cy + Math.sin(a) * len); x.stroke(); }
    x.globalAlpha = 1; glowDot(x, cx, cy, w * 0.55, '#ff9f1cff'); glowDot(x, cx, cy, w * 0.3, '#fff3c4ff'); glowDot(x, cx, cy, w * 0.12, '#ffffffff');
  } },
  blackhole: { name: 'Event Horizon', draw(x, w, h, r) {
    x.fillStyle = '#020104'; x.fillRect(0, 0, w, h); stars(x, w, h, 60, r, '#e9d5ff');
    const cx = w / 2, cy = h * 0.45;
    x.save(); x.translate(cx, cy); x.scale(1, 0.32);
    for (let i = 0; i < 26; i++) { x.strokeStyle = ['#ff7a00', '#ffd27a', '#ff3d00', '#fff1c9'][i % 4]; x.globalAlpha = 0.18 + r() * 0.3; x.lineWidth = 2 + r() * 3; x.beginPath(); x.arc(0, 0, w * (0.32 + r() * 0.14), 0, Math.PI * 2); x.stroke(); }
    x.restore(); x.globalAlpha = 1;
    x.shadowColor = '#ff9a3c'; x.shadowBlur = 22; x.fillStyle = '#000'; x.beginPath(); x.arc(cx, cy, w * 0.2, 0, Math.PI * 2); x.fill(); x.shadowBlur = 0;
    x.save(); x.translate(cx, cy); x.scale(1, 0.32); x.strokeStyle = '#ffe2a8'; x.globalAlpha = 0.85; x.lineWidth = 3; x.beginPath(); x.arc(0, 0, w * 0.36, 0, Math.PI); x.stroke(); x.restore(); x.globalAlpha = 1;
  } },
  galaxy: { name: 'Spiral Galaxy', draw(x, w, h, r) {
    grad(x, w, h, ['#050314', '#0c0a2e', '#04020e']); stars(x, w, h, 50, r);
    const cx = w / 2, cy = h / 2;
    for (let arm = 0; arm < 2; arm++) for (let i = 0; i < 420; i++) {
      const t = i / 420, a = arm * Math.PI + t * Math.PI * 3.2, d = t * w * 0.62 + (r() - 0.5) * 10;
      x.globalAlpha = (1 - t) * 0.9; x.fillStyle = r() < 0.3 ? '#f9a8d4' : r() < 0.5 ? '#a5b4fc' : '#ffffff';
      x.fillRect(cx + Math.cos(a) * d, cy + Math.sin(a) * d * 0.8, 1.5, 1.5);
    }
    x.globalAlpha = 1; glowDot(x, cx, cy, w * 0.3, '#fde68acc'); glowDot(x, cx, cy, w * 0.1, '#ffffffff');
  } },
  comet: { name: 'Comet Trail', draw(x, w, h, r) {
    grad(x, w, h, ['#020617', '#0b1d3a', '#0f2b52']); stars(x, w, h, 80, r);
    x.save(); x.translate(w * 0.75, h * 0.18); x.rotate(Math.PI * 0.62);
    const g = x.createLinearGradient(0, 0, h * 1.1, 0); g.addColorStop(0, '#e0f2feff'); g.addColorStop(0.15, '#7dd3fccc'); g.addColorStop(1, '#38bdf800');
    x.fillStyle = g; x.beginPath(); x.moveTo(0, -6); x.lineTo(h * 1.1, -26); x.lineTo(h * 1.1, 26); x.lineTo(0, 6); x.fill(); x.restore();
    glowDot(x, w * 0.75, h * 0.18, 22, '#ffffffff');
  } },
  solar: { name: 'Solar Flare', draw(x, w, h, r) {
    grad(x, w, h, ['#ffe08a', '#ff9a1f', '#e8410f', '#7a1104']);
    for (let i = 0; i < 160; i++) { x.globalAlpha = 0.15 + r() * 0.25; x.fillStyle = r() < 0.5 ? '#fff4b8' : '#b3260a'; const s = 3 + r() * 9; x.beginPath(); x.arc(r() * w, r() * h, s, 0, Math.PI * 2); x.fill(); }
    x.globalAlpha = 0.8; x.strokeStyle = '#fff7d1'; x.lineWidth = 3; x.shadowColor = '#ffd36b'; x.shadowBlur = 14;
    for (let k = 0; k < 3; k++) { x.beginPath(); const sx = r() * w; x.moveTo(sx, h); x.bezierCurveTo(sx + 30, h * 0.5, sx - 40, h * 0.3, sx + (r() - 0.5) * 30, h * (0.1 + r() * 0.2)); x.stroke(); }
    x.globalAlpha = 1; x.shadowBlur = 0;
  } },
  constellation: { name: 'Constellation', draw(x, w, h, r) {
    grad(x, w, h, ['#030b1f', '#0a1a3d', '#06122b']); stars(x, w, h, 70, r, '#bfdbfe');
    const pts = Array.from({ length: 9 }, () => [12 + r() * (w - 24), 12 + r() * (h - 24)]).sort((a, b) => a[1] - b[1]);
    x.strokeStyle = '#93c5fd'; x.globalAlpha = 0.7; x.lineWidth = 1.5; x.beginPath(); pts.forEach(([px, py], i) => (i ? x.lineTo(px, py) : x.moveTo(px, py))); x.stroke(); x.globalAlpha = 1;
    pts.forEach(([px, py]) => { glowDot(x, px, py, 9, '#dbeafeff'); x.fillStyle = '#fff'; x.fillRect(px - 1.5, py - 1.5, 3, 3); });
  } },
};
const presetDef = (k) => CAPE_PRESETS[k] || PREMIUM_CAPES[k];

/** Builds a full cape texture (front art + darker inner side + edges + elytra) and returns a PNG data URL. */
export async function buildCape({ preset, image }) {
  const tex = document.createElement('canvas'); tex.width = 64 * S; tex.height = 32 * S;
  const t = tex.getContext('2d');
  const art = document.createElement('canvas'); art.width = FRONT.w; art.height = FRONT.h;
  const a = art.getContext('2d');
  if (preset) presetDef(preset).draw(a, art.width, art.height, rng(preset.length * 7919));
  else {
    const img = await loadImg(image);
    const s = Math.max(art.width / img.width, art.height / img.height);
    a.imageSmoothingQuality = 'high';
    a.drawImage(img, (art.width - img.width * s) / 2, (art.height - img.height * s) / 2, img.width * s, img.height * s);
  }
  // average edge color for the cape's sides/top
  const d = a.getImageData(0, 0, art.width, 4).data; let rr = 0, gg = 0, bb = 0;
  for (let i = 0; i < d.length; i += 4) { rr += d[i]; gg += d[i + 1]; bb += d[i + 2]; }
  const n = d.length / 4; const edge = `rgb(${rr / n | 0},${gg / n | 0},${bb / n | 0})`;
  t.fillStyle = edge; t.fillRect(0, 0, 22 * S, 17 * S);
  t.drawImage(art, FRONT.x, FRONT.y);
  t.save(); t.translate(22 * S, 1 * S); t.scale(-1, 1); t.drawImage(art, 0, 0); t.restore(); // inner side mirrored
  t.fillStyle = 'rgba(0,0,0,.45)'; t.fillRect(12 * S, 1 * S, 10 * S, 16 * S);
  t.drawImage(art, 0, 0, art.width, art.height * 0.7, 22 * S, 0, 24 * S, 22 * S); // elytra
  return tex.toDataURL('image/png');
}

/** Draws the front face of a cape texture into a canvas. */
export async function drawCapeFront(canvas, src) {
  const img = await loadImg(src);
  const k = img.width / 64;
  canvas.width = 10 * k; canvas.height = 16 * k;
  const x = canvas.getContext('2d'); x.imageSmoothingEnabled = false;
  x.drawImage(img, 1 * k, 1 * k, 10 * k, 16 * k, 0, 0, canvas.width, canvas.height);
}

// ---------------------------------------------------------------- flat skin render (front view)
export async function drawSkinFront(canvas, src, slim = false, scale = 10) {
  const img = await loadImg(src);
  const legacy = img.height === 32;
  const aw = slim ? 3 : 4;
  canvas.width = 16 * scale; canvas.height = 32 * scale;
  const x = canvas.getContext('2d'); x.imageSmoothingEnabled = false;
  const part = (sx, sy, w, h, dx, dy, flip = false) => {
    x.save();
    if (flip) { x.translate((dx + w) * scale, dy * scale); x.scale(-1, 1); x.drawImage(img, sx, sy, w, h, 0, 0, w * scale, h * scale); }
    else x.drawImage(img, sx, sy, w, h, dx * scale, dy * scale, w * scale, h * scale);
    x.restore();
  };
  const ax = 4 - aw;
  part(8, 8, 8, 8, 4, 0); part(40, 8, 8, 8, 4, 0); // head + hat
  part(20, 20, 8, 12, 4, 8); // body
  part(44, 20, aw, 12, ax, 8); // right arm
  legacy ? part(44, 20, aw, 12, 12, 8, true) : part(36, 52, aw, 12, 12, 8); // left arm
  part(4, 20, 4, 12, 4, 20); // right leg
  legacy ? part(4, 20, 4, 12, 8, 20, true) : part(20, 52, 4, 12, 8, 20); // left leg
  if (!legacy) { part(20, 36, 8, 12, 4, 8); part(44, 36, aw, 12, ax, 8); part(52, 52, aw, 12, 12, 8); part(4, 36, 4, 12, 4, 20); part(4, 52, 4, 12, 8, 20); }
}
