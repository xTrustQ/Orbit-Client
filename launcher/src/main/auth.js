// Microsoft -> Xbox Live -> XSTS -> Minecraft authentication + multi-account storage.
const { BrowserWindow, safeStorage, session, shell } = require('electron');
const crypto = require('crypto');
const { Store, fetchJson } = require('./util');

const CLIENT_ID = '00000000402b5328';
const REDIRECT = 'https://login.live.com/oauth20_desktop.srf';
const SCOPE = 'service::user.auth.xboxlive.com::MBI_SSL';

const store = new Store('accounts', { accounts: [], selected: null });

// ---- token encryption at rest (OS keychain / DPAPI via Electron safeStorage) ----
const enc = (s) => (s && safeStorage.isEncryptionAvailable() ? 'enc:' + safeStorage.encryptString(s).toString('base64') : s);
const dec = (s) => (s && s.startsWith('enc:') ? safeStorage.decryptString(Buffer.from(s.slice(4), 'base64')) : s);

function publicAccount(a) {
  return { id: a.id, name: a.name, type: a.type, selected: store.get('selected') === a.id };
}
function list() { return store.get('accounts').map(publicAccount); }
function getRaw(id) { return store.get('accounts').find((a) => a.id === id); }
function selected() {
  const all = store.get('accounts');
  return all.find((a) => a.id === store.get('selected')) || all[0] || null;
}

function saveAccount(acc) {
  const all = store.get('accounts').filter((a) => a.id !== acc.id);
  all.push(acc);
  store.patch({ accounts: all, selected: acc.id });
}

/** Opens the Microsoft sign-in window and resolves with an OAuth code. */
function promptForCode(parent) {
  return new Promise((resolve, reject) => {
    const partition = 'msauth-' + crypto.randomBytes(4).toString('hex'); // fresh cookies => pick any account
    const win = new BrowserWindow({
      width: 500, height: 650, parent, modal: !!parent, autoHideMenuBar: true, title: 'Sign in with Microsoft',
      backgroundColor: '#ffffff', webPreferences: { partition },
    });
    let finished = false;
    const check = (url) => {
      if (!url.startsWith(REDIRECT)) return;
      const q = new URL(url).searchParams;
      finished = true;
      win.close();
      session.fromPartition(partition).clearStorageData().catch(() => {});
      if (q.get('code')) resolve(q.get('code'));
      else reject(new Error(q.get('error_description') || q.get('error') || 'Login cancelled'));
    };
    // Account pages (Forgot password, create account, security info) need a full browser: open them there.
    // The links Microsoft gives us carry this window's sign-in session; opened in another browser that session is
    // missing and the reset page shows no ways to get a code. So open Microsoft's clean pages instead.
    const external = (url) => /account\.live\.com|account\.microsoft\.com|signup\.live\.com|passwordreset|ResetPassword|support\.microsoft/i.test(url);
    const clean = (url) => {
      if (/passwordreset|ResetPassword|password\/reset|iforgot/i.test(url)) return 'https://account.live.com/password/reset';
      if (/signup\.live\.com|CreateAccount/i.test(url)) return 'https://signup.live.com/';
      if (/recover|acsr/i.test(url)) return 'https://account.live.com/acsr';
      return url;
    };
    const openOutside = (url) => shell.openExternal(clean(url));
    win.webContents.setWindowOpenHandler(({ url }) => { openOutside(url); return { action: 'deny' }; });
    win.webContents.on('will-navigate', (e, url) => { if (external(url)) { e.preventDefault(); openOutside(url); } });
    win.webContents.on('will-redirect', (e, url) => { if (external(url) && !url.startsWith(REDIRECT)) { e.preventDefault(); openOutside(url); } });
    win.webContents.on('will-redirect', (_e, url) => check(url));
    win.webContents.on('did-navigate', (_e, url) => check(url));
    win.on('closed', () => { if (!finished) reject(new Error('Login window closed')); });
    const url = new URL('https://login.live.com/oauth20_authorize.srf');
    url.search = new URLSearchParams({ client_id: CLIENT_ID, response_type: 'code', redirect_uri: REDIRECT, scope: SCOPE, prompt: 'select_account' });
    win.loadURL(url.toString());
  });
}

async function msToken(params) {
  return fetchJson('https://login.live.com/oauth20_token.srf', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: CLIENT_ID, redirect_uri: REDIRECT, scope: SCOPE, ...params }).toString(),
  });
}

