// Small synthesized UI sounds (no audio files). Turn off in Settings → Appearance → Sound effects.
let ctx = null, master = null, enabled = true;

function ac() {
  if (!ctx) {
    ctx = new (window.AudioContext || window.webkitAudioContext)();
    master = ctx.createGain(); master.gain.value = 0.22; master.connect(ctx.destination);
  }
  if (ctx.state === 'suspended') ctx.resume();
  return ctx;
}

/** One tone with a quick attack and exponential release; freq can glide to `to`. */
function tone({ freq = 600, to = null, type = 'sine', dur = 0.12, vol = 0.5, delay = 0 }) {
  if (!enabled) return;
  try {
    const a = ac(), t = a.currentTime + delay;
    const o = a.createOscillator(), g = a.createGain();
    o.type = type; o.frequency.setValueAtTime(freq, t);
    if (to) o.frequency.exponentialRampToValueAtTime(to, t + dur);
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(vol, t + 0.008);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g); g.connect(master);
    o.start(t); o.stop(t + dur + 0.02);
  } catch { /* audio not available */ }
}

/** Filtered noise burst (whooshes). */
function noise({ dur = 0.3, from = 400, to = 2400, vol = 0.25, delay = 0 }) {
  if (!enabled) return;
  try {
    const a = ac(), t = a.currentTime + delay;
    const buf = a.createBuffer(1, Math.ceil(a.sampleRate * dur), a.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / d.length);
    const src = a.createBufferSource(); src.buffer = buf;
    const f = a.createBiquadFilter(); f.type = 'bandpass'; f.Q.value = 1.2;
    f.frequency.setValueAtTime(from, t); f.frequency.exponentialRampToValueAtTime(to, t + dur);
    const g = a.createGain(); g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    src.connect(f); f.connect(g); g.connect(master);
    src.start(t);
  } catch { /* ignore */ }
}

export const sfx = {
  setEnabled(on) { enabled = on !== false; },
  hover: () => tone({ freq: 1800, dur: 0.03, vol: 0.05, type: 'sine' }),
  click: () => tone({ freq: 880, to: 1250, dur: 0.07, vol: 0.18, type: 'triangle' }),
  pop: () => { tone({ freq: 520, to: 1040, dur: 0.12, vol: 0.25 }); tone({ freq: 1560, dur: 0.08, vol: 0.08, delay: 0.05 }); },
  page: () => noise({ dur: 0.22, from: 600, to: 2600, vol: 0.08 }),
  toggleOn: () => { tone({ freq: 660, dur: 0.07, vol: 0.18 }); tone({ freq: 990, dur: 0.1, vol: 0.18, delay: 0.06 }); },
  toggleOff: () => { tone({ freq: 760, dur: 0.07, vol: 0.15 }); tone({ freq: 500, dur: 0.1, vol: 0.15, delay: 0.06 }); },
  success: () => [523, 659, 784, 1047].forEach((f, i) => tone({ freq: f, dur: 0.22, vol: 0.14, delay: i * 0.07 })),
  error: () => { tone({ freq: 300, to: 180, dur: 0.25, vol: 0.2, type: 'sawtooth' }); },
  launch: () => {
    noise({ dur: 1.4, from: 120, to: 3200, vol: 0.22 });
    tone({ freq: 110, to: 880, dur: 1.3, vol: 0.18, type: 'sawtooth' });
    [784, 988, 1175].forEach((f, i) => tone({ freq: f, dur: 0.3, vol: 0.12, delay: 1.1 + i * 0.08 }));
  },
  coin: () => { tone({ freq: 988, dur: 0.08, vol: 0.2, type: 'square' }); tone({ freq: 1319, dur: 0.25, vol: 0.18, type: 'square', delay: 0.08 }); },
};
