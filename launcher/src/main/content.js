// Content browser: Modrinth + CurseForge search, install with dependencies, modpacks, performance pack.
const fs = require('fs');
const path = require('path');
const AdmZip = require('adm-zip');
const { PATHS, Store, fetchJson, download, safeJoin, readJson, sha1File } = require('./util');
const instances = require('./instances');

const settings = new Store('settings', {});
const MR = 'https://api.modrinth.com/v2';
const CF = 'https://api.curseforge.com/v1';
const CF_CLASS = { mod: 6, resourcepack: 12, shader: 6552, modpack: 4471, datapack: 6945 };
const CF_LOADER = { forge: 1, fabric: 4, quilt: 5, neoforge: 6 };

/** Mods that make the game faster. Installed into new Fabric/Quilt instances when available. */
const PERFORMANCE_PACK = ['fabric-api', 'sodium', 'lithium', 'ferrite-core', 'entityculling', 'immediatelyfast', 'modernfix', 'moreculling', 'dynamic-fps', 'krypton'];

// ------------------------------------------------------------------ search
async function search({ source = 'modrinth', type = 'mod', query = '', gameVersion, loader, category, environment, sort = 'downloads', offset = 0 }) {
  return source === 'curseforge'
    ? cfSearch({ type, query, gameVersion, loader, category, sort, offset })
    : mrSearch({ type, query, gameVersion, loader, category, environment, sort, offset });
}

async function mrSearch({ type, query, gameVersion, loader, category, environment, sort, offset }) {
  const facets = [[`project_type:${type}`]];
  if (gameVersion) facets.push([`versions:${gameVersion}`]);
  if (loader && (type === 'mod' || type === 'modpack')) facets.push([`categories:${loader}`]);
  if (category) facets.push([`categories:${category}`]);
  if (environment === 'client') facets.push(['client_side:required', 'client_side:optional']);
  if (environment === 'server') facets.push(['server_side:required', 'server_side:optional']);
  const q = new URLSearchParams({ query, facets: JSON.stringify(facets), index: sort, limit: '20', offset: String(offset) });
  const r = await fetchJson(`${MR}/search?${q}`);
  return {
    total: r.total_hits,
    hits: r.hits.map((h) => ({
      id: h.project_id, slug: h.slug, title: h.title, author: h.author, description: h.description, icon: h.icon_url,
      downloads: h.downloads, follows: h.follows, categories: h.display_categories || h.categories, source: 'modrinth', type,
      url: `https://modrinth.com/${type}/${h.slug}`, color: h.color,
    })),
  };
}

function cfKey() {
  const key = settings.get('curseforgeKey');
  if (!key) { const e = new Error('CurseForge needs an API key. Get a free one at console.curseforge.com and paste it in Settings → Integrations.'); e.code = 'NO_CF_KEY'; throw e; }
  return { 'x-api-key': key, Accept: 'application/json' };
}
async function cfSearch({ type, query, gameVersion, loader, category, sort, offset }) {
  const sortField = { downloads: 6, follows: 2, newest: 11, updated: 3, relevance: 2 }[sort] || 6;
  const q = new URLSearchParams({ gameId: '432', classId: String(CF_CLASS[type] || 6), searchFilter: query, sortField: String(sortField), sortOrder: 'desc', pageSize: '20', index: String(offset) });
  if (gameVersion) q.set('gameVersion', gameVersion);
  if (loader && type === 'mod' && CF_LOADER[loader]) q.set('modLoaderType', String(CF_LOADER[loader]));
  if (category) q.set('categoryId', String(category));
  const r = await fetchJson(`${CF}/mods/search?${q}`, { headers: cfKey() });
  return {
    total: Math.min(r.pagination.totalCount, 10000),
    hits: r.data.map((m) => ({
      id: String(m.id), slug: m.slug, title: m.name, author: m.authors?.[0]?.name || '', description: m.summary, icon: m.logo?.thumbnailUrl,
      downloads: m.downloadCount, categories: (m.categories || []).map((c) => c.name).slice(0, 3), source: 'curseforge', type, url: m.links?.websiteUrl,
    })),
  };
}

async function categories(source, type) {
  if (source === 'curseforge') {
    const r = await fetchJson(`${CF}/categories?gameId=432&classId=${CF_CLASS[type] || 6}`, { headers: cfKey() });
    return r.data.map((c) => ({ id: String(c.id), name: c.name }));
  }
  const all = await fetchJson(`${MR}/tag/category`);
  return all.filter((c) => c.project_type === type && c.header !== 'resolutions').map((c) => ({ id: c.name, name: c.name.replace(/-/g, ' ') }));
}

