// Discord Rich Presence over Discord's local IPC pipe (no extra packages).
// Discord shows "Playing <your Discord application's name>" — name the application "Orbit Client".
const net = require('net');

let sock = null, ready = false, clientId = null, current = null, retry = null;

function frame(op, data) {
  const json = Buffer.from(JSON.stringify(data));
  const head = Buffer.alloc(8);
  head.writeInt32LE(op, 0);
  head.writeInt32LE(json.length, 4);
  return Buffer.concat([head, json]);
}

function pipePath(i) { return process.platform === 'win32' ? `\\\\?\\pipe\\discord-ipc-${i}` : `${process.env.XDG_RUNTIME_DIR || process.env.TMPDIR || '/tmp'}/discord-ipc-${i}`; }

function connect(i = 0) {
  if (!clientId || sock) return;
  const s = net.createConnection(pipePath(i));
  s.once('connect', () => {
    sock = s;
    s.write(frame(0, { v: 1, client_id: clientId }));
  });
  s.on('data', (buf) => {
    try {
      const msg = JSON.parse(buf.subarray(8).toString());
      if (msg.evt === 'READY') { ready = true; push(); }
    } catch { /* partial frame, ignore */ }
  });
  s.on('error', () => { s.destroy(); if (!sock && i < 9) connect(i + 1); else schedule(); });
  s.on('close', () => { sock = null; ready = false; schedule(); });
}

function schedule() {
  if (retry || !clientId) return;
  retry = setTimeout(() => { retry = null; connect(0); }, 15000);
}

function push() {
  if (!sock || !ready) return;
  sock.write(frame(1, { cmd: 'SET_ACTIVITY', args: { pid: process.pid, activity: current }, nonce: String(Date.now()) }));
}

/** Enable/disable with an application ID from discord.com/developers. */
function configure(id) {
  const next = id && /^\d{15,22}$/.test(id) ? id : null;
  if (next === clientId) return;
  clientId = next;
  if (sock) { sock.destroy(); sock = null; ready = false; }
  if (clientId) connect(0);
}

function setActivity(a) {
  current = a ? {
    details: a.details, state: a.state,
    timestamps: a.start ? { start: Math.floor(a.start / 1000) } : undefined,
    assets: { large_image: 'orbit', large_text: 'Orbit Client', small_image: a.small || undefined, small_text: a.smallText || undefined },
    buttons: undefined,
  } : null;
  push();
}

module.exports = { configure, setActivity };
