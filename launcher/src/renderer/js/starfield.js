// Animated deep-space backdrop: 3 parallax star layers, twinkle, mouse drift and shooting stars.
export function startStarfield(canvas) {
  const ctx = canvas.getContext('2d');
  let w = 0, h = 0, stars = [], shooting = [], mx = 0, my = 0, tx = 0, ty = 0, last = performance.now();
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  const accent = () => getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#b18cff';
  const animated = () => document.documentElement.dataset.anim !== 'off';

  function resize() {
    w = canvas.clientWidth; h = canvas.clientHeight;
    canvas.width = w * dpr; canvas.height = h * dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const count = Math.round((w * h) / 2600);
    stars = Array.from({ length: count }, () => {
      const depth = Math.random() < 0.65 ? 0 : Math.random() < 0.75 ? 1 : 2; // far, mid, near
      return { x: Math.random() * w, y: Math.random() * h, depth, r: [0.5, 0.9, 1.4][depth] * (0.7 + Math.random() * 0.6), phase: Math.random() * 6.28, speed: 0.4 + Math.random() * 1.6, tint: Math.random() < 0.12 };
    });
  }

  window.addEventListener('resize', resize);
  window.addEventListener('mousemove', (e) => { tx = (e.clientX / w - 0.5) * 2; ty = (e.clientY / h - 0.5) * 2; });
  resize();

  function frame(now) {
    const dt = Math.min(0.05, (now - last) / 1000); last = now;
    mx += (tx - mx) * 0.04; my += (ty - my) * 0.04;
    ctx.clearRect(0, 0, w, h);
    const acc = accent();
    const t = now / 1000;
    for (const s of stars) {
      if (animated()) { s.x -= (s.depth + 1) * 2.2 * dt; if (s.x < -4) { s.x = w + 4; s.y = Math.random() * h; } }
      const px = s.x - mx * (s.depth + 1) * 7, py = s.y - my * (s.depth + 1) * 7;
      const tw = animated() ? 0.55 + 0.45 * Math.sin(t * s.speed + s.phase) : 0.8;
      ctx.globalAlpha = tw * [0.55, 0.8, 1][s.depth];
      ctx.fillStyle = s.tint ? acc : '#ffffff';
      ctx.beginPath(); ctx.arc(px, py, s.r, 0, 6.283); ctx.fill();
      if (s.depth === 2 && tw > 0.85) { // little cross flare on bright near stars
        ctx.globalAlpha = (tw - 0.85) * 3; ctx.fillRect(px - 4, py - 0.3, 8, 0.6); ctx.fillRect(px - 0.3, py - 4, 0.6, 8);
      }
    }
    if (animated() && Math.random() < dt * 0.25) shooting.push({ x: Math.random() * w * 0.8 + w * 0.2, y: Math.random() * h * 0.4, vx: -(500 + Math.random() * 300), vy: 180 + Math.random() * 120, life: 1 });
    for (const s of shooting) {
      s.x += s.vx * dt; s.y += s.vy * dt; s.life -= dt * 1.2;
      const g = ctx.createLinearGradient(s.x, s.y, s.x - s.vx * 0.12, s.y - s.vy * 0.12);
      g.addColorStop(0, '#ffffff'); g.addColorStop(1, 'rgba(255,255,255,0)');
      ctx.globalAlpha = Math.max(0, s.life); ctx.strokeStyle = g; ctx.lineWidth = 1.6;
      ctx.beginPath(); ctx.moveTo(s.x, s.y); ctx.lineTo(s.x - s.vx * 0.12, s.y - s.vy * 0.12); ctx.stroke();
    }
    shooting = shooting.filter((s) => s.life > 0);
    ctx.globalAlpha = 1;
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
}
