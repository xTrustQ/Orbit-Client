// Skins library + Mojang skin upload, Mojang capes, and Orbit client-side capes.
const fs = require('fs');
const path = require('path');
const { PATHS, readJson, writeJson, fetchJson, uid } = require('./util');
const auth = require('./auth');

const SKIN_INDEX = path.join(PATHS.skins, 'index.json');
const CAPE_INDEX = path.join(PATHS.capes, 'index.json');
const dataUrl = (file) => 'data:image/png;base64,' + fs.readFileSync(file).toString('base64');
const MC = 'https://api.minecraftservices.com/minecraft/profile';

// ---------------------------------------------------------------- skins
function listSkins() {
  return readJson(SKIN_INDEX, []).filter((s) => fs.existsSync(path.join(PATHS.skins, s.file))).map((s) => ({ ...s, data: dataUrl(path.join(PATHS.skins, s.file)) }));
}
function saveSkin({ name, model = 'classic', png }) {
  const buf = Buffer.isBuffer(png) ? png : Buffer.from(String(png).replace(/^data:image\/png;base64,/, ''), 'base64');
  if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error('Skin must be a PNG file');
  const w = buf.readUInt32BE(16), h = buf.readUInt32BE(20);
  if (w !== 64 || (h !== 64 && h !== 32)) throw new Error(`Skins must be 64×64 or 64×32 (this one is ${w}×${h})`);
  const id = uid();
  fs.writeFileSync(path.join(PATHS.skins, id + '.png'), buf);
  const all = readJson(SKIN_INDEX, []);
  all.unshift({ id, name: name || 'Skin ' + (all.length + 1), model, file: id + '.png', addedAt: Date.now() });
  writeJson(SKIN_INDEX, all);
  return listSkins();
}
function updateSkin(id, patch) {
  const all = readJson(SKIN_INDEX, []).map((s) => (s.id === id ? { ...s, name: patch.name ?? s.name, model: patch.model ?? s.model } : s));
  writeJson(SKIN_INDEX, all);
  return listSkins();
}
function deleteSkin(id) {
  const all = readJson(SKIN_INDEX, []);
  const s = all.find((x) => x.id === id);
  if (s) fs.rmSync(path.join(PATHS.skins, s.file), { force: true });
  writeJson(SKIN_INDEX, all.filter((x) => x.id !== id));
  return listSkins();
}
async function importSkinFromUsername(username) {
  const p = await fetchJson(`https://api.mojang.com/users/profiles/minecraft/${encodeURIComponent(username)}`).catch(() => null);
  if (!p) throw new Error('Player not found');
  const prof = await fetchJson(`https://sessionserver.mojang.com/session/minecraft/profile/${p.id}`);
  const tex = JSON.parse(Buffer.from(prof.properties.find((x) => x.name === 'textures').value, 'base64').toString());
  const skin = tex.textures.SKIN;
  if (!skin) throw new Error('That player uses a default skin');
  const buf = Buffer.from(await (await fetch(skin.url)).arrayBuffer());
  return saveSkin({ name: p.name, model: skin.metadata?.model === 'slim' ? 'slim' : 'classic', png: buf });
}
async function applySkin(skinId) {
  const acc = auth.selected();
  if (!acc) throw new Error('Sign in first');
  const a = await auth.ensureToken(acc.id);
  if (a.type !== 'msa') throw new Error('Offline accounts cannot change skins');
  const s = readJson(SKIN_INDEX, []).find((x) => x.id === skinId);
  const form = new FormData();
  form.append('variant', s.model === 'slim' ? 'slim' : 'classic');
  form.append('file', new Blob([fs.readFileSync(path.join(PATHS.skins, s.file))], { type: 'image/png' }), 'skin.png');
  const res = await fetch(MC + '/skins', { method: 'POST', headers: { Authorization: 'Bearer ' + a.token }, body: form });
  if (!res.ok) throw new Error('Skin upload failed: ' + res.status + ' ' + (await res.text()).slice(0, 200));
  return true;
}

