// Orbit Store: catalogue, Orbit Coins balance and owned items. The game reads the same file (-Dorbit.store)
// to unlock premium emotes; bought capes are added to the Capes page. Real-money coin purchases need a
// payment backend — until one is configured, buying coins reports "coming soon" and never charges anything.
//
// The owner account (OWNER_UUID, verified against Minecraft's profile API) has unlimited coins and can
// add its own cosmetics to the store on this PC.
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const { PATHS, readJson, writeJson } = require('./util');
const auth = require('./auth');

const FILE = path.join(PATHS.root, 'store.json');
const CUSTOM_DIR = path.join(PATHS.root, 'store-items');
const OWNER_UUID = 'ef994bbdc4f4440385066cfb15b28b38'; // TrustQ
const OWNER_COINS = 999999;

const COIN_PACKS = [
  { id: 'coins-500', coins: 500, bonus: 0, price: '€4.99' },
  { id: 'coins-1100', coins: 1000, bonus: 100, price: '€9.99', tag: 'Popular' },
  { id: 'coins-2400', coins: 2000, bonus: 400, price: '€19.99' },
  { id: 'coins-6500', coins: 5000, bonus: 1500, price: '€49.99', tag: 'Best value' },
];

const ITEMS = [
  // capes (designs live in the renderer's art.js PREMIUM_CAPES)
  { id: 'cape:supernova', type: 'cape', preset: 'supernova', name: 'Supernova', price: 600, desc: 'A star exploding in gold and violet' },
  { id: 'cape:blackhole', type: 'cape', preset: 'blackhole', name: 'Event Horizon', price: 800, desc: 'A glowing accretion disk around nothing at all' },
  { id: 'cape:galaxy', type: 'cape', preset: 'galaxy', name: 'Spiral Galaxy', price: 650, desc: 'Billions of stars, one cape' },
  { id: 'cape:comet', type: 'cape', preset: 'comet', name: 'Comet Trail', price: 450, desc: 'An icy comet streaking past' },
  { id: 'cape:solar', type: 'cape', preset: 'solar', name: 'Solar Flare', price: 550, desc: 'The surface of the sun, up close' },
  { id: 'cape:constellation', type: 'cape', preset: 'constellation', name: 'Constellation', price: 400, desc: 'Star lines on deep navy' },
  // emotes
  { id: 'emote:floss', type: 'emote', name: 'Floss', price: 400, desc: 'The classic arm swing, loops for 4 seconds', orb: ['#bef264', '#16a34a'] },
  { id: 'emote:disco', type: 'emote', name: 'Disco', price: 500, desc: 'Point to the sky, point to the floor', orb: ['#f0abfc', '#9333ea'] },
  { id: 'emote:dab', type: 'emote', name: 'Dab', price: 300, desc: 'Hit it. Once.', orb: ['#fde68a', '#ea580c'] },
  { id: 'emote:windmill', type: 'emote', name: 'Windmill', price: 350, desc: 'Both arms, full rotation, maximum chaos', orb: ['#a5f3fc', '#0284c7'] },
  { id: 'emote:zombie', type: 'emote', name: 'Zombie', price: 300, desc: 'Arms out, slow shuffle, very spooky', orb: ['#86efac', '#166534'] },
  { id: 'emote:tpose', type: 'emote', name: 'T-Pose', price: 250, desc: 'Assert dominance', orb: ['#e5e7eb', '#4b5563'] },
];
const FREE_EMOTES = ['Wave', 'Clap', 'Cheer', 'Salute', 'Point', 'Bow', 'Facepalm', 'Shrug'];

function state() {
  const s = readJson(FILE, {});
  return { coins: Number(s.coins) || 0, owned: Array.isArray(s.owned) ? s.owned : [], custom: Array.isArray(s.custom) ? s.custom : [] };
}
const save = (s) => writeJson(FILE, { coins: s.coins, owned: s.owned, custom: s.custom });

