// Picks the right Java runtime per Minecraft version and auto-downloads it from Adoptium.
const fs = require('fs');
const path = require('path');
const { execFile } = require('child_process');
const AdmZip = require('adm-zip');
const { PATHS, download } = require('./util');

const exe = process.platform === 'win32' ? 'javaw.exe' : 'java';

function findJavaIn(dir, depth = 0) {
  if (!fs.existsSync(dir) || depth > 4) return null;
  const candidate = path.join(dir, 'bin', exe);
  if (fs.existsSync(candidate)) return candidate;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.isDirectory()) { const f = findJavaIn(path.join(dir, e.name), depth + 1); if (f) return f; }
  }
  return null;
}

/** Ensures a managed JRE of the given major version exists; returns path to java executable. */
const pending = new Map();
function ensureJava(major, onProgress) {
  if (!pending.has(major)) pending.set(major, installJava(major, onProgress).finally(() => pending.delete(major)));
  return pending.get(major);
}

async function installJava(major, onProgress) {
  const dir = path.join(PATHS.java, String(major));
  const existing = findJavaIn(dir);
  if (existing) return existing;

  const os = { win32: 'windows', darwin: 'mac', linux: 'linux' }[process.platform];
  const arch = { x64: 'x64', arm64: 'aarch64', ia32: 'x32' }[process.arch] || 'x64';
  const url = `https://api.adoptium.net/v3/binary/latest/${major}/ga/${os}/${arch}/jre/hotspot/normal/eclipse`;
  const archive = path.join(PATHS.cache, `jre-${major}${os === 'windows' ? '.zip' : '.tar.gz'}`);
  const staging = `${dir}.extracting`;

  let lastErr;
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      fs.rmSync(archive, { force: true }); // never reuse a possibly broken archive
      await download(url, archive, { onProgress: (d, t) => onProgress?.(`Downloading Java ${major}`, d, t) });
      onProgress?.(`Extracting Java ${major}`, 0, 0);
      fs.rmSync(staging, { recursive: true, force: true });
      fs.mkdirSync(staging, { recursive: true });
      if (archive.endsWith('.zip')) new AdmZip(archive).extractAllTo(staging, true); // throws on a damaged zip
      else await new Promise((res, rej) => execFile('tar', ['-xzf', archive, '-C', staging], (e) => (e ? rej(e) : res())));
      if (!findJavaIn(staging)) throw new Error('Java archive did not contain a java executable');
      fs.rmSync(dir, { recursive: true, force: true });
      fs.renameSync(staging, dir);
      fs.rmSync(archive, { force: true });
      return findJavaIn(dir);
    } catch (e) {
      lastErr = e;
      fs.rmSync(staging, { recursive: true, force: true });
      onProgress?.(`Java download was damaged — retrying`, 0, 0);
    }
  }
  fs.rmSync(archive, { force: true });
  throw new Error(`Could not install Java ${major}: ${lastErr.message}`);
}

/** Java major version required by a Minecraft version JSON (falls back to heuristics). */
function requiredMajor(versionJson, mcVersion) {
  if (versionJson?.javaVersion?.majorVersion) return versionJson.javaVersion.majorVersion;
  const m = /^1\.(\d+)(?:\.(\d+))?/.exec(mcVersion);
  if (!m) return 21;
  const minor = +m[1], patch = +(m[2] || 0);
  if (minor > 20 || (minor === 20 && patch >= 5)) return 21;
  if (minor >= 17) return 17;
  return 8;
}

module.exports = { ensureJava, requiredMajor };