// ------------------------------------------------------------------ install
const loadersFor = (inst, type) => {
  if (type === 'mod') return inst.loader === 'quilt' ? ['quilt', 'fabric'] : [inst.loader];
  if (type === 'shader') return ['iris', 'optifine', 'canvas'];
  if (type === 'datapack') return ['datapack'];
  if (type === 'resourcepack') return ['minecraft'];
  return null;
};

async function mrBestVersion(projectId, inst, type) {
  const q = new URLSearchParams({ game_versions: JSON.stringify([inst.mcVersion]) });
  const loaders = loadersFor(inst, type);
  if (loaders) q.set('loaders', JSON.stringify(loaders));
  const versions = await fetchJson(`${MR}/project/${projectId}/version?${q}`);
  return versions.find((v) => v.version_type === 'release') || versions[0] || null;
}

// Same mod requested twice for the same profile (double click, or as a dependency of something
// else being installed right now) → share the one install instead of racing on the same files.
const installing = new Map();
function install(req, onProgress, seen) {
  if (seen && seen.has(req.projectId)) return Promise.resolve(); // dependency cycle: already being handled up the chain
  const key = `${req.instanceId}:${req.projectId}`;
  if (installing.has(key)) return installing.get(key);
  const p = installInner(req, onProgress, seen).finally(() => installing.delete(key));
  installing.set(key, p);
  return p;
}

async function installInner({ source, projectId, type, instanceId, meta = {} }, onProgress, seen = new Set()) {
  const inst = instances.get(instanceId);
  if (type === 'mod' && inst.loader === 'vanilla') throw new Error('This profile is Vanilla — mods need a Fabric or Quilt profile.');
  if (seen.has(projectId)) return;
  seen.add(projectId);
  const dir = path.join(instances.dirOf(inst.id), instances.CONTENT_DIRS[type] || 'mods');
  if (instances.hasProject(inst.id, type, projectId)) return;

  if (source === 'curseforge') {
    const q = new URLSearchParams({ gameVersion: inst.mcVersion, pageSize: '20' });
    if (type === 'mod' && CF_LOADER[inst.loader]) q.set('modLoaderType', String(CF_LOADER[inst.loader]));
    const files = (await fetchJson(`${CF}/mods/${projectId}/files?${q}`, { headers: cfKey() })).data;
    const file = files.find((f) => f.releaseType === 1) || files[0];
    if (!file) throw new Error(`No ${meta.title || 'file'} build for ${inst.mcVersion}${type === 'mod' ? ' ' + inst.loader : ''}`);
    if (!file.downloadUrl) throw new Error(`The author of ${meta.title || 'this project'} disabled third-party downloads. Download it from the CurseForge website instead.`);
    await download(file.downloadUrl, path.join(dir, file.fileName), { sha1: file.hashes?.find((x) => x.algo === 1)?.value, onProgress: (d, t) => onProgress?.(`Downloading ${file.fileName}`, d, t) });
    instances.recordContent(inst.id, file.fileName, { title: meta.title, icon: meta.icon, source, projectId });
    for (const dep of file.dependencies || []) if (dep.relationType === 3) {
      const m = (await fetchJson(`${CF}/mods/${dep.modId}`, { headers: cfKey() })).data;
      await install({ source, projectId: String(dep.modId), type, instanceId, meta: { title: m.name, icon: m.logo?.thumbnailUrl } }, onProgress, seen).catch(() => {});
    }
    return;
  }

  const v = await mrBestVersion(projectId, inst, type);
  if (!v) throw new Error(`No ${meta.title || 'version'} build for ${inst.mcVersion}${type === 'mod' ? ' ' + inst.loader : ''}`);
  const file = v.files.find((f) => f.primary) || v.files[0];
  await download(file.url, safeJoin(dir, file.filename), { sha1: file.hashes?.sha1, onProgress: (d, t) => onProgress?.(`Downloading ${file.filename}`, d, t) });
  if (!meta.title) { const p = await fetchJson(`${MR}/project/${projectId}`); meta = { title: p.title, icon: p.icon_url }; }
  instances.recordContent(inst.id, file.filename, { title: meta.title, icon: meta.icon, source: 'modrinth', projectId });
  for (const dep of v.dependencies || []) if (dep.dependency_type === 'required' && dep.project_id) {
    await install({ source, projectId: dep.project_id, type, instanceId }, onProgress, seen).catch(() => {});
  }
}