// ---------------------------------------------------------------- owner check
let verified = { id: null, ok: false };
/** True when the selected account is the owner's Microsoft account (checked with Minecraft's API once per session). */
async function isOwner() {
  const a = auth.selected();
  if (!a || a.type !== 'msa' || String(a.id).replace(/-/g, '') !== OWNER_UUID) return false;
  if (verified.id === a.id) return verified.ok;
  try {
    const p = await auth.profile(a.id);
    verified = { id: a.id, ok: String(p.id || '').replace(/-/g, '') === OWNER_UUID };
  } catch {
    return true; // offline: the account was added through a real Microsoft login, so trust it for now
  }
  return verified.ok;
}

// ---------------------------------------------------------------- catalogue
function customItems(s) {
  return s.custom.filter((c) => fs.existsSync(path.join(CUSTOM_DIR, c.file))).map((c) => ({
    id: c.id, type: 'cape', custom: true, name: c.name, price: c.price, desc: c.desc || 'Added by Orbit',
    image: 'data:image/png;base64,' + fs.readFileSync(path.join(CUSTOM_DIR, c.file)).toString('base64'),
  }));
}

async function catalogue() {
  const s = state();
  const owner = await isOwner();
  if (owner) { // the game reads `owned` from the file, so unlock everything there too
    const all = [...ITEMS.map((i) => i.id), ...s.custom.map((c) => c.id)];
    if (all.some((id) => !s.owned.includes(id))) save({ ...s, owned: [...new Set([...s.owned, ...all])] });
  }
  return {
    coins: owner ? OWNER_COINS : s.coins,
    owner,
    purchasesEnabled: false,
    packs: COIN_PACKS,
    items: [...customItems(s), ...ITEMS].map((i) => ({ ...i, owned: owner || s.owned.includes(i.id) })),
    free: FREE_EMOTES,
  };
}

/** Spend coins on an item (free for the owner). */
async function buyItem(id) {
  const s = state();
  const item = ITEMS.find((i) => i.id === id) || s.custom.find((i) => i.id === id);
  if (!item) throw new Error('Unknown item');
  if (s.owned.includes(id)) return catalogue();
  const owner = await isOwner();
  if (!owner && s.coins < item.price) { const e = new Error(`You need ${item.price - s.coins} more Orbit Coins.`); e.code = 'NOT_ENOUGH_COINS'; throw e; }
  save({ ...s, coins: owner ? s.coins : s.coins - item.price, owned: [...s.owned, id] });
  return catalogue();
}

function buyCoins() {
  const e = new Error('Buying Orbit Coins is coming soon — payments are not set up yet, nothing was charged.');
  e.code = 'COMING_SOON';
  throw e;
}

// ---------------------------------------------------------------- owner tools
async function requireOwner() { if (!(await isOwner())) throw new Error('Only the Orbit owner account can do that.'); }

/** Owner: add a cape (PNG data URL already fitted to the 64x32 cape template) to the store. */
async function addItem({ name, price, desc, png }) {
  await requireOwner();
  name = String(name || '').trim().slice(0, 32);
  price = Math.max(0, Math.min(100000, Math.round(Number(price) || 0)));
  if (!name) throw new Error('Give the cosmetic a name.');
  if (!/^data:image\/png;base64,/.test(png || '')) throw new Error('Pick an image first.');
  fs.mkdirSync(CUSTOM_DIR, { recursive: true });
  const id = 'custom:' + crypto.randomBytes(6).toString('hex');
  const file = id.slice(7) + '.png';
  fs.writeFileSync(path.join(CUSTOM_DIR, file), Buffer.from(png.replace(/^data:image\/png;base64,/, ''), 'base64'));
  const s = state();
  save({ ...s, custom: [{ id, name, price, desc: String(desc || '').slice(0, 80), file }, ...s.custom] });
  return catalogue();
}

/** Owner: remove a cosmetic they added. */
async function removeItem(id) {
  await requireOwner();
  const s = state();
  const c = s.custom.find((x) => x.id === id);
  if (c) fs.rmSync(path.join(CUSTOM_DIR, c.file), { force: true });
  save({ ...s, custom: s.custom.filter((x) => x.id !== id), owned: s.owned.filter((x) => x !== id) });
  return catalogue();
}

module.exports = { FILE, catalogue, buyItem, buyCoins, addItem, removeItem, isOwner, ITEMS };