/** The selected account's current skin as a data URL (for the 3D preview). */
async function currentSkin() {
  const acc = auth.selected();
  let url = `https://mc-heads.net/skin/${encodeURIComponent(acc ? acc.name : 'MHF_Steve')}`, model = 'classic';
  if (acc?.type === 'msa') {
    try {
      const p = await auth.profile(acc.id);
      const s = (p.skins || []).find((x) => x.state === 'ACTIVE');
      if (s) { url = s.url.replace(/^http:/, 'https:'); model = s.variant === 'SLIM' ? 'slim' : 'classic'; }
    } catch { /* fall back to mc-heads */ }
  }
  const res = await fetch(url);
  if (!res.ok) return null;
  return { data: 'data:image/png;base64,' + Buffer.from(await res.arrayBuffer()).toString('base64'), model };
}

// ---------------------------------------------------------------- capes
async function mojangCapes() {
  const acc = auth.selected();
  if (!acc) return { capes: [], skin: null };
  const p = await auth.profile(acc.id);
  return {
    offline: !!p.offline,
    capes: (p.capes || []).map((c) => ({ id: c.id, alias: c.alias, url: c.url, active: c.state === 'ACTIVE' })),
    skin: (p.skins || []).find((s) => s.state === 'ACTIVE') || null,
  };
}
async function setMojangCape(capeId) {
  const acc = auth.selected();
  const a = await auth.ensureToken(acc.id);
  if (a.type !== 'msa') throw new Error('Offline accounts have no capes');
  const headers = { Authorization: 'Bearer ' + a.token, 'Content-Type': 'application/json' };
  if (capeId) await fetchJson(MC + '/capes/active', { method: 'PUT', headers, body: JSON.stringify({ capeId }) });
  else await fetchJson(MC + '/capes/active', { method: 'DELETE', headers });
  return mojangCapes();
}

function novaCapes() {
  const sel = readJson(PATHS.cosmetics, {}).capeId || null;
  return readJson(CAPE_INDEX, []).filter((c) => fs.existsSync(path.join(PATHS.capes, c.file))).map((c) => ({ ...c, data: dataUrl(path.join(PATHS.capes, c.file)), selected: c.id === sel }));
}
function saveOrbitCape({ name, png, preset = null }) {
  const id = preset ? 'preset-' + preset : uid();
  fs.writeFileSync(path.join(PATHS.capes, id + '.png'), Buffer.from(png.replace(/^data:image\/png;base64,/, ''), 'base64'));
  const all = readJson(CAPE_INDEX, []).filter((c) => c.id !== id);
  all.push({ id, name, file: id + '.png', preset, favorite: false });
  writeJson(CAPE_INDEX, all);
  return novaCapes();
}
function deleteOrbitCape(id) {
  writeJson(CAPE_INDEX, readJson(CAPE_INDEX, []).filter((c) => c.id !== id));
  fs.rmSync(path.join(PATHS.capes, id + '.png'), { force: true });
  if (readJson(PATHS.cosmetics, {}).capeId === id) selectOrbitCape(null);
  return novaCapes();
}
function favoriteOrbitCape(id, favorite) {
  writeJson(CAPE_INDEX, readJson(CAPE_INDEX, []).map((c) => (c.id === id ? { ...c, favorite } : c)));
  return novaCapes();
}
/** The Orbit mod reads this file (path passed with -Dorbit.cosmetics) to render your client cape. */
function selectOrbitCape(id) {
  const c = readJson(CAPE_INDEX, []).find((x) => x.id === id);
  writeJson(PATHS.cosmetics, { capeId: c ? c.id : null, capeFile: c ? path.join(PATHS.capes, c.file) : null, updatedAt: Date.now() });
  return novaCapes();
}

module.exports = { currentSkin, listSkins, saveSkin, updateSkin, deleteSkin, importSkinFromUsername, applySkin, mojangCapes, setMojangCape, novaCapes, saveOrbitCape, deleteOrbitCape, favoriteOrbitCape, selectOrbitCape };
