package dev.orbit.client.emote;

import com.google.gson.Gson;
import dev.orbit.client.OrbitClient;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Which emotes sit on the wheel: 3 pages of 6 slots, edited in Locker → Emotes (saved in config/orbit-emote-wheel.json). */
public final class EmoteWheel {
    private EmoteWheel() {}

    public static final int PAGES = 3, SLOTS = 6;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("orbit-emote-wheel.json");
    private static String[][] pages;

    private static String[][] data() {
        if (pages != null) return pages;
        try { if (Files.exists(FILE)) pages = new Gson().fromJson(Files.readString(FILE), String[][].class); }
        catch (Exception e) { OrbitClient.LOG.warn("Could not read emote wheel", e); }
        if (pages == null || pages.length != PAGES) {
            pages = new String[PAGES][SLOTS];
            Emote[] all = Emote.values();
            for (int i = 0; i < Math.min(all.length, PAGES * SLOTS); i++) pages[i / SLOTS][i % SLOTS] = all[i].name();
        }
        for (int p = 0; p < PAGES; p++) if (pages[p] == null || pages[p].length != SLOTS) pages[p] = java.util.Arrays.copyOf(pages[p] == null ? new String[0] : pages[p], SLOTS);
        if (!checkedNew) { checkedNew = true; addNewEmotes(); }
        return pages;
    }

    /** emotes added in an update go into free wheel slots once (config/orbit-emote-known.json remembers which we've seen) */
    private static boolean checkedNew;
    private static void addNewEmotes() {
        Path known = FILE.resolveSibling("orbit-emote-known.json");
        java.util.Set<String> seen = new java.util.HashSet<>();
        try { if (Files.exists(known)) seen.addAll(java.util.Arrays.asList(new Gson().fromJson(Files.readString(known), String[].class))); }
        catch (Exception ignored) { }
        boolean first = seen.isEmpty(), changed = false;
        for (Emote e : Emote.values()) {
            if (seen.contains(e.name())) continue;
            seen.add(e.name());
            if (first && !e.hasProp()) continue; // first run: only the new prop emotes are "new"
            boolean placed = false;
            for (String[] pg : pages) for (String n : pg) if (e.name().equals(n)) placed = true;
            for (int p = 0; p < PAGES && !placed; p++) for (int s = 0; s < SLOTS && !placed; s++)
                if (pages[p][s] == null) { pages[p][s] = e.name(); placed = true; changed = true; }
        }
        try { Files.createDirectories(known.getParent()); Files.writeString(known, new Gson().toJson(seen.toArray(new String[0]))); } catch (Exception ignored) { }
        if (changed) save();
    }

    private static void save() {
        try { Files.createDirectories(FILE.getParent()); Files.writeString(FILE, new Gson().toJson(data())); }
        catch (Exception e) { OrbitClient.LOG.warn("Could not save emote wheel", e); }
    }

    public static Emote get(int page, int slot) {
        String n = data()[page][slot];
        if (n == null) return null;
        try { return Emote.valueOf(n); } catch (IllegalArgumentException e) { return null; }
    }

    /** page the emote is on, or -1 */
    public static int pageOf(Emote e) {
        for (int p = 0; p < PAGES; p++) for (int s = 0; s < SLOTS; s++) if (e.name().equals(data()[p][s])) return p;
        return -1;
    }

    /** puts the emote in the first free slot of the page; false when the page is full */
    public static boolean add(int page, Emote e) {
        if (pageOf(e) >= 0) remove(e);
        for (int s = 0; s < SLOTS; s++) if (get(page, s) == null) { data()[page][s] = e.name(); save(); return true; }
        return false;
    }

    public static void clear(int page, int slot) { data()[page][slot] = null; save(); }

    public static void remove(Emote e) {
        for (int p = 0; p < PAGES; p++) for (int s = 0; s < SLOTS; s++) if (e.name().equals(data()[p][s])) data()[p][s] = null;
        save();
    }

    public static int count(int page) { int n = 0; for (int s = 0; s < SLOTS; s++) if (get(page, s) != null) n++; return n; }
}
