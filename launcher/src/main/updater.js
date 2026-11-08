// Auto-updater: checks the newest GitHub release of the Orbit repo (Settings → About → Update repository,
// e.g. "yourfriend/orbit"), downloads its Orbit.exe and swaps it in place of the running portable exe.
//
// Publishing an update = push a tag like v1.3.0 (the release workflow builds and uploads Orbit.exe),
// or create a GitHub release by hand and attach Orbit.exe. The tag must be higher than package.json's version.
const { app } = require('electron');
const fs = require('fs');
const path = require('path');
const os = require('os');
const { spawn } = require('child_process');

const REPO = /^[\w.-]+\/[\w.-]+$/;
const parse = (v) => String(v || '').replace(/^v/i, '').split(/[.-]/).slice(0, 3).map((n) => parseInt(n, 10) || 0);
function newer(a, b) { const x = parse(a), y = parse(b); for (let i = 0; i < 3; i++) if (x[i] !== y[i]) return x[i] > y[i]; return false; }

let latest = null; // last check result

async function check(repo) {
  if (!REPO.test(repo || '')) throw new Error('Set the update repository first (owner/name)');
  const r = await fetch(`https://api.github.com/repos/${repo}/releases/latest`, { headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'Orbit-Launcher' } });
  if (r.status === 404) throw new Error('No releases found in ' + repo);
  if (!r.ok) throw new Error('GitHub answered ' + r.status);
  const rel = await r.json();
  const asset = (rel.assets || []).find((a) => /^orbit.*\.exe$/i.test(a.name));
  latest = {
    current: app.getVersion(), version: (rel.tag_name || '').replace(/^v/i, ''), name: rel.name || rel.tag_name,
    notes: (rel.body || '').slice(0, 2000), url: rel.html_url, asset: asset ? { name: asset.name, size: asset.size, url: asset.browser_download_url } : null,
  };
  latest.available = !!asset && newer(latest.version, latest.current);
  return latest;
}

/** where the portable exe really lives (electron-builder's portable target unpacks to temp and tells us here) */
const exePath = () => process.env.PORTABLE_EXECUTABLE_FILE || process.execPath;

async function install(progress) {
  if (!latest?.available) throw new Error('No update to install');
  if (!app.isPackaged) throw new Error('Updates only install in the built Orbit.exe');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'orbit-update-'));
  const file = path.join(dir, 'Orbit.exe');
  const r = await fetch(latest.asset.url, { headers: { 'User-Agent': 'Orbit-Launcher' } });
  if (!r.ok || !r.body) throw new Error('Download failed (' + r.status + ')');
  const total = +r.headers.get('content-length') || latest.asset.size || 0;
  const out = fs.createWriteStream(file);
  let done = 0;
  for await (const chunk of r.body) { done += chunk.length; out.write(chunk); progress?.(done, total); }
  await new Promise((res, rej) => out.end((e) => (e ? rej(e) : res())));
  if (total && fs.statSync(file).size !== total) throw new Error('Download was incomplete');

  // a tiny script waits for us to exit, replaces the exe, and starts the new one
  const target = exePath();
  const bat = path.join(dir, 'update.cmd');
  fs.writeFileSync(bat, [
    '@echo off',
    'set n=0',
    ':retry',
    'timeout /t 1 /nobreak >nul',
    `move /y "${file}" "${target}" >nul 2>&1`,
    'if errorlevel 1 (',
    '  set /a n+=1',
    '  if %n% lss 30 goto retry',
    '  exit /b 1',
    ')',
    `start "" "${target}"`,
    `rmdir /s /q "${dir}"`,
  ].join('\r\n'));
  spawn('cmd.exe', ['/c', bat], { detached: true, stdio: 'ignore', windowsHide: true }).unref();
  setTimeout(() => app.quit(), 300);
  return true;
}

module.exports = { check, install, newer, last: () => latest };