async function installPerformancePack(instanceId, onProgress) {
  const inst = instances.get(instanceId);
  if (!['fabric', 'quilt'].includes(inst.loader)) return [];
  const installed = [];
  for (const slug of PERFORMANCE_PACK) {
    try {
      const p = await fetchJson(`${MR}/project/${slug}`);
      onProgress?.(`Performance pack: ${p.title}`, 0, 0);
      if (await mrBestVersion(p.id, inst, 'mod')) {
        await install({ source: 'modrinth', projectId: p.id, type: 'mod', instanceId, meta: { title: p.title, icon: p.icon_url } }, onProgress);
        installed.push(p.title);
      }
    } catch { /* skip mods unavailable for this version */ }
  }
  return installed;
}

/**
 * Checks every Modrinth-installed jar in a profile against Modrinth's SHA-1 index and re-downloads
 * any that are damaged. One API call when everything is fine. Returns the repaired file names.
 */
async function repairMods(instanceId, onProgress) {
  const inst = instances.get(instanceId);
  const dir = path.join(instances.dirOf(instanceId), 'mods');
  if (!fs.existsSync(dir)) return [];
  const meta = readJson(path.join(instances.dirOf(instanceId), '.orbit-content.json'), {});
  // Everything except files we know came from elsewhere (Orbit itself, CurseForge, manual adds with a record).
  const jars = fs.readdirSync(dir).filter((f) => f.endsWith('.jar') && (!meta[f] || meta[f].source === 'modrinth'));
  if (!jars.length) return [];
  onProgress?.('Checking mods', 0, 0);
  const byHash = {};
  for (const f of jars) byHash[await sha1File(path.join(dir, f))] = f;
  const known = await fetchJson(`${MR}/version_files`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ hashes: Object.keys(byHash), algorithm: 'sha1' }) });
  const repaired = [];
  for (const [hash, f] of Object.entries(byHash)) {
    if (known[hash]) continue;
    const found = meta[f]?.projectId ? await fileInProject(meta[f].projectId, f) : await findByFilename(f, inst);
    if (!found) continue; // a manual/unknown jar we can't identify: leave it alone
    onProgress?.(`Repairing ${f}`, 0, 0);
    await download(found.file.url, path.join(dir, f), { sha1: found.file.hashes.sha1 });
    instances.recordContent(instanceId, f, { title: found.title || meta[f]?.title || f, icon: found.icon || meta[f]?.icon, source: 'modrinth', projectId: found.projectId });
    repaired.push(f);
  }

  // Install required dependencies that are missing (e.g. after an earlier failed install).
  const hashes = [];
  for (const f of fs.readdirSync(dir).filter((x) => x.endsWith('.jar'))) hashes.push(await sha1File(path.join(dir, f)));
  const versions = Object.values(await fetchJson(`${MR}/version_files`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ hashes, algorithm: 'sha1' }) }));
  const present = new Set(versions.map((v) => v.project_id));
  for (const v of versions) for (const d of v.dependencies || []) {
    if (d.dependency_type !== 'required' || !d.project_id || present.has(d.project_id)) continue;
    present.add(d.project_id);
    try {
      onProgress?.('Installing missing dependency', 0, 0);
      await install({ source: 'modrinth', projectId: d.project_id, type: 'mod', instanceId });
      repaired.push('+ ' + d.project_id);
    } catch { /* not available for this version */ }
  }
  return repaired;
}

async function fileInProject(projectId, filename, inst) {
  const q = inst ? `?game_versions=${encodeURIComponent(JSON.stringify([inst.mcVersion]))}` : '';
  const versions = await fetchJson(`${MR}/project/${projectId}/version${q}`);
  const file = versions.flatMap((v) => v.files).find((x) => x.filename === filename);
  return file ? { file, projectId } : null;
}

