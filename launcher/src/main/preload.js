const { contextBridge, ipcRenderer } = require('electron');

const call = async (channel, ...args) => {
  const r = await ipcRenderer.invoke(channel, ...args);
  if (!r.ok) { const e = new Error(r.error); e.code = r.code; throw e; }
  return r.data;
};

// Every main-process handler is reachable as nova.<group>.<name>(...)
const groups = {
  win: ['minimize', 'maximize', 'close'],
  app: ['info', 'openPath', 'openExternal'],
  settings: ['get', 'set', 'pickJava'],
  accounts: ['list', 'addMicrosoft', 'addOffline', 'remove', 'select'],
  instances: ['list', 'get', 'versions', 'loaderVersions', 'create', 'update', 'remove', 'duplicate', 'openFolder', 'contents', 'toggleContent', 'removeContent', 'performancePack', 'repair', 'addLocalFiles', 'settingsSources', 'importSettings'],
  content: ['search', 'categories', 'install', 'installModpack'],
  game: ['launch', 'stop', 'running'],
  skins: ['current', 'list','pickFile', 'save', 'update', 'delete', 'importUsername', 'apply'],
  spotify: ['status', 'connect', 'disconnect'],
  store: ['catalogue', 'buyItem', 'buyCoins', 'addItem', 'removeItem'],
  update: ['check', 'install'],
  capes: ['mojang', 'setMojang', 'nova', 'saveOrbit', 'deleteOrbit', 'favoriteOrbit', 'selectOrbit', 'pickImage'],
};
const api = {};
for (const [g, names] of Object.entries(groups)) {
  api[g] = {};
  for (const n of names) api[g][n] = (...a) => call(`${g}:${n}`, ...a);
}
const allowed = ['game:progress', 'game:log', 'game:state', 'task:progress', 'win:maximized', 'bridge:changed', 'update:available', 'update:progress'];
// (update:* events come from updater.js via main.js)
api.on = (channel, cb) => {
  if (!allowed.includes(channel)) return () => {};
  const l = (_e, p) => cb(p);
  ipcRenderer.on(channel, l);
  return () => ipcRenderer.removeListener(channel, l);
};
contextBridge.exposeInMainWorld('nova', api);
