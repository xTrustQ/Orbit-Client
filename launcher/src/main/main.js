const { app, BrowserWindow, ipcMain, dialog, shell, nativeTheme } = require('electron');
const fs = require('fs');
const path = require('path');

app.setName('OrbitClient');
const { PATHS, Store } = require('./util');
const auth = require('./auth');
const instances = require('./instances');
const content = require('./content');
const cosmetics = require('./cosmetics');
const launcher = require('./launch');
const spotify = require('./spotify');
const store = require('./store');
const discord = require('./discord');

// defaults baked into the build (package.json → "orbit"): the shared server and the GitHub repo for updates
const BUILT = (() => { try { return require('../../package.json').orbit || {}; } catch { return {}; } })();
const settings = new Store('settings', {
  theme: 'nebula', font: 'clean', animations: true, memory: null, optimizedJvm: true, javaPath: '', jvmArgs: '',
  closeOnLaunch: false, curseforgeKey: '', performancePack: true, standardMods: true, resolution: {}, lastInstance: null, background: 'stars',
  discordEnabled: true, discordAppId: '', syncServer: '', apiServer: '', updateRepo: '', autoUpdate: true,
});
// a build that ships with a server / repo fills them in for players who haven't set their own
for (const k of ['syncServer', 'updateRepo']) if (!settings.get(k) && BUILT[k]) settings.set(k, BUILT[k]);
// Clean is the default typography now: switch everyone over once (they can still pick Orbit or Pixel afterwards)
if (!settings.get('cleanFontDefault')) { settings.set('font', 'clean'); settings.set('cleanFontDefault', true); }
const updater = require('./updater');

// Discord Rich Presence: "Playing Orbit Client" (needs your Discord application ID in Settings).
function applyDiscord() { discord.configure(settings.get('discordEnabled') !== false ? settings.get('discordAppId') : null); }
const idlePresence = () => discord.setActivity({ details: 'In the launcher', state: 'Getting ready for liftoff' });
applyDiscord();
idlePresence();

