// Auto-updater: checks the newest GitHub release of the Orbit repo (Settings → About → Update repository,
// e.g. "xTrustQ/Orbit-Client"), downloads its Orbit.exe in the background and swaps it in place of the
// running portable exe — silently, when the launcher is closed (or on the next start, whichever comes first).
//
// Publishing an update = make a GitHub release with a tag like v1.3.1 (the release workflow builds and uploads
// Orbit.exe). The tag must be higher than the version the players have.
const { app } = require('electron');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const REPO = /^[\w.-]+\/[\w.-]+$/;
const parse = (v) => String(v || '').replace(/^v/i, '').split(/[.-]/).slice(0, 3).map((n) => parseInt(n, 10) || 0);
function newer(a, b) { const x = parse(a), y = parse(b); for (let i = 0; i < 3; i++) if (x[i] !== y[i]) return x[i] > y[i]; return false; }

const STAGE = () => path.join(app.getPath('userData'), 'update');
const STAGED_INFO = () => path.join(STAGE(), 'staged.json');
let latest = null;      // last check result
let downloading = null; // promise while a download runs

async function check(repo) {
  if (!REPO.test(repo || '')) throw new Error('Set the update repository first (owner/name)');
  const r = await fetch(`https://api.github.com/repos/${repo}/releases/latest`, { headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'Orbit-Launcher' } });
  if (r.status === 404) throw new Error('No releases found in ' + repo);
  if (!r.ok) throw new Error('GitHub answered ' + r.status);
  const rel = await r.json();
  // installed Orbit updates with the setup; the portable Orbit.exe swaps itself for the new Orbit.exe
  const assets = rel.assets || [];
  const asset = portable()
    ? assets.find((a) => /^orbit\.exe$/i.test(a.name)) || assets.find((a) => /^orbit(?!.*setup).*\.exe$/i.test(a.name))
    : assets.find((a) => /^orbit-setup\.exe$/i.test(a.name));
  latest = {
    current: app.getVersion(), version: (rel.tag_name || '').replace(/^v/i, ''), name: rel.name || rel.tag_name,
    notes: (rel.body || '').slice(0, 2000), url: rel.html_url, asset: asset ? { name: asset.name, size: asset.size, url: asset.browser_download_url } : null,
  };
  latest.available = !!asset && newer(latest.version, latest.current);
  const st = staged();
  latest.ready = !!st && st.version === latest.version;
  return latest;
}

/** where the portable exe really lives (electron-builder's portable target unpacks to temp and tells us here) */
const exePath = () => process.env.PORTABLE_EXECUTABLE_FILE || process.execPath;
/** true for the single-file Orbit.exe, false for Orbit installed with Orbit-Setup.exe */
const portable = () => !!process.env.PORTABLE_EXECUTABLE_FILE;

/** the downloaded update waiting to be installed, if it is newer than what runs now */
function staged() {
  try {
    const s = JSON.parse(fs.readFileSync(STAGED_INFO(), 'utf8'));
    if (newer(s.version, app.getVersion()) && fs.existsSync(s.file) && fs.statSync(s.file).size === s.size) return s;
  } catch { /* none */ }
  return null;
}

/** downloads the update next to the launcher's data (once); resolves with the staged info */
function download(progress) {
  if (!latest?.available) return Promise.reject(new Error('No update to download'));
  const st = staged();
  if (st && st.version === latest.version) return Promise.resolve(st);
  if (downloading) return downloading;
  downloading = (async () => {
    fs.rmSync(STAGE(), { recursive: true, force: true });
    fs.mkdirSync(STAGE(), { recursive: true });
    const file = path.join(STAGE(), portable() ? `Orbit-${latest.version}.exe` : `Orbit-Setup-${latest.version}.exe`);
    const r = await fetch(latest.asset.url, { headers: { 'User-Agent': 'Orbit-Launcher' } });
    if (!r.ok || !r.body) throw new Error('Download failed (' + r.status + ')');
    const total = +r.headers.get('content-length') || latest.asset.size || 0;
    const out = fs.createWriteStream(file + '.part');
    let done = 0;
    for await (const chunk of r.body) { done += chunk.length; if (!out.write(chunk)) await new Promise((res) => out.once('drain', res)); progress?.(done, total); }
    await new Promise((res, rej) => out.end((e) => (e ? rej(e) : res())));
    const size = fs.statSync(file + '.part').size;
    if (total && size !== total) throw new Error('Download was incomplete');
    fs.renameSync(file + '.part', file);
    const info = { version: latest.version, file, size };
    fs.writeFileSync(STAGED_INFO(), JSON.stringify(info));
    return info;
  })().finally(() => { downloading = null; });
  return downloading;
}

/** swaps the staged exe in once this process has exited; restart = start the new version afterwards */
function applyStaged(restart) {
  const st = staged();
  if (!st || !app.isPackaged) return false;
  // counts tries, so a file that can't be replaced (e.g. no write access) never traps the launcher in a loop
  st.attempts = (st.attempts || 0) + 1;
  try { fs.writeFileSync(STAGED_INFO(), JSON.stringify(st)); } catch { /* ignore */ }
  const target = exePath();
  const bat = path.join(STAGE(), 'update.cmd');
  if (!portable() || /setup/i.test(path.basename(st.file))) {
    // installed Orbit: run the new setup silently once we've exited (--force-run starts Orbit again afterwards)
    fs.writeFileSync(bat, [
      '@echo off',
      'timeout /t 2 /nobreak >nul',
      `"${st.file}" /S${restart ? ' --force-run' : ''}`,
      `del /q "${st.file}" "${STAGED_INFO()}" >nul 2>&1`,
    ].join('\r\n'));
    spawn('cmd.exe', ['/c', bat], { detached: true, stdio: 'ignore', windowsHide: true }).unref();
    return true;
  }
  fs.writeFileSync(bat, [
    '@echo off',
    'set n=0',
    ':retry',
    'timeout /t 1 /nobreak >nul',
    `copy /y "${st.file}" "${target}" >nul 2>&1`,
    'if errorlevel 1 (',
    '  set /a n+=1',
    '  if %n% lss 60 goto retry',
    '  exit /b 1',
    ')',
    `del /q "${st.file}" "${STAGED_INFO()}" >nul 2>&1`,
    ...(restart ? [`start "" "${target}"`] : []),
  ].join('\r\n'));
  spawn('cmd.exe', ['/c', bat], { detached: true, stdio: 'ignore', windowsHide: true }).unref();
  return true;
}

/** "Update now" button: download if needed, then restart into the new version */
async function install(progress) {
  if (!app.isPackaged) throw new Error('Updates only install in the built Orbit.exe');
  await download(progress);
  if (!applyStaged(true)) throw new Error('Could not install the update');
  setTimeout(() => app.quit(), 300);
  return true;
}

module.exports = { check, download, install, applyStaged, staged, newer, last: () => latest };
