// Private local connection between the Orbit launcher and the running game (title-screen Accounts
// and Locker tabs). Listens on 127.0.0.1 only, on a random port, and every URL must contain a random
// secret that is handed to the game with -Dorbit.bridge — other programs can't use it.
const http = require('http');
const crypto = require('crypto');
const auth = require('./auth');
const cosmetics = require('./cosmetics');

let server = null, base = null, notify = () => {}, getWin = () => null;

const dashed = (id) => String(id).replace(/-/g, '').replace(/^(\w{8})(\w{4})(\w{4})(\w{4})(\w{12})$/, '$1-$2-$3-$4-$5');

const routes = {
  'GET accounts': () => auth.list().map((a) => ({ id: a.id, name: a.name, type: a.type, selected: a.selected })),
  'POST accounts/switch': async ({ id }) => {
    const a = await auth.ensureToken(String(id));
    auth.select(a.id);
    notify('accounts');
    return { name: a.name, uuid: dashed(a.id), token: a.token, type: a.type };
  },
  'POST accounts/microsoft': async () => {
    const win = getWin();
    if (win && !win.isDestroyed()) { if (win.isMinimized()) win.restore(); win.show(); win.focus(); }
    const l = await auth.addMicrosoft(win || undefined);
    notify('accounts');
    return l.map((a) => ({ id: a.id, name: a.name, type: a.type, selected: a.selected }));
  },
  'POST accounts/remove': ({ id }) => { const l = auth.remove(String(id)); notify('accounts'); return l.map((a) => ({ id: a.id, name: a.name, type: a.type, selected: a.selected })); },
  'POST accounts/offline': ({ name }) => { const l = auth.addOffline(String(name || '')); notify('accounts'); return l.map((a) => ({ id: a.id, name: a.name, type: a.type, selected: a.selected })); },

  'GET skins': () => cosmetics.listSkins().map((s) => ({ id: s.id, name: s.name, model: s.model, data: s.data })),
  'POST skins/delete': ({ id }) => { cosmetics.deleteSkin(String(id)); notify('skins'); return routes['GET skins'](); },
  'POST skins/apply': async ({ id }) => { await cosmetics.applySkin(String(id)); notify('skins'); return { ok: true }; },
  'POST skins/username': async ({ name }) => {
    const acc = auth.selected();
    if (acc && acc.type !== 'msa') throw new Error('Offline accounts already show the skin of their username — add an offline account with that name instead');
    const list = await cosmetics.importSkinFromUsername(String(name || ''));
    await cosmetics.applySkin(list[0].id);
    notify('skins');
    return { ok: true, id: list[0].id };
  },

  'GET capes': () => cosmetics.novaCapes().map((c) => ({ id: c.id, name: c.name, selected: c.selected, data: c.data })),
  'POST capes/select': ({ id }) => { cosmetics.selectOrbitCape(id || null); notify('capes'); return routes['GET capes'](); },
  // official Minecraft (Mojang) capes on the signed-in account, with their textures as data URLs
  'GET capes/mojang': async () => {
    const r = await cosmetics.mojangCapes();
    const capes = await Promise.all((r.capes || []).map(async (c) => {
      let data = null;
      try { const res = await fetch(c.url); if (res.ok) data = 'data:image/png;base64,' + Buffer.from(await res.arrayBuffer()).toString('base64'); } catch {}
      return { id: c.id, name: c.alias || 'Cape', active: !!c.active, data };
    }));
    return { offline: !!r.offline, capes };
  },
  'POST capes/mojang/select': async ({ id }) => {
    await cosmetics.setMojangCape(id || null);
    if (id) cosmetics.selectOrbitCape(null); // wearing a Minecraft cape: take the Orbit cape off so it shows
    notify('capes');
    return routes['GET capes/mojang']();
  },
};

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (c) => { data += c; if (data.length > 65536) { reject(new Error('Too large')); req.destroy(); } });
    req.on('end', () => { try { resolve(data ? JSON.parse(data) : {}); } catch { reject(new Error('Bad JSON')); } });
  });
}

function start(onChange, windowGetter) {
  if (onChange) notify = onChange;
  if (windowGetter) getWin = windowGetter;
  if (base) return Promise.resolve(base);
  const secret = crypto.randomBytes(24).toString('hex');
  server = http.createServer(async (req, res) => {
    const send = (code, body) => { res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8' }); res.end(JSON.stringify(body)); };
    const url = new URL(req.url, 'http://127.0.0.1');
    if (!url.pathname.startsWith('/' + secret + '/')) return send(404, { error: 'Not found' });
    const route = routes[req.method + ' ' + url.pathname.slice(secret.length + 2)];
    if (!route) return send(404, { error: 'Unknown request' });
    try { send(200, await route(req.method === 'POST' ? await readBody(req) : {})); }
    catch (e) { send(400, { error: e.message || String(e) }); }
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => { base = `http://127.0.0.1:${server.address().port}/${secret}/`; resolve(base); }));
}

const url = () => base;
module.exports = { start, url };
