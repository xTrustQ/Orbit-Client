// Performance profiles: one click sets Minecraft's video options, Sodium's speed options and Iris shaders for a profile.
// Only the listed keys change — everything else (keybinds, sound, chat …) stays as it is.
const fs = require('fs');
const path = require('path');

const PRESETS = {
  // as many FPS as possible: short view distance, no fancy effects, no shaders
  fps: {
    options: { graphicsPreset: '"custom"', renderDistance: 8, simulationDistance: 6, particles: 2, renderClouds: '"false"', entityShadows: false,
      biomeBlendRadius: 0, ao: false, mipmapLevels: 0, cutoutLeaves: false, improvedTransparency: false, enableVsync: false, maxFps: 260,
      entityDistanceScaling: 0.75, weatherRadius: 5, chunkSectionFadeInTime: 0, vignette: false },
    shaders: false,
  },
  // good looks, smooth play
  balanced: {
    options: { graphicsPreset: '"custom"', renderDistance: 12, simulationDistance: 8, particles: 1, renderClouds: '"fast"', entityShadows: true,
      biomeBlendRadius: 2, ao: true, mipmapLevels: 4, cutoutLeaves: true, improvedTransparency: false, enableVsync: false, maxFps: 260,
      entityDistanceScaling: 1.0, weatherRadius: 10, chunkSectionFadeInTime: 0.75, vignette: true },
    shaders: false,
  },
  // far view, every effect, shaders on (if a shader pack is installed)
  beautiful: {
    options: { graphicsPreset: '"custom"', renderDistance: 20, simulationDistance: 12, particles: 0, renderClouds: '"true"', entityShadows: true,
      biomeBlendRadius: 5, ao: true, mipmapLevels: 4, cutoutLeaves: true, improvedTransparency: true, enableVsync: true, maxFps: 260,
      entityDistanceScaling: 1.5, weatherRadius: 10, chunkSectionFadeInTime: 0.75, vignette: true },
    shaders: true,
  },
};

function editKeyValueFile(file, sep, changes) {
  const lines = fs.existsSync(file) ? fs.readFileSync(file, 'utf8').split(/\r?\n/) : [];
  const done = new Set();
  const out = lines.map((l) => {
    const i = l.indexOf(sep);
    if (i <= 0 || l.startsWith('#')) return l;
    const k = l.slice(0, i);
    if (!(k in changes)) return l;
    done.add(k);
    return k + sep + changes[k];
  });
  for (const [k, v] of Object.entries(changes)) if (!done.has(k)) out.push(k + sep + v);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, out.join('\n').replace(/\n+$/, '') + '\n');
}

/** applies a preset to a profile folder; returns what happened with shaders */
function apply(gameDir, preset) {
  const p = PRESETS[preset];
  if (!p) throw new Error('Unknown performance profile');
  editKeyValueFile(path.join(gameDir, 'options.txt'), ':', Object.fromEntries(Object.entries(p.options).map(([k, v]) => [k, String(v)])));

  // Sodium: its own speed switches (only if Sodium has written its config already)
  const sodium = path.join(gameDir, 'config', 'sodium-options.json');
  if (fs.existsSync(sodium)) {
    try {
      const s = JSON.parse(fs.readFileSync(sodium, 'utf8'));
      s.performance = { ...(s.performance || {}), animate_only_visible_textures: true, use_entity_culling: true, use_fog_occlusion: true, use_block_face_culling: true };
      fs.writeFileSync(sodium, JSON.stringify(s, null, 2));
    } catch { /* leave a file we can't read alone */ }
  }

  // Iris shaders
  const packs = fs.existsSync(path.join(gameDir, 'shaderpacks')) ? fs.readdirSync(path.join(gameDir, 'shaderpacks')).filter((f) => /\.zip$/i.test(f) || fs.statSync(path.join(gameDir, 'shaderpacks', f)).isDirectory()) : [];
  const irisFile = path.join(gameDir, 'config', 'iris.properties');
  let shaders = 'off';
  if (p.shaders) {
    if (!packs.length) shaders = 'none';
    else {
      let current = '';
      try { current = (fs.readFileSync(irisFile, 'utf8').match(/^shaderPack=(.*)$/m) || [])[1] || ''; } catch { /* no file yet */ }
      const pack = packs.includes(current) ? current : packs[0];
      editKeyValueFile(irisFile, '=', { enableShaders: 'true', shaderPack: pack });
      shaders = pack;
    }
  } else if (fs.existsSync(irisFile)) editKeyValueFile(irisFile, '=', { enableShaders: 'false' });
  return { preset, shaders };
}

module.exports = { apply, PRESETS };
