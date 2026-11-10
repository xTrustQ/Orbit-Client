# Orbit sync server

This server lets Orbit players see each other's emotes. Each player's Orbit mod connects to it, and it forwards an emote to everyone on the **same Minecraft server**. Players only see emotes from people in the same game.

## Run it

```bash
npm install
npm start
```

It listens on port `8787`, or on `PORT` if you set it.

## Make it public

1. Host it on any Node host (Railway, Render, Fly.io, a VPS…).
2. Give it HTTPS so it's reachable as `wss://your-domain`.
3. Paste that address in **Orbit launcher → Settings → Integrations → Orbit sync server**.

Every Orbit player has to use the same address, so in practice you set it as the default before handing out Orbit:
put it in `launcher/package.json` → `"orbit": { "syncServer": "wss://your-domain" }` and every new launcher uses it.

### Example: Render (free)
1. Push this repo to GitHub, then on render.com → **New → Web Service** → pick the repo.
2. Root directory `orbit-server`, build command `npm install`, start command `npm start`.
3. Add a **Disk** (e.g. mounted at `/data`) and set the environment variable `DATA_DIR=/data` — otherwise
   everyone's cosmetics are forgotten whenever the service restarts.
4. Render gives you `https://orbit-xyz.onrender.com` → the sync address is `wss://orbit-xyz.onrender.com`.

## Keep data across restarts (needed for Settings sync on Render's free plan)

Render's free plan wipes the disk whenever the server sleeps or restarts. Cosmetics don't mind (every game
re-sends them), but **Settings sync** needs real storage. Free fix, 2 minutes:

1. Create a free database at **upstash.com** → *Redis* → *Create database* (any region near your Render region).
2. On the database page copy **UPSTASH_REDIS_REST_URL** and **UPSTASH_REDIS_REST_TOKEN**.
3. Render → your Orbit server → **Environment** → add both as environment variables → *Save, rebuild and deploy*.

Without them, settings are kept only until the server restarts.

## Player profiles (orbit-client.com/players.html)
- A game that is signed in (the same Mojang-verified login as cosmetics) sends its login token with the live
  connection; only then does the server keep a profile: playtime (only while in a world/server), sessions,
  first/last seen and PvP stats the game reports every 30 s (`stats` message, small deltas, capped).
- Players can turn their profile private in the Orbit menu (Interface → Public player profile).
- `GET /api/player/<name>` → profile (or `{private:true}` / 404), `GET /api/leaderboard` → top 10 by playtime.
- **Profiles need the Upstash store above** on Render's free plan — otherwise they reset whenever the server sleeps.

## Friends 2.0 & statistics
- Each game tells the server which Minecraft server it's on; the server shows that **only** to players the person
  added as a friend themselves (and only if "Show friends which server I'm on" is on).
- Invites (`invite`) are delivered only if the receiver has the sender as a friend — no spam from strangers.
- `GET /api/stats` → `{online, inGame, peak, players}` — used by the website's live counter.

## Cosmetics API

The same server (same port) also hosts the **cosmetics API**: every Orbit player publishes what they wear —
hat, wings, aura and their Orbit cape — and fetches what the players around them wear. So Orbit players see
each other's cosmetics on any Minecraft server. The mod uses the sync server's address automatically
(`wss://x` → `https://x`), or a separate one from **Settings → Integrations → Cosmetics API**.

| Method | Path | What |
|---|---|---|
| `POST` | `/api/auth/start` `{uuid, name}` | returns `{serverId}` |
| `POST` | `/api/auth/finish` `{name, serverId}` | asks Mojang whether that account "joined" `serverId`, returns `{token, uuid}` (valid 30 days) |
| `PUT` | `/api/cosmetics` (`Authorization: Bearer <token>`) `{hat, wings, aura, cape}` | saves your cosmetics; `cape` is a base64 PNG (≤ 400 KB) |
| `GET` | `/api/cosmetics?uuids=a,b,c` | `{uuid: {hat, wings, aura, cape, updated}}` for up to 120 players |
| `GET` | `/api/capes/<sha1>.png` | a cape image |

**Nobody can change someone else's cosmetics.** Sign-in works like joining a Minecraft server: the game tells
Mojang "I'm joining server X" with your real session, and the API checks with Mojang that it really was you.
Your Minecraft access token never leaves your PC. Offline/cracked accounts can see others' cosmetics but can't publish their own.

Data lives in `DATA_DIR` (default `orbit-server/data`): `cosmetics.json`, `tokens.json` and `capes/`.

## What it accepts
- **Message types:** only `hello` (who you are and which server you're on), `emote` (one of the 14 known emotes) and `stop`.
- **Limits:** messages are capped at 1 KB, with about 2 per second per player.
- **No accounts:** player UUIDs aren't verified, so someone could fake another player's emote. That's harmless for cosmetics, but don't rely on it for anything important.
