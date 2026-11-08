// Spotify login (Authorization Code + PKCE, no client secret) for the in-game lyrics module.
// The token file is handed to the game with -Dorbit.spotify; the mod refreshes tokens itself.
const http = require('http');
const crypto = require('crypto');
const path = require('path');
const fs = require('fs');
const { shell } = require('electron');
const { PATHS, readJson, writeJson, fetchJson } = require('./util');

const PORT = 43821;
const REDIRECT = `http://127.0.0.1:${PORT}/callback`;
const FILE = path.join(PATHS.root, 'spotify.json');
const SCOPES = 'user-read-currently-playing user-read-playback-state user-modify-playback-state user-read-private';
const b64url = (buf) => buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

function status() {
  const s = readJson(FILE, {});
  return { connected: !!s.refreshToken, controls: !!s.refreshToken && String(s.scope || '').includes('user-modify-playback-state'), clientId: s.clientId || '', name: s.name || '', redirectUri: REDIRECT };
}

function waitForCode(state) {
  return new Promise((resolve, reject) => {
    const server = http.createServer((req, res) => {
      const url = new URL(req.url, REDIRECT);
      if (url.pathname !== '/callback') { res.writeHead(404); res.end(); return; }
      const ok = url.searchParams.get('state') === state && url.searchParams.get('code');
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end(`<body style="background:#05030d;color:#f1ecff;font-family:system-ui;display:grid;place-items:center;height:100vh;margin:0"><div style="text-align:center"><h1 style="letter-spacing:.3em">ORBIT</h1><p>${ok ? 'Spotify connected — you can close this tab.' : 'Login failed — please try again from the launcher.'}</p></div></body>`);
      clearTimeout(timer);
      server.close();
      if (ok) resolve(url.searchParams.get('code'));
      else reject(new Error(url.searchParams.get('error') || 'Login was cancelled'));
    });
    const timer = setTimeout(() => { server.close(); reject(new Error('Timed out waiting for Spotify (3 min)')); }, 180000);
    server.on('error', (e) => reject(new Error(e.code === 'EADDRINUSE' ? `Port ${PORT} is busy — close other apps using it and retry` : e.message)));
    server.listen(PORT, '127.0.0.1');
  });
}

async function connect(clientId) {
  if (!/^[0-9a-f]{32}$/i.test(clientId || '')) throw new Error('That does not look like a Spotify Client ID (32 hex characters).');
  const verifier = b64url(crypto.randomBytes(48));
  const challenge = b64url(crypto.createHash('sha256').update(verifier).digest());
  const state = b64url(crypto.randomBytes(12));
  const auth = new URL('https://accounts.spotify.com/authorize');
  auth.search = new URLSearchParams({ client_id: clientId, response_type: 'code', redirect_uri: REDIRECT, code_challenge_method: 'S256', code_challenge: challenge, scope: SCOPES, state }).toString();
  const codePromise = waitForCode(state);
  await shell.openExternal(auth.toString());
  const code = await codePromise;
  const tok = await fetchJson('https://accounts.spotify.com/api/token', {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'authorization_code', code, redirect_uri: REDIRECT, client_id: clientId, code_verifier: verifier }).toString(),
  });
  let name = '';
  try { name = (await fetchJson('https://api.spotify.com/v1/me', { headers: { Authorization: 'Bearer ' + tok.access_token } })).display_name || ''; } catch { /* optional */ }
  writeJson(FILE, { clientId, refreshToken: tok.refresh_token, accessToken: tok.access_token, expiresAt: Date.now() + (tok.expires_in - 60) * 1000, scope: tok.scope || SCOPES, name });
  return status();
}

function disconnect() {
  const s = readJson(FILE, {});
  writeJson(FILE, { clientId: s.clientId || '' });
  return status();
}

const tokenFile = () => (fs.existsSync(FILE) ? FILE : null);

module.exports = { status, connect, disconnect, tokenFile };
