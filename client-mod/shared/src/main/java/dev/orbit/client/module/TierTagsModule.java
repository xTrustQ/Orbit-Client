package dev.orbit.client.module;

import dev.orbit.client.module.setting.Setting;

/**
 * Tier Tags: PvP tiers next to players' names (name tags and the Tab list), from MCTiers, PvPTiers and/or SubTiers,
 * shown like "⚔ LT3 | Name" (gamemode icon, tier, a bar) — each list in front of or behind the name.
 * Replaces the separate TierTagger mod. Looked up by name, cached for a while (dev.orbit.client.pvp.TierTags).
 */
public final class TierTagsModule extends Module {
    public static final String[] MODES = {"Best", "Sword", "Axe", "UHC", "Crystal", "Pot", "NethOP", "SMP", "Mace"};
    public static final String[] SIDES = {"In front of the name", "Behind the name"};
    public final Setting.Bool mctiers = add(new Setting.Bool("mctiers", "MCTiers (mctiers.io)", true));
    public final Setting.Mode mcSide = (Setting.Mode) add(new Setting.Mode("mcSide", "MCTiers position", SIDES[0], SIDES)).visibleWhen(this.mctiers::on);
    public final Setting.Bool subtiers = add(new Setting.Bool("subtiers", "SubTiers (subtiers.net)", true));
    public final Setting.Mode subSide = (Setting.Mode) add(new Setting.Mode("subSide", "SubTiers position", SIDES[1], SIDES)).visibleWhen(this.subtiers::on);
    public final Setting.Bool pvptiers = add(new Setting.Bool("pvptiers", "PvPTiers (pvptiers.com)", false));
    public final Setting.Mode pvpSide = (Setting.Mode) add(new Setting.Mode("pvpSide", "PvPTiers position", SIDES[0], SIDES)).visibleWhen(this.pvptiers::on);
    public final Setting.Mode mode = add(new Setting.Mode("mode", "Gamemode (MCTiers / PvPTiers)", "Best", MODES));
    public final Setting.Mode modeStyle = add(new Setting.Mode("modeStyle", "Gamemode shown as", "Icon", "Icon", "Icon + name", "Name", "Hidden"));
    public final Setting.Bool region = add(new Setting.Bool("region", "Show region (EU, NA…)", false));
    public final Setting.Bool nametags = add(new Setting.Bool("nametags", "On name tags", true));
    public final Setting.Bool tablist = add(new Setting.Bool("tablist", "In the Tab list", true));
    public final Setting.Bool retired = add(new Setting.Bool("retired", "Show retired tiers", true));
    public final Setting.Bool self = add(new Setting.Bool("self", "Show my own tier", true));

    public TierTagsModule() { super("tiertags", "Tier Tags", "PvP tiers next to names — MCTiers, PvPTiers and SubTiers, in front of or behind the name", Category.COMBAT, true); }
    public boolean front(Setting.Mode side) { return side.is(SIDES[0]); }
}
