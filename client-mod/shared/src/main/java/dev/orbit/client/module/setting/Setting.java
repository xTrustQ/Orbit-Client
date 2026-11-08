package dev.orbit.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.function.BooleanSupplier;

/** A typed, serialisable module option. The menu builds its controls from these automatically. */
public abstract class Setting<T> {
    public final String id;
    public final String name;
    protected T value;
    protected final T def;
    private BooleanSupplier visible = () -> true;

    protected Setting(String id, String name, T def) {
        this.id = id;
        this.name = name;
        this.def = def;
        this.value = def;
    }

    public T get() { return value; }
    public void set(T v) { value = v; }
    public void reset() { value = def; }
    public boolean isVisible() { return visible.getAsBoolean(); }
    public Setting<T> visibleWhen(BooleanSupplier s) { visible = s; return this; }

    public abstract JsonElement save();
    public abstract void load(JsonElement e);

    // ------------------------------------------------------------------ types
    public static final class Bool extends Setting<Boolean> {
        public Bool(String id, String name, boolean def) { super(id, name, def); }
        public boolean on() { return value; }
        public void toggle() { value = !value; }
        @Override public JsonElement save() { return new JsonPrimitive(value); }
        @Override public void load(JsonElement e) { value = e.getAsBoolean(); }
    }

    public static final class Slider extends Setting<Double> {
        public final double min, max, step;
        public final String suffix;
        public Slider(String id, String name, double def, double min, double max, double step, String suffix) {
            super(id, name, def); this.min = min; this.max = max; this.step = step; this.suffix = suffix;
        }
        public float f() { return value.floatValue(); }
        public int i() { return (int) Math.round(value); }
        @Override public void set(Double v) {
            double s = Math.round((v - min) / step) * step + min;
            value = Math.max(min, Math.min(max, s));
        }
        public double fraction() { return (value - min) / (max - min); }
        public String display() {
            return (step >= 1 ? String.valueOf((int) Math.round(value)) : String.format(java.util.Locale.ROOT, "%.2f", value)) + suffix;
        }
        @Override public JsonElement save() { return new JsonPrimitive(value); }
        @Override public void load(JsonElement e) { set(e.getAsDouble()); }
    }

    /** ARGB colour. */
    public static final class Color extends Setting<Integer> {
        public Color(String id, String name, int argb) { super(id, name, argb); }
        public int argb() { return value; }
        public int rgb() { return value & 0xFFFFFF; }
        @Override public JsonElement save() { return new JsonPrimitive(String.format("#%08X", value)); }
        @Override public void load(JsonElement e) { value = (int) Long.parseLong(e.getAsString().replace("#", ""), 16); }
    }

    public static final class Mode extends Setting<String> {
        public final List<String> options;
        public Mode(String id, String name, String def, String... options) { super(id, name, def); this.options = List.of(options); }
        public boolean is(String s) { return value.equals(s); }
        public void cycle(int dir) {
            int i = options.indexOf(value);
            value = options.get(Math.floorMod(i + dir, options.size()));
        }
        @Override public JsonElement save() { return new JsonPrimitive(value); }
        @Override public void load(JsonElement e) { String s = e.getAsString(); if (options.contains(s)) value = s; }
    }

    /** Rebinds a Minecraft key binding from the Orbit menu (stored by Minecraft's own options.txt). */
    public static final class Key extends Setting<Integer> {
        public final java.util.function.Supplier<net.minecraft.client.option.KeyBinding> binding;
        public Key(String id, String name, java.util.function.Supplier<net.minecraft.client.option.KeyBinding> binding) { super(id, name, 0); this.binding = binding; }
        @Override public JsonElement save() { return null; }
        @Override public void load(JsonElement e) {}
        @Override public void reset() {
            var kb = binding.get();
            dev.orbit.client.compat.Compat.rebind(kb, kb.getDefaultKey().getCode());
        }
    }

    /** A button that runs an action (e.g. open the crosshair painter). */
    public static final class Action extends Setting<Runnable> {
        public final String label;
        public Action(String id, String name, String label, Runnable run) { super(id, name, run); this.label = label; }
        @Override public JsonElement save() { return null; }
        @Override public void load(JsonElement e) {}
        @Override public void reset() {}
    }

    /** A square pixel grid (custom crosshair), stored as rows of 0/1. */
    public static final class Pixels extends Setting<boolean[]> {
        public final int size;
        public Pixels(String id, String name, int size, boolean[] def) { super(id, name, def.clone()); this.size = size; }
        public boolean get(int x, int y) { return value[y * size + x]; }
        public void set(int x, int y, boolean on) { value[y * size + x] = on; }
        public void clear() { java.util.Arrays.fill(value, false); }
        @Override public void reset() { value = def.clone(); }
        @Override public JsonElement save() {
            StringBuilder b = new StringBuilder();
            for (boolean v : value) b.append(v ? '1' : '0');
            return new JsonPrimitive(b.toString());
        }
        @Override public void load(JsonElement e) {
            String s = e.getAsString();
            if (s.length() != size * size) return;
            for (int i = 0; i < s.length(); i++) value[i] = s.charAt(i) == '1';
        }
    }
}