/** Identify a jar with no install record from its filename (e.g. "sodium-fabric-0.8.14+mc1.21.11.jar" → sodium). */
const FILE_ALIASES = [[/^voicechat-/i, 'simple-voice-chat'], [/^tiertagger-/i, 'tiertagger'], [/^sodium-/i, 'sodium'], [/^iris-/i, 'iris'], [/^lithium-/i, 'lithium'], [/^fabric-api-/i, 'fabric-api']];
async function findByFilename(filename, inst) {
  for (const [re, slug] of FILE_ALIASES) {
    if (!re.test(filename)) continue;
    const found = await fileInProject(slug, filename, inst).catch(() => null);
    if (found) { const p = await fetchJson(`${MR}/project/${slug}`).catch(() => ({})); return { ...found, projectId: p.id || slug, title: p.title, icon: p.icon_url }; }
  }
  const query = filename.replace(/\.jar$/i, '').replace(/[-_+](fabric|quilt|forge|neoforge|mc)?[-_+]?v?\d.*$/i, '').replace(/[-_](fabric|quilt)$/i, '').replace(/[-_]/g, ' ');
  if (!query.trim()) return null;
  const res = await mrSearch({ type: 'mod', query, gameVersion: inst.mcVersion, loader: inst.loader === 'quilt' ? '' : inst.loader, sort: 'relevance', offset: 0 });
  for (const hit of res.hits.slice(0, 5)) {
    const found = await fileInProject(hit.id, filename, inst).catch(() => null);
    if (found) return { ...found, title: hit.title, icon: hit.icon };
  }
  return null;
}

/** Mods every Orbit profile gets: proximity voice chat and Shield Fixes (tier tags are built into Orbit now). */
const STANDARD_MODS = ['simple-voice-chat', 'complete-shield-fixes'];
async function installStandardMods(instanceId, onProgress, only = STANDARD_MODS) {
  const inst = instances.get(instanceId);
  if (!['fabric', 'quilt'].includes(inst.loader)) return [];
  const installed = [];
  for (const slug of only) {
    try {
      const p = await fetchJson(`${MR}/project/${slug}`);
      onProgress?.(`Standard mods: ${p.title}`, 0, 0);
      if (await mrBestVersion(p.id, inst, 'mod')) {
        await install({ source: 'modrinth', projectId: p.id, type: 'mod', instanceId, meta: { title: p.title, icon: p.icon_url } }, onProgress);
        installed.push(p.title);
      }
    } catch { /* not available for this version */ }
  }
  return installed;
}

/**
 * Older profiles: add standard mods that came out after the profile was made (once — a mod you remove stays
 * removed), and switch off TierTagger once, because Orbit shows tier tags itself now (no double tags).
 */
async function catchUpStandardMods(instanceId, onProgress) {
  const inst = instances.get(instanceId);
  if (!['fabric', 'quilt'].includes(inst.loader)) return [];
  const offered = new Set(inst.standardOffered || []);
  const done = [];
  if (!inst.tierTaggerRetired) {
    const dir = path.join(instances.dirOf(instanceId), 'mods');
    try { for (const f of fs.readdirSync(dir)) if (/^tiertagger-.*\.jar$/i.test(f)) { fs.renameSync(path.join(dir, f), path.join(dir, f + '.disabled')); done.push('TierTagger switched off (Orbit Tier Tags replaces it)'); } } catch { /* no mods folder yet */ }
    inst.tierTaggerRetired = true;
  }
  const have = instances.contents(instanceId, 'mod').map((c) => (c.projectId || '') + ' ' + (c.file || '')).join('\n').toLowerCase();
  const missing = STANDARD_MODS.filter((s) => !offered.has(s));
  for (const slug of missing) offered.add(slug);
  inst.standardOffered = [...offered];
  instances.save(inst);
  const toInstall = [];
  for (const slug of missing) {
    let id = slug;
    try { id = (await fetchJson(`${MR}/project/${slug}`)).id || slug; } catch { /* offline */ }
    if (!have.includes(id.toLowerCase()) && !have.includes(slug.replace(/^complete-/, '').replace(/-/g, ''))) toInstall.push(slug);
  }
  if (toInstall.length) done.push(...await installStandardMods(instanceId, onProgress, toInstall));
  return done;
}

// ------------------------------------------------------------------ modpacks
function extractOverrides(zip, prefix, destDir) {
  for (const e of zip.getEntries()) {
    if (e.isDirectory || !e.entryName.startsWith(prefix + '/')) continue;
    const out = safeJoin(destDir, e.entryName.slice(prefix.length + 1));
    fs.mkdirSync(path.dirname(out), { recursive: true });
    fs.writeFileSync(out, e.getData());
  }
}

