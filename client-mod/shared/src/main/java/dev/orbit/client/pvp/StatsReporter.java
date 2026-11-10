package dev.orbit.client.pvp;

import com.google.gson.JsonObject;
import dev.orbit.client.emote.EmoteSync;

/**
 * Sends your PvP numbers (kills, deaths, hits …) to the Orbit server for your player profile on orbit-client.com:
 * only what changed since the last report, every 30 s and when you leave a world. The server keeps them only for
 * signed-in players, and only shows them if your profile is public.
 */
public final class StatsReporter {
    private StatsReporter() {}

    private static int hits, misses, kills, deaths, wtaps, combo;
    private static float dealt, taken;
    private static long next;

    public static void tick() {
        long now = System.currentTimeMillis();
        if (now < next) return;
        next = now + 30_000;
        report();
    }

    /** now (e.g. when leaving a server) */
    public static void report() {
        if (!EmoteSync.connected()) return;
        // the session counters start again from 0 when you join a world: then everything counts as new
        int h = Combat.hits, m = Combat.misses, k = Combat.kills, d = Combat.deaths, w = Combat.wtaps, c = Combat.bestCombo;
        float dd = Combat.damageDealt, dt = Combat.damageTaken;
        if (h < hits || m < misses || k < kills || d < deaths || w < wtaps || dd < dealt || dt < taken) { hits = misses = kills = deaths = wtaps = combo = 0; dealt = taken = 0; }
        JsonObject o = new JsonObject();
        boolean any = false;
        if (h > hits) { o.addProperty("hits", h - hits); any = true; }
        if (m > misses) { o.addProperty("misses", m - misses); any = true; }
        if (k > kills) { o.addProperty("kills", k - kills); any = true; }
        if (d > deaths) { o.addProperty("deaths", d - deaths); any = true; }
        if (w > wtaps) { o.addProperty("wtaps", w - wtaps); any = true; }
        if (dd - dealt >= 1) { o.addProperty("dealt", (int) (dd - dealt)); any = true; }
        if (dt - taken >= 1) { o.addProperty("taken", (int) (dt - taken)); any = true; }
        if (c > combo) { o.addProperty("bestCombo", c); any = true; }
        if (!any || !EmoteSync.sendStats(o)) return;
        hits = h; misses = m; kills = k; deaths = d; wtaps = w; combo = c;
        if (dd - dealt >= 1) dealt = dd;
        if (dt - taken >= 1) taken = dt;
    }
}
