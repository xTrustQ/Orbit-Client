// Import game settings (options.txt: keybinds, render distance, sensitivity, video settings …) from
// other launchers into an Orbit profile. Never copies mods, worlds or resource packs.
const fs = require('fs');
const path = require('path');
const os = require('os');

const APPDATA = process.env.APPDATA || path.join(os.homedir(), 'AppData', 'Roaming');
const HOME = os.homedir();
const SKIP = new Set(['mods', 'saves', 'resourcepacks', 'shaderpacks', 'assets', 'libraries', 'versions', 'natives', 'logs', 'crash-reports',
  'screenshots', 'cache', 'config', 'jre', 'java', 'runtime', 'meta', 'downloads', 'node_modules', '.git', 'defaultconfigs', 'kubejs', 'schematics']);

// where each launcher keeps its game folders, and how deep to look
const SOURCES = [
  { launcher: 'Minecraft Launcher', dir: path.join(APPDATA, '.minecraft'), depth: 0 },
  { launcher: 'Modrinth', dir: path.join(APPDATA, 'ModrinthApp', 'profiles'), depth: 1 },
  { launcher: 'Modrinth', dir: path.join(APPDATA, 'com.modrinth.theseus', 'profiles'), depth: 1 },
  { launcher: 'Lunar Client', dir: path.join(HOME, '.lunarclient', 'profiles'), depth: 2 },
  { launcher: 'NoRisk Client', dir: path.join(APPDATA, 'norisk'), depth: 5 },
  { launcher: 'NoRisk Client', dir: path.join(HOME, '.norisk'), depth: 5 },
  { launcher: 'CurseForge', dir: path.join(HOME, 'curseforge', 'minecraft', 'Instances'), depth: 1 },
  { launcher: 'Prism Launcher', dir: path.join(APPDATA, 'PrismLauncher', 'instances'), depth: 2 },
  { launcher: 'MultiMC', dir: path.join(HOME, 'MultiMC', 'instances'), depth: 2 },
  { launcher: 'Feather Client', dir: path.join(APPDATA, '.feather'), depth: 4 },
  { launcher: 'Badlion Client', dir: path.join(APPDATA, '.badlion'), depth: 4 },
  { launcher: 'GDLauncher', dir: path.join(APPDATA, 'gdlauncher_next', 'instances'), depth: 2 },
  { launcher: 'ATLauncher', dir: path.join(APPDATA, 'ATLauncher', 'instances'), depth: 1 },
];

function findOptions(dir, depth, out) {
  let entries;
  try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return; }
  for (const e of entries) {
    if (e.isFile() && e.name === 'options.txt') out.push(path.join(dir, e.name));
  }
  if (depth <= 0) return;
  for (const e of entries) {
    if (e.isDirectory() && !SKIP.has(e.name.toLowerCase())) findOptions(path.join(dir, e.name), depth - 1, out);
  }
}

/** Every options.txt we can find, newest first. */
function sources() {
  const seen = new Set(), list = [];
  for (const s of SOURCES) {
    const found = [];
    findOptions(s.dir, s.depth, found);
    for (const file of found) {
      const key = file.toLowerCase();
      if (seen.has(key)) continue;
      seen.add(key);
      let text = '', stat;
      try { text = fs.readFileSync(file, 'utf8'); stat = fs.statSync(file); } catch { continue; }
      const lines = text.split(/\r?\n/);
      const get = (k) => (lines.find((l) => l.startsWith(k + ':')) || '').slice(k.length + 1);
      // a readable name: the profile/instance folder (skip ".minecraft"/"minecraft")
      let folder = path.dirname(file), name = path.basename(folder);
      if (/^\.?minecraft$/i.test(name) && s.depth > 0) name = path.basename(path.dirname(folder));
      if (s.depth === 0) name = 'Default';
      list.push({
        launcher: s.launcher, name, path: file, modified: stat.mtimeMs,
        keybinds: lines.filter((l) => l.startsWith('key_')).length,
        renderDistance: get('renderDistance'), sensitivity: get('mouseSensitivity'), fov: get('fov'),
      });
    }
  }
  return list.sort((a, b) => b.modified - a.modified);
}

/**
 * Copies settings into the profile folder. `what` = { keybinds, settings }; both = the whole file.
 * The profile's previous options.txt is kept as options.txt.orbit-backup.
 */
function importInto(targetDir, sourcePath, what = { keybinds: true, settings: true }) {
  if (!what.keybinds && !what.settings) throw new Error('Pick keybinds, settings or both');
  if (path.basename(sourcePath) !== 'options.txt' || !fs.existsSync(sourcePath)) throw new Error('Settings file not found');
  const src = fs.readFileSync(sourcePath, 'utf8').split(/\r?\n/).filter(Boolean);
  const dest = path.join(targetDir, 'options.txt');
  fs.mkdirSync(targetDir, { recursive: true });
  const old = fs.existsSync(dest) ? fs.readFileSync(dest, 'utf8').split(/\r?\n/).filter(Boolean) : [];
  if (old.length) fs.copyFileSync(dest, dest + '.orbit-backup');

  const isKey = (l) => l.startsWith('key_');
  const take = (l) => (isKey(l) ? what.keybinds : what.settings);
  const map = new Map();
  for (const l of old) { const i = l.indexOf(':'); if (i > 0) map.set(l.slice(0, i), l); }
  let changed = 0;
  for (const l of src) {
    const i = l.indexOf(':');
    if (i <= 0 || !take(l)) continue;
    const k = l.slice(0, i);
    if (k === 'version' && old.length) continue; // keep the profile's own data version
    if (map.get(k) !== l) changed++;
    map.set(k, l);
  }
  fs.writeFileSync(dest, [...map.values()].join('\n') + '\n');
  return { changed, file: dest };
}

module.exports = { sources, importInto };