async function installModpack({ source, projectId, meta = {}, gameVersion }, onProgress) {
  const tmp = path.join(PATHS.cache, 'pack-' + Date.now() + '.zip');
  let inst;
  try {
    if (source === 'curseforge') {
      const q = new URLSearchParams({ pageSize: '20' });
      if (gameVersion) q.set('gameVersion', gameVersion);
      const files = (await fetchJson(`${CF}/mods/${projectId}/files?${q}`, { headers: cfKey() })).data;
      const file = files.find((f) => f.releaseType === 1) || files[0];
      if (!file?.downloadUrl) throw new Error('This modpack cannot be downloaded through third-party launchers.');
      await download(file.downloadUrl, tmp, { onProgress: (d, t) => onProgress?.('Downloading modpack', d, t) });
      const zip = new AdmZip(tmp);
      const manifest = JSON.parse(zip.readAsText('manifest.json'));
      const loaderId = (manifest.minecraft.modLoaders.find((l) => l.primary) || manifest.minecraft.modLoaders[0])?.id || '';
      const [loader, loaderVersion] = loaderId.split(/-(.+)/);
      if (!['fabric', 'quilt'].includes(loader)) throw new Error(`Orbit supports Fabric/Quilt packs — this pack uses ${loader || 'an unknown loader'}.`);
      inst = instances.create({ name: manifest.name || meta.title, mcVersion: manifest.minecraft.version, loader, loaderVersion, icon: meta.icon, group: 'Modpacks' });
      const ids = manifest.files.filter((f) => f.required !== false).map((f) => f.fileID);
      const infos = (await fetchJson(`${CF}/mods/files`, { method: 'POST', headers: { ...cfKey(), 'Content-Type': 'application/json' }, body: JSON.stringify({ fileIds: ids }) })).data;
      let i = 0;
      for (const f of infos) {
        onProgress?.(`Installing mods (${++i}/${infos.length})`, i, infos.length);
        if (!f.downloadUrl) continue;
        const sub = f.fileName.endsWith('.zip') ? 'resourcepacks' : 'mods';
        await download(f.downloadUrl, safeJoin(path.join(instances.dirOf(inst.id), sub), f.fileName), { sha1: f.hashes?.find((x) => x.algo === 1)?.value });
      }
      extractOverrides(zip, manifest.overrides || 'overrides', instances.dirOf(inst.id));
      return inst;
    }

    const versions = await fetchJson(`${MR}/project/${projectId}/version${gameVersion ? `?game_versions=${encodeURIComponent(JSON.stringify([gameVersion]))}` : ''}`);
    const v = versions.find((x) => x.version_type === 'release') || versions[0];
    if (!v) throw new Error('No modpack version found');
    const file = v.files.find((f) => f.primary) || v.files[0];
    await download(file.url, tmp, { onProgress: (d, t) => onProgress?.('Downloading modpack', d, t) });
    const zip = new AdmZip(tmp);
    const index = JSON.parse(zip.readAsText('modrinth.index.json'));
    const deps = index.dependencies;
    const loader = deps['fabric-loader'] ? 'fabric' : deps['quilt-loader'] ? 'quilt' : deps.forge || deps.neoforge ? null : 'vanilla';
    if (!loader) throw new Error('Orbit supports Fabric/Quilt packs — this pack uses Forge/NeoForge.');
    inst = instances.create({ name: index.name || meta.title, mcVersion: deps.minecraft, loader, loaderVersion: deps['fabric-loader'] || deps['quilt-loader'] || null, icon: meta.icon, group: 'Modpacks' });
    const files = index.files.filter((f) => f.env?.client !== 'unsupported');
    let i = 0;
    for (const f of files) {
      onProgress?.(`Installing files (${++i}/${files.length})`, i, files.length);
      await download(f.downloads[0], safeJoin(instances.dirOf(inst.id), f.path), { sha1: f.hashes?.sha1 });
    }
    extractOverrides(zip, 'overrides', instances.dirOf(inst.id));
    extractOverrides(zip, 'client-overrides', instances.dirOf(inst.id));
    return inst;
  } catch (e) {
    if (inst) instances.remove(inst.id);
    throw e;
  } finally {
    fs.rmSync(tmp, { force: true });
  }
}

module.exports = { search, categories, install, repairMods, installPerformancePack, installStandardMods, catchUpStandardMods, installModpack, PERFORMANCE_PACK, STANDARD_MODS };
