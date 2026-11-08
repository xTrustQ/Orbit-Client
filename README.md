# Orbit

A space-themed Minecraft client in two parts:

| Part | What it is |
|---|---|
| `Orbit.exe` | Double-click to open the launcher (no install needed) |
| `launcher/` | Electron launcher source: profiles, Modrinth + CurseForge browser, skins, capes, account switcher, Spotify connect, 7 space themes |
| `client-mod/` | In-game Fabric mod for **Minecraft 1.20–1.20.1, 1.21–1.21.1, 1.21.4 and 1.21.11**: space menus, 31 mods in Render / Combat / HUD / Player tabs, emotes, in-game editor |
| `orbit-server/` | Small sync server you host so Orbit players see each other's emotes |

## Features

**Launcher**
- **Space design:** live starfield with shooting stars, nebula clouds, a floating glass command bar and warp page transitions. Themes: Nebula, Deep Space, Supernova, Aurora, Mars, Eclipse, Lunar. Fonts: Orbitron, clean or pixel.
- **Launch pad:** your 3D skin on glowing orbit rings, a profile picker and a one-click launch.
- **Profiles:** Vanilla / Fabric / Quilt for any version. New Fabric/Quilt profiles get:
  - the **FPS boost pack**: Sodium, Lithium, FerriteCore, EntityCulling, ImmediatelyFast, ModernFix, MoreCulling, Dynamic FPS, Krypton
  - the **standard mods**: Simple Voice Chat and TierTagger
  - the Orbit mod
- **Mods:** search Modrinth/CurseForge for mods, modpacks, resource packs, shaders and datapacks.
- **Skins & capes:** a skin library, official Minecraft capes, and 15 free Orbit capes, or upload any image as a cape.
- **Accounts:** multiple Microsoft accounts with a one-click switcher.
- **Spotify:** connect once in Settings → Spotify lyrics.
- **Store:** spend Orbit Coins on 6 premium capes and 6 premium emotes. Buying coins with real money is **coming soon**: payments aren't connected yet, so nothing is ever charged. The owner account (TrustQ, checked against Minecraft's API) has unlimited coins, owns everything and can add its own capes to the store on that PC.
- **Discord status:** shows "Playing Orbit Client" with your version and profile. You need your own Discord application ID; Settings → Integrations has the steps.

**In-game**
- **Orbit menus:** clean space-themed **title**, **Singleplayer** (world icons, search, play) and **Multiplayer** (server icons, MOTD, players, ping) screens. **Sky:** only changes the menu background, and **Vanilla menu** opens the normal title screen.
- **Right Shift** opens the Orbit menu: smooth Montserrat font and anti-aliased corners, a tab bar (Render, Combat, HUD, Player, Settings), search, All / On / Favourites filters and a card grid. Turn the smooth font off under Settings → Interface. Every key (zoom, freelook, emotes, the menu itself) can be rebound right there. Every HUD element has size, colour, rainbow, shadow, background colour/opacity, rounded corners, border and brackets, and Edit HUD lets you drag and resize them.
- **Render:** Sky Changer (off until you turn it on), Fullbright, No Fog (Nether/End too), Time Changer (only for you), Motion Blur, Own Nametag in third person.
- **Combat:** Armor Status, Potion timers, CPS, Keystrokes, Crosshair (5 styles + a 15×15 pixel painter), No Hurt Cam, Saturation (AppleSkin-style: gold saturation outline, flashing preview of the food in your hand, exhaustion bar).
- **HUD:** FPS, Coordinates, Direction, Ping, Clock, Memory, Speed, Server, Day, Scoreboard (resize / move / background / hide), Spotify card (cover, progress, works with Cyrillic and other scripts; open chat to click previous / pause / next, needs Spotify Premium), Spotify sky lyrics (big lines alternating left and right with a karaoke fill).
- **Player:** Zoom, Freelook (hold or toggle Left Alt), Emotes, Toggle Sprint, Orbit capes.
- **Emotes:** press **B** for the emote wheel. 8 free emotes (Wave, Clap, Cheer, Salute, Point, Bow, Facepalm, Shrug) and 6 store emotes. Other Orbit players see them once you host `orbit-server` and set its address in Settings → Integrations.
- **Spotify Lyrics:** synced lyrics of the song you're playing, shown big in the sky above your crosshair (or as a card). Lyrics come from LRCLIB.
- **Sky changer:** Aurora, Nebula, Synthwave, Blood Moon, Deep Space, Cotton Candy, Golden Hour, Void.

## Spotify setup (one time)
Spotify only lets apps read "now playing" through your own free developer app:
1. Go to developer.spotify.com/dashboard and click **Create app**, choosing **Web API**.
2. Add the redirect URI `http://127.0.0.1:43821/callback`.
3. Paste the app's **Client ID** in Orbit → Settings → Spotify lyrics and click **Connect**.

## Building from source
- Launcher: `cd launcher`, `npm install`, `npm start`. Run `npx electron-builder --win` to create `dist/Orbit.exe`.
- Mod (JDK 21): `client-mod\build-all.bat` builds every version into `launcher/resources/orbit-<version>.jar`.
- Layout: `client-mod/shared/` is the common code. `client-mod/versions/<mc>/` holds only the files that differ for that version (`Compat`, `OrbitBaseScreen`, the sky renderer and some mixins).
- Tests: `gradlew runClientGameTest` (1.21.4 / 1.21.11), or set `ORBIT_AUTOTEST=<dir>` and run `gradlew runClient` (1.20.1 / 1.21.1). Both create a world and screenshot every feature.

## Auto-updates (GitHub releases)

1. Put the project in a GitHub repo, e.g. `yourfriend/orbit`.
2. Set `launcher/package.json` → `"orbit": { "updateRepo": "yourfriend/orbit" }` (builds from the release
   workflow fill this in automatically), or have each player enter it in **Settings → About → Update repository**.
3. To publish an update, push a version tag higher than the current one:
   ```bash
   git tag v1.3.0
   git push origin v1.3.0
   ```
   `.github/workflows/release.yml` builds the mod and the launcher on Windows and attaches `Orbit.exe` to the release.
   You can also make a release by hand and upload an `Orbit.exe` to it.
4. Each launcher checks on start, shows the changelog and, after **Update now**, downloads the new exe,
   replaces itself and restarts.

## Shared cosmetics

Host `orbit-server` (see `orbit-server/README.md`) and put its address in `"orbit": { "syncServer": "wss://…" }`.
Then all Orbit players see each other's hats, wings, auras and Orbit capes, plus the Orbit badge on name tags.
In-game toggle: **Cosmetics → Show other Orbit players' cosmetics**.

## Notes
- Simple Voice Chat only works on servers that also run it.
- Forge/NeoForge profiles aren't supported. Use Fabric or Quilt.
- CurseForge needs a free API key (Settings → Integrations).
- Orbit capes are client-side: only you see them.
- Data lives in `%APPDATA%\OrbitClient`. Data from the old Nova name is moved there automatically.
