// Game launching (via minecraft-launcher-core) with tuned JVM flags and per-instance game dirs.
const os = require('os');
const path = require('path');
const { Client } = require('minecraft-launcher-core');
const { PATHS, Store } = require('./util');
const instances = require('./instances');
const auth = require('./auth');
const java = require('./java');
const content = require('./content');

const settings = new Store('settings', {});
const running = new Map(); // instanceId -> { client, startedAt, pid }

/** JVM flags tuned for client-side Minecraft (short GC pauses = fewer stutters). */
const OPTIMIZED_JVM = [
  '-XX:+UnlockExperimentalVMOptions', '-XX:+UseG1GC', '-XX:G1NewSizePercent=20', '-XX:G1ReservePercent=20',
  '-XX:MaxGCPauseMillis=50', '-XX:G1HeapRegionSize=32M', '-XX:+ParallelRefProcEnabled', '-XX:+DisableExplicitGC',
  '-XX:+UseStringDeduplication', '-Dsun.rmi.dgc.server.gcInterval=2147483646',
];

function defaultMemory() {
  const totalGb = os.totalmem() / 1024 ** 3;
  return totalGb >= 16 ? 6144 : totalGb >= 8 ? 4096 : 2048;
}

const starting = new Set();
async function launch(instanceId, emit) {
  if (running.has(instanceId)) throw new Error('This profile is already running');
  if (starting.has(instanceId)) throw new Error('This profile is already launching — hang on');
  starting.add(instanceId);
  try { return await doLaunch(instanceId, emit); } finally { starting.delete(instanceId); }
}

async function doLaunch(instanceId, emit) {
  const inst = instances.get(instanceId);
  const acc = auth.selected();
  if (!acc) throw new Error('Add an account first (top-right).');
  const progress = (label, done = 0, total = 0) => emit('game:progress', { instanceId, label, done, total });

  progress('Signing in');
  const a = await auth.ensureToken(acc.id);

  progress('Preparing version');
  const vjson = await instances.versionJson(inst.mcVersion);
  let javaPath = settings.get('javaPath') || (await java.ensureJava(java.requiredMajor(vjson, inst.mcVersion), progress));
  // a custom java.exe would open a black console window now that the game runs on its own: use javaw.exe next to it
  if (process.platform === 'win32' && /java\.exe$/i.test(javaPath)) {
    const w = javaPath.replace(/java\.exe$/i, 'javaw.exe');
    if (require('fs').existsSync(w)) javaPath = w;
  }
  const custom = await instances.ensureLoaderProfile(inst);
  instances.syncOrbitMod(inst);
  try { await require('./store').catalogue(); } catch { /* keeps owner unlocks in store.json current for the game */ }
  try {
    const fixed = await content.repairMods(inst.id, progress);
    if (fixed.length) emit('game:log', { instanceId, line: '[Orbit] Repaired damaged mods: ' + fixed.join(', ') });
  } catch (e) { emit('game:log', { instanceId, line: '[Orbit] Mod check skipped: ' + e.message }); }

  const memory = inst.memory || settings.get('memory') || defaultMemory();
  const optimized = settings.get('optimizedJvm') !== false;
  const gameDir = instances.dirOf(inst.id);
  const bridgeUrl = await require('./bridge').start().catch(() => null);
  const extra = [
    ...(optimized ? OPTIMIZED_JVM : []),
    ...(bridgeUrl ? [`-Dorbit.bridge=${bridgeUrl}`] : []),
    `-Dorbit.cosmetics=${PATHS.cosmetics}`,
    '-Dorbit.launcher=1',
    `-Dorbit.spotify=${path.join(PATHS.root, 'spotify.json')}`,
    `-Dorbit.store=${path.join(PATHS.root, 'store.json')}`,
    ...(settings.get('syncServer') ? [`-Dorbit.sync=${settings.get('syncServer')}`] : []),
    ...(settings.get('apiServer') ? [`-Dorbit.api=${settings.get('apiServer')}`] : []),
    ...String(inst.jvmArgs || settings.get('jvmArgs') || '').split(/\s+/).filter(Boolean),
  ];
  const res = settings.get('resolution') || {};

  const client = new Client();
  const opts = {
    root: PATHS.minecraft,
    authorization: {
      access_token: a.token, client_token: a.id, uuid: a.id, name: a.name, user_properties: '{}',
      meta: { type: a.type === 'msa' ? 'msa' : 'mojang', demo: false },
    },
    version: { number: inst.mcVersion, type: vjson.type || 'release', ...(custom ? { custom } : {}) },
    memory: { max: `${memory}M`, min: `${Math.min(1024, memory)}M` },
    javaPath,
    customArgs: extra,
    // detached: the game is its own process, so closing the launcher doesn't close Minecraft
    overrides: { gameDirectory: gameDir, cwd: gameDir, detached: true, maxSockets: 16 },
    ...(res.width && res.height ? { window: { width: res.width, height: res.height, fullscreen: !!res.fullscreen } } : {}),
  };

  client.on('progress', (e) => progress(`Downloading ${e.type}`, e.task, e.total));
  client.on('download-status', (e) => e.type === 'version-jar' && progress('Downloading game', e.current, e.total));
  client.on('debug', (line) => emit('game:log', { instanceId, line: String(line) }));
  client.on('data', (line) => {
    const s = String(line);
    emit('game:log', { instanceId, line: s });
    if (/Setting user:|Backend library:|LWJGL Version/.test(s)) emit('game:state', { instanceId, state: 'running' });
  });
  client.on('close', (code) => {
    const r = running.get(instanceId);
    running.delete(instanceId);
    const cur = instances.get(instanceId);
    instances.save({ ...cur, playtime: (cur.playtime || 0) + (r ? Date.now() - r.startedAt : 0) });
    emit('game:state', { instanceId, state: 'stopped', code });
  });

  progress('Launching');
  const proc = await client.launch(opts);
  if (!proc) throw new Error('Launch failed — check the console for details');
  running.set(instanceId, { client, proc, startedAt: Date.now() });
  instances.save({ ...instances.get(instanceId), lastPlayed: Date.now() });
  emit('game:state', { instanceId, state: 'starting', pid: proc.pid });
  return true;
}

function stop(instanceId) {
  const r = running.get(instanceId);
  if (r?.proc) r.proc.kill();
  return true;
}
const runningIds = () => [...running.keys()];

module.exports = { launch, stop, runningIds, defaultMemory };
