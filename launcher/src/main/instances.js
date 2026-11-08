// Instance (profile) management: create / list / edit / delete, loaders, installed content.
const fs = require('fs');
const path = require('path');
const { app } = require('electron');
const { PATHS, readJson, writeJson, fetchJson, uid } = require('./util');

const LOADERS = {
  fabric: { meta: 'https://meta.fabricmc.net/v2', prefix: 'fabric-loader' },
  quilt: { meta: 'https://meta.quiltmc.org/v3', prefix: 'quilt-loader' },
};

const dirOf = (id) => path.join(PATHS.instances, id);
const fileOf = (id) => path.join(dirOf(id), 'instance.json');

function list() {
  if (!fs.existsSync(PATHS.instances)) return [];
  return fs.readdirSync(PATHS.instances)
    .map((id) => readJson(fileOf(id), null))
    .filter(Boolean)
    .sort((a, b) => (b.lastPlayed || b.createdAt) - (a.lastPlayed || a.createdAt));
}
function get(id) {
  const inst = readJson(fileOf(id), null);
  if (!inst) throw new Error('Instance not found');
  return inst;
}
function save(inst) { writeJson(fileOf(inst.id), inst); return inst; }

function create({ name, mcVersion, loader = 'vanilla', loaderVersion = null, icon = null, group = null }) {
  const id = (name || 'instance').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 24) + '-' + uid();
  const inst = { id, name: name || `${loader} ${mcVersion}`, mcVersion, loader, loaderVersion, icon, group, createdAt: Date.now(), lastPlayed: null, memory: null, jvmArgs: '', playtime: 0 };
  fs.mkdirSync(path.join(dirOf(id), 'mods'), { recursive: true });
  return save(inst);
}
function update(id, patch) { const allowed = ['name', 'icon', 'group', 'memory', 'jvmArgs', 'loaderVersion', 'favorite']; const inst = get(id); for (const k of allowed) if (k in patch) inst[k] = patch[k]; return save(inst); }
function remove(id) { fs.rmSync(dirOf(id), { recursive: true, force: true }); return list(); }
function duplicate(id) {
  const src = get(id);
  const copy = create({ ...src, name: src.name + ' (copy)' });
  fs.cpSync(dirOf(id), dirOf(copy.id), { recursive: true, filter: (s) => path.basename(s) !== 'instance.json' });
  return copy;
}

// ---------- versions ----------
let manifestCache = null;
async function mcVersions() {
  if (!manifestCache) manifestCache = await fetchJson('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json');
  return manifestCache.versions.map((v) => ({ id: v.id, type: v.type, url: v.url, releaseTime: v.releaseTime }));
}
async function versionJson(mcVersion) {
  const cached = path.join(PATHS.minecraft, 'versions', mcVersion, mcVersion + '.json');
  const local = readJson(cached, null);
  if (local) return local;
  const v = (await mcVersions()).find((x) => x.id === mcVersion);
  if (!v) throw new Error('Unknown Minecraft version ' + mcVersion);
  return fetchJson(v.url);
}
async function loaderVersions(loader, mcVersion) {
  const L = LOADERS[loader];
  if (!L) return [];
  const rows = await fetchJson(`${L.meta}/versions/loader/${mcVersion}`);
  return rows.map((r) => ({ version: r.loader.version, stable: r.loader.stable ?? !/beta|pre/.test(r.loader.version) }));
}

/** Writes the loader's launch profile into the shared versions folder; returns its version id. */
async function ensureLoaderProfile(inst) {
  const L = LOADERS[inst.loader];
  if (!L) return null;
  if (!inst.loaderVersion) {
    const vs = await loaderVersions(inst.loader, inst.mcVersion);
    if (!vs.length) throw new Error(`${inst.loader} does not support ${inst.mcVersion}`);
    inst.loaderVersion = (vs.find((v) => v.stable) || vs[0]).version;
    save(inst);
  }
  const vid = `${L.prefix}-${inst.loaderVersion}-${inst.mcVersion}`;
  const file = path.join(PATHS.minecraft, 'versions', vid, vid + '.json');
  if (!fs.existsSync(file)) {
    const profile = await fetchJson(`${L.meta}/versions/loader/${inst.mcVersion}/${inst.loaderVersion}/profile/json`);
    writeJson(file, profile);
  }
  return vid;
}

