package dev.orbit.client.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.orbit.client.module.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    /** Tabs in the Orbit menu, in display order. */
    public enum Category {
        RENDER("Render", "See the world in a different light"),
        COMBAT("Combat", "Everything you need in a fight"),
        HUD("HUD", "Information on your screen"),
        PLAYER("Player", "Movement, camera & emotes"),
        CLIENT("Settings", "Orbit itself: theme, menus & keys");
        public final String title, subtitle;
        Category(String title, String subtitle) { this.title = title; this.subtitle = subtitle; }
    }

    public final String id;
    public final String name;
    public final String description;
    public Category category;
    private boolean enabled;
    private boolean favourite;
    private final boolean toggleable;
    private Item icon = Items.PAPER;
    protected final List<Setting<?>> settings = new ArrayList<>();
    protected final MinecraftClient mc = MinecraftClient.getInstance();

    protected Module(String id, String name, String description, Category category, boolean enabledByDefault) {
        this(id, name, description, category, enabledByDefault, true);
    }

    protected Module(String id, String name, String description, Category category, boolean enabledByDefault, boolean toggleable) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = enabledByDefault || !toggleable;
        this.toggleable = toggleable;
    }

    protected <S extends Setting<?>> S add(S s) { settings.add(s); return s; }
    public List<Setting<?>> settings() { return settings; }

    /** Item drawn as this module's icon in the menu. */
    public Item icon() { return icon; }
    public Module icon(Item item) { icon = item; return this; }
    public Module in(Category c) { category = c; return this; }

    public boolean isFavourite() { return favourite; }
    public void setFavourite(boolean f) { favourite = f; }

    public boolean isEnabled() { return enabled; }
    public boolean isToggleable() { return toggleable; }
    public void toggle() { setEnabled(!enabled); }
    public void setEnabled(boolean on) {
        if (!toggleable || on == enabled) return;
        enabled = on;
        if (on) onEnable(); else onDisable();
    }

    protected void onEnable() {}
    protected void onDisable() {}
    public void tick() {}

    public JsonObject save() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", enabled);
        o.addProperty("favourite", favourite);
        JsonObject s = new JsonObject();
        for (Setting<?> st : settings) { JsonElement e = st.save(); if (e != null) s.add(st.id, e); }
        o.add("settings", s);
        return o;
    }

    public void load(JsonObject o) {
        if (o.has("enabled") && toggleable) enabled = o.get("enabled").getAsBoolean();
        if (o.has("favourite")) favourite = o.get("favourite").getAsBoolean();
        if (o.has("settings")) {
            JsonObject s = o.getAsJsonObject("settings");
            for (Setting<?> st : settings) {
                JsonElement e = s.get(st.id);
                if (e != null) try { st.load(e); } catch (Exception ignored) { }
            }
        }
    }
}
