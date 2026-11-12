package dev.orbit.client.module;

import dev.orbit.client.module.setting.Setting;

/**
 * Tier Tags: PvP tiers next to players' names (name tags and the Tab list), from MCTiers, PvPTiers and/or SubTiers.
 * Replaces the separate TierTagger mod. Looked up by name, cached for a while (dev.orbit.client.pvp.TierTags).
 */
public final class TierTagsModule extends Module {
    public static final String[] MODES = {"Best", "Sword", "Axe", "UHC", "Crystal", "Pot", "NethOP", "SMP", "Mace"};
    public final Setting.Bool mctiers = add(new Setting.Bool("mctiers", "MCTiers (mctiers.io)", true));
    public final Setting.Bool pvptiers = add(new Setting.Bool("pvptiers", "PvPTiers (pvptiers.com)", false));
    public final Setting.Bool subtiers = add(new Setting.Bool("subtiers", "SubTiers (subtiers.net)", true));
    public final Setting.Mode mode = add(new Setting.Mode("mode", "Gamemode (MCTiers / PvPTiers)", "Best", MODES));
    public final Setting.Bool showMode = add(new Setting.Bool("showMode", "Show the gamemode name", true));
    public final Setting.Bool nametags = add(new Setting.Bool("nametags", "On name tags", true));
    public final Setting.Bool tablist = add(new Setting.Bool("tablist", "In the Tab list", true));
    public final Setting.Bool retired = add(new Setting.Bool("retired", "Show retired tiers", true));
    public final Setting.Bool self = add(new Setting.Bool("self", "Show my own tier", true));

    public TierTagsModule() { super("tiertags", "Tier Tags", "PvP tiers next to names — MCTiers, PvPTiers and SubTiers, pick what to show", Category.COMBAT, true); }
}