let win;
function createWindow() {
  win = new BrowserWindow({
    width: 1280, height: 800, minWidth: 1020, minHeight: 640, frame: false, show: false,
    backgroundColor: '#040b09', titleBarStyle: 'hidden',
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: false },
  });
  win.loadFile(path.join(__dirname, '..', 'renderer', 'index.html'));
  win.once('ready-to-show', () => win.show());
  win.on('maximize', () => emit('win:maximized', true));
  win.on('unmaximize', () => emit('win:maximized', false));
  // Dev aid: ORBIT_SNAPSHOT=<dir> captures every page to PNG and exits (used for UI checks).
  if (process.env.ORBIT_SNAPSHOT) {
    win.webContents.on('console-message', (_e, level, msg, line, src) => level >= 2 && console.log('[renderer]', msg, src + ':' + line));
    win.webContents.once('did-finish-load', async () => {
      const out = process.env.ORBIT_SNAPSHOT;
      fs.mkdirSync(out, { recursive: true });
      const wait = (ms) => new Promise((r) => setTimeout(r, ms));
      if (process.env.ORBIT_EVAL) { await wait(2500); console.log('[eval]', JSON.stringify(await win.webContents.executeJavaScript(process.env.ORBIT_EVAL))); }
      for (const page of (process.env.ORBIT_PAGES || 'play,profiles,mods,skins,capes,settings').split(',')) {
        await win.webContents.executeJavaScript(`document.querySelector('[data-page="${page}"]')?.click()`);
        await wait(+process.env.ORBIT_WAIT || 3500);
        fs.writeFileSync(path.join(out, page + '.png'), (await win.webContents.capturePage()).toPNG());
      }
      app.quit();
    });
  }
  win.webContents.setWindowOpenHandler(({ url }) => { if (/^https:\/\//.test(url)) shell.openExternal(url); return { action: 'deny' }; });
}

function emit(channel, payload) { if (win && !win.isDestroyed()) win.webContents.send(channel, payload); }

/** Registers an IPC handler that returns {ok,data} or {ok:false,error} so the UI can show clean errors. */
function handle(channel, fn) {
  ipcMain.handle(channel, async (_e, ...args) => {
    try { return { ok: true, data: await fn(...args) }; } catch (err) { console.error(channel, err); return { ok: false, error: err.message || String(err), code: err.code }; }
  });
}
const task = (id) => (label, done = 0, total = 0) => emit('task:progress', { id, label, done, total });

// window
handle('win:minimize', () => win.minimize());
handle('win:maximize', () => (win.isMaximized() ? win.unmaximize() : win.maximize()));
handle('win:close', () => win.close());
handle('app:info', () => ({ version: app.getVersion(), dataDir: PATHS.root, novaVersions: instances.novaVersions(), platform: process.platform }));
handle('app:openPath', (p) => shell.openPath(p || PATHS.root));
handle('app:openExternal', (url) => /^https:\/\//.test(url) && shell.openExternal(url));

// settings
handle('settings:get', () => ({ ...settings.all(), defaultMemory: launcher.defaultMemory(), totalMemory: Math.floor(require('os').totalmem() / 1048576) }));
handle('settings:set', (patch) => { settings.patch(patch); applyDiscord(); return settings.all(); });
handle('settings:pickJava', async () => {
  const r = await dialog.showOpenDialog(win, { title: 'Select java / javaw executable', properties: ['openFile'], filters: process.platform === 'win32' ? [{ name: 'Java', extensions: ['exe'] }] : [] });
  return r.canceled ? null : r.filePaths[0];
});

// updates (GitHub releases)
handle('update:check', () => updater.check(settings.get('updateRepo')));
handle('update:install', () => updater.install((done, total) => emit('update:progress', { done, total })));

// accounts
handle('accounts:list', () => auth.list());
handle('accounts:addMicrosoft', () => auth.addMicrosoft(win));
handle('accounts:addOffline', (name) => auth.addOffline(name));
handle('accounts:remove', (id) => auth.remove(id));
handle('accounts:select', (id) => auth.select(id));

// instances
handle('instances:list', () => instances.list().map((i) => ({ ...i, running: launcher.runningIds().includes(i.id) })));
handle('instances:get', (id) => instances.get(id));
handle('instances:settingsSources', () => require('./settingsImport').sources());
handle('instances:importSettings', (id, file, what) => require('./settingsImport').importInto(instances.dirOf(id), file, what));
handle('instances:versions', () => instances.mcVersions());
handle('instances:loaderVersions', (loader, mc) => instances.loaderVersions(loader, mc));
handle('instances:create', async (opts) => {
  const inst = instances.create(opts);
  const t = task('create-' + inst.id);
  if (opts.performancePack !== false && ['fabric', 'quilt'].includes(inst.loader)) {
    await instances.ensureLoaderProfile(inst).catch(() => {});
    await content.installPerformancePack(inst.id, t);
  }
  if (settings.get('standardMods') !== false && ['fabric', 'quilt'].includes(inst.loader)) {
    await instances.ensureLoaderProfile(inst).catch(() => {});
    await content.installStandardMods(inst.id, t);
  }
  // Orbit needs Fabric API; make sure it's there even when the FPS pack was skipped.
  if (instances.syncOrbitMod(inst) && !instances.contents(inst.id, 'mod').some((c) => /fabric-api/i.test(c.file))) {
    await content.install({ source: 'modrinth', projectId: 'P7dR8mSH', type: 'mod', instanceId: inst.id, meta: { title: 'Fabric API' } }, t).catch(() => {});
  }
  emit('task:progress', { id: 'create-' + inst.id, done: 1, total: 1, finished: true });
  return inst;
});
handle('instances:update', (id, patch) => instances.update(id, patch));
handle('instances:remove', (id) => instances.remove(id));
handle('instances:duplicate', (id) => instances.duplicate(id));
handle('instances:openFolder', (id) => shell.openPath(instances.dirOf(id)));
handle('instances:contents', (id, kind) => instances.contents(id, kind));
handle('instances:toggleContent', (id, kind, file, enabled) => instances.toggleContent(id, kind, file, enabled));
handle('instances:removeContent', (id, kind, file) => instances.removeContent(id, kind, file));
handle('instances:repair', (id) => content.repairMods(id, task('repair-' + id)));
handle('instances:performancePack', (id) => content.installPerformancePack(id, task('perf-' + id)));
handle('instances:performanceProfile', (id, preset) => {
  if (launcher.runningIds().includes(id)) throw new Error('Close Minecraft first — it would overwrite the settings when it exits');
  const r = require('./performance').apply(instances.dirOf(id), preset);
  instances.update(id, { performanceProfile: preset });
  return r;
});
handle('instances:addLocalFiles', async (id, kind) => {
  const r = await dialog.showOpenDialog(win, { properties: ['openFile', 'multiSelections'], filters: [{ name: 'Content', extensions: kind === 'mod' ? ['jar'] : ['zip'] }] });
  for (const f of r.filePaths) fs.copyFileSync(f, path.join(instances.dirOf(id), instances.CONTENT_DIRS[kind], path.basename(f)));
  return instances.contents(id, kind);
});

// content browser
handle('content:search', (q) => content.search(q));
handle('content:categories', (source, type) => content.categories(source, type));
handle('content:install', (req) => content.install(req, task('install-' + req.projectId)));
handle('content:installModpack', (req) => content.installModpack(req, task('install-' + req.projectId)));

// game
handle('game:launch', async (id) => {
  settings.set('lastInstance', id);
  const inst = instances.get(id);
  const started = Date.now();
  await launcher.launch(id, (ch, p) => {
    emit(ch, p);
    if (ch === 'game:state' && (p.state === 'starting' || p.state === 'running')) discord.setActivity({ details: 'Playing Minecraft ' + inst.mcVersion, state: inst.name, start: started });
    if (ch === 'game:state' && p.state === 'stopped') {
      idlePresence();
      // the game was closed: bring the launcher back to the front
      if (win && !win.isDestroyed()) {
        if (win.isMinimized()) win.restore();
        win.show();
        win.setAlwaysOnTop(true);
        win.focus();
        setTimeout(() => { if (!win.isDestroyed()) win.setAlwaysOnTop(false); }, 400);
      }
    }
  });
  if (settings.get('closeOnLaunch')) win.minimize();
  return true;
});
handle('game:stop', (id) => launcher.stop(id));
handle('game:running', () => launcher.runningIds());

// store
handle('store:catalogue', () => store.catalogue());
handle('store:buyItem', (id) => store.buyItem(id));
handle('store:buyCoins', (id) => store.buyCoins(id));
handle('store:addItem', (item) => store.addItem(item));
handle('store:removeItem', (id) => store.removeItem(id));

// spotify (lyrics)
handle('spotify:status', () => spotify.status());
handle('spotify:connect', (clientId) => spotify.connect(clientId));
handle('spotify:disconnect', () => spotify.disconnect());

// skins
handle('skins:list', () => cosmetics.listSkins());
handle('skins:current', () => cosmetics.currentSkin());
handle('skins:pickFile', async () => {
  const r = await dialog.showOpenDialog(win, { properties: ['openFile'], filters: [{ name: 'Skin', extensions: ['png'] }] });
  if (r.canceled) return null;
  return { name: path.basename(r.filePaths[0], '.png'), data: 'data:image/png;base64,' + fs.readFileSync(r.filePaths[0]).toString('base64') };
});
handle('skins:save', (s) => cosmetics.saveSkin(s));
handle('skins:update', (id, patch) => cosmetics.updateSkin(id, patch));
handle('skins:delete', (id) => cosmetics.deleteSkin(id));
handle('skins:importUsername', (name) => cosmetics.importSkinFromUsername(name));
handle('skins:apply', (id) => cosmetics.applySkin(id));

// capes
handle('capes:mojang', () => cosmetics.mojangCapes());
handle('capes:setMojang', (id) => cosmetics.setMojangCape(id));
handle('capes:nova', () => cosmetics.novaCapes());
handle('capes:saveOrbit', (c) => cosmetics.saveOrbitCape(c));
handle('capes:deleteOrbit', (id) => cosmetics.deleteOrbitCape(id));
handle('capes:favoriteOrbit', (id, f) => cosmetics.favoriteOrbitCape(id, f));
handle('capes:selectOrbit', (id) => cosmetics.selectOrbitCape(id));
handle('capes:pickImage', async () => {
  const r = await dialog.showOpenDialog(win, { properties: ['openFile'], filters: [{ name: 'Image', extensions: ['png', 'jpg', 'jpeg', 'gif', 'webp'] }] });
  if (r.canceled) return null;
  const ext = path.extname(r.filePaths[0]).slice(1).replace('jpg', 'jpeg');
  return { name: path.basename(r.filePaths[0]).replace(/\.[^.]+$/, ''), data: `data:image/${ext};base64,` + fs.readFileSync(r.filePaths[0]).toString('base64') };
});

nativeTheme.themeSource = 'dark';
app.whenReady().then(() => {
  // an update was downloaded but not installed yet (e.g. the PC was shut down): install it now and restart
  const pending = settings.get('autoUpdate') !== false && updater.staged();
  if (pending && app.isPackaged && (pending.attempts || 0) < 2 && updater.applyStaged(true)) { updateApplied = true; return app.quit(); }
  createWindow();
  // in-game Accounts / Locker tabs talk to the launcher through this; tell the window when they change things
  require('./bridge').start((kind) => emit('bridge:changed', kind), () => win).catch(() => {});
  // silent updates: look for a new release a few seconds after start and every 30 minutes, download it in the
  // background, and install it when the launcher is closed
  const autoCheck = () => {
    if (settings.get('autoUpdate') === false || !settings.get('updateRepo')) return;
    updater.check(settings.get('updateRepo'))
      .then((u) => u.available && updater.download().then(() => emit('update:ready', updater.last())))
      .catch((e) => console.warn('update check:', e.message));
  };
  setTimeout(autoCheck, 4000);
  setInterval(autoCheck, 30 * 60 * 1000);
});
let updateApplied = false;
app.on('will-quit', () => {
  // close the launcher → the downloaded update replaces Orbit.exe; it's the new version next time you open it
  if (!updateApplied && settings.get('autoUpdate') !== false) updateApplied = updater.applyStaged(false);
});
app.on('window-all-closed', () => app.quit());