async function minecraftFromMs(msAccessToken) {
  const json = { 'Content-Type': 'application/json', Accept: 'application/json' };
  const xbl = await fetchJson('https://user.auth.xboxlive.com/user/authenticate', {
    method: 'POST', headers: json,
    body: JSON.stringify({ Properties: { AuthMethod: 'RPS', SiteName: 'user.auth.xboxlive.com', RpsTicket: msAccessToken }, RelyingParty: 'http://auth.xboxlive.com', TokenType: 'JWT' }),
  });
  let xsts;
  try {
    xsts = await fetchJson('https://xsts.auth.xboxlive.com/xsts/authorize', {
      method: 'POST', headers: json,
      body: JSON.stringify({ Properties: { SandboxId: 'RETAIL', UserTokens: [xbl.Token] }, RelyingParty: 'rp://api.minecraftservices.com/', TokenType: 'JWT' }),
    });
  } catch (e) {
    if (String(e.message).includes('2148916233')) throw new Error('This Microsoft account has no Xbox profile. Sign in once at minecraft.net first.');
    if (String(e.message).includes('2148916238')) throw new Error('Child account — it must be added to a Microsoft Family to play.');
    throw e;
  }
  const uhs = xbl.DisplayClaims.xui[0].uhs;
  const mc = await fetchJson('https://api.minecraftservices.com/authentication/login_with_xbox', {
    method: 'POST', headers: json, body: JSON.stringify({ identityToken: `XBL3.0 x=${uhs};${xsts.Token}` }),
  });
  let profile;
  try {
    profile = await fetchJson('https://api.minecraftservices.com/minecraft/profile', { headers: { Authorization: 'Bearer ' + mc.access_token } });
  } catch (e) {
    if (e.status === 404) throw new Error("This account doesn't own Minecraft: Java Edition.");
    throw e;
  }
  return { mcToken: mc.access_token, mcExpires: Date.now() + (mc.expires_in - 60) * 1000, profile };
}

async function addMicrosoft(parent) {
  const code = await promptForCode(parent);
  const ms = await msToken({ code, grant_type: 'authorization_code' });
  const mc = await minecraftFromMs(ms.access_token);
  saveAccount({
    id: mc.profile.id, name: mc.profile.name, type: 'msa',
    msRefresh: enc(ms.refresh_token), mcToken: enc(mc.mcToken), mcExpires: mc.mcExpires,
  });
  return list();
}

function addOffline(name) {
  if (!store.get('accounts').some((a) => a.type === 'msa')) throw new Error('Add a Microsoft account that owns Minecraft before adding offline accounts.');
  if (!/^[A-Za-z0-9_]{3,16}$/.test(name)) throw new Error('Name must be 3-16 letters, digits or _');
  const id = crypto.createHash('md5').update('OfflinePlayer:' + name).digest('hex');
  saveAccount({ id, name, type: 'offline' });
  return list();
}

function remove(id) {
  const all = store.get('accounts').filter((a) => a.id !== id);
  store.patch({ accounts: all, selected: store.get('selected') === id ? all[0]?.id ?? null : store.get('selected') });
  return list();
}
function select(id) { store.set('selected', id); return list(); }

/** Returns a fresh Minecraft access token for an account (refreshing if needed). */
async function ensureToken(id) {
  const a = getRaw(id);
  if (!a) throw new Error('No account selected');
  if (a.type !== 'msa') return { ...a, token: '0' };
  if (a.mcToken && a.mcExpires > Date.now()) return { ...a, token: dec(a.mcToken) };
  const ms = await msToken({ refresh_token: dec(a.msRefresh), grant_type: 'refresh_token' });
  const mc = await minecraftFromMs(ms.access_token);
  const updated = { ...a, name: mc.profile.name, msRefresh: enc(ms.refresh_token), mcToken: enc(mc.mcToken), mcExpires: mc.mcExpires };
  const all = store.get('accounts').map((x) => (x.id === id ? updated : x));
  store.set('accounts', all);
  return { ...updated, token: mc.mcToken };
}

async function profile(id) {
  const a = await ensureToken(id);
  if (a.type !== 'msa') return { id: a.id, name: a.name, skins: [], capes: [], offline: true };
  return fetchJson('https://api.minecraftservices.com/minecraft/profile', { headers: { Authorization: 'Bearer ' + a.token } });
}

module.exports = { list, selected, addMicrosoft, addOffline, remove, select, ensureToken, profile };