// ---------- installed content ----------
const CONTENT_DIRS = { mod: 'mods', resourcepack: 'resourcepacks', shader: 'shaderpacks', datapack: 'datapacks' };

function contents(id, kind = 'mod') {
  const dir = path.join(dirOf(id), CONTENT_DIRS[kind] || 'mods');
  if (!fs.existsSync(dir)) return [];
  const meta = readJson(path.join(dirOf(id), '.orbit-content.json'), {});
  return fs.readdirSync(dir)
    .filter((f) => /\.(jar|zip)(\.disabled)?$/i.test(f))
    .map((f) => {
      const file = f.replace(/\.disabled$/, '');
      const m = meta[file] || {};
      return { file, enabled: !f.endsWith('.disabled'), size: fs.statSync(path.join(dir, f)).size, title: m.title || file.replace(/\.(jar|zip)$/i, ''), icon: m.icon || null, source: m.source || null, projectId: m.projectId || null };
    })
    .sort((a, b) => a.title.localeCompare(b.title));
}
function recordContent(id, file, meta) {
  const f = path.join(dirOf(id), '.orbit-content.json');
  const all = readJson(f, {});
  all[file] = meta;
  writeJson(f, all);
}
function toggleContent(id, kind, file, enabled) {
  const dir = path.join(dirOf(id), CONTENT_DIRS[kind]);
  const on = path.join(dir, file), off = on + '.disabled';
  if (enabled && fs.existsSync(off)) fs.renameSync(off, on);
  if (!enabled && fs.existsSync(on)) fs.renameSync(on, off);
  return contents(id, kind);
}
function removeContent(id, kind, file) {
  const dir = path.join(dirOf(id), CONTENT_DIRS[kind]);
  for (const f of [file, file + '.disabled']) fs.rmSync(path.join(dir, f), { force: true });
  return contents(id, kind);
}
function hasProject(id, kind, projectId) {
  return contents(id, kind).some((c) => c.projectId === projectId);
}

// ---------- Orbit client mod ----------
// One Orbit build per Minecraft line; extra versions in a line reuse that build (each build declares the same range).
const ORBIT_BUILDS = { '1.21.11': '1.21.11' }; // Orbit is built for 1.21.11 only
function resourcesDirs() {
  return [path.join(process.resourcesPath || '', 'resources'), path.join(app.getAppPath(), 'resources')];
}
/** Path of the Orbit jar built for this Minecraft version, or null when there isn't one. */
function novaJarPath(mcVersion) {
  const build = ORBIT_BUILDS[mcVersion];
  if (!build) return null;
  for (const dir of resourcesDirs()) {
    const p = path.join(dir, `orbit-${build}.jar`);
    if (fs.existsSync(p)) return p;
  }
  return null;
}
/** Minecraft versions that get the Orbit in-game mod (only those whose jar is actually bundled). */
function novaVersions() { return Object.keys(ORBIT_BUILDS).filter((v) => novaJarPath(v)); }

/** Copies the matching Orbit build into Fabric/Quilt instances. Returns true if installed. */
function syncOrbitMod(inst) {
  if (!['fabric', 'quilt'].includes(inst.loader)) return false;
  const jar = novaJarPath(inst.mcVersion);
  if (!jar) return false;
  const mods = path.join(dirOf(inst.id), 'mods');
  fs.mkdirSync(mods, { recursive: true });
  const dest = path.join(mods, 'orbit.jar');
  if (fs.existsSync(dest + '.disabled')) return false; // user disabled it on purpose
  const src = fs.statSync(jar);
  if (!fs.existsSync(dest) || fs.statSync(dest).size !== src.size || fs.statSync(dest).mtimeMs < src.mtimeMs) {
    fs.copyFileSync(jar, dest);
    fs.utimesSync(dest, new Date(), new Date(src.mtimeMs + 1)); // ensure later syncs see it as up to date
  }
  recordContent(inst.id, 'orbit.jar', { title: 'Orbit', source: 'nova', projectId: 'orbit' });
  return true;
}

module.exports = {
  LOADERS, novaVersions, dirOf, list, get, save, create, update, remove, duplicate,
  mcVersions, versionJson, loaderVersions, ensureLoaderProfile,
  CONTENT_DIRS, contents, recordContent, toggleContent, removeContent, hasProject, syncOrbitMod, novaJarPath,
};
