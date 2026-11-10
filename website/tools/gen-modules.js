// Builds website/modules.json (the mod browser on orbit-client.com) straight from the Orbit mod's source code,
// so the website always lists exactly the mods the client has. Run:  node website/tools/gen-modules.js
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const MOD_DIR = path.join(ROOT, 'client-mod', 'shared', 'src', 'main', 'java', 'dev', 'orbit', 'client', 'module');
const files = fs.readdirSync(MOD_DIR).filter((f) => f.endsWith('.java'));
const src = Object.fromEntries(files.map((f) => [f, fs.readFileSync(path.join(MOD_DIR, f), 'utf8')]));

// every module class: its id, name, description, default category and how many settings it has
const classes = {};
for (const text of Object.values(src)) {
  const re = /class\s+(\w+)\s+extends\s+(Module|HudModule)\b/g;
  let m;
  const starts = [];
  while ((m = re.exec(text))) starts.push({ name: m[1], hud: m[2] === 'HudModule', at: m.index });
  starts.forEach((s, i) => {
    const body = text.slice(s.at, i + 1 < starts.length ? starts[i + 1].at : text.length);
    const sup = body.match(/super\(\s*"([\w-]+)"\s*,\s*"([^"]+)"\s*,\s*"((?:[^"\\]|\\.)*)"([^;]*)\);/);
    if (!sup) return;
    const cat = (sup[4].match(/Category\.(\w+)/) || [])[1];
    classes[s.name] = { id: sup[1], name: sup[2], desc: sup[3].replace(/\\"/g, '"'), category: s.hud ? 'HUD' : cat || 'CLIENT',
      settings: (body.match(/add\(new Setting\.(?!Action)/g) || []).length };
  });
}

// what's actually registered (and the category it's shown under) — ModuleManager decides
const mgr = src['ModuleManager.java'];
const out = [];
const seen = new Set();
const reg = /new\s+(?:\w+\.)?(\w+)\(\)(?:\s*,\s*Items\.\w+)?(?:\s*,\s*Category\.(\w+))?/g;
let r;
while ((r = reg.exec(mgr))) {
  const c = classes[r[1]];
  if (!c || seen.has(c.id)) continue;
  seen.add(c.id);
  out.push({ ...c, category: r[2] || c.category });
}
// classes ModuleManager uses another way (e.g. HUD elements kept in fields)
for (const [cls, c] of Object.entries(classes)) if (!seen.has(c.id) && new RegExp(`\\b${cls}\\b`).test(mgr)) { seen.add(c.id); out.push(c); }

const order = ['COMBAT', 'HUD', 'RENDER', 'PLAYER', 'CLIENT'];
out.sort((a, b) => order.indexOf(a.category) - order.indexOf(b.category) || a.name.localeCompare(b.name));
fs.writeFileSync(path.join(ROOT, 'website', 'modules.json'), JSON.stringify(out, null, 1));
const counts = {};
for (const m of out) counts[m.category] = (counts[m.category] || 0) + 1;
console.log(out.length, 'modules', JSON.stringify(counts));
