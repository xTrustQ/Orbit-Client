// Launcher-wide polish: card spotlight that follows the mouse, button ripples, gentle 3D tilt on
// cards, and UI sounds on clicks / toggles. Everything is event-delegated, so new pages get it too.
import { sfx } from './sfx.js';

const reduce = () => document.documentElement.dataset.anim === 'off';
let lastHover = null;

export function startFx() {
  // spotlight + tilt
  document.addEventListener('pointermove', (e) => {
    const card = e.target.closest?.('.card');
    if (card) {
      const r = card.getBoundingClientRect();
      card.style.setProperty('--mx', `${e.clientX - r.left}px`);
      card.style.setProperty('--my', `${e.clientY - r.top}px`);
      if (card.matches('.hover') && !reduce()) {
        const rx = ((e.clientY - r.top) / r.height - 0.5) * -4, ry = ((e.clientX - r.left) / r.width - 0.5) * 5;
        card.style.setProperty('--tilt', `perspective(900px) rotateX(${rx.toFixed(2)}deg) rotateY(${ry.toFixed(2)}deg)`);
      }
    }
    const hov = e.target.closest?.('.btn, .tab, .chip, .card.hover, .icon-btn');
    if (hov && hov !== lastHover) { lastHover = hov; sfx.hover(); }
    if (!hov) lastHover = null;
  }, { passive: true });
  document.addEventListener('pointerout', (e) => {
    const card = e.target.closest?.('.card.hover');
    if (card && !card.contains(e.relatedTarget)) card.style.removeProperty('--tilt');
  });

  // ripples + click sounds
  document.addEventListener('pointerdown', (e) => {
    const b = e.target.closest?.('.btn, .liftoff, .tab, .chip');
    if (!b || b.disabled) return;
    if (!reduce()) {
      const r = b.getBoundingClientRect(), s = Math.max(r.width, r.height) * 1.6;
      const span = document.createElement('span');
      span.className = 'ripple';
      span.style.cssText = `width:${s}px;height:${s}px;left:${e.clientX - r.left - s / 2}px;top:${e.clientY - r.top - s / 2}px`;
      b.appendChild(span);
      setTimeout(() => span.remove(), 650);
    }
  });
  document.addEventListener('click', (e) => {
    const t = e.target.closest?.('.toggle');
    if (t) { setTimeout(() => (t.classList.contains('on') ? sfx.toggleOn() : sfx.toggleOff()), 0); return; }
    if (e.target.closest?.('.liftoff')) return; // has its own launch sound
    if (e.target.closest?.('.btn, .tab, .chip, .icon-btn, .picker-row, .acc-row')) sfx.click();
  });
}
