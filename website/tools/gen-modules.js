// Builds website/modules.json (the mod browser on orbit-client.com) straight from the Orbit mod's source code,
// so the website always lists exactly the mods the client has. Run:  node website/tools/gen-modules.js
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const MOD_DIR = path.join(ROOT, 'client-mod', 'shared', 'src', 'main', 'java', 'dev', 'orbit', 'client', 'module');
const files = fs.readdirSync(MOD_DIR).filter((f) => f.endsWith('.java'));
const src = Object.fromEntries(files.map((f) => [f, fs.readFileSync(path.join(MOD_DIR, f), 'utf8')]));

// String[] constants (Mode options like THEMES / ACCENTS), from every Java file of the mod
const JAVA_ROOT = path.join(ROOT, 'client-mod', 'shared', 'src', 'main', 'java');
const consts = {};
(function walk(d) {
  for (const f of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, f.name);
    if (f.isDirectory()) walk(p);
    else if (f.name.endsWith('.java')) {
      const t = fs.readFileSync(p, 'utf8');
      for (const m of t.matchAll(/String\[\]\s+(\w+)\s*=\s*(?:new String\[\]\s*)?\{([^}]*)\}/g)) consts[m[1]] = [...m[2].matchAll(/"((?:[^"\\]|\\.)*)"/g)].map((x) => x[1]);
    }
  }
})(JAVA_ROOT);

/** split a Java argument list at top-level commas */
function args(s) {
  const out = []; let depth = 0, q = false, cur = '';
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (q) { cur += c; if (c === '\\') cur += s[++i]; else if (c === '"') q = false; continue; }
    if (c === '"') q = true;
    if ('([{'.includes(c)) depth++;
    if (')]}'.includes(c)) depth--;
    if (c === ',' && depth === 0) { out.push(cur.trim()); cur = ''; } else cur += c;
  }
  if (cur.trim()) out.push(cur.trim());
  return out;
}
const str = (a) => (/^"((?:[^"\\]|\\.)*)"$/.exec(a || '') || [])[1]?.replace(/\\"/g, '"');
const num = (a) => (/^-?[\d.]+[dfDF]?$/.test(a || '') ? parseFloat(a) : null);
/** the settings a class adds (Bool / Slider / Mode / Color), in menu order */
function settings(body) {
  const out = [];
  for (const m of body.matchAll(/add\(new Setting\.(Bool|Slider|Mode|Color)\(((?:[^;"]|"(?:[^"\\]|\\.)*")*?)\)\)/g)) {
    const a = args(m[2]);
    const s = { type: m[1].toLowerCase(), id: str(a[0]), name: str(a[1]) };
    if (!s.id || !s.name) continue;
    if (s.type === 'bool') s.value = a[2] === 'true';
    else if (s.type === 'slider') Object.assign(s, { value: num(a[2]), min: num(a[3]), max: num(a[4]), step: num(a[5]) || 1, unit: str(a[6]) || '' });
    else if (s.type === 'color') { const h = /0x([0-9a-f]{8})/i.exec(a[2] || ''); s.value = h ? '#' + h[1].slice(2) : '#ffffff'; }
    else {
      let opts = [];
      for (const x of a.slice(3)) { const v = str(x); if (v !== undefined) opts.push(v); else if (consts[x.split('.').pop()]) opts.push(...consts[x.split('.').pop()]); }
      s.options = opts;
      s.value = str(a[2]) ?? opts[0];
      if (!opts.includes(s.value)) opts.unshift(s.value);
    }
    if (s.type === 'slider' && (s.value === null || s.min === null || s.max === null)) continue;
    out.push(s);
  }
  return out;
}
const HUD_COMMON = settings(src['HudModule.java'] || '');

// every module class: its id, name, description, default category, whether it starts on, and its settings
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
    const flags = sup[4].match(/\b(true|false)\b/g) || [];
    const toggle = s.hud || flags[1] !== 'false';
    const opts = (s.hud ? HUD_COMMON : []).concat(settings(body));
    classes[s.name] = { id: sup[1], name: sup[2], desc: sup[3].replace(/\\"/g, '"'), category: s.hud ? 'HUD' : cat || 'CLIENT',
      on: !toggle || flags[0] === 'true', toggle,
      settings: (body.match(/add\(new Setting\.(?!Action)/g) || []).length + (s.hud ? HUD_COMMON.length : 0), opts };
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
